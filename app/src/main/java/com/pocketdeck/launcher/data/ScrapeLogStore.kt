package com.pocketdeck.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 封面与元数据请求日志存储（写入系统缓存临时目录 `cacheDir/logs/scrape.log`）。
 * 启动时读取一次磁盘日志到内存 [lines] 流，运行期新事件同步追加至内存流与文件，无需定时轮询读盘。
 */
object ScrapeLogStore {
    private const val DIR_NAME = "logs"
    private const val FILE_NAME = "scrape.log"
    private const val MAX_BYTES = 512 * 1024L
    private const val KEEP_BYTES = 320 * 1024
    private const val MAX_MEMORY_LINES = 500

    @Volatile
    private var logFile: File? = null
    private val lock = Any()
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val SECRET_QUERY = Regex("""([?&](?:sspassword|devpassword|apikey|api_key|client_secret|password|token)=)([^&]+)""", RegexOption.IGNORE_CASE)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun init(context: Context) {
        if (logFile != null) return
        synchronized(lock) {
            if (logFile == null) {
                val dir = File(context.applicationContext.cacheDir, DIR_NAME)
                dir.mkdirs()
                val file = File(dir, FILE_NAME)
                logFile = file
                if (file.isFile) {
                    _lines.value = runCatching {
                        file.readLines().asReversed().take(MAX_MEMORY_LINES)
                    }.getOrDefault(emptyList())
                }
            }
        }
    }

    fun filePath(): String = logFile?.absolutePath ?: "(uninitialized)"

    fun nodeFromUrl(urlStr: String): String = runCatching {
        val host = java.net.URL(urlStr).host.lowercase()
        when {
            "libretro" in host -> "libretro"
            "jsdelivr" in host -> "jsdelivr"
            "github" in host || "githubusercontent" in host -> "github"
            "hasheous" in host -> "hasheous"
            "arcadeitalia" in host -> "arcadeitalia"
            "wikidata" in host -> "wikidata"
            "screenscraper" in host -> "screenscraper"
            "thegamesdb" in host -> "thegamesdb"
            "igdb" in host -> "igdb"
            "twitch" in host -> "twitch_auth"
            else -> host.ifBlank { "http" }
        }
    }.getOrDefault("http")

    fun sanitize(raw: String): String = SECRET_QUERY.replace(raw) { m -> "${m.groupValues[1]}***" }

    fun log(
        category: String,
        node: String,
        action: String,
        url: String? = null,
        status: Int? = null,
        result: String? = null,
        error: String? = null,
        elapsedMs: Long? = null,
    ) {
        val file = logFile ?: return
        val ts = synchronized(lock) { timeFmt.format(Date()) }
        val parts = ArrayList<String>(6)
        parts += "[$ts] [$category] [$node] $action"
        if (status != null) parts += "HTTP $status"
        if (elapsedMs != null) parts += "${elapsedMs}ms"
        if (!url.isNullOrBlank()) parts += "url=${sanitize(url)}"
        if (!result.isNullOrBlank()) parts += "result=${sanitize(result).replace('\n', ' ').take(180)}"
        if (!error.isNullOrBlank()) parts += "error=${sanitize(error).replace('\n', ' ').take(180)}"
        val rawLine = parts.joinToString(" | ")
        val diskLine = rawLine + "\n"

        synchronized(lock) {
            _lines.value = (listOf(rawLine) + _lines.value).take(MAX_MEMORY_LINES)
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > MAX_BYTES) {
                    val bytes = file.readBytes()
                    val tailStart = (bytes.size - KEEP_BYTES).coerceAtLeast(0)
                    val firstNewline = bytes.indexOf('\n'.code.toByte(), tailStart)
                    val sliceFrom = if (firstNewline in tailStart until bytes.size) firstNewline + 1 else tailStart
                    file.writeBytes(bytes.copyOfRange(sliceFrom, bytes.size))
                }
                file.appendText(diskLine)
            }
        }
    }

    private fun ByteArray.indexOf(target: Byte, fromIndex: Int): Int {
        for (i in fromIndex until size) {
            if (this[i] == target) return i
        }
        return -1
    }

    /** 读取最新日志行（按时间倒序，最新在最前）。 */
    fun readRecentLines(limit: Int = 400): List<String> {
        val file = logFile ?: return emptyList()
        return synchronized(lock) {
            if (!file.isFile) return@synchronized emptyList()
            runCatching {
                file.readLines().asReversed().take(limit)
            }.getOrDefault(emptyList())
        }
    }

    /** 读取完整原始日志文本（供 Web 下载 / App 导出使用）。 */
    fun readRawText(): String {
        val file = logFile ?: return ""
        return synchronized(lock) {
            if (!file.isFile) return@synchronized ""
            runCatching { file.readText() }.getOrDefault("")
        }
    }

    /** 将日志导出到指定磁盘目录，生成带时间戳的 .log 文件。 */
    fun exportToDirectory(dir: File): Result<File> = runCatching {
        if (!dir.exists() && !dir.mkdirs()) {
            throw java.io.IOException("Cannot create directory: ${dir.absolutePath}")
        }
        if (!dir.isDirectory || !dir.canWrite()) {
            throw java.io.IOException("Directory is not writable: ${dir.absolutePath}")
        }
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var target = File(dir, "pocketdeck-scrape-$ts.log")
        var idx = 1
        while (target.exists()) {
            target = File(dir, "pocketdeck-scrape-$ts-$idx.log")
            idx++
        }
        val content = readRawText()
        target.writeText(content)
        target
    }

    fun clear() {
        val file = logFile ?: return
        synchronized(lock) {
            _lines.value = emptyList()
            runCatching {
                if (file.exists()) file.writeText("")
            }
        }
    }
}

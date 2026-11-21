package com.pocketdeck.launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 封面与元数据请求日志存储（写入系统缓存临时目录 `cacheDir/logs/scrape.log`）。
 * 采用异步非阻塞 Channel + 批量刷盘，彻底避免高并发刮削时因同步写盘/截断文件阻塞 IO 线程池。
 */
object ScrapeLogStore {
    private const val DIR_NAME = "logs"
    private const val FILE_NAME = "scrape.log"
    private const val MAX_BYTES = 512 * 1024L
    private const val KEEP_BYTES = 320 * 1024
    private const val MAX_MEMORY_LINES = 500

    @Volatile
    private var logFile: File? = null
    private val fileLock = Any()
    private val memLock = Any()
    private val ring = ArrayDeque<String>(MAX_MEMORY_LINES + 8)
    private val timeFmt = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
    private val SECRET_QUERY = Regex(
        """([?&](?:sspassword|devpassword|apikey|api_key|client_secret|password|token)=)([^&]+)""",
        RegexOption.IGNORE_CASE,
    )

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _newBatches = MutableSharedFlow<List<String>>(extraBufferCapacity = 32)
    val newBatches: SharedFlow<List<String>> = _newBatches.asSharedFlow()

    private val _cleared = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val cleared: SharedFlow<Unit> = _cleared.asSharedFlow()

    private val writeChannel = Channel<String>(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var writerStarted = false

    fun init(context: Context) {
        if (logFile != null) return
        synchronized(fileLock) {
            if (logFile == null) {
                val dir = File(context.applicationContext.cacheDir, DIR_NAME)
                dir.mkdirs()
                val file = File(dir, FILE_NAME)
                logFile = file
                if (file.isFile) {
                    val loaded = runCatching {
                        file.readLines().asReversed().take(MAX_MEMORY_LINES)
                    }.getOrDefault(emptyList())
                    synchronized(memLock) {
                        ring.clear()
                        loaded.forEach { ring.addLast(it) }
                        _lines.value = loaded
                    }
                }
            }
        }
        ensureWriterStarted()
    }

    private fun ensureWriterStarted() {
        if (writerStarted) return
        synchronized(fileLock) {
            if (writerStarted) return
            writerStarted = true
            ioScope.launch {
                val batch = ArrayList<String>(64)
                while (true) {
                    val first = writeChannel.receive()
                    batch.clear()
                    batch += first
                    // 短暂合并连续并发日志，减少磁盘与 StateFlow 刷新频次
                    delay(40L)
                    while (batch.size < 128) {
                        val next = writeChannel.tryReceive().getOrNull() ?: break
                        batch += next
                    }
                    flushBatch(batch)
                }
            }
        }
    }

    private fun flushBatch(batch: List<String>) {
        if (batch.isEmpty()) return
        synchronized(memLock) {
            for (line in batch) {
                ring.addFirst(line)
                while (ring.size > MAX_MEMORY_LINES) ring.removeLast()
            }
            _lines.value = ring.toList()
        }
        _newBatches.tryEmit(batch.toList())
        val file = logFile ?: return
        val payload = buildString(batch.size * 96) {
            for (line in batch) {
                append(line).append('\n')
            }
        }
        synchronized(fileLock) {
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > MAX_BYTES) {
                    val bytes = file.readBytes()
                    val tailStart = (bytes.size - KEEP_BYTES).coerceAtLeast(0)
                    val firstNewline = bytes.indexOf('\n'.code.toByte(), tailStart)
                    val sliceFrom = if (firstNewline in tailStart until bytes.size) firstNewline + 1 else tailStart
                    file.writeBytes(bytes.copyOfRange(sliceFrom, bytes.size))
                }
                file.appendText(payload)
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
            "gametdb" in host -> "gametdb"
            "steamgriddb" in host -> "steamgriddb"
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
        if (logFile == null) return
        val ts = timeFmt.get()?.format(Date()) ?: ""
        val parts = ArrayList<String>(6)
        parts += "[$ts] [$category] [$node] $action"
        if (status != null) parts += "HTTP $status"
        if (elapsedMs != null) parts += "${elapsedMs}ms"
        if (!url.isNullOrBlank()) parts += "url=${sanitize(url)}"
        if (!result.isNullOrBlank()) parts += "result=${sanitize(result).replace('\n', ' ').take(180)}"
        if (!error.isNullOrBlank()) parts += "error=${sanitize(error).replace('\n', ' ').take(180)}"
        writeChannel.trySend(parts.joinToString(" | "))
    }

    private fun ByteArray.indexOf(target: Byte, fromIndex: Int): Int {
        for (i in fromIndex until size) {
            if (this[i] == target) return i
        }
        return -1
    }

    /** 读取最新日志行（按时间倒序，最新在最前）。 */
    fun readRecentLines(limit: Int = 400): List<String> {
        synchronized(memLock) {
            if (ring.isNotEmpty()) return ring.take(limit)
        }
        val file = logFile ?: return emptyList()
        return synchronized(fileLock) {
            if (!file.isFile) return@synchronized emptyList()
            runCatching {
                file.readLines().asReversed().take(limit)
            }.getOrDefault(emptyList())
        }
    }

    /** 读取完整原始日志文本（供 Web 下载 / App 导出使用）。 */
    fun readRawText(): String {
        val file = logFile ?: return ""
        return synchronized(fileLock) {
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
        synchronized(memLock) {
            ring.clear()
            _lines.value = emptyList()
        }
        _cleared.tryEmit(Unit)
        val file = logFile ?: return
        ioScope.launch {
            synchronized(fileLock) {
                runCatching {
                    if (file.exists()) file.writeText("")
                }
            }
        }
    }
}

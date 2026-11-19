package com.pocketdeck.launcher.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 字体选项描述。
 * @param id 持久化键（"default" / "system_sans" / "system_serif" / "system_mono" / "file:<path>" / "custom:<name>|<path>"）
 * @param label 显示名称
 * @param isBuiltIn 是否为内置预设（非系统字体文件）
 * @param isCustom 是否为用户选择的本地字体文件
 */
data class FontOption(
    val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,
    val isCustom: Boolean = false,
)

/**
 * 读取系统已安装字体与用户自定义字体文件，并解析为 Compose [FontFamily]（进程级内存缓存 + 严格校验与缺失回退）。
 */
object SystemFonts {
    const val DEFAULT_ID = "default"
    const val SYSTEM_SANS_ID = "system_sans"
    const val SYSTEM_SERIF_ID = "system_serif"
    const val SYSTEM_MONO_ID = "system_mono"
    private const val FILE_PREFIX = "file:"
    private const val CUSTOM_PREFIX = "custom:"

    private const val MIN_FONT_BYTES = 128L
    private const val MAX_FONT_BYTES = 64L * 1024L * 1024L

    private val familyCache = ConcurrentHashMap<String, FontFamily>().apply {
        put(DEFAULT_ID, InterFamily)
        put(SYSTEM_SANS_ID, FontFamily.SansSerif)
        put(SYSTEM_SERIF_ID, FontFamily.Serif)
        put(SYSTEM_MONO_ID, FontFamily.Monospace)
    }

    private val IGNORED_PREFIXES = listOf(
        "notocoloremoji",
        "androidclock",
        "droidsansfallback",
        "samsungcolor",
    )

    private val WEIGHT_SUFFIX_REGEX = Regex(
        """-(Regular|Bold|Italic|BoldItalic|Medium|Light|Thin|Black|SemiBold|ExtraBold|ExtraLight|Book|Normal|Condensed|CondensedBold)$""",
        RegexOption.IGNORE_CASE,
    )

    /** 列出内置字体 + 系统可用字体（按家族去重并排序）。 */
    fun availableFonts(
        defaultLabel: String = "PocketDeck (Inter)",
        sansLabel: String = "System Sans-Serif",
        serifLabel: String = "System Serif",
        monoLabel: String = "System Monospace",
    ): List<FontOption> {
        val builtIn = listOf(
            FontOption(DEFAULT_ID, defaultLabel, isBuiltIn = true),
            FontOption(SYSTEM_SANS_ID, sansLabel, isBuiltIn = true),
            FontOption(SYSTEM_SERIF_ID, serifLabel, isBuiltIn = true),
            FontOption(SYSTEM_MONO_ID, monoLabel, isBuiltIn = true),
        )

        val candidates = LinkedHashMap<String, Pair<File, Int>>() // cleanName -> (file, score)
        fun considerFile(file: File?) {
            if (file == null || !file.isFile || !file.canRead()) return
            val ext = file.extension.lowercase()
            if (ext !in setOf("ttf", "otf", "ttc")) return
            val rawName = file.nameWithoutExtension
            val lower = rawName.lowercase()
            if (IGNORED_PREFIXES.any { lower.startsWith(it) }) return
            if ("emoji" in lower || "symbol" in lower || "clock" in lower) return

            val clean = rawName
                .replace(WEIGHT_SUFFIX_REGEX, "")
                .replace('_', ' ')
                .replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " ")
                .trim()
                .ifBlank { rawName }

            // 优先选 Regular 权重且非斜体/非窄体
            var score = 0
            if (lower.endsWith("-regular") || !rawName.contains('-')) score += 100
            if ("italic" in lower || "oblique" in lower) score -= 50
            if ("thin" in lower || "light" in lower || "black" in lower || "condensed" in lower) score -= 20
            if ("bold" in lower && !lower.endsWith("-regular")) score -= 30

            val existing = candidates[clean]
            if (existing == null || score > existing.second) {
                candidates[clean] = file to score
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                android.graphics.fonts.SystemFonts.getAvailableFonts().forEach { font ->
                    considerFile(font.file)
                }
            }
        }

        listOf("/system/fonts", "/product/fonts", "/system/product/fonts", "/vendor/fonts").forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { considerFile(it) }
            }
        }

        val systemList = candidates.entries
            .sortedBy { it.key.lowercase() }
            .map { (name, pair) -> FontOption("$FILE_PREFIX${pair.first.absolutePath}", name, isBuiltIn = false) }

        return builtIn + systemList
    }

    fun isCustomFont(fontId: String?): Boolean =
        !fontId.isNullOrBlank() && fontId.startsWith(CUSTOM_PREFIX)

    /** 仅从进程级内存缓存读取（不触发磁盘 I/O）；未命中返回 null。 */
    fun cachedOrNull(fontId: String?): FontFamily? {
        val id = fontId?.takeIf { it.isNotBlank() } ?: DEFAULT_ID
        return familyCache[id]
    }

    /** 检查字体文件当前是否仍可用（内置字体恒为 true，文件/自定义字体检查内存缓存或磁盘可读性与头部）。 */
    fun isFontAvailable(fontId: String?): Boolean {
        val id = fontId?.takeIf { it.isNotBlank() } ?: DEFAULT_ID
        if (id == DEFAULT_ID || id == SYSTEM_SANS_ID || id == SYSTEM_SERIF_ID || id == SYSTEM_MONO_ID) return true
        val path = extractFilePath(id) ?: return false
        val file = File(path)
        return file.isFile && file.canRead() && file.length() in MIN_FONT_BYTES..MAX_FONT_BYTES
    }

    /** 解析字体 ID 为 Compose [FontFamily]。加载成功写入系统级缓存；若文件已删除或损坏则安全回退默认字体。 */
    fun resolve(fontId: String?): FontFamily {
        val id = fontId?.takeIf { it.isNotBlank() } ?: DEFAULT_ID
        familyCache[id]?.let { return it }

        val path = extractFilePath(id) ?: return InterFamily
        val file = File(path)
        val loaded = loadValidatedFontFamily(file) ?: return InterFamily
        familyCache[id] = loaded
        return loaded
    }

    /** 安全在后台校验并加载本地字体文件用于预览（不影响系统全局字体）。 */
    suspend fun previewFontFamily(file: File): FontFamily? = withContext(Dispatchers.IO) {
        loadValidatedFontFamily(file)
    }

    /** 从本地磁盘路径校验并导入字体文件为界面自定义字体。 */
    suspend fun importFromFile(context: Context, file: File): Result<String> = withContext(Dispatchers.IO) {
        installValidatedCustomFont(context, file, file.name)
    }

    /**
     * 从 SAF [Uri] 校验并导入用户选择的字体文件到应用私有目录，成功后立即写入全局字体缓存。
     */
    suspend fun importFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "font_probe_${System.currentTimeMillis()}.tmp")
        try {
            val rawDisplayName = queryDisplayName(context, uri)
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot open font uri"))
            var totalBytes = 0L
            input.use { src ->
                tempFile.outputStream().use { dst ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val read = src.read(buf)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_FONT_BYTES) {
                            return@withContext Result.failure(IllegalArgumentException("Font file too large"))
                        }
                        dst.write(buf, 0, read)
                    }
                }
            }

            if (totalBytes < MIN_FONT_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Font file too small"))
            }

            installValidatedCustomFont(context, tempFile, rawDisplayName)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun installValidatedCustomFont(
        context: Context,
        sourceFile: File,
        rawDisplayName: String?,
    ): Result<String> = runCatching {
        val ext = detectFontExtension(sourceFile, rawDisplayName)
            ?: throw IllegalArgumentException("Unsupported font header")

        val typeface = loadValidatedTypeface(sourceFile)
            ?: throw IllegalArgumentException("Invalid typeface")
        val family = runCatching { FontFamily(typeface) }.getOrNull()
            ?: throw IllegalArgumentException("Cannot build FontFamily")

        val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
        val destFile = File(fontsDir, "custom_${System.currentTimeMillis()}.$ext")
        sourceFile.copyTo(destFile, overwrite = true)

        // 清理旧的自定义字体文件与缓存条目，避免目录膨胀
        fontsDir.listFiles()?.forEach { old ->
            if (old.absolutePath != destFile.absolutePath && old.name.startsWith("custom_")) {
                runCatching { old.delete() }
            }
        }
        familyCache.keys.filter { it.startsWith(CUSTOM_PREFIX) }.forEach { familyCache.remove(it) }

        val cleanLabel = cleanFontLabel(rawDisplayName, destFile.nameWithoutExtension)
            .replace("|", " ")
            .trim()
            .ifBlank { "Custom Font" }
        val fontId = "$CUSTOM_PREFIX$cleanLabel|${destFile.absolutePath}"
        familyCache[fontId] = family
        fontId
    }

    /** 返回字体 ID 的可读显示名。 */
    fun displayName(
        fontId: String?,
        defaultLabel: String = "PocketDeck (Inter)",
        sansLabel: String = "System Sans-Serif",
        serifLabel: String = "System Serif",
        monoLabel: String = "System Monospace",
        missingFmt: String? = null,
    ): String {
        val id = fontId?.takeIf { it.isNotBlank() } ?: DEFAULT_ID
        return when {
            id == DEFAULT_ID -> defaultLabel
            id == SYSTEM_SANS_ID -> sansLabel
            id == SYSTEM_SERIF_ID -> serifLabel
            id == SYSTEM_MONO_ID -> monoLabel
            id.startsWith(CUSTOM_PREFIX) -> {
                val payload = id.removePrefix(CUSTOM_PREFIX)
                val label = payload.substringBefore('|').ifBlank { "Custom Font" }
                if (missingFmt != null && !isFontAvailable(id) && familyCache[id] == null) {
                    missingFmt.format(label)
                } else {
                    label
                }
            }
            id.startsWith(FILE_PREFIX) -> {
                val file = File(id.removePrefix(FILE_PREFIX))
                val name = cleanFontLabel(file.name, file.nameWithoutExtension)
                if (missingFmt != null && !isFontAvailable(id) && familyCache[id] == null) {
                    missingFmt.format(name)
                } else {
                    name
                }
            }
            else -> defaultLabel
        }
    }

    private fun extractFilePath(id: String): String? = when {
        id.startsWith(CUSTOM_PREFIX) -> id.removePrefix(CUSTOM_PREFIX).substringAfter('|', "").takeIf { it.isNotBlank() }
        id.startsWith(FILE_PREFIX) -> id.removePrefix(FILE_PREFIX).takeIf { it.isNotBlank() }
        else -> null
    }

    private fun cleanFontLabel(rawFileName: String?, fallback: String): String {
        val base = rawFileName
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: fallback
        return base
            .replace(WEIGHT_SUFFIX_REGEX, "")
            .replace('_', ' ')
            .replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " ")
            .trim()
            .ifBlank { base }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    /** 检查文件头 Magic Bytes（SFNT TrueType / OpenType CFF / TrueType Collection），拦截伪造扩展名的非字体文件。 */
    private fun detectFontExtension(file: File, rawName: String?): String? {
        if (!file.isFile || !file.canRead() || file.length() !in MIN_FONT_BYTES..MAX_FONT_BYTES) return null
        val header = ByteArray(4)
        val read = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrDefault(-1)
        if (read < 4) return null

        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF

        val isTrueType = (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) ||
            (b0 == 0x74 && b1 == 0x72 && b2 == 0x75 && b3 == 0x65) || // "true"
            (b0 == 0x74 && b1 == 0x79 && b2 == 0x70 && b3 == 0x31)    // "typ1"
        val isOpenType = b0 == 0x4F && b1 == 0x54 && b2 == 0x54 && b3 == 0x4F // "OTTO"
        val isTtc = b0 == 0x74 && b1 == 0x74 && b2 == 0x63 && b3 == 0x66      // "ttcf"

        return when {
            isTtc -> "ttc"
            isOpenType -> "otf"
            isTrueType -> {
                val origExt = rawName?.substringAfterLast('.', "")?.lowercase()
                if (origExt == "otf") "otf" else "ttf"
            }
            else -> null
        }
    }

    private fun loadValidatedTypeface(file: File): Typeface? {
        if (detectFontExtension(file, file.name) == null) return null
        val tf = runCatching { Typeface.createFromFile(file) }.getOrNull() ?: return null
        // Android 在解析失败时常静默返回默认 Typeface，必须剔除
        if (tf == Typeface.DEFAULT || tf == Typeface.DEFAULT_BOLD ||
            tf == Typeface.SANS_SERIF || tf == Typeface.SERIF || tf == Typeface.MONOSPACE
        ) {
            return null
        }
        val paint = Paint().apply {
            typeface = tf
            textSize = 32f
        }
        val hasRenderableGlyph = listOf("A", "a", "0", "1", "中", "あ", " ")
            .any { runCatching { paint.hasGlyph(it) }.getOrDefault(false) } ||
            runCatching { paint.measureText("Aa01中") > 0f }.getOrDefault(false)
        return if (hasRenderableGlyph) tf else null
    }

    private fun loadValidatedFontFamily(file: File): FontFamily? {
        val tf = loadValidatedTypeface(file) ?: return null
        return runCatching { FontFamily(tf) }.getOrNull()
    }
}

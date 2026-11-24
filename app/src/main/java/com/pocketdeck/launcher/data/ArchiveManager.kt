package com.pocketdeck.launcher.data

import com.github.junrar.Archive as RarArchive
import com.github.junrar.UnrarCallback
import com.github.junrar.volume.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ArchiveEntryInfo(
    val name: String,
    val isDir: Boolean,
    val size: Long,
)

data class DecodedText(
    val text: String,
    val charset: Charset,
    val hasBom: Boolean,
    val label: String,
)

/**
 * 常见游戏压缩包（ZIP / 7Z / RAR / TAR / GZ / BZ2 / XZ）与文件类型识别、预览、解压、压缩工具。
 * 解压全程校验规范化路径，防止 Zip-Slip 目录穿越。
 */
object ArchiveManager {
    private const val MAX_EXTRACT_FILES = 10_000
    private const val MAX_EXTRACT_BYTES = 16L * 1024L * 1024L * 1024L
    private const val MAX_INSPECT_TAR_BYTES = 64L * 1024L * 1024L
    private const val MAX_WALK_DEPTH = 32

    private val ARCHIVE_SINGLE_EXTS = setOf(
        "zip", "cbz", "7z", "rar", "tar", "tgz", "tbz2", "txz", "gz", "bz2", "xz",
    )
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
    private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "opus", "wma", "mid", "midi")
    private val FONT_EXTS = setOf("ttf", "otf", "ttc")
    private val TEXT_EXTS = setOf(
        "txt", "md", "log", "cfg", "ini", "conf", "json", "xml", "yaml", "yml",
        "toml", "properties", "cue", "m3u", "cht", "nfo", "tsv", "csv",
        "sh", "bat", "lpl", "opt", "glsl", "slang", "glslp", "slangp", "inf",
        "js", "mjs", "cjs", "jsx", "ts", "tsx", "css", "scss", "html", "htm", "py", "java", "kt", "sql",
    )

    fun isArchive(file: File): Boolean {
        if (!file.isFile) return false
        val lower = file.name.lowercase()
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tar.bz2") || lower.endsWith(".tar.xz")) return true
        return file.extension.lowercase() in ARCHIVE_SINGLE_EXTS
    }

    fun isImageFile(file: File): Boolean =
        file.isFile && file.extension.lowercase() in IMAGE_EXTS

    fun isAudioFile(file: File): Boolean =
        file.isFile && file.extension.lowercase() in AUDIO_EXTS

    fun isFontFile(file: File): Boolean =
        file.isFile && file.extension.lowercase() in FONT_EXTS

    /** 展开所选条目（支持把多选里的文件夹展开为普通文件，跳过符号链接并限制递归深度与环路）。 */
    fun collectShareableFiles(entries: List<File>, maxFiles: Int = 200): List<File> {
        val out = LinkedHashMap<String, File>()
        val visitedDirs = HashSet<String>()
        fun walk(f: File, depth: Int) {
            if (out.size >= maxFiles || depth > MAX_WALK_DEPTH || f.name.startsWith(".")) return
            if (isSymlink(f)) return
            if (f.isFile && f.canRead()) {
                out.putIfAbsent(f.absolutePath, f)
            } else if (f.isDirectory) {
                val canon = runCatching { f.canonicalPath }.getOrNull() ?: return
                if (!visitedDirs.add(canon)) return
                f.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { walk(it, depth + 1) }
            }
        }
        entries.forEach { walk(it, 0) }
        return out.values.toList()
    }

    fun isKnownTextFile(file: File): Boolean =
        file.isFile && file.extension.lowercase() in TEXT_EXTS

    /** 判断未知扩展名的小文件是否可视为文本（支持 UTF-8/BOM/UTF-16，不含裸 NUL 字节，上限 2 MB）。 */
    fun looksLikeTextFile(file: File): Boolean {
        if (!file.isFile || !file.canRead()) return false
        val len = file.length()
        if (len > 2L * 1024L * 1024L) return false
        if (len == 0L) return true
        val sampleSize = minOf(len, 4096L).toInt()
        val buf = ByteArray(sampleSize)
        val n = runCatching { FileInputStream(file).use { it.read(buf) } }.getOrDefault(-1)
        if (n <= 0) return false
        if (n >= 2 && (
                (buf[0] == 0xFF.toByte() && buf[1] == 0xFE.toByte() && !(n >= 4 && buf[2] == 0.toByte() && buf[3] == 0.toByte())) ||
                    (buf[0] == 0xFE.toByte() && buf[1] == 0xFF.toByte())
                )
        ) {
            return true
        }
        var nonPrintable = 0
        for (i in 0 until n) {
            val b = buf[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x09 || (b in 0x0E..0x1F && b != 0x1B)) nonPrintable++
        }
        return nonPrintable * 10 <= n
    }

    /**
     * 按 BOM、严格 UTF-8、无 BOM UTF-16 与 CJK 编码评分（GBK / Big5 / Shift_JIS / GB18030）自动识别文本编码。
     * [truncated] 为 true 时允许剥离末尾被截断的不完整多字节尾巴，避免末尾出现 `\uFFFD`。
     */
    fun decodeTextBytes(
        bytes: ByteArray,
        length: Int = bytes.size,
        truncated: Boolean = false,
    ): DecodedText {
        val safeLen = length.coerceIn(0, bytes.size)
        if (safeLen == 0) {
            return DecodedText("", StandardCharsets.UTF_8, false, "UTF-8")
        }

        val b0 = bytes[0].toInt() and 0xFF
        val b1 = if (safeLen > 1) bytes[1].toInt() and 0xFF else -1
        val b2 = if (safeLen > 2) bytes[2].toInt() and 0xFF else -1
        val b3 = if (safeLen > 3) bytes[3].toInt() and 0xFF else -1

        // 1. BOM 检测
        if (safeLen >= 3 && b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
            val bodyLen = safeLen - 3
            val s = tryDecodeStrict(bytes, 3, bodyLen, StandardCharsets.UTF_8, truncated, 3)
                ?: String(bytes, 3, bodyLen, StandardCharsets.UTF_8)
            return DecodedText(s, StandardCharsets.UTF_8, true, "UTF-8-BOM")
        }
        if (safeLen >= 2 && b0 == 0xFF && b1 == 0xFE && !(safeLen >= 4 && b2 == 0 && b3 == 0)) {
            val evenLen = (safeLen - 2) and -2
            val s = tryDecodeStrict(bytes, 2, evenLen, StandardCharsets.UTF_16LE, truncated = false, maxTrim = 0)
                ?: String(bytes, 2, evenLen, StandardCharsets.UTF_16LE)
            return DecodedText(s, StandardCharsets.UTF_16LE, true, "UTF-16LE")
        }
        if (safeLen >= 2 && b0 == 0xFE && b1 == 0xFF) {
            val evenLen = (safeLen - 2) and -2
            val s = tryDecodeStrict(bytes, 2, evenLen, StandardCharsets.UTF_16BE, truncated = false, maxTrim = 0)
                ?: String(bytes, 2, evenLen, StandardCharsets.UTF_16BE)
            return DecodedText(s, StandardCharsets.UTF_16BE, true, "UTF-16BE")
        }

        // 2. 严格 UTF-8
        tryDecodeStrict(bytes, 0, safeLen, StandardCharsets.UTF_8, truncated, 3)?.let {
            return DecodedText(it, StandardCharsets.UTF_8, false, "UTF-8")
        }

        // 3. 无 BOM UTF-16 启发式（偶数长且单字节高/低位零分布明显）
        if (safeLen >= 4 && safeLen % 2 == 0) {
            var evenZero = 0
            var oddZero = 0
            var idx = 0
            while (idx < safeLen) {
                if (bytes[idx] == 0.toByte()) evenZero++
                if (bytes[idx + 1] == 0.toByte()) oddZero++
                idx += 2
            }
            if (oddZero > safeLen / 6 && evenZero == 0) {
                tryDecodeStrict(bytes, 0, safeLen, StandardCharsets.UTF_16LE, truncated = false, maxTrim = 0)?.let {
                    return DecodedText(it, StandardCharsets.UTF_16LE, false, "UTF-16LE")
                }
            }
            if (evenZero > safeLen / 6 && oddZero == 0) {
                tryDecodeStrict(bytes, 0, safeLen, StandardCharsets.UTF_16BE, truncated = false, maxTrim = 0)?.let {
                    return DecodedText(it, StandardCharsets.UTF_16BE, false, "UTF-16BE")
                }
            }
        }

        // 4. 传统东亚双字节编码候选（GBK / Big5 / Shift_JIS）加权评分
        var lowTrailPairs = 0
        var totalDbcsPairs = 0
        var p = 0
        while (p < safeLen) {
            val lead = bytes[p].toInt() and 0xFF
            if (lead in 0x81..0xFE && p + 1 < safeLen) {
                val trail = bytes[p + 1].toInt() and 0xFF
                totalDbcsPairs++
                if (trail in 0x40..0x7E) lowTrailPairs++
                p += 2
            } else {
                p++
            }
        }
        val big5TrailRatioHigh = totalDbcsPairs >= 4 && lowTrailPairs * 4 >= totalDbcsPairs

        data class ScoredCandidate(val score: Int, val decoded: DecodedText)

        val candidates = ArrayList<ScoredCandidate>(3)
        for (name in listOf("GBK", "Big5", "Shift_JIS")) {
            val cs = runCatching { if (Charset.isSupported(name)) Charset.forName(name) else null }.getOrNull() ?: continue
            val s = tryDecodeStrict(bytes, 0, safeLen, cs, truncated, 1) ?: continue
            var score = 0
            for (ch in s) {
                val code = ch.code
                score += when {
                    ch == '\uFFFD' -> -50
                    code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t' -> -30
                    code in 0xE000..0xF8FF -> -25
                    ch == '\n' || ch == '\r' || ch == '\t' || code in 0x20..0x7E -> 1
                    code in 0x3040..0x30FF -> if (name == "Shift_JIS") 5 else 1
                    code in 0x3000..0x303F || code in 0xFF01..0xFF5E || code in 0x2010..0x2027 -> 3
                    code in 0xFF61..0xFF9F -> -3
                    code in 0x4E00..0x9FFF -> 3
                    code in 0x3400..0x4DBF -> -2
                    else -> 0
                }
            }
            if (big5TrailRatioHigh) {
                if (name == "Big5") score += totalDbcsPairs * 2
            } else {
                score += when (name) {
                    "GBK" -> 2
                    "Big5" -> 1
                    else -> 0
                }
            }
            candidates += ScoredCandidate(score, DecodedText(s, cs, false, name))
        }
        candidates.maxByOrNull { it.score }?.let { return it.decoded }

        // 5. 四字节 GB18030 与西欧单字节兜底
        runCatching { if (Charset.isSupported("GB18030")) Charset.forName("GB18030") else null }.getOrNull()?.let { gb18030 ->
            tryDecodeStrict(bytes, 0, safeLen, gb18030, truncated, 3)?.let {
                return DecodedText(it, gb18030, false, "GB18030")
            }
        }
        runCatching { if (Charset.isSupported("windows-1252")) Charset.forName("windows-1252") else null }.getOrNull()?.let { win1252 ->
            tryDecodeStrict(bytes, 0, safeLen, win1252, truncated = false, maxTrim = 0)?.let {
                return DecodedText(it, win1252, false, "Windows-1252")
            }
        }
        return DecodedText(String(bytes, 0, safeLen, StandardCharsets.UTF_8), StandardCharsets.UTF_8, false, "UTF-8")
    }

    /** 按原编码写出修改后文本；若新增了原字符集无法表示的字符则自动无损升级为 UTF-8。 */
    fun encodeTextPreserveCharset(
        text: String,
        charset: Charset,
        hasBom: Boolean = false,
    ): ByteArray {
        val canKeep = charset == StandardCharsets.UTF_8 ||
            runCatching { charset.newEncoder().canEncode(text) }.getOrDefault(false)
        val targetCharset = if (canKeep) charset else StandardCharsets.UTF_8
        val keepBom = hasBom && canKeep
        val body = text.toByteArray(targetCharset)
        if (!keepBom) return body
        val bom = when (targetCharset) {
            StandardCharsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            StandardCharsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            StandardCharsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            else -> ByteArray(0)
        }
        return if (bom.isEmpty()) body else bom + body
    }

    private fun tryDecodeStrict(
        bytes: ByteArray,
        offset: Int,
        len: Int,
        charset: Charset,
        truncated: Boolean,
        maxTrim: Int,
    ): String? {
        val minLen = if (truncated) (len - maxTrim).coerceAtLeast(0) else len
        for (tryLen in len downTo minLen) {
            if (tryLen == 0) return ""
            try {
                return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, tryLen))
                    .toString()
            } catch (_: CharacterCodingException) {
                // 若截断发生在多字节尾部，逐字节回退重测
            }
        }
        return null
    }

    fun suggestedFolderName(file: File): String {
        val name = file.name
        val lower = name.lowercase()
        return when {
            lower.endsWith(".tar.gz") -> name.dropLast(7)
            lower.endsWith(".tar.bz2") -> name.dropLast(8)
            lower.endsWith(".tar.xz") -> name.dropLast(7)
            else -> file.nameWithoutExtension
        }.ifBlank { "extracted" }
    }

    suspend fun inspect(file: File): Result<List<ArchiveEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val lower = file.name.lowercase()
            val out = ArrayList<ArchiveEntryInfo>()
            when {
                lower.endsWith(".zip") || lower.endsWith(".cbz") -> {
                    openZipWithFallback(file) { zf ->
                        val en = zf.entries
                        while (en.hasMoreElements() && out.size < 5000) {
                            val e = en.nextElement()
                            out += ArchiveEntryInfo(e.name, e.isDirectory, e.size.coerceAtLeast(0L))
                        }
                    }
                }
                lower.endsWith(".7z") -> {
                    SevenZFile.builder().setFile(file).get().use { sz ->
                        for (e in sz.entries) {
                            if (out.size >= 5000) break
                            out += ArchiveEntryInfo(e.name, e.isDirectory, e.size.coerceAtLeast(0L))
                        }
                    }
                }
                lower.endsWith(".rar") -> {
                    RarArchive(file).use { rar ->
                        require(!rar.isEncrypted) { "Encrypted RAR archive is not supported" }
                        for (fh in rar.fileHeaders) {
                            if (out.size >= 5000) break
                            val name = fh.fileName.replace('\\', '/')
                            out += ArchiveEntryInfo(name, fh.isDirectory, fh.fullUnpackSize.coerceAtLeast(0L))
                        }
                    }
                }
                isTarArchive(lower) -> {
                    openTarStream(file, lower, maxUncompressedBytes = MAX_INSPECT_TAR_BYTES).use { tis ->
                        while (out.size < 5000) {
                            val e = try {
                                tis.nextEntry
                            } catch (_: TarInspectLimitException) {
                                break
                            } ?: break
                            out += ArchiveEntryInfo(e.name, e.isDirectory, e.size.coerceAtLeast(0L))
                        }
                    }
                }
                lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz") -> {
                    out += ArchiveEntryInfo(file.nameWithoutExtension, false, file.length())
                }
                else -> error("Unsupported archive format")
            }
            out
        }
    }

    suspend fun extract(
        file: File,
        destDir: File,
        onProgress: (count: Int, entryName: String, bytesDone: Long) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
        onBytesProgress: (entryName: String, bytesDone: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
        overwriteExisting: Boolean = true,
        skipHiddenEntries: Boolean = false,
        progressThrottleMs: Long = 150L,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!destDir.exists()) check(destDir.mkdirs()) { "Cannot create directory ${destDir.path}" }
            require(destDir.isDirectory) { "Target is not a directory" }
            val destCanon = destDir.canonicalPath
            val lower = file.name.lowercase()
            var count = 0
            var bytesDone = 0L
            var totalBytes = 0L
            var lastReportMs = 0L
            var maxReportedBytes = 0L
            var lastEntryName = ""

            fun reportProgress(entryName: String, force: Boolean = false, bytesOverride: Long = bytesDone) {
                if (entryName.isNotEmpty()) lastEntryName = entryName
                val now = System.currentTimeMillis()
                if (force || lastReportMs == 0L || now - lastReportMs >= progressThrottleMs) {
                    lastReportMs = now
                    val effectiveTotal = if (totalBytes > 0L) maxOf(totalBytes, bytesDone) else 0L
                    val candidate = if (effectiveTotal > 0L) {
                        maxOf(bytesDone, bytesOverride.coerceAtMost(effectiveTotal))
                    } else {
                        maxOf(bytesDone, bytesOverride)
                    }
                    maxReportedBytes = maxOf(maxReportedBytes, candidate)
                    val label = entryName.ifEmpty { lastEntryName.ifEmpty { file.name } }
                    onProgress(count, label, maxReportedBytes)
                    onBytesProgress(label, maxReportedBytes, effectiveTotal)
                }
            }

            fun isHiddenEntry(entryName: String): Boolean =
                entryName.replace('\\', '/').split('/').any { seg ->
                    seg.isNotEmpty() && seg != "." && seg != ".." && seg.startsWith(".")
                }

            fun resolveSafeTarget(entryName: String): File {
                require('\u0000' !in entryName) { "Unsafe archive path: $entryName" }
                val normalized = entryName.replace('\\', '/').trimStart('/')
                val target = File(destDir, normalized)
                val targetCanon = target.canonicalPath
                require(targetCanon == destCanon || targetCanon.startsWith("$destCanon${File.separator}")) {
                    "Unsafe archive path: $entryName"
                }
                return target
            }

            fun resolveOutputTarget(rawTarget: File): File {
                if (overwriteExisting || !rawTarget.exists()) return rawTarget
                val parent = rawTarget.parentFile ?: destDir
                val nonClobber = uniqueFile(parent, rawTarget.name)
                val canon = nonClobber.canonicalPath
                require(canon == destCanon || canon.startsWith("$destCanon${File.separator}")) {
                    "Unsafe archive target: ${nonClobber.name}"
                }
                return nonClobber
            }

            fun copyStreamChecked(input: InputStream, rawTarget: File, entryName: String) {
                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries (limit $MAX_EXTRACT_FILES)" }
                val target = resolveOutputTarget(rawTarget)
                target.parentFile?.mkdirs()
                val buf = ByteArray(64 * 1024)
                target.outputStream().use { out ->
                    while (true) {
                        if (isCancelled()) error("Cancelled")
                        val n = input.read(buf)
                        if (n <= 0) break
                        bytesDone += n
                        require(bytesDone <= MAX_EXTRACT_BYTES) { "Extracted archive exceeds safety size limit" }
                        out.write(buf, 0, n)
                        reportProgress(entryName)
                    }
                }
            }

            when {
                lower.endsWith(".zip") || lower.endsWith(".cbz") -> {
                    openZipWithFallback(file) { zf ->
                        totalBytes = zf.entries.asSequence().sumOf { e ->
                            if (e.isDirectory || (skipHiddenEntries && isHiddenEntry(e.name))) 0L
                            else e.size.coerceAtLeast(0L)
                        }
                        val en = zf.entries
                        while (en.hasMoreElements()) {
                            if (isCancelled()) error("Cancelled")
                            val e = en.nextElement()
                            val target = resolveSafeTarget(e.name)
                            if (skipHiddenEntries && isHiddenEntry(e.name)) continue
                            if (e.isDirectory) {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries (limit $MAX_EXTRACT_FILES)" }
                                target.mkdirs()
                            } else {
                                count++
                                reportProgress(e.name, force = true)
                                zf.getInputStream(e).use { copyStreamChecked(it, target, e.name) }
                            }
                        }
                    }
                }
                lower.endsWith(".7z") -> {
                    SevenZFile.builder().setFile(file).get().use { sz ->
                        totalBytes = sz.entries.sumOf { e ->
                            if (e.isDirectory || (skipHiddenEntries && isHiddenEntry(e.name))) 0L
                            else e.size.coerceAtLeast(0L)
                        }
                        while (true) {
                            if (isCancelled()) error("Cancelled")
                            val e = sz.nextEntry ?: break
                            val safeTarget = resolveSafeTarget(e.name)
                            if (skipHiddenEntries && isHiddenEntry(e.name)) continue
                            if (e.isDirectory) {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries" }
                                safeTarget.mkdirs()
                            } else {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries" }
                                reportProgress(e.name, force = true)
                                val target = resolveOutputTarget(safeTarget)
                                target.parentFile?.mkdirs()
                                target.outputStream().use { out ->
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        if (isCancelled()) error("Cancelled")
                                        val n = sz.read(buf)
                                        if (n <= 0) break
                                        bytesDone += n
                                        require(bytesDone <= MAX_EXTRACT_BYTES) { "Extracted archive exceeds safety size limit" }
                                        out.write(buf, 0, n)
                                        reportProgress(e.name)
                                    }
                                }
                            }
                        }
                    }
                }
                lower.endsWith(".rar") -> {
                    var currentEntryName = ""
                    var currentEntryBaseBytes = 0L
                    var currentEntryUnpackSize = 0L
                    var currentEntryPackSize = 0L
                    var currentEntryPackedRead = 0L
                    var lastVolumePackedRead = 0L
                    var inEntryExtract = false
                    val rarCallback = object : UnrarCallback {
                        override fun isNextVolumeReady(nextVolume: Volume): Boolean = !isCancelled()
                        override fun volumeProgressChanged(current: Long, total: Long) {
                            if (isCancelled()) error("Cancelled")
                            val delta = if (current >= lastVolumePackedRead) current - lastVolumePackedRead else current
                            lastVolumePackedRead = current
                            if (inEntryExtract && delta > 0L) {
                                currentEntryPackedRead += delta
                                if (currentEntryPackSize > 0L && currentEntryUnpackSize > 0L) {
                                    val est = ((currentEntryUnpackSize.toDouble() *
                                        currentEntryPackedRead.coerceAtMost(currentEntryPackSize).toDouble()) /
                                        currentEntryPackSize.toDouble()).toLong()
                                    reportProgress(currentEntryName, bytesOverride = currentEntryBaseBytes + est)
                                }
                            }
                        }
                    }
                    RarArchive(file, rarCallback).use { rar ->
                        require(!rar.isEncrypted) { "Encrypted RAR archive is not supported" }
                        totalBytes = rar.fileHeaders.sumOf { fh ->
                            val name = fh.fileName.replace('\\', '/')
                            if (fh.isDirectory || (skipHiddenEntries && isHiddenEntry(name))) 0L
                            else fh.fullUnpackSize.coerceAtLeast(0L)
                        }
                        while (true) {
                            if (isCancelled()) error("Cancelled")
                            val fh = rar.nextFileHeader() ?: break
                            val cleanName = fh.fileName.replace('\\', '/')
                            val safeTarget = resolveSafeTarget(cleanName)
                            if (skipHiddenEntries && isHiddenEntry(cleanName)) continue
                            if (fh.isDirectory) {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries" }
                                safeTarget.mkdirs()
                            } else {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries" }
                                require(bytesDone + fh.fullUnpackSize.coerceAtLeast(0L) <= MAX_EXTRACT_BYTES) {
                                    "Extracted archive exceeds safety size limit"
                                }
                                currentEntryName = cleanName
                                currentEntryBaseBytes = bytesDone
                                currentEntryUnpackSize = fh.fullUnpackSize.coerceAtLeast(0L)
                                currentEntryPackSize = fh.fullPackSize.coerceAtLeast(0L)
                                currentEntryPackedRead = 0L
                                inEntryExtract = true
                                reportProgress(cleanName, force = count == 1)
                                val target = resolveOutputTarget(safeTarget)
                                target.parentFile?.mkdirs()
                                try {
                                    target.outputStream().use { rawOut ->
                                        val guardedOut = object : java.io.FilterOutputStream(BufferedOutputStream(rawOut, 64 * 1024)) {
                                            private var singleByteTick = 0
                                            override fun write(b: Int) {
                                                if (isCancelled()) error("Cancelled")
                                                bytesDone += 1L
                                                require(bytesDone <= MAX_EXTRACT_BYTES) { "Extracted archive exceeds safety size limit" }
                                                out.write(b)
                                                if ((++singleByteTick and 0x3FFF) == 0) {
                                                    reportProgress(cleanName)
                                                }
                                            }
                                            override fun write(b: ByteArray, off: Int, len: Int) {
                                                if (isCancelled()) error("Cancelled")
                                                bytesDone += len.toLong()
                                                require(bytesDone <= MAX_EXTRACT_BYTES) { "Extracted archive exceeds safety size limit" }
                                                out.write(b, off, len)
                                                reportProgress(cleanName)
                                            }
                                        }
                                        rar.extractFile(fh, guardedOut)
                                        guardedOut.flush()
                                    }
                                } catch (t: Throwable) {
                                    runCatching { target.delete() }
                                    throw t
                                } finally {
                                    inEntryExtract = false
                                }
                            }
                        }
                    }
                }
                isTarArchive(lower) -> {
                    openTarStream(file, lower).use { tis ->
                        while (true) {
                            if (isCancelled()) error("Cancelled")
                            val e = tis.nextEntry ?: break
                            if (e.isSymbolicLink || e.isLink) continue
                            val target = resolveSafeTarget(e.name)
                            if (skipHiddenEntries && isHiddenEntry(e.name)) continue
                            if (e.isDirectory) {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries (limit $MAX_EXTRACT_FILES)" }
                                target.mkdirs()
                            } else {
                                count++
                                reportProgress(e.name, force = true)
                                copyStreamChecked(tis, target, e.name)
                            }
                        }
                    }
                }
                lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz") -> {
                    val outName = file.nameWithoutExtension.ifBlank { "${file.name}.out" }
                    val target = resolveSafeTarget(outName)
                    if (!skipHiddenEntries || !isHiddenEntry(outName)) {
                        count = 1
                        reportProgress(outName, force = true)
                        val base = BufferedInputStream(FileInputStream(file), 64 * 1024)
                        val comp: InputStream = try {
                            when {
                                lower.endsWith(".gz") -> GzipCompressorInputStream(base)
                                lower.endsWith(".bz2") -> BZip2CompressorInputStream(base)
                                else -> XZCompressorInputStream(base)
                            }
                        } catch (t: Throwable) {
                            runCatching { base.close() }
                            throw t
                        }
                        comp.use { copyStreamChecked(it, target, outName) }
                    }
                }
                else -> error("Unsupported archive format")
            }
            if (count > 0 && lastEntryName.isNotEmpty()) {
                reportProgress(lastEntryName, force = true)
            }
            count
        }
    }

    suspend fun compressToZip(
        sources: List<File>,
        destZip: File,
        onProgress: (entryName: String) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        onBytesProgress: (entryName: String, bytesDone: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        val part = File(destZip.parentFile ?: destZip.absoluteFile.parentFile, ".zip_${System.currentTimeMillis()}.part")
        val partCanon = runCatching { part.canonicalPath }.getOrDefault(part.absolutePath)
        val destCanon = runCatching { destZip.canonicalPath }.getOrDefault(destZip.absolutePath)
        runCatching {
            val totalBytes = sources.sumOf(::measureEntrySize)
            var bytesDone = 0L
            var lastReportMs = 0L
            onBytesProgress(destZip.name, 0L, totalBytes)

            ZipOutputStream(BufferedOutputStream(part.outputStream(), 64 * 1024)).use { zos ->
                fun writeFile(file: File, entryName: String) {
                    if (isCancelled()) error("Cancelled")
                    onProgress(entryName)
                    onBytesProgress(entryName, bytesDone, totalBytes)
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { fis ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (isCancelled()) error("Cancelled")
                            val n = fis.read(buf)
                            if (n <= 0) break
                            zos.write(buf, 0, n)
                            bytesDone += n
                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= 150L) {
                                lastReportMs = now
                                onBytesProgress(entryName, bytesDone, totalBytes)
                            }
                        }
                    }
                    zos.closeEntry()
                }

                val visitedDirs = HashSet<String>()
                fun walk(entry: File, prefix: String, depth: Int) {
                    if (isCancelled()) error("Cancelled")
                    if (depth > MAX_WALK_DEPTH) return
                    // 跳过符号链接，阻断借 ROM 目录内软链越权打包应用私密文件（如密钥/数据库）或循环递归
                    if (isSymlink(entry)) return
                    val entryCanon = runCatching { entry.canonicalPath }.getOrDefault(entry.absolutePath)
                    if (entryCanon == partCanon || entryCanon == destCanon) return
                    val name = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                    if (entry.isDirectory) {
                        if (!visitedDirs.add(entryCanon)) return
                        val children = entry.listFiles().orEmpty()
                        if (children.isEmpty()) {
                            zos.putNextEntry(ZipEntry("$name/"))
                            zos.closeEntry()
                        } else {
                            children.forEach { walk(it, name, depth + 1) }
                        }
                    } else if (entry.isFile) {
                        writeFile(entry, name)
                    }
                }

                for (s in sources) walk(s, "", 0)
            }
            if (destZip.exists()) destZip.delete()
            check(part.renameTo(destZip)) { "Cannot finalize zip file" }
            destZip
        }.onFailure {
            runCatching { part.delete() }
        }
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun measureEntrySize(
        entry: File,
        visitedDirs: MutableSet<String> = HashSet(),
        depth: Int = 0,
    ): Long {
        if (depth > MAX_WALK_DEPTH || isSymlink(entry)) return 0L
        if (entry.isFile) return entry.length()
        if (!entry.isDirectory) return 0L
        val canon = runCatching { entry.canonicalPath }.getOrNull() ?: return 0L
        if (!visitedDirs.add(canon)) return 0L
        var sum = 0L
        entry.listFiles()?.forEach { sum += measureEntrySize(it, visitedDirs, depth + 1) }
        return sum
    }

    private fun isTarArchive(lower: String): Boolean =
        lower.endsWith(".tar") || lower.endsWith(".tgz") || lower.endsWith(".tar.gz") ||
            lower.endsWith(".tbz2") || lower.endsWith(".tar.bz2") ||
            lower.endsWith(".txz") || lower.endsWith(".tar.xz")

    private class TarInspectLimitException : IOException("Tar preview byte limit exceeded")

    private fun openTarStream(file: File, lower: String, maxUncompressedBytes: Long = Long.MAX_VALUE): TarArchiveInputStream {
        val base = BufferedInputStream(FileInputStream(file), 64 * 1024)
        val unwrapped: InputStream = when {
            lower.endsWith(".tgz") || lower.endsWith(".tar.gz") -> GzipCompressorInputStream(base)
            lower.endsWith(".tbz2") || lower.endsWith(".tar.bz2") -> BZip2CompressorInputStream(base)
            lower.endsWith(".txz") || lower.endsWith(".tar.xz") -> XZCompressorInputStream(base)
            else -> base
        }
        val guarded = if (maxUncompressedBytes < Long.MAX_VALUE) {
            object : FilterInputStream(unwrapped) {
                private var readTotal = 0L
                override fun read(): Int {
                    val b = super.read()
                    if (b >= 0) {
                        readTotal++
                        if (readTotal > maxUncompressedBytes) throw TarInspectLimitException()
                    }
                    return b
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) {
                        readTotal += n
                        if (readTotal > maxUncompressedBytes) throw TarInspectLimitException()
                    }
                    return n
                }
                override fun skip(n: Long): Long {
                    if (n <= 0L) return 0L
                    val remaining = maxUncompressedBytes - readTotal
                    if (remaining <= 0L) throw TarInspectLimitException()
                    // 按剩余预算+1 截断底层 skip，防止 TarArchiveInputStream.nextEntry 通过 skip() 绕过解压上限
                    val capped = minOf(n, remaining + 1L)
                    val skipped = super.skip(capped)
                    if (skipped > 0L) {
                        readTotal += skipped
                        if (readTotal > maxUncompressedBytes) throw TarInspectLimitException()
                    }
                    return skipped
                }
            }
        } else {
            unwrapped
        }
        return TarArchiveInputStream(guarded)
    }

    private inline fun <T> openZipWithFallback(file: File, block: (ZipFile) -> T): T {
        val utf8 = ZipFile.builder().setFile(file).setCharset(Charsets.UTF_8).get()
        val hasBadNames = runCatching {
            val en = utf8.entries
            var bad = false
            while (en.hasMoreElements()) {
                if ('�' in en.nextElement().name) {
                    bad = true
                    break
                }
            }
            bad
        }.getOrDefault(false)
        if (!hasBadNames) {
            return utf8.use(block)
        }
        utf8.close()
        val gbk = runCatching { Charset.forName("GBK") }.getOrDefault(Charsets.UTF_8)
        return ZipFile.builder().setFile(file).setCharset(gbk).get().use(block)
    }
}

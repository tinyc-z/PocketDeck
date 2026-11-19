package com.pocketdeck.launcher.data

import com.github.junrar.Archive as RarArchive
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
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ArchiveEntryInfo(
    val name: String,
    val isDir: Boolean,
    val size: Long,
)

/**
 * 常见游戏压缩包（ZIP / 7Z / RAR / TAR / GZ / BZ2 / XZ）与文件类型识别、预览、解压、压缩工具。
 * 解压全程校验规范化路径，防止 Zip-Slip 目录穿越。
 */
object ArchiveManager {
    private const val MAX_EXTRACT_FILES = 10_000
    private const val MAX_EXTRACT_BYTES = 16L * 1024L * 1024L * 1024L

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

    /** 展开所选条目（支持把多选里的文件夹展开为普通文件，用于多文件分享）。 */
    fun collectShareableFiles(entries: List<File>, maxFiles: Int = 200): List<File> {
        val out = LinkedHashMap<String, File>()
        fun walk(f: File) {
            if (out.size >= maxFiles || f.name.startsWith(".")) return
            if (f.isFile && f.canRead()) {
                out.putIfAbsent(f.absolutePath, f)
            } else if (f.isDirectory) {
                f.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { walk(it) }
            }
        }
        entries.forEach(::walk)
        return out.values.toList()
    }

    fun isKnownTextFile(file: File): Boolean =
        file.isFile && file.extension.lowercase() in TEXT_EXTS

    /** 判断未知扩展名的小文件是否可视为 UTF-8/ASCII 文本（不含 NUL 字节，上限 2 MB）。 */
    fun looksLikeTextFile(file: File): Boolean {
        if (!file.isFile || !file.canRead()) return false
        val len = file.length()
        if (len > 2L * 1024L * 1024L) return false
        if (len == 0L) return true
        val sampleSize = minOf(len, 4096L).toInt()
        val buf = ByteArray(sampleSize)
        val n = runCatching { FileInputStream(file).use { it.read(buf) } }.getOrDefault(-1)
        if (n <= 0) return false
        var nonPrintable = 0
        for (i in 0 until n) {
            val b = buf[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x09 || (b in 0x0E..0x1F && b != 0x1B)) nonPrintable++
        }
        return nonPrintable * 10 <= n
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
                    openTarStream(file, lower).use { tis ->
                        while (out.size < 5000) {
                            val e = tis.nextEntry ?: break
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

            fun reportProgress(entryName: String, force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (force || now - lastReportMs >= 150L) {
                    lastReportMs = now
                    onProgress(count, entryName, bytesDone)
                    onBytesProgress(entryName, bytesDone, totalBytes)
                }
            }

            fun resolveSafeTarget(entryName: String): File {
                val normalized = entryName.replace('\\', '/').trimStart('/')
                val target = File(destDir, normalized)
                val targetCanon = target.canonicalPath
                require(targetCanon == destCanon || targetCanon.startsWith("$destCanon${File.separator}")) {
                    "Unsafe archive path: $entryName"
                }
                return target
            }

            fun copyStreamChecked(input: InputStream, target: File, entryName: String) {
                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries (limit $MAX_EXTRACT_FILES)" }
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
                        totalBytes = zf.entries.asSequence().sumOf { it.size.coerceAtLeast(0L) }
                        val en = zf.entries
                        while (en.hasMoreElements()) {
                            if (isCancelled()) error("Cancelled")
                            val e = en.nextElement()
                            val target = resolveSafeTarget(e.name)
                            if (e.isDirectory) {
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
                        totalBytes = sz.entries.sumOf { it.size.coerceAtLeast(0L) }
                        while (true) {
                            if (isCancelled()) error("Cancelled")
                            val e = sz.nextEntry ?: break
                            val target = resolveSafeTarget(e.name)
                            if (e.isDirectory) {
                                target.mkdirs()
                            } else {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries" }
                                reportProgress(e.name, force = true)
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
                    RarArchive(file).use { rar ->
                        require(!rar.isEncrypted) { "Encrypted RAR archive is not supported" }
                        while (true) {
                            if (isCancelled()) error("Cancelled")
                            val fh = rar.nextFileHeader() ?: break
                            val cleanName = fh.fileName.replace('\\', '/')
                            val target = resolveSafeTarget(cleanName)
                            if (fh.isDirectory) {
                                target.mkdirs()
                            } else {
                                count++
                                require(count <= MAX_EXTRACT_FILES) { "Too many archive entries" }
                                require(bytesDone + fh.fullUnpackSize.coerceAtLeast(0L) <= MAX_EXTRACT_BYTES) {
                                    "Extracted archive exceeds safety size limit"
                                }
                                reportProgress(cleanName, force = true)
                                target.parentFile?.mkdirs()
                                try {
                                    target.outputStream().use { rawOut ->
                                        val guardedOut = object : java.io.FilterOutputStream(rawOut) {
                                            override fun write(b: Int) {
                                                if (isCancelled()) error("Cancelled")
                                                bytesDone += 1L
                                                require(bytesDone <= MAX_EXTRACT_BYTES) { "Extracted archive exceeds safety size limit" }
                                                out.write(b)
                                            }
                                            override fun write(b: ByteArray, off: Int, len: Int) {
                                                if (isCancelled()) error("Cancelled")
                                                bytesDone += len.toLong()
                                                require(bytesDone <= MAX_EXTRACT_BYTES) { "Extracted archive exceeds safety size limit" }
                                                out.write(b, off, len)
                                            }
                                        }
                                        rar.extractFile(fh, guardedOut)
                                    }
                                } catch (t: Throwable) {
                                    runCatching { target.delete() }
                                    throw t
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
                            if (e.isDirectory) {
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
                else -> error("Unsupported archive format")
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

                fun walk(entry: File, prefix: String) {
                    if (isCancelled()) error("Cancelled")
                    val entryCanon = runCatching { entry.canonicalPath }.getOrDefault(entry.absolutePath)
                    if (entryCanon == partCanon || entryCanon == destCanon) return
                    val name = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                    if (entry.isDirectory) {
                        val children = entry.listFiles().orEmpty()
                        if (children.isEmpty()) {
                            zos.putNextEntry(ZipEntry("$name/"))
                            zos.closeEntry()
                        } else {
                            children.forEach { walk(it, name) }
                        }
                    } else if (entry.isFile) {
                        writeFile(entry, name)
                    }
                }

                for (s in sources) walk(s, "")
            }
            if (destZip.exists()) destZip.delete()
            check(part.renameTo(destZip)) { "Cannot finalize zip file" }
            destZip
        }.onFailure {
            runCatching { part.delete() }
        }
    }

    private fun measureEntrySize(entry: File): Long {
        if (entry.isFile) return entry.length()
        if (!entry.isDirectory) return 0L
        var sum = 0L
        entry.listFiles()?.forEach { sum += measureEntrySize(it) }
        return sum
    }

    private fun isTarArchive(lower: String): Boolean =
        lower.endsWith(".tar") || lower.endsWith(".tgz") || lower.endsWith(".tar.gz") ||
            lower.endsWith(".tbz2") || lower.endsWith(".tar.bz2") ||
            lower.endsWith(".txz") || lower.endsWith(".tar.xz")

    private fun openTarStream(file: File, lower: String): TarArchiveInputStream {
        val base = BufferedInputStream(FileInputStream(file), 64 * 1024)
        val unwrapped: InputStream = when {
            lower.endsWith(".tgz") || lower.endsWith(".tar.gz") -> GzipCompressorInputStream(base)
            lower.endsWith(".tbz2") || lower.endsWith(".tar.bz2") -> BZip2CompressorInputStream(base)
            lower.endsWith(".txz") || lower.endsWith(".tar.xz") -> XZCompressorInputStream(base)
            else -> base
        }
        return TarArchiveInputStream(unwrapped)
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

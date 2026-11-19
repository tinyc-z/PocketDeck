package com.pocketdeck.launcher.data

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * ROM 身份识别提取器：在低 I/O 开销下提取三类精准指纹并持久化到 [ScrapeDatabase.rom_identity]：
 * 1. 无头 CRC32（支持直读 ROM、`.zip` 智能选主 ROM 条目、`.7z` 元数据直读 CRC、剥离 iNES/SMC/FDS/A78/LNX 头）
 * 2. CHD v4/v5 头内嵌 SHA1（<128B 瞬时读取，无需解压上 GB 光盘镜像）
 * 3. 主机/掌机光盘与卡带 Serial / Game ID（GC / Wii / NDS / 3DS / Switch / PSP / PS1 / PS2 / Dreamcast / Saturn）
 */
class RomIdentityExtractor(private val db: ScrapeDatabase) {

    data class Identity(
        val crc32: String? = null,
        val sha1: String? = null,
        val serial: String? = null,
    )

    private val memCache = ConcurrentHashMap<String, Identity>()

    fun clearMemoryCache() {
        memCache.clear()
    }

    fun extract(file: File, platformId: String? = null): Identity {
        if (!file.isFile) return Identity()
        val path = file.path
        memCache[path]?.let { return it }

        val size = file.length()
        val mtime = file.lastModified()
        db.getRomIdentity(path, size, mtime)?.let { cached ->
            val id = Identity(cached.crc32, cached.sha1, cached.serial)
            memCache[path] = id
            return id
        }

        val ext = file.extension.lowercase()
        var crc32: String? = null
        var sha1: String? = null
        var serial: String? = extractSerialFromFileName(file.name, platformId)

        when (ext) {
            "zip" -> {
                val zipRes = inspectZip(file)
                crc32 = zipRes.first
                if (serial == null) serial = zipRes.second
            }
            "7z" -> {
                crc32 = inspectSevenZipCrc(file)
            }
            "chd" -> {
                sha1 = extractChdSha1(file)
            }
            "cue", "gdi", "m3u" -> {
                if (serial == null) serial = extractSerialFromDiscIndex(file, platformId)
            }
            else -> {
                if (serial == null) {
                    serial = extractHeaderSerial(file, ext, platformId)
                }
                if (size in 1L..MAX_CRC_SIZE) {
                    crc32 = computeHeaderlessCrc32(file, ext, size)
                }
            }
        }

        val identity = Identity(crc32 = crc32, sha1 = sha1, serial = serial)
        memCache[path] = identity
        db.putRomIdentity(path, size, mtime, ScrapeDatabase.RomIdentityRow(crc32, sha1, serial))
        return identity
    }

    // ───────────────────────── ZIP / 7Z / 无头 CRC32 ─────────────────────────

    private fun inspectZip(file: File): Pair<String?, String?> = runCatching {
        ZipFile(file).use { zip ->
            var bestEntry: ZipEntry? = null
            var bestScore = Int.MIN_VALUE
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val name = e.name
                if (name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")) continue
                val entryExt = name.substringAfterLast('.', "").lowercase()
                if (entryExt in NON_ROM_ARCHIVE_EXT) continue
                val score = (if (entryExt in KNOWN_ROM_EXT) 1_000_000 else 0) +
                    e.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (score > bestScore) {
                    bestScore = score
                    bestEntry = e
                }
            }
            val entry = bestEntry ?: return@runCatching null to null
            val entryExt = entry.name.substringAfterLast('.', "").lowercase()
            val headerSize = detectHeaderSizeForExt(entryExt, entry.size)
            val crc = if (headerSize > 0 && entry.size in 1L..MAX_CRC_SIZE) {
                zip.getInputStream(entry).use { stream ->
                    computeStreamCrc32WithHeaderCheck(stream, entryExt, entry.size)
                }
            } else if (entry.crc >= 0) {
                "%08X".format(entry.crc)
            } else {
                null
            }
            val serial = extractSerialFromFileName(entry.name.substringAfterLast('/'), null)
            crc to serial
        }
    }.getOrDefault(null to null)

    private fun inspectSevenZipCrc(file: File): String? = runCatching {
        SevenZFile.Builder().setFile(file).get().use { sz ->
            var bestCrc: Long? = null
            var bestScore = Int.MIN_VALUE
            for (entry in sz.entries) {
                if (entry.isDirectory || !entry.hasCrc) continue
                val name = entry.name ?: continue
                if (name.startsWith("__MACOSX/")) continue
                val entryExt = name.substringAfterLast('.', "").lowercase()
                if (entryExt in NON_ROM_ARCHIVE_EXT) continue
                val score = (if (entryExt in KNOWN_ROM_EXT) 1_000_000 else 0) +
                    entry.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (score > bestScore) {
                    bestScore = score
                    bestCrc = entry.crcValue
                }
            }
            bestCrc?.let { "%08X".format(it and 0xFFFFFFFFL) }
        }
    }.getOrNull()

    private fun computeHeaderlessCrc32(file: File, ext: String, size: Long): String? = runCatching {
        FileInputStream(file).use { input ->
            computeStreamCrc32WithHeaderCheck(input, ext, size)
        }
    }.getOrNull()

    private fun computeStreamCrc32WithHeaderCheck(
        input: InputStream,
        ext: String,
        totalSize: Long,
    ): String {
        val crc = CRC32()
        val buf = ByteArray(16 * 1024)
        var first = true
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            if (first) {
                first = false
                val skip = headerSkipBytes(buf, read, ext, totalSize)
                if (skip in 1 until read) {
                    crc.update(buf, skip, read - skip)
                    continue
                }
            }
            crc.update(buf, 0, read)
        }
        return "%08X".format(crc.value)
    }

    private fun detectHeaderSizeForExt(ext: String, totalSize: Long): Int = when (ext) {
        "nes" -> 16
        "fds" -> 16
        "a78" -> 128
        "lnx" -> 64
        "smc", "sfc", "swc", "fig" -> if (totalSize > 512 && (totalSize % 1024L) == 512L) 512 else 0
        else -> 0
    }

    private fun headerSkipBytes(buf: ByteArray, read: Int, ext: String, totalSize: Long): Int = when (ext) {
        "nes" -> if (read >= 16 && buf[0] == 'N'.code.toByte() && buf[1] == 'E'.code.toByte() &&
            buf[2] == 'S'.code.toByte() && buf[3] == 0x1A.toByte()
        ) 16 else 0
        "fds" -> if (read >= 16 && buf[0] == 'F'.code.toByte() && buf[1] == 'D'.code.toByte() &&
            buf[2] == 'S'.code.toByte() && buf[3] == 0x1A.toByte()
        ) 16 else 0
        "a78" -> if (read >= 128 && buf[1] == 'A'.code.toByte() && buf[2] == 'T'.code.toByte() &&
            buf[3] == 'A'.code.toByte() && buf[4] == 'R'.code.toByte() && buf[5] == 'I'.code.toByte()
        ) 128 else 0
        "lnx" -> if (read >= 64 && buf[0] == 'L'.code.toByte() && buf[1] == 'Y'.code.toByte() &&
            buf[2] == 'N'.code.toByte() && buf[3] == 'X'.code.toByte()
        ) 64 else 0
        "smc", "sfc", "swc", "fig" -> if (read >= 512 && totalSize > 512 && (totalSize % 1024L) == 512L) 512 else 0
        else -> 0
    }

    // ───────────────────────── CHD v4/v5 SHA1 ─────────────────────────

    private fun extractChdSha1(file: File): String? = runCatching {
        if (file.length() < 124) return@runCatching null
        RandomAccessFile(file, "r").use { raf ->
            val hdr = ByteArray(124)
            raf.readFully(hdr)
            // Magic: "MComprHD"
            if (String(hdr, 0, 8, Charsets.US_ASCII) != "MComprHD") return@runCatching null
            val version = readIntBE(hdr, 12)
            val sha1Offset = when (version) {
                5 -> 84 // v5: data SHA1 at offset 0x54 (84..103)
                4 -> 80 // v4: SHA1 at offset 0x50 (80..99)
                3 -> 80
                else -> return@runCatching null
            }
            var allZero = true
            val sb = StringBuilder(40)
            for (i in 0 until 20) {
                val b = hdr[sha1Offset + i].toInt() and 0xFF
                if (b != 0) allZero = false
                sb.append("%02x".format(b))
            }
            if (allZero) null else sb.toString()
        }
    }.getOrNull()

    // ───────────────────────── Serial / Game ID 提取 ─────────────────────────

    private fun extractSerialFromFileName(fileName: String, platformId: String?): String? {
        // Switch 16 位十六进制 Title ID：[0100XXXXXXXXXXXX]
        if (platformId == "switch" || fileName.endsWith(".nsp", true) || fileName.endsWith(".xci", true) ||
            fileName.endsWith(".nsz", true) || fileName.endsWith(".xcz", true)
        ) {
            SWITCH_TID_REGEX.find(fileName)?.groupValues?.getOrNull(1)?.let { return it.uppercase() }
        }
        // PS1 / PS2 / PSP / PS3 常见编码（SLUS-00594, ULUS-10041, BLUS30443 等）
        SONY_SERIAL_REGEX.find(fileName)?.let { m ->
            val prefix = m.groupValues[1].uppercase()
            val num = m.groupValues[2].replace(".", "")
            return "$prefix-$num"
        }
        // 括号内 6 位 Wii/GC ID（如 [GALE01] 或 [RMGE01]）
        if (platformId in setOf("wii", "gamecube", "wiiu")) {
            WII_GC_BRACKET_ID.find(fileName)?.groupValues?.getOrNull(1)?.let { return it.uppercase() }
        }
        return null
    }

    private fun extractSerialFromDiscIndex(indexFile: File, platformId: String?): String? = runCatching {
        val parent = indexFile.parentFile ?: return@runCatching null
        indexFile.useLines { lines ->
            for (raw in lines.take(64)) {
                val trimmed = raw.trim()
                val quoted = Regex("\"([^\"]+)\"").find(trimmed)?.groupValues?.getOrNull(1)
                val targetName = (quoted ?: trimmed.substringAfterLast(' '))
                    .replace('\\', '/').substringAfterLast('/').trim()
                if (targetName.isNotEmpty()) {
                    val f = File(parent, targetName)
                    if (f.isFile) {
                        extractHeaderSerial(f, f.extension.lowercase(), platformId)?.let { return@runCatching it }
                    }
                }
            }
        }
        null
    }.getOrNull()

    private fun extractHeaderSerial(file: File, ext: String, platformId: String?): String? = runCatching {
        val len = file.length()
        if (len < 32) return@runCatching null
        RandomAccessFile(file, "r").use { raf ->
            // 1. Nintendo DS / DSi: Game Code at 0x0C (4 ASCII uppercase/digits)
            if (ext == "nds" || ext == "dsi" || platformId == "nds" || platformId == "dsi") {
                if (len >= 0x14) {
                    val buf = ByteArray(16)
                    raf.seek(0x0C)
                    raf.readFully(buf, 0, 4)
                    val code = String(buf, 0, 4, Charsets.US_ASCII)
                    if (NDS_CODE_REGEX.matches(code)) return@runCatching code
                }
            }

            // 2. GameCube / Wii ISO, GCM, WBFS, RVZ
            if (ext in setOf("iso", "gcm", "wbfs", "rvz", "gcz") &&
                (platformId == "gamecube" || platformId == "wii" || ext in setOf("gcm", "wbfs", "rvz", "gcz"))
            ) {
                val head = ByteArray(minOf(len, 1024L).toInt())
                raf.seek(0L)
                raf.readFully(head)
                // ISO/GCM: Wii magic 0x5D1C9EA3 at 0x18 or GC magic 0xC2339F3D at 0x1C
                if (head.size >= 32) {
                    val wiiMagic = readIntBE(head, 0x18) == 0x5D1C9EA3
                    val gcMagic = readIntBE(head, 0x1C) == 0xC2339F3D.toInt()
                    if (wiiMagic || gcMagic) {
                        val id = String(head, 0, 6, Charsets.US_ASCII)
                        if (WII_GC_ID_REGEX.matches(id)) return@runCatching id
                    }
                }
                // WBFS: magic "WBFS" at 0x00, disc header at 0x200
                if (head.size >= 0x208 && String(head, 0, 4, Charsets.US_ASCII) == "WBFS") {
                    val id = String(head, 0x200, 6, Charsets.US_ASCII)
                    if (WII_GC_ID_REGEX.matches(id)) return@runCatching id
                }
                // RVZ/WIA: magic "RVZ\x01" or "WIA\x01" at 0x00, disc header at 0x58
                if (head.size >= 0x60 &&
                    (String(head, 0, 3, Charsets.US_ASCII) == "RVZ" || String(head, 0, 3, Charsets.US_ASCII) == "WIA")
                ) {
                    val id = String(head, 0x58, 6, Charsets.US_ASCII)
                    if (WII_GC_ID_REGEX.matches(id)) return@runCatching id
                }
            }

            // 3. 3DS (.3ds / .cci): search NCSD/NCCH product code "CTR-P-XXXX" in first 16 KB
            if (ext in setOf("3ds", "cci", "cxi") || platformId == "3ds") {
                val buf = ByteArray(minOf(len, 16 * 1024L).toInt())
                raf.seek(0L)
                raf.readFully(buf)
                val ascii = String(buf, Charsets.ISO_8859_1)
                THREEDS_CODE_REGEX.find(ascii)?.groupValues?.getOrNull(1)?.let { return@runCatching it }
            }

            // 4. PSP / PS1 / PS2 / PS3: scan first 96 KB for Sony product serial
            if (platformId in setOf("psp", "psx", "ps2", "ps3") || ext in setOf("iso", "bin", "img", "pbp")) {
                val readLen = minOf(len, 96 * 1024L).toInt()
                val buf = ByteArray(readLen)
                raf.seek(0L)
                raf.readFully(buf)
                val ascii = String(buf, Charsets.ISO_8859_1)
                SONY_SERIAL_REGEX.find(ascii)?.let { m ->
                    val prefix = m.groupValues[1].uppercase()
                    val num = m.groupValues[2].replace(".", "")
                    return@runCatching "$prefix-$num"
                }
            }

            // 5. Saturn / Dreamcast IP.BIN header
            if (platformId in setOf("saturn", "dreamcast")) {
                val readLen = minOf(len, 8192L).toInt()
                val buf = ByteArray(readLen)
                raf.seek(0L)
                raf.readFully(buf)
                val ascii = String(buf, Charsets.ISO_8859_1)
                val idx = ascii.indexOf("SEGA SEGASATURN").takeIf { it >= 0 }
                    ?: ascii.indexOf("SEGA SEGAKATANA").takeIf { it >= 0 }
                if (idx != null && idx + 0x4A <= ascii.length) {
                    val code = ascii.substring(idx + 0x20, idx + 0x2A).trim()
                    if (code.length in 5..10 && code.all { it.isLetterOrDigit() || it == '-' }) {
                        return@runCatching code
                    }
                }
            }
            null
        }
    }.getOrNull()

    private fun readIntBE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)

    companion object {
        const val MAX_CRC_SIZE = 32L * 1024 * 1024

        private val NON_ROM_ARCHIVE_EXT = setOf(
            "txt", "nfo", "diz", "md", "pdf", "jpg", "jpeg", "png", "gif", "webp",
            "bmp", "url", "htm", "html", "xml", "json", "sfv", "md5", "sha1",
        )
        private val KNOWN_ROM_EXT = setOf(
            "nes", "fds", "sfc", "smc", "fig", "swc", "gb", "gbc", "gba", "nds", "dsi",
            "n64", "z64", "v64", "md", "gen", "smd", "sms", "gg", "sg", "32x",
            "pce", "ngp", "ngc", "ws", "wsc", "a26", "a52", "a78", "lnx", "j64",
            "col", "int", "vec", "rom", "bin", "iso", "cso", "chd", "pbp", "3ds", "cci",
            "nsp", "xci", "gcm", "wbfs", "rvz", "gcz", "p8",
        )

        private val SWITCH_TID_REGEX = Regex("""\[([0-9A-Fa-f]{16})\]""")
        private val SONY_SERIAL_REGEX = Regex(
            """\b(SLUS|SCUS|SLES|SCES|SLPS|SCPS|SLPM|SCPM|SLED|SCED|PAPX|PBPX|ULUS|UCUS|ULES|UCES|ULJM|UCJM|ULJS|UCJS|ULJP|NPJH|NPUH|NPEH|NPJG|NPUG|NPEG|BLUS|BCUS|BLES|BCES|BLJM|BCJM|BLJS|BCJS)[-_ ]?(\d{3}\.?\d{2})\b""",
            RegexOption.IGNORE_CASE,
        )
        private val WII_GC_ID_REGEX = Regex("""^[A-Z0-9]{6}$""")
        private val WII_GC_BRACKET_ID = Regex("""\[([A-Z0-9]{6})\]""")
        private val NDS_CODE_REGEX = Regex("""^[A-Z0-9]{4}$""")
        private val THREEDS_CODE_REGEX = Regex("""CTR-[A-Z]-([A-Z0-9]{4})""")
    }
}

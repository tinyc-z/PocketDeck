package com.pocketdeck.launcher.lan

import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.data.sanitizeUploadFileName
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Web 管理端文件写入与目录创建安全校验工具：
 * 1) 强制单段文件名剥离控制字符、限制长度并禁止 `.` 前缀隐藏文件；
 * 2) 限制文本编辑接口仅可写入可编辑的纯文本文件（拦截 ROM/存档/二进制/隐藏文件）；
 * 3) 禁止 `auto-create-platforms` 把共享存储根目录或系统敏感目录提升为可读写的 `romDirs`。
 */
internal object WebFileSecurity {

    private val PROTECTED_TOP_DIRS = setOf(
        "android",
        "dcim",
        "pictures",
        "movies",
        "music",
        "documents",
        "download",
        "downloads",
        "alarms",
        "notifications",
        "ringtones",
        "podcasts",
        "audiobooks",
        "obb",
        "data",
    )

    private val NON_EDITABLE_BINARY_EXTS = setOf(
        // 常见 ROM / 镜像 / 安装包 / 存档扩展名（即使为 0 字节也不允许通过文本接口覆写）
        "gba", "gb", "gbc", "nes", "fds", "unf", "unif", "sfc", "smc", "fig", "swc", "bs",
        "nds", "dsi", "ids", "3ds", "cia", "cxi", "cci", "3dsx",
        "n64", "z64", "v64", "ndd", "gcm", "gcz", "rvz", "ciso", "wbfs",
        "iso", "cso", "chd", "pbp", "elf", "bin", "img", "ccd", "mdf", "mds", "toc",
        "md", "gen", "smd", "sg", "neo", "nsp", "xci", "nsz", "xcz", "nro",
        "sav", "srm", "state", "sta", "oops", "eep", "fla",
        "apk", "dex", "so", "exe", "dll",
    )

    fun sanitizeWebFileName(raw: String): String? =
        sanitizeUploadFileName(
            raw = raw,
            stripControlChars = true,
            maxLength = 255,
            allowDotPrefix = false,
        )

    fun isEditableWebTextFile(file: File, content: String? = null): Boolean {
        if (!file.isFile || file.name.startsWith(".")) return false
        if (content != null && '\u0000' in content) return false
        if (ArchiveManager.isArchive(file) ||
            ArchiveManager.isImageFile(file) ||
            ArchiveManager.isAudioFile(file) ||
            ArchiveManager.isFontFile(file)
        ) {
            return false
        }
        val ext = file.extension.lowercase()
        if (ext in NON_EDITABLE_BINARY_EXTS) return false
        return ArchiveManager.isKnownTextFile(file) || ArchiveManager.looksLikeTextFile(file)
    }

    sealed interface AutoCreateTargetResult {
        data class Ok(val targetRoot: File, val allowAddAsRomRoot: Boolean) : AutoCreateTargetResult
        data class Error(val httpStatus: Int, val code: String) : AutoCreateTargetResult
    }

    fun resolveAutoCreateTarget(
        dirPath: String,
        parentName: String,
        romRoots: List<File>,
        volumeRoots: List<File>,
    ): AutoCreateTargetResult {
        val rawDir = dirPath.trim()
        val rawParent = parentName.trim()
        val romCanons = romRoots.mapNotNull { r -> runCatching { r.canonicalPath }.getOrNull() }.toSet()
        val volCanons = volumeRoots.mapNotNull { v -> runCatching { v.canonicalPath }.getOrNull() }.toSet()

        if (rawDir.isNotEmpty()) {
            val dirFile = File(rawDir)
            val dirCanon = runCatching { dirFile.canonicalPath }.getOrNull()
                ?: return AutoCreateTargetResult.Error(400, "invalid_params")
            val inRomRoots = romCanons.any { rc -> dirCanon == rc || dirCanon.startsWith("$rc${File.separator}") }
            if (inRomRoots && dirFile.isDirectory) {
                if (rawParent.isEmpty()) {
                    return AutoCreateTargetResult.Ok(dirFile, allowAddAsRomRoot = true)
                }
                val cleanParent = sanitizeWebFileName(rawParent)
                    ?: return AutoCreateTargetResult.Error(400, "invalid_parent_name")
                val target = File(dirFile, cleanParent)
                val targetCanon = runCatching { target.canonicalPath }.getOrNull()
                    ?: return AutoCreateTargetResult.Error(400, "invalid_parent_name")
                if (!targetCanon.startsWith("$dirCanon${File.separator}")) {
                    return AutoCreateTargetResult.Error(403, "forbidden_target")
                }
                return AutoCreateTargetResult.Ok(target, allowAddAsRomRoot = true)
            }
        }

        // 未命中已配置 ROM 根目录时，只允许在存储卷（内部存储 / SD 卡）根目录本身之下显式新建非敏感子目录
        val baseDir = if (rawDir.isNotEmpty()) {
            val reqFile = File(rawDir)
            val reqCanon = runCatching { reqFile.canonicalPath }.getOrNull()
                ?: return AutoCreateTargetResult.Error(403, "forbidden_dir")
            if (reqCanon !in volCanons || !reqFile.isDirectory) {
                return AutoCreateTargetResult.Error(403, "forbidden_dir")
            }
            reqFile
        } else {
            volumeRoots.firstOrNull { it.isDirectory }
                ?: return AutoCreateTargetResult.Error(400, "invalid_params")
        }

        if (rawParent.isEmpty()) {
            return AutoCreateTargetResult.Error(400, "parent_name_required")
        }
        val cleanParent = sanitizeWebFileName(rawParent)
            ?: return AutoCreateTargetResult.Error(400, "invalid_parent_name")
        if (cleanParent.lowercase() in PROTECTED_TOP_DIRS) {
            return AutoCreateTargetResult.Error(403, "protected_dir")
        }
        val baseCanon = runCatching { baseDir.canonicalPath }.getOrNull()
            ?: return AutoCreateTargetResult.Error(400, "invalid_params")
        val target = File(baseDir, cleanParent)
        val targetCanon = runCatching { target.canonicalPath }.getOrNull()
            ?: return AutoCreateTargetResult.Error(400, "invalid_parent_name")
        if (targetCanon != "$baseCanon${File.separator}$cleanParent" || targetCanon in volCanons) {
            return AutoCreateTargetResult.Error(403, "forbidden_target")
        }
        return AutoCreateTargetResult.Ok(target, allowAddAsRomRoot = true)
    }

    /** 校验 [file] 的规范路径严格位于 [dir] 目录之内（防同前缀目录与 `..`/符号链接逃逸）。 */
    fun isInsideDir(file: File, dir: File): Boolean {
        val dirCanon = runCatching { dir.canonicalPath }.getOrNull() ?: return false
        val fileCanon = runCatching { file.canonicalPath }.getOrNull() ?: return false
        val prefix = if (dirCanon.endsWith(File.separator)) dirCanon else "$dirCanon${File.separator}"
        return fileCanon.startsWith(prefix)
    }

    /** 允许通过 Web 管理端 `sourceUrl` 由掌机代下载封面的可信公开 CDN 域名白名单。 */
    private val ALLOWED_COVER_EXACT_HOSTS = setOf(
        "thumbnails.libretro.com",
        "cdn.jsdelivr.net",
        "fastly.jsdelivr.net",
        "raw.githubusercontent.com",
        "art.gametdb.com",
        "images.igdb.com",
        "hasheous.org",
        "upload.wikimedia.org",
        "commons.wikimedia.org",
        "adb.arcadeitalia.net",
    )

    private val ALLOWED_COVER_SUFFIX_HOSTS = setOf(
        ".thegamesdb.net",
        ".steamgriddb.com",
        ".screenscraper.fr",
        ".hasheous.org",
        ".igdb.com",
        ".gametdb.com",
        ".libretro.com",
        ".arcadeitalia.net",
    )

    /** 校验封面代下载 `sourceUrl`：仅放行 HTTPS、标准端口、无 userInfo 且命中可信封面域名白名单。 */
    fun isAllowedCoverSourceUrl(urlStr: String): Boolean {
        val trimmed = urlStr.trim()
        if (trimmed.isEmpty() || trimmed.length > 2048) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host.isEmpty()) return false
        if (host in ALLOWED_COVER_EXACT_HOSTS) return true
        return ALLOWED_COVER_SUFFIX_HOSTS.any { suffix -> host.endsWith(suffix) && host.length > suffix.length }
    }

    /** 校验解析后的 IP 地址属于公网可达地址（拒绝回环、内网、链路本地、多播、CGNAT 与 IPv6 ULA）。 */
    fun isPublicInetAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress
        ) {
            return false
        }
        val raw = addr.address
        when (addr) {
            is Inet4Address -> {
                val b0 = raw[0].toInt() and 0xFF
                val b1 = raw[1].toInt() and 0xFF
                // 0.0.0.0/8, 100.64.0.0/10 (CGNAT), 192.0.0.0/24, 198.18.0.0/15, 240.0.0.0/4
                if (b0 == 0) return false
                if (b0 == 100 && b1 in 64..127) return false
                if (b0 == 192 && b1 == 0 && (raw[2].toInt() and 0xFF) == 0) return false
                if (b0 == 198 && (b1 == 18 || b1 == 19)) return false
                if (b0 >= 240) return false
            }
            is Inet6Address -> {
                val b0 = raw[0].toInt() and 0xFF
                // fc00::/7 (Unique Local Address)
                if ((b0 and 0xFE) == 0xFC) return false
                // ::ffff:0:0/96 IPv4-mapped IPv6
                val isV4Mapped = (0 until 10).all { raw[it].toInt() == 0 } &&
                    (raw[10].toInt() and 0xFF) == 0xFF &&
                    (raw[11].toInt() and 0xFF) == 0xFF
                if (isV4Mapped) {
                    val v4 = InetAddress.getByAddress(raw.copyOfRange(12, 16))
                    return isPublicInetAddress(v4)
                }
            }
        }
        return true
    }

    /** 校验快传/Web 平台目录 ID 仅含安全字符（防斜杠与 `..` 目录逃逸）。 */
    private val SAFE_PLATFORM_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,40}$")
    fun isSafePlatformId(raw: String?): Boolean = !raw.isNullOrEmpty() && SAFE_PLATFORM_ID_REGEX.matches(raw)
}

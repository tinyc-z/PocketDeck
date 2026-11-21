package com.pocketdeck.launcher.lan

import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.data.sanitizeUploadFileName
import java.io.File

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
}

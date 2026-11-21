package com.pocketdeck.launcher.data

import java.io.File

private val SAFE_KEY_REGEX = Regex("""[^a-zA-Z0-9._-]""")
private val CONTROL_CHARS_REGEX = Regex("""[\u0000-\u001F\u007F]""")

/** 将平台名/缓存键规范为仅含 `[a-zA-Z0-9._-]` 的安全文件名。 */
internal fun sanitizeFileKey(name: String): String = name.replace(SAFE_KEY_REGEX, "_")

/** 校验并清洗外部传入的单段文件名（拦截 `.`、`..`、路径分隔符、控制字符及可选隐藏 `.` 前缀）。 */
internal fun sanitizeUploadFileName(
    raw: String,
    stripControlChars: Boolean = false,
    maxLength: Int = Int.MAX_VALUE,
    allowDotPrefix: Boolean = true,
): String? {
    val cleaned = (if (stripControlChars) raw.replace(CONTROL_CHARS_REGEX, "") else raw).trim()
    if (cleaned.isEmpty() || cleaned.length > maxLength || cleaned == "." || cleaned == "..") return null
    if (!allowDotPrefix && cleaned.startsWith(".")) return null
    if ('/' in cleaned || '\\' in cleaned || '\u0000' in cleaned) return null
    return cleaned
}

/** 目标文件已存在时按 `stem_1.ext`、`stem_2.ext` 顺延生成不重名文件。 */
internal fun uniqueFile(dir: File, name: String): File {
    var candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var n = 1
    while (candidate.exists()) {
        candidate = File(dir, "${stem}_$n$ext")
        n++
    }
    return candidate
}

/** 通过文件头魔数识别图片格式，返回 `(ext, mimeType)`；非支持图片返回 `null`。 */
internal fun detectImageFormat(bytes: ByteArray): Pair<String, String>? = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png" to "image/png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg" to "image/jpeg"
    bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "webp" to "image/webp"
    bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "gif" to "image/gif"
    else -> null
}

/** 按文件扩展名推断常见 MIME 类型。 */
internal fun mimeTypeForFile(
    file: File,
    defaultMime: String = "application/octet-stream",
): String = when (file.extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "zip" -> "application/zip"
    "txt", "cfg", "ini", "json" -> "text/plain"
    else -> defaultMime
}

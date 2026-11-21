package com.pocketdeck.launcher.data

import java.util.Locale

private const val KB = 1024.0
private const val MB = KB * 1024.0
private const val GB = MB * 1024.0

private fun formatKbMbGb(bytes: Long, kbDecimals: Int, suffix: String = ""): String {
    val kb = bytes / KB
    if (kb < KB) return String.format(Locale.US, "%.${kbDecimals}f KB%s", kb, suffix)
    val mb = kb / KB
    if (mb < KB) return String.format(Locale.US, "%.1f MB%s", mb, suffix)
    return String.format(Locale.US, "%.2f GB%s", mb / KB, suffix)
}

/** 紧凑文件大小（`<= 0` 返回 `null`），用于列表角标、摘要与存储剩余空间。 */
internal fun formatCompactSize(bytes: Long): String? =
    if (bytes <= 0L) null else formatKbMbGb(bytes, kbDecimals = 0)

/** 通用文件大小（`<= 0` 返回 `"0 B"`）。 */
internal fun formatSize(bytes: Long): String = formatCompactSize(bytes) ?: "0 B"

/** 缓存字节数显示（保留 1 位 KB 小数，`<= 0` 返回 `"0 B"`）。 */
internal fun formatCacheBytes(bytes: Long): String =
    if (bytes <= 0L) "0 B" else formatKbMbGb(bytes, kbDecimals = 1)

/** 传输速率显示（`0 B/s`、`N B/s`、KB/MB/GB`/s`）。 */
internal fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec <= 0L -> "0 B/s"
    bytesPerSec < 1024L -> "$bytesPerSec B/s"
    else -> "${formatSize(bytesPerSec)}/s"
}

/** 游戏详情与属性页用的精细文件大小（`< 1 KB` 显示 `B`，`< 100 KB` 显示 1 位小数，其余显示 0 位小数）。 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> {
        val kb = bytes / KB
        formatKbMbGb(bytes, kbDecimals = if (kb >= 100.0) 0 else 1)
    }
    else -> formatKbMbGb(bytes, kbDecimals = 0)
}

/** HTTP 日志摘要用的极简紧凑大小（`< 0` 显示 `?B`）。 */
internal fun formatHttpLogBytes(bytes: Long): String = when {
    bytes < 0L -> "?B"
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024L -> "${bytes / 1024}KB"
    else -> String.format(Locale.US, "%.1fMB", bytes / MB)
}

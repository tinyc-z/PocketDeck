package com.pocketdeck.launcher.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

internal data class ExternalStorageVolume(
    val dir: File,
    val description: String,
)

private val SKIPPED_STORAGE_NAMES = setOf("emulated", "self", "enc_emulated")

/**
 * 枚举当前挂载的外置存储卷（TF / SD / USB OTG），去重规范路径并兼容部分掌机固件路径。
 */
internal fun listExternalStorageVolumes(
    context: Context,
    fallbackLabel: String = "TF / SD",
    includeExternalFilesDirs: Boolean = true,
    requireReadable: Boolean = false,
): List<ExternalStorageVolume> {
    val primaryDir = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
    val primaryCanon = primaryDir?.let { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
    val seen = HashSet<String>()
    val out = ArrayList<ExternalStorageVolume>()

    fun record(dir: File?, desc: String) {
        if (dir == null || !dir.isDirectory) return
        if (requireReadable && !dir.canRead()) return
        val canon = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (canon == "/" || canon == "/storage" || canon == "/mnt" || canon == primaryCanon) return
        if (seen.add(canon)) {
            out += ExternalStorageVolume(dir, desc.ifBlank { dir.name.ifBlank { fallbackLabel } })
        }
    }

    val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            sm.storageVolumes.forEach { vol ->
                if (!vol.isPrimary && vol.state == Environment.MEDIA_MOUNTED) {
                    val volDir = vol.directory
                    val desc = runCatching { vol.getDescription(context) }.getOrNull().orEmpty()
                        .ifBlank { volDir?.name ?: fallbackLabel }
                    record(volDir, desc)
                }
            }
        }
    }

    if (includeExternalFilesDirs) {
        runCatching {
            context.getExternalFilesDirs(null).orEmpty().forEach { appDir ->
                var cur: File? = appDir
                repeat(4) { cur = cur?.parentFile }
                record(cur, cur?.name.orEmpty().ifBlank { fallbackLabel })
            }
        }
    }

    runCatching {
        File("/storage").listFiles()
            ?.sortedBy { it.name }
            ?.forEach { vol ->
                if (vol.name !in SKIPPED_STORAGE_NAMES) {
                    record(vol, vol.name)
                }
            }
    }

    return out
}

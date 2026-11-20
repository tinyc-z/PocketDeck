package com.pocketdeck.launcher.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val tagName: String,
    val changelog: String,
    val apkUrl: String?,
    val htmlUrl: String,
)

/**
 * 应用内更新检查器（GitHub Releases API + APK 下载进度 + FileProvider 拉起安装）。
 */
object UpdateChecker {

    private const val LATEST_RELEASE_API = "https://api.github.com/repos/tinyc-z/PocketDeck/releases/latest"

    /** 语义化版本号对比：remote > current 返回 true（兼容 `v0.6.0` / `0.6.0-beta1` 等格式）。 */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val r = parseVersionParts(remote)
        val c = parseVersionParts(current)
        val maxLen = maxOf(r.size, c.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        return clean.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    }

    /** 查询 GitHub 最新 Release；若已是最新版则返回 Result.success(null)。 */
    suspend fun checkLatestRelease(currentVersion: String): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PocketDeck/$currentVersion")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            val version = tagName.removePrefix("v").removePrefix("V")
            if (version.isEmpty() || !isNewerVersion(version, currentVersion)) {
                return@runCatching null
            }
            val changelog = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", "https://github.com/tinyc-z/PocketDeck/releases/latest")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }
            ReleaseInfo(
                versionName = version,
                tagName = tagName,
                changelog = changelog,
                apkUrl = apkUrl,
                htmlUrl = htmlUrl,
            )
        }
    }

    /** 下载 APK 至 `cacheDir/updates/` 并回调进度 `[0f..1f]`。 */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val dest = File(dir, "PocketDeck-update.apk")
            val tmp = File(dir, "PocketDeck-update.apk.tmp")
            if (tmp.exists()) tmp.delete()

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PocketDeck-Updater")
            }
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.use { inp ->
                tmp.outputStream().buffered().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0L) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            conn.disconnect()
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            onProgress(1f)
            dest
        }
    }

    /** 通过 FileProvider 拉起系统 APK 安装器。 */
    fun installApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

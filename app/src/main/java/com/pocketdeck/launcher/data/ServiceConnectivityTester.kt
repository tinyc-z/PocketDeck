package com.pocketdeck.launcher.data

import android.content.Context
import android.os.SystemClock
import com.pocketdeck.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

enum class ServiceProbeStatus {
    Pending,
    Reachable,
    Slow,
    Unreachable,
    Timeout,
    Offline,
}

data class ServiceProbeResult(
    val id: String,
    val nameRes: Int,
    val host: String,
    val probeUrl: String,
    val status: ServiceProbeStatus = ServiceProbeStatus.Pending,
    val latencyMs: Long = -1L,
    val httpCode: Int = 0,
)

/**
 * 轻量级并发服务连通性探测：
 * - 先通过 [NetworkStatus.isOnline] 判定设备是否联网（覆盖 Wi-Fi / 移动数据 SIM / 以太网 / VPN），离线时 0ms 直接返回 Offline；
 * - 联网时并发发送轻量 HEAD 请求（超时 4 秒），不消耗 API 配额，逐项回调最新状态供 UI 实时刷新。
 */
object ServiceConnectivityTester {

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_000
    private const val SLOW_THRESHOLD_MS = 1_500L

    fun initialItems(): List<ServiceProbeResult> = listOf(
        ServiceProbeResult(
            id = "libretro_fastly",
            nameRes = R.string.conn_svc_libretro_fastly,
            host = "fastly.jsdelivr.net",
            probeUrl = "https://fastly.jsdelivr.net/",
        ),
        ServiceProbeResult(
            id = "libretro_cdn",
            nameRes = R.string.conn_svc_libretro_cdn,
            host = "cdn.jsdelivr.net",
            probeUrl = "https://cdn.jsdelivr.net/",
        ),
        ServiceProbeResult(
            id = "gametdb",
            nameRes = R.string.conn_svc_gametdb,
            host = "www.gametdb.com",
            probeUrl = "https://www.gametdb.com/",
        ),
        ServiceProbeResult(
            id = "arcadeitalia",
            nameRes = R.string.conn_svc_arcadeitalia,
            host = "adb.arcadeitalia.net",
            probeUrl = "https://adb.arcadeitalia.net/",
        ),
        ServiceProbeResult(
            id = "hasheous",
            nameRes = R.string.conn_svc_hasheous,
            host = "hasheous.org",
            probeUrl = "https://hasheous.org/",
        ),
        ServiceProbeResult(
            id = "wikidata",
            nameRes = R.string.conn_svc_wikidata,
            host = "www.wikidata.org",
            probeUrl = "https://www.wikidata.org/w/api.php",
        ),
        ServiceProbeResult(
            id = "screenscraper",
            nameRes = R.string.conn_svc_screenscraper,
            host = "api.screenscraper.fr",
            probeUrl = "https://api.screenscraper.fr/",
        ),
        ServiceProbeResult(
            id = "thegamesdb",
            nameRes = R.string.conn_svc_thegamesdb,
            host = "api.thegamesdb.net",
            probeUrl = "https://api.thegamesdb.net/",
        ),
        ServiceProbeResult(
            id = "igdb",
            nameRes = R.string.conn_svc_igdb,
            host = "id.twitch.tv",
            probeUrl = "https://id.twitch.tv/",
        ),
        ServiceProbeResult(
            id = "steamgriddb",
            nameRes = R.string.conn_svc_steamgriddb,
            host = "www.steamgriddb.com",
            probeUrl = "https://www.steamgriddb.com/",
        ),
        ServiceProbeResult(
            id = "github",
            nameRes = R.string.conn_svc_github,
            host = "api.github.com",
            probeUrl = "https://api.github.com/",
        ),
    )

    suspend fun probeAll(
        context: Context,
        onItemUpdated: (ServiceProbeResult) -> Unit,
    ): List<ServiceProbeResult> = coroutineScope {
        val items = initialItems()
        if (!NetworkStatus.isOnline(context)) {
            return@coroutineScope items.map { item ->
                item.copy(status = ServiceProbeStatus.Offline).also(onItemUpdated)
            }
        }
        items.map { item ->
            async(Dispatchers.IO) {
                val res = probeSingle(item)
                withContext(Dispatchers.Main.immediate) {
                    onItemUpdated(res)
                }
                res
            }
        }.awaitAll()
    }

    private fun probeSingle(item: ServiceProbeResult): ServiceProbeResult {
        val startMs = SystemClock.elapsedRealtime()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(item.probeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "PocketDeck/1.0")
            }
            val code = conn.responseCode
            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            // 1xx..4xx 均说明 DNS + TCP + TLS + HTTP 链路畅通（部分 API 对 HEAD 返回 401/403/404/405 属正常）
            val status = when {
                code in 100..499 && elapsed <= SLOW_THRESHOLD_MS -> ServiceProbeStatus.Reachable
                code in 100..499 -> ServiceProbeStatus.Slow
                else -> ServiceProbeStatus.Unreachable
            }
            item.copy(status = status, latencyMs = elapsed, httpCode = code)
        } catch (_: SocketTimeoutException) {
            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            item.copy(status = ServiceProbeStatus.Timeout, latencyMs = elapsed)
        } catch (_: IOException) {
            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            item.copy(status = ServiceProbeStatus.Unreachable, latencyMs = elapsed)
        } catch (_: Throwable) {
            item.copy(status = ServiceProbeStatus.Unreachable)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}

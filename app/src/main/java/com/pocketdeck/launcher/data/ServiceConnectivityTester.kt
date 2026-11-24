package com.pocketdeck.launcher.data

import android.content.Context
import android.os.SystemClock
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

data class SourceProbeState(
    val status: ServiceProbeStatus = ServiceProbeStatus.Pending,
    val latencyMs: Long = -1L,
)

/**
 * 内联数据源连通性探测：
 * - 探测前先通过 [NetworkStatus.isOnline] 判定设备是否联网（覆盖 Wi-Fi / 移动网络 SIM / 以太网 / VPN），离线时 0ms 直接标记 Offline；
 * - 联网时按去重后的后端节点并发发送轻量 HEAD 请求（超时 4 秒，不消耗 API 配额），
 *   将结果实时映射回 [ScraperCatalog.CoverIds] 与 [ScraperCatalog.MetaIds] 对应的全部行。
 */
object ServiceConnectivityTester {

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_000
    private const val SLOW_THRESHOLD_MS = 1_500L

    private data class ProbeGroup(
        val sourceIds: List<String>,
        val urls: List<String>,
    )

    private val probeGroups: List<ProbeGroup> = listOf(
        // Libretro 封面/截图/数据库共享多镜像容灾：并发探测 Fastly 与 jsDelivr，取最快可达节点
        ProbeGroup(
            sourceIds = listOf(
                ScraperCatalog.CoverIds.LIBRETRO_BOXARTS,
                ScraperCatalog.CoverIds.LIBRETRO_SNAPS,
                ScraperCatalog.MetaIds.LIBRETRO_DB,
            ),
            urls = listOf(
                "https://fastly.jsdelivr.net/",
                "https://cdn.jsdelivr.net/",
            ),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.CoverIds.GAMETDB),
            urls = listOf("https://www.gametdb.com/"),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.CoverIds.HASHEOUS),
            urls = listOf("https://hasheous.org/"),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.CoverIds.ARCADEITALIA),
            urls = listOf("https://adb.arcadeitalia.net/"),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.CoverIds.STEAMGRIDDB),
            urls = listOf("https://www.steamgriddb.com/"),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.CoverIds.SCREENSCRAPER),
            urls = listOf("https://api.screenscraper.fr/"),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.CoverIds.THEGAMESDB),
            urls = listOf("https://api.thegamesdb.net/"),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.CoverIds.IGDB),
            urls = listOf("https://id.twitch.tv/"),
        ),
        ProbeGroup(
            sourceIds = listOf(ScraperCatalog.MetaIds.WIKIDATA),
            urls = listOf("https://www.wikidata.org/w/api.php"),
        ),
    )

    fun allSourceIds(): List<String> = probeGroups.flatMap { it.sourceIds }

    /**
     * 并发探测全部刮削源并在每组返回时实时回调 [onGroupUpdated]。
     * @return 按去重后端节点统计的 `(reachableCount, unreachableCount)`。
     */
    suspend fun probeSources(
        context: Context,
        onGroupUpdated: (Map<String, SourceProbeState>) -> Unit,
    ): Pair<Int, Int> = coroutineScope {
        if (!NetworkStatus.isOnline(context)) {
            val offlineMap = allSourceIds().associateWith { SourceProbeState(ServiceProbeStatus.Offline) }
            onGroupUpdated(offlineMap)
            return@coroutineScope 0 to probeGroups.size
        }

        val groupResults = probeGroups.map { group ->
            async(Dispatchers.IO) {
                val state = if (group.urls.size == 1) {
                    probeSingleUrl(group.urls[0])
                } else {
                    val sub = group.urls.map { url -> async(Dispatchers.IO) { probeSingleUrl(url) } }.awaitAll()
                    pickBest(sub)
                }
                val patch = group.sourceIds.associateWith { state }
                withContext(Dispatchers.Main.immediate) {
                    onGroupUpdated(patch)
                }
                state
            }
        }.awaitAll()

        val reachable = groupResults.count {
            it.status == ServiceProbeStatus.Reachable || it.status == ServiceProbeStatus.Slow
        }
        reachable to (groupResults.size - reachable)
    }

    private fun pickBest(states: List<SourceProbeState>): SourceProbeState {
        val ok = states.filter {
            it.status == ServiceProbeStatus.Reachable || it.status == ServiceProbeStatus.Slow
        }
        if (ok.isNotEmpty()) return ok.minBy { it.latencyMs }
        return states.firstOrNull { it.status == ServiceProbeStatus.Timeout }
            ?: states.firstOrNull()
            ?: SourceProbeState(ServiceProbeStatus.Unreachable)
    }

    private fun probeSingleUrl(urlStr: String): SourceProbeState {
        val startMs = SystemClock.elapsedRealtime()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "PocketDeck/1.0")
            }
            val code = conn.responseCode
            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            val status = when {
                code in 100..499 && elapsed <= SLOW_THRESHOLD_MS -> ServiceProbeStatus.Reachable
                code in 100..499 -> ServiceProbeStatus.Slow
                else -> ServiceProbeStatus.Unreachable
            }
            SourceProbeState(status = status, latencyMs = elapsed)
        } catch (_: SocketTimeoutException) {
            SourceProbeState(ServiceProbeStatus.Timeout, (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L))
        } catch (_: IOException) {
            SourceProbeState(ServiceProbeStatus.Unreachable, (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L))
        } catch (_: Throwable) {
            SourceProbeState(ServiceProbeStatus.Unreachable)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}

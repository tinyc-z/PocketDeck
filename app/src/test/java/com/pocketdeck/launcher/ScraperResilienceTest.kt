package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.HostCircuitBreaker
import com.pocketdeck.launcher.data.ScrapeLogStore
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.lan.WebFileSecurity
import com.pocketdeck.launcher.ui.settings.isScrapeLogErrorLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperResilienceTest {

    @Test
    fun circuitBreakerTripsAfterThresholdAndGuardsHalfOpenProbe() {
        var now = 10_000L
        val breaker = HostCircuitBreaker(
            failureThreshold = 3,
            cooldownMs = 120_000L,
            probeGraceMs = 20_000L,
            clockMs = { now },
        )
        val host = "raw.githubusercontent.com"

        assertFalse(breaker.shouldSkip(host))
        assertFalse(breaker.recordTransportFailure(host))
        assertFalse(breaker.recordTransportFailure(host))
        assertFalse(breaker.isCurrentlyOpen(host))

        // 第 3 次连续传输失败触发熔断
        assertTrue(breaker.recordTransportFailure(host))
        assertTrue(breaker.isCurrentlyOpen(host))
        assertTrue(breaker.shouldSkip(host))
        // 不影响其他健康域名
        assertFalse(breaker.isCurrentlyOpen("hasheous.org"))
        assertFalse(breaker.shouldSkip("hasheous.org"))

        // 冷却未到期保持熔断
        now += 119_999L
        assertTrue(breaker.shouldSkip(host))

        // 冷却到期：仅放行首个半开探针，并发第二个请求继续跳过防惊群
        now += 1L
        assertFalse(breaker.shouldSkip(host))
        assertTrue(breaker.shouldSkip(host))

        // 若半开探针继续失败，单次失败即可立刻续期 120s 冷却
        assertTrue(breaker.recordTransportFailure(host))
        assertTrue(breaker.isCurrentlyOpen(host))

        // 再次到期并成功收到 HTTP 响应后彻底恢复
        now += 120_000L
        assertFalse(breaker.shouldSkip(host))
        breaker.recordSuccess(host)
        assertFalse(breaker.isCurrentlyOpen(host))
        assertFalse(breaker.shouldSkip(host))
        // 成功清零计数，单次新失败不会立刻再熔断
        assertFalse(breaker.recordTransportFailure(host))
        assertFalse(breaker.isCurrentlyOpen(host))
    }

    @Test
    fun circuitBreakerResetAllAndHostExtraction() {
        var now = 5_000L
        val breaker = HostCircuitBreaker(failureThreshold = 2, cooldownMs = 60_000L, clockMs = { now })
        val url = "https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_Nintendo_Entertainment_System@master/Named_Boxarts/Ji%20Du%20Shan%20(CN)%5Bp%5D.png"
        assertEquals("cdn.jsdelivr.net", HostCircuitBreaker.extractHost(url))

        breaker.recordTransportFailureForUrl(url)
        assertTrue(breaker.recordTransportFailureForUrl(url))
        assertTrue(breaker.isUrlCurrentlyOpen(url))

        breaker.resetAll()
        assertFalse(breaker.isUrlCurrentlyOpen(url))
        assertFalse(breaker.shouldSkipUrl(url))
    }

    @Test
    fun libretroMirrorsUseFastlyInsteadOfDeadThumbnailsHostAndRespectBreaker() {
        val breaker = HostCircuitBreaker(failureThreshold = 1, cooldownMs = 60_000L)
        val system = "Nintendo - Nintendo Entertainment System"
        val file = "Ji Du Shan En Chou Ji (1996)(Waixing)(CN)[p].png"

        val cdnUrl = CoverSource.buildLibretroThumbUrlPure(system, "Named_Boxarts", file, CoverSource.MIRROR_JSDELIVR_CDN)
        val fastlyUrl = CoverSource.buildLibretroThumbUrlPure(system, "Named_Boxarts", file, CoverSource.MIRROR_JSDELIVR_FASTLY)
        val ghUrl = CoverSource.buildLibretroThumbUrlPure(system, "Named_Boxarts", file, CoverSource.MIRROR_GITHUB_RAW)

        assertTrue(cdnUrl.startsWith("https://cdn.jsdelivr.net/gh/libretro-thumbnails/"))
        assertTrue(fastlyUrl.startsWith("https://fastly.jsdelivr.net/gh/libretro-thumbnails/"))
        assertTrue(ghUrl.startsWith("https://raw.githubusercontent.com/libretro-thumbnails/"))
        assertTrue(cdnUrl.endsWith("%5Bp%5D.png"))
        assertTrue(WebFileSecurity.isAllowedCoverSourceUrl(fastlyUrl))
        assertEquals("jsdelivr_fastly", ScrapeLogStore.nodeFromUrl(fastlyUrl))

        // 现役镜像备选列表绝不出现已停用的 thumbnails.libretro.com
        val altsFromCdn = CoverSource.libretroAlternativeMirrorsFor(cdnUrl, breaker)
        assertEquals(listOf(CoverSource.MIRROR_JSDELIVR_FASTLY, CoverSource.MIRROR_GITHUB_RAW), altsFromCdn.map { it.first })
        assertTrue(altsFromCdn.none { "thumbnails.libretro.com" in it.second })

        // 旧版 thumbnails.libretro.com 缓存链接自动迁移到全部 3 个现役镜像
        val legacyUrl = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/Legend%20of%20Zelda%2C%20The%20(USA).png"
        val migrated = CoverSource.libretroAlternativeMirrorsFor(legacyUrl, breaker)
        assertEquals(
            listOf(CoverSource.MIRROR_JSDELIVR_CDN, CoverSource.MIRROR_JSDELIVR_FASTLY, CoverSource.MIRROR_GITHUB_RAW),
            migrated.map { it.first },
        )

        // cdn 与 github 熔断后，选路自动切到 fastly，且备选列表自动剔除熔断主机
        breaker.recordTransportFailure("cdn.jsdelivr.net")
        breaker.recordTransportFailure("raw.githubusercontent.com")
        assertEquals(
            CoverSource.MIRROR_JSDELIVR_FASTLY,
            CoverSource.pickHealthyLibretroMirror(CoverSource.MIRROR_JSDELIVR_CDN, breaker),
        )
        assertEquals(
            listOf(CoverSource.MIRROR_JSDELIVR_FASTLY),
            CoverSource.libretroAlternativeMirrorsFor(cdnUrl, breaker).map { it.first },
        )

        // 全部 3 个镜像都熔断时，备选列表为空，0ms 跳过
        breaker.recordTransportFailure("fastly.jsdelivr.net")
        assertTrue(CoverSource.libretroAlternativeMirrorsFor(cdnUrl, breaker).isEmpty())
    }

    @Test
    fun libretroFailoverSkipsAuthoritativeLive404ButRetriesTransportAndLegacy404() {
        val cdnUrl = "https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_NES@master/Named_Titles/Foo.png"
        val legacyUrl = "https://thumbnails.libretro.com/Nintendo%20-%20NES/Named_Boxarts/Foo.png"

        assertFalse(CoverSource.shouldFailoverLibretro(cdnUrl, statusCode = 404, transportError = false))
        assertTrue(CoverSource.shouldFailoverLibretro(cdnUrl, statusCode = null, transportError = true))
        assertTrue(CoverSource.shouldFailoverLibretro(cdnUrl, statusCode = 403, transportError = false))
        assertTrue(CoverSource.shouldFailoverLibretro(cdnUrl, statusCode = 429, transportError = false))
        assertTrue(CoverSource.shouldFailoverLibretro(cdnUrl, statusCode = 502, transportError = false))
        assertTrue(CoverSource.shouldFailoverLibretro(legacyUrl, statusCode = 404, transportError = false))
    }

    @Test
    fun parseRetryAfterHandlesHeadersJsonAndClamping() {
        val hasheousBody = """{ "error": "Rate limit exceeded.", "profile": "Default Rate Limit", "retryAfterSeconds": 1 }"""
        assertEquals(1_150L, ScraperClient.parseRetryAfterMs(null, hasheousBody))
        assertEquals(2_150L, ScraperClient.parseRetryAfterMs("2", hasheousBody))
        assertEquals(15_000L, ScraperClient.parseRetryAfterMs("999", null))
        assertNull(ScraperClient.parseRetryAfterMs(null, """{"error":"unknown"}"""))
        assertNull(ScraperClient.parseRetryAfterMs("0", null))
    }

    @Test
    fun scrapeLogErrorClassifierSeparates404MissesFromRealErrors() {
        val hashMiss = "[2026-11-23 16:15:45.291] [META] [hasheous] GET | HTTP 404 | 715ms | url=https://hasheous.org/api/v1/Lookup/ByHash/crc/6429C3F1 | result=miss (hash not in db)"
        val coverMiss = "[2026-11-23 16:15:45.862] [COVER] [jsdelivr] DOWNLOAD | HTTP 404 | 271ms | url=https://cdn.jsdelivr.net/a.png | result=miss (HTTP 404)"
        val rateLimit = "[2026-11-23 16:15:55.979] [META] [hasheous] GET | HTTP 429 | 373ms | url=https://hasheous.org/api/v1/Lookup/ByHash/crc/894EF894 | error=Rate limit exceeded."
        val sslErr = "[2026-11-23 16:15:45.590] [COVER] [github] DOWNLOAD | 5011ms | url=https://raw.githubusercontent.com/a.png | error=SSLHandshakeException: connection closed"
        val serverErr = "[2026-11-23 16:15:46.000] [META] [wikidata] GET | HTTP 503 | 410ms | url=https://www.wikidata.org/w/api.php"

        assertFalse(isScrapeLogErrorLine(hashMiss))
        assertFalse(isScrapeLogErrorLine(coverMiss))
        assertTrue(isScrapeLogErrorLine(rateLimit))
        assertTrue(isScrapeLogErrorLine(sslErr))
        assertTrue(isScrapeLogErrorLine(serverErr))
    }
}

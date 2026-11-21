package com.pocketdeck.launcher.data

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 统一的轻量 HTTP 请求日志拦截器，输出到 logcat（Tag: `PocketDeckHTTP`）。
 *
 * 用于快速验证 App 的网络请求是否合理：
 * - 实时显示并发数（`inflight`）、请求来源（`scraper` / `coil`）、HTTP 状态码、耗时与响应体大小；
 * - 区分正常失败与主动取消（`CANCELED`）；
 * - 自动脱敏 URL 查询参数中的密码与密钥（`apikey`、`devpassword`、`sspassword`、`client_secret`、`token`）。
 */
class HttpLogInterceptor(
    private val source: String,
) : Interceptor {

    companion object {
        const val TAG = "PocketDeckHTTP"
        private val seq = AtomicLong(0L)
        private val inflight = AtomicInteger(0)

        private val SENSITIVE_PARAMS = setOf(
            "apikey", "api_key", "key", "devpassword", "sspassword",
            "password", "client_secret", "secret", "token", "access_token",
        )

        fun sanitizeUrl(raw: String): String {
            val qIdx = raw.indexOf('?')
            if (qIdx < 0) return raw
            val base = raw.substring(0, qIdx)
            val query = raw.substring(qIdx + 1)
            val masked = query.split('&').joinToString("&") { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) {
                    part
                } else {
                    val k = part.substring(0, eq)
                    if (k.lowercase() in SENSITIVE_PARAMS) "$k=***" else part
                }
            }
            return "$base?$masked"
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val id = seq.incrementAndGet()
        val url = sanitizeUrl(req.url.toString())
        val cur = inflight.incrementAndGet()
        val t0 = System.nanoTime()

        logInfo("--> [$source] #$id inflight=$cur ${req.method} $url")
        return try {
            val resp = chain.proceed(req)
            val ms = (System.nanoTime() - t0) / 1_000_000L
            val len = resp.body?.contentLength() ?: -1L
            val left = inflight.decrementAndGet()
            logInfo("<-- [$source] #$id ${resp.code} ${ms}ms ${formatHttpLogBytes(len)} inflight=$left $url")
            resp
        } catch (e: Throwable) {
            val ms = (System.nanoTime() - t0) / 1_000_000L
            val left = inflight.decrementAndGet()
            val canceled = chain.call().isCanceled() ||
                (e is IOException && e.message?.contains("Canceled", ignoreCase = true) == true)
            val tag = if (canceled) "CANCELED" else "ERR"
            logWarn("<-- [$source] #$id $tag ${ms}ms inflight=$left $url (${e.javaClass.simpleName}: ${e.message})")
            throw e
        }
    }

    private fun logInfo(msg: String) {
        runCatching { Log.i(TAG, msg) }
    }

    private fun logWarn(msg: String) {
        runCatching { Log.w(TAG, msg) }
    }
}

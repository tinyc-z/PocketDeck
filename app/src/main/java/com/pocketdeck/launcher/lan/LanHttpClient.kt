package com.pocketdeck.launcher.lan

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket

/**
 * 局域网对端轻量 HTTP/HTTPS 客户端（供 QuickShare / LocalSend v2 外发请求共用）：
 * - 有界行长（8KB）、有界 Header 条数（64）与总字节（32KB），彻底阻断恶意对端无 `\n` 字节流引发零点击 OOM；
 * - 响应体按 16KB 分块动态增长，不按未验证的 `Content-Length` 预分配大数组；
 * - LocalSend HTTPS 支持握手后 X.509 证书 SHA-256 指纹常量时间校验（阻断局域网 MITM）。
 */
internal object LanHttpClient {

    const val MAX_LINE_BYTES = 8 * 1024
    const val MAX_HEADER_COUNT = 64
    const val MAX_TOTAL_HEADER_BYTES = 32 * 1024
    const val MAX_CONTROL_RESPONSE_BYTES = 4 * 1024 * 1024

    fun httpJson(
        lan: LanInterface,
        ip: String,
        port: Int,
        useHttps: Boolean,
        sslContext: SSLContext?,
        expectedFingerprint: String? = null,
        method: String,
        path: String,
        jsonBody: String?,
        readTimeoutMs: Int = 10_000,
        onSocketOpened: ((Socket) -> Unit)? = null,
    ): Pair<Int, String> {
        val sock = if (useHttps && sslContext != null) {
            LanInterfaces.openBoundTlsSocket(
                lan = lan,
                sslContext = sslContext,
                targetIp = ip,
                targetPort = port,
                connectTimeoutMs = 4000,
                readTimeoutMs = readTimeoutMs,
                expectedFingerprint = expectedFingerprint,
            )
        } else {
            LanInterfaces.openBoundSocket(lan, ip, port, connectTimeoutMs = 4000, readTimeoutMs = readTimeoutMs)
        }
        onSocketOpened?.invoke(sock)
        return sock.use { s ->
            val bodyBytes = jsonBody?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
            val out = BufferedOutputStream(s.getOutputStream())
            val reqHead = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: $ip:$port\r\n")
                append("Connection: close\r\n")
                if (jsonBody != null) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                append("\r\n")
            }
            out.write(reqHead.toByteArray(StandardCharsets.US_ASCII))
            if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
            out.flush()
            readHttpResponse(s.getInputStream())
        }
    }

    fun streamUpload(
        lan: LanInterface,
        ip: String,
        port: Int,
        useHttps: Boolean,
        sslContext: SSLContext?,
        expectedFingerprint: String? = null,
        path: String,
        file: File,
        isCancelled: () -> Boolean,
        onSocketOpened: ((Socket) -> Unit)? = null,
        onProgress: (sentBytes: Long, speedBps: Long) -> Unit,
    ): Int {
        val total = file.length()
        val sock = if (useHttps && sslContext != null) {
            LanInterfaces.openBoundTlsSocket(
                lan = lan,
                sslContext = sslContext,
                targetIp = ip,
                targetPort = port,
                connectTimeoutMs = 5000,
                readTimeoutMs = 120_000,
                expectedFingerprint = expectedFingerprint,
            )
        } else {
            LanInterfaces.openBoundSocket(lan, ip, port, connectTimeoutMs = 5000, readTimeoutMs = 120_000)
        }
        onSocketOpened?.invoke(sock)
        return sock.use { s ->
            val out = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
            val reqHead = buildString {
                append("POST $path HTTP/1.1\r\n")
                append("Host: $ip:$port\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: $total\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(reqHead.toByteArray(StandardCharsets.US_ASCII))
            val startMs = System.currentTimeMillis()
            var lastEmitMs = startMs
            var sent = 0L
            FileInputStream(file).use { fis ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (isCancelled()) break
                    val n = fis.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    sent += n
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 120L || sent >= total) {
                        lastEmitMs = now
                        val sec = ((now - startMs).coerceAtLeast(1L)) / 1000.0
                        onProgress(sent, (sent / sec).toLong())
                    }
                }
            }
            out.flush()
            if (isCancelled()) return 499
            readHttpResponse(s.getInputStream()).first
        }
    }

    fun readHttpResponse(
        rawStream: InputStream,
        maxBodyBytes: Int = MAX_CONTROL_RESPONSE_BYTES,
    ): Pair<Int, String> {
        val input = if (rawStream is BufferedInputStream) rawStream else BufferedInputStream(rawStream, 8 * 1024)
        val statusLine = readBoundedLine(input, MAX_LINE_BYTES) ?: return 500 to ""
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 500
        var contentLen = -1
        var chunked = false
        var headerCount = 0
        var totalHeaderBytes = statusLine.length + 2
        while (true) {
            val line = readBoundedLine(input, MAX_LINE_BYTES) ?: break
            if (line.isEmpty()) break
            headerCount++
            if (headerCount > MAX_HEADER_COUNT) {
                throw IOException("HTTP response header count exceeds $MAX_HEADER_COUNT")
            }
            totalHeaderBytes += line.length + 2
            if (totalHeaderBytes > MAX_TOTAL_HEADER_BYTES) {
                throw IOException("HTTP response headers exceed $MAX_TOTAL_HEADER_BYTES bytes")
            }
            val idx = line.indexOf(':')
            if (idx > 0) {
                val hName = line.substring(0, idx).trim()
                val hVal = line.substring(idx + 1).trim()
                if (hName.equals("content-length", ignoreCase = true)) {
                    contentLen = hVal.toIntOrNull()?.takeIf { it >= 0 } ?: -1
                } else if (hName.equals("transfer-encoding", ignoreCase = true) && hVal.contains("chunked", ignoreCase = true)) {
                    chunked = true
                }
            }
        }
        val cap = maxBodyBytes.coerceAtLeast(0)
        val body = if (chunked) {
            val baos = ByteArrayOutputStream(minOf(cap, 4096))
            while (baos.size() < cap) {
                val sizeLine = readBoundedLine(input, 4096)?.substringBefore(';')?.trim() ?: break
                val chunkSize = sizeLine.toIntOrNull(16)
                if (chunkSize == null || chunkSize < 0) {
                    throw IOException("Invalid response chunk size: $sizeLine")
                }
                if (chunkSize == 0) break
                var rem = chunkSize
                val tmp = ByteArray(minOf(chunkSize, 4096))
                while (rem > 0) {
                    val n = input.read(tmp, 0, minOf(rem, tmp.size))
                    if (n <= 0) break
                    val allowed = minOf(n, cap - baos.size())
                    if (allowed > 0) baos.write(tmp, 0, allowed)
                    rem -= n
                }
                readBoundedLine(input, 4096) // trailing CRLF
            }
            String(baos.toByteArray(), StandardCharsets.UTF_8)
        } else if (contentLen > 0) {
            val target = minOf(contentLen, cap)
            val baos = ByteArrayOutputStream(minOf(target, 16 * 1024))
            val tmp = ByteArray(minOf(target, 16 * 1024))
            var rem = target
            while (rem > 0) {
                val n = input.read(tmp, 0, minOf(tmp.size, rem))
                if (n <= 0) break
                baos.write(tmp, 0, n)
                rem -= n
            }
            String(baos.toByteArray(), StandardCharsets.UTF_8)
        } else if (contentLen == 0) {
            ""
        } else {
            val baos = ByteArrayOutputStream(minOf(cap, 4096))
            val tmp = ByteArray(4096)
            while (baos.size() < cap) {
                val maxRead = minOf(tmp.size, cap - baos.size())
                val n = input.read(tmp, 0, maxRead)
                if (n <= 0) break
                baos.write(tmp, 0, n)
            }
            String(baos.toByteArray(), StandardCharsets.UTF_8)
        }
        return code to body
    }

    fun readBoundedLine(input: InputStream, maxBytes: Int = MAX_LINE_BYTES): String? {
        val baos = ByteArrayOutputStream(128)
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                if (baos.size() >= maxBytes) {
                    throw IOException("HTTP line exceeds $maxBytes bytes")
                }
                baos.write(b)
            }
            b = input.read()
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    /** 校验 TLS 握手后的对端 X.509 证书 SHA-256 指纹（支持大小写/冒号格式，常量时间比对；缺失或非法指纹一律失败关闭）。 */
    fun verifyPeerCertificateFingerprint(tls: SSLSocket, expectedFingerprint: String?) {
        val normalizedExpected = normalizeFingerprint(expectedFingerprint)
            ?: throw SSLPeerUnverifiedException("Missing or malformed expected TLS fingerprint")
        val cert = runCatching { tls.session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
            ?: throw SSLPeerUnverifiedException("Missing peer X.509 certificate")
        val actual = sha256HexLower(cert.encoded)
        val ok = MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.US_ASCII),
            normalizedExpected.toByteArray(StandardCharsets.US_ASCII),
        )
        if (!ok) {
            throw SSLPeerUnverifiedException("Peer TLS certificate fingerprint mismatch")
        }
    }

    internal fun normalizeFingerprint(raw: String?): String? {
        val clean = raw?.replace(":", "")?.trim()?.lowercase().orEmpty()
        return clean.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
    }

    internal fun sha256HexLower(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

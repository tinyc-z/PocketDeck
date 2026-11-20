package com.pocketdeck.launcher.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

/**
 * 轻量级纯 Kotlin 协程 HTTP/1.1 服务端：
 * - 严格绑定指定物理网卡 IP，支持首选端口被占用时自动递增端口
 * - 支持大文件流式上传/下载（不占用大内存，支持 HTTP Range 断点/分段下载）
 * - 可选传入 [sslContext] 启用原生 TLS/HTTPS 服务（用于 LocalSend v2 HTTPS 协议兼容）
 */
class HttpMiniServer(
    private val bindAddress: Inet4Address,
    private val preferredPort: Int,
    private val portScanCount: Int = 20,
    private val sslContext: SSLContext? = null,
    private val handler: suspend (HttpRequest, HttpResponseWriter) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    var boundPort: Int = -1
        private set

    fun start(): Int {
        stop()
        var ss: ServerSocket? = null
        var chosen = -1
        val sslFactory = sslContext?.serverSocketFactory
        for (offset in 0 until portScanCount) {
            val port = preferredPort + offset
            val candidate = runCatching {
                val sock = sslFactory?.createServerSocket() ?: ServerSocket()
                sock.apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindAddress, port), 64)
                    if (this is SSLServerSocket) {
                        needClientAuth = false
                        wantClientAuth = false
                    }
                }
            }.getOrNull()
            if (candidate != null) {
                ss = candidate
                chosen = port
                break
            }
        }
        val activeSocket = ss ?: error("Cannot bind port $preferredPort..${preferredPort + portScanCount - 1} on ${bindAddress.hostAddress}")
        serverSocket = activeSocket
        boundPort = chosen
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        acceptJob = s.launch {
            while (isActive && !activeSocket.isClosed) {
                val client = runCatching { activeSocket.accept() }.getOrNull() ?: break
                s.launch { handleClient(client) }
            }
        }
        return chosen
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        scope?.cancel()
        scope = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        closeAllSockets()
        boundPort = -1
    }

    /** 强行关闭指定客户端 IP 的所有活跃 HTTP Socket（会立即中断其进行中的上传与下载）。 */
    fun closeSocketsByIp(ip: String) {
        if (ip.isBlank()) return
        for (s in activeSockets) {
            if (s.inetAddress?.hostAddress == ip) {
                runCatching { s.close() }
            }
        }
    }

    /** 强行关闭当前所有活跃的 HTTP Socket。 */
    fun closeAllSockets() {
        for (s in activeSockets) {
            runCatching { s.close() }
        }
        activeSockets.clear()
    }

    private suspend fun handleClient(socket: Socket) {
        activeSockets.add(socket)
        try {
            socket.use { activeSocket ->
            runCatching {
                activeSocket.soTimeout = 100_000
                val rawRemoteIp = activeSocket.inetAddress?.hostAddress.orEmpty()
                val rawIn = BufferedInputStream(activeSocket.getInputStream(), 64 * 1024)
                val rawOut = BufferedOutputStream(activeSocket.getOutputStream(), 64 * 1024)

                val requestLine = readAsciiLine(rawIn) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val fullTarget = parts[1]
                val qIdx = fullTarget.indexOf('?')
                val rawPath = if (qIdx >= 0) fullTarget.substring(0, qIdx) else fullTarget
                val rawQuery = if (qIdx >= 0) fullTarget.substring(qIdx + 1) else ""

                val headers = LinkedHashMap<String, String>()
                while (true) {
                    val line = readAsciiLine(rawIn) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val k = line.substring(0, colon).trim().lowercase()
                        val v = line.substring(colon + 1).trim()
                        headers[k] = v
                    }
                }

                val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                val isChunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
                val cookies = parseCookies(headers["cookie"].orEmpty())
                val queryParams = parseQuery(rawQuery)
                val req = HttpRequest(
                    method = method,
                    path = urlDecode(rawPath.replace("+", "%2B")),
                    query = queryParams,
                    headers = headers,
                    cookies = cookies,
                    contentLength = contentLength,
                    isChunked = isChunked,
                    bodyStream = rawIn,
                    remoteIp = rawRemoteIp,
                    cancelConnection = { runCatching { activeSocket.close() } },
                    isPeerClosed = {
                        if (activeSocket.isClosed || activeSocket.isInputShutdown) {
                            true
                        } else {
                            val prevTimeout = runCatching { activeSocket.soTimeout }.getOrDefault(0)
                            try {
                                activeSocket.soTimeout = 120
                                rawIn.mark(1)
                                val b = rawIn.read()
                                if (b == -1) {
                                    true
                                } else {
                                    runCatching { rawIn.reset() }
                                    false
                                }
                            } catch (_: java.net.SocketTimeoutException) {
                                false
                            } catch (_: Throwable) {
                                true
                            } finally {
                                runCatching { activeSocket.soTimeout = prevTimeout }
                            }
                        }
                    },
                )
                val writer = HttpResponseWriter(rawOut)
                handler(req, writer)
                rawOut.flush()
            }
        }
        } finally {
            activeSockets.remove(socket)
        }
    }

    private fun readAsciiLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream(128)
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 16 * 1024) break
            b = input.read()
        }
        return buf.toString(StandardCharsets.UTF_8.name())
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq >= 0) {
                out[urlDecode(pair.substring(0, eq))] = urlDecode(pair.substring(eq + 1))
            } else {
                out[urlDecode(pair)] = ""
            }
        }
        return out
    }

    private fun parseCookies(header: String): Map<String, String> {
        if (header.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in header.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0) {
                out[part.substring(0, eq).trim()] = part.substring(eq + 1).trim()
            }
        }
        return out
    }

    private fun urlDecode(s: String): String =
        runCatching { URLDecoder.decode(s, StandardCharsets.UTF_8.name()) }.getOrDefault(s)
}

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val cookies: Map<String, String>,
    val contentLength: Long,
    val isChunked: Boolean = false,
    val bodyStream: InputStream,
    val remoteIp: String,
    val cancelConnection: () -> Unit = {},
    val isPeerClosed: () -> Boolean = { false },
) {
    fun readBodyBytes(maxBytes: Int = 2 * 1024 * 1024): ByteArray {
        if (isChunked) {
            val out = ByteArrayOutputStream()
            copyChunkedTo(out, maxBytes.toLong())
            return out.toByteArray()
        }
        val len = contentLength.coerceIn(0L, maxBytes.toLong()).toInt()
        if (len <= 0) return ByteArray(0)
        val out = ByteArray(len)
        var readTotal = 0
        while (readTotal < len) {
            val n = bodyStream.read(out, readTotal, len - readTotal)
            if (n <= 0) break
            readTotal += n
        }
        return if (readTotal == len) out else out.copyOf(readTotal)
    }

    fun readBodyUtf8(maxBytes: Int = 2 * 1024 * 1024): String =
        String(readBodyBytes(maxBytes), StandardCharsets.UTF_8)

    /** 将请求体流式写入 [OutputStream]，精确读取 [contentLength] 字节（支持 chunked 与大文件无 OOM）。 */
    fun copyBodyTo(out: OutputStream, onProgress: (Long) -> Unit = {}): Long {
        if (isChunked) {
            return copyChunkedTo(out, Long.MAX_VALUE, onProgress)
        }
        var remaining = contentLength
        if (remaining <= 0L) return 0L
        val buf = ByteArray(64 * 1024)
        var copied = 0L
        while (remaining > 0L) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val n = bodyStream.read(buf, 0, toRead)
            if (n <= 0) break
            out.write(buf, 0, n)
            copied += n
            remaining -= n
            onProgress(copied)
        }
        out.flush()
        return copied
    }

    private fun copyChunkedTo(
        out: OutputStream,
        maxBytes: Long,
        onProgress: (Long) -> Unit = {},
    ): Long {
        var copied = 0L
        val buf = ByteArray(64 * 1024)
        while (true) {
            val sizeLine = readChunkLine() ?: break
            val hex = sizeLine.substringBefore(';').trim()
            val chunkSize = hex.toLongOrNull(16) ?: break
            if (chunkSize == 0L) {
                // Consume trailing headers until empty line
                while (true) {
                    val t = readChunkLine() ?: break
                    if (t.isEmpty()) break
                }
                break
            }
            var rem = chunkSize
            while (rem > 0L) {
                val toRead = minOf(buf.size.toLong(), rem).toInt()
                val n = bodyStream.read(buf, 0, toRead)
                if (n <= 0) return copied
                val writable = minOf(n.toLong(), (maxBytes - copied).coerceAtLeast(0L)).toInt()
                if (writable > 0) {
                    out.write(buf, 0, writable)
                    copied += writable
                    onProgress(copied)
                }
                rem -= n
            }
            // Consume CRLF after chunk data
            readChunkLine()
        }
        out.flush()
        return copied
    }

    private fun readChunkLine(): String? {
        val baos = ByteArrayOutputStream(32)
        var b = bodyStream.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            if (baos.size() > 4096) break
            b = bodyStream.read()
        }
        return baos.toString(StandardCharsets.US_ASCII.name())
    }
}

class HttpResponseWriter(private val out: OutputStream) {
    var defaultHeaders: Map<String, String> = emptyMap()
    var onBytesSent: ((Long) -> Unit)? = null

    fun sendText(
        status: Int,
        body: String,
        contentType: String = "text/plain; charset=utf-8",
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        sendBytes(status, body.toByteArray(StandardCharsets.UTF_8), contentType, extraHeaders)
    }

    fun sendJson(
        status: Int,
        json: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        sendBytes(status, json.toByteArray(StandardCharsets.UTF_8), "application/json; charset=utf-8", extraHeaders)
    }

    fun sendBytes(
        status: Int,
        bytes: ByteArray,
        contentType: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        writeHeader(status, bytes.size.toLong(), contentType, extraHeaders)
        out.write(bytes)
        out.flush()
        if (bytes.isNotEmpty()) {
            onBytesSent?.invoke(bytes.size.toLong())
        }
    }

    fun sendFile(
        file: File,
        contentType: String = "application/octet-stream",
        attachmentName: String? = null,
        rangeHeader: String? = null,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Long {
        if (!file.isFile || !file.canRead()) {
            sendText(404, "Not found")
            return 0L
        }
        val totalLen = file.length()
        val extra = LinkedHashMap<String, String>()
        extra["Accept-Ranges"] = "bytes"
        if (!attachmentName.isNullOrBlank()) {
            val encoded = java.net.URLEncoder.encode(attachmentName, "UTF-8").replace("+", "%20")
            val ascii = attachmentName.replace(Regex("""[^\x20-\x7E]"""), "_").replace("\"", "_")
            extra["Content-Disposition"] = "attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded"
        }

        var start = 0L
        var end = (totalLen - 1).coerceAtLeast(0L)
        var partial = false
        if (totalLen > 0 && !rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val spec = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val sPart = spec.substring(0, dash).trim()
                val ePart = spec.substring(dash + 1).trim()
                val s = sPart.toLongOrNull()
                val e = ePart.toLongOrNull()
                if (s != null && s in 0 until totalLen) {
                    start = s
                    if (e != null && e in s until totalLen) end = e
                    partial = true
                } else if (s == null && e != null && e > 0) {
                    start = (totalLen - e).coerceAtLeast(0L)
                    partial = true
                }
            }
        }

        val len = if (totalLen == 0L) 0L else (end - start + 1L)
        if (partial) {
            extra["Content-Range"] = "bytes $start-$end/$totalLen"
        }
        writeHeader(if (partial) 206 else 200, len, contentType, extra)
        var sent = 0L
        if (len > 0L) {
            FileInputStream(file).use { fis ->
                if (start > 0L) {
                    var toSkip = start
                    while (toSkip > 0L) {
                        val skipped = fis.skip(toSkip)
                        if (skipped <= 0L) break
                        toSkip -= skipped
                    }
                }
                val buf = ByteArray(64 * 1024)
                var remaining = len
                while (remaining > 0L) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val n = fis.read(buf, 0, toRead)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    sent += n
                    remaining -= n
                    onProgress(sent, len)
                }
            }
        }
        out.flush()
        return sent
    }

    private fun writeHeader(
        status: Int,
        contentLength: Long,
        contentType: String,
        extraHeaders: Map<String, String>,
    ) {
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            204 -> "No Content"
            206 -> "Partial Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            408 -> "Request Timeout"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            422 -> "Unprocessable Entity"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            else -> "Error"
        }
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
        sb.append("Content-Type: ").append(contentType).append("\r\n")
        sb.append("Content-Length: ").append(contentLength).append("\r\n")
        if (extraHeaders.keys.none { it.equals("Cache-Control", ignoreCase = true) }) {
            sb.append("Cache-Control: no-store\r\n")
        }
        sb.append("X-Content-Type-Options: nosniff\r\n")
        sb.append("X-Frame-Options: DENY\r\n")
        sb.append("Referrer-Policy: no-referrer\r\n")
        sb.append("Connection: close\r\n")
        for ((k, v) in defaultHeaders) {
            if (k !in extraHeaders) {
                sb.append(k).append(": ").append(v).append("\r\n")
            }
        }
        for ((k, v) in extraHeaders) {
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.US_ASCII))
    }
}

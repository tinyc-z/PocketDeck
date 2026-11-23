package com.pocketdeck.launcher.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

/**
 * 轻量级纯 Kotlin 协程 HTTP/1.1 服务端：
 * - 严格绑定指定物理网卡 IP，支持首选端口被占用时自动递增端口
 * - 限制最大并发连接数与专用有界 IO 调度器，避免慢连接耗尽全局 `Dispatchers.IO`
 * - 强制请求头行长/条数/总字节上限与短读取超时，阻断 Header 洪流 OOM
 * - 支持大文件流式上传/下载（不占用大内存，支持 HTTP Range 断点/分段下载及严格 Chunked 完整性校验）
 * - 可选传入 [sslContext] 启用原生 TLS/HTTPS 服务（用于 LocalSend v2 HTTPS 协议兼容）
 */
class HttpMiniServer(
    private val bindAddress: Inet4Address,
    private val preferredPort: Int,
    private val portScanCount: Int = 20,
    private val sslContext: SSLContext? = null,
    private val maxConcurrentConnections: Int = MAX_CONCURRENT_CONNECTIONS,
    private val handler: suspend (HttpRequest, HttpResponseWriter) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val connectionSlots = Semaphore(maxConcurrentConnections.coerceAtLeast(1))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serverDispatcher = Dispatchers.IO.limitedParallelism(MAX_SERVER_WORKERS)

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
        val s = CoroutineScope(SupervisorJob() + serverDispatcher)
        scope = s
        acceptJob = s.launch {
            while (isActive && !activeSocket.isClosed) {
                val client = runCatching { activeSocket.accept() }.getOrNull() ?: break
                if (!connectionSlots.tryAcquire()) {
                    runCatching {
                        client.soTimeout = 2_000
                        HttpResponseWriter(client.getOutputStream()).sendText(503, "Too many connections")
                    }
                    runCatching { client.close() }
                    continue
                }
                s.launch {
                    try {
                        handleClient(client)
                    } finally {
                        connectionSlots.release()
                    }
                }
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
                    activeSocket.soTimeout = HEADER_TIMEOUT_MS
                    val rawRemoteIp = activeSocket.inetAddress?.hostAddress.orEmpty()
                    val rawIn = BufferedInputStream(activeSocket.getInputStream(), 32 * 1024)
                    val rawOut = BufferedOutputStream(activeSocket.getOutputStream(), 32 * 1024)
                    val writer = HttpResponseWriter(rawOut)

                    val requestLine = try {
                        readBoundedAsciiLine(rawIn, MAX_HEADER_LINE_BYTES)
                    } catch (_: HeaderLimitException) {
                        writer.sendText(431, "Request line too large")
                        rawOut.flush()
                        return
                    } ?: return

                    val parts = requestLine.split(' ')
                    if (parts.size < 2) {
                        writer.sendText(400, "Bad request")
                        rawOut.flush()
                        return
                    }
                    val method = parts[0].uppercase()
                    val fullTarget = parts[1]
                    val qIdx = fullTarget.indexOf('?')
                    val rawPath = if (qIdx >= 0) fullTarget.substring(0, qIdx) else fullTarget
                    val rawQuery = if (qIdx >= 0) fullTarget.substring(qIdx + 1) else ""

                    val headers = try {
                        readBoundedHeaders(rawIn)
                    } catch (_: HeaderLimitException) {
                        writer.sendText(431, "Request header fields too large")
                        rawOut.flush()
                        return
                    }

                    val rawContentLength = headers["content-length"]
                    val contentLength = if (rawContentLength != null) {
                        val parsed = rawContentLength.toLongOrNull()
                        if (parsed == null || parsed < 0L) {
                            writer.sendText(400, "Invalid Content-Length")
                            rawOut.flush()
                            return
                        }
                        parsed
                    } else {
                        0L
                    }
                    val isChunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
                    val cookies = parseCookies(headers["cookie"].orEmpty())
                    val queryParams = parseQuery(rawQuery)

                    // 请求头校验通过后放宽为传输超时，支持大文件流式上传与 SSE 长连接
                    activeSocket.soTimeout = TRANSFER_TIMEOUT_MS

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
                    handler(req, writer)
                    rawOut.flush()
                }
            }
        } finally {
            activeSockets.remove(socket)
        }
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

    internal class HeaderLimitException(message: String) : IOException(message)

    companion object {
        const val MAX_CONCURRENT_CONNECTIONS = 32
        const val MAX_SERVER_WORKERS = 16
        const val HEADER_TIMEOUT_MS = 10_000
        const val TRANSFER_TIMEOUT_MS = 100_000
        const val MAX_HEADER_LINE_BYTES = 8 * 1024
        const val MAX_HEADER_COUNT = 64
        const val MAX_TOTAL_HEADER_BYTES = 32 * 1024

        internal fun readBoundedAsciiLine(input: InputStream, maxBytes: Int = MAX_HEADER_LINE_BYTES): String? {
            val buf = ByteArrayOutputStream(128)
            var b = input.read()
            if (b == -1) return null
            while (b != -1) {
                if (b == '\n'.code) break
                if (b != '\r'.code) {
                    if (buf.size() >= maxBytes) {
                        throw HeaderLimitException("Header line exceeds $maxBytes bytes")
                    }
                    buf.write(b)
                }
                b = input.read()
            }
            return buf.toString(StandardCharsets.UTF_8.name())
        }

        internal fun readBoundedHeaders(
            input: InputStream,
            maxCount: Int = MAX_HEADER_COUNT,
            maxTotalBytes: Int = MAX_TOTAL_HEADER_BYTES,
            maxLineBytes: Int = MAX_HEADER_LINE_BYTES,
        ): LinkedHashMap<String, String> {
            val headers = LinkedHashMap<String, String>()
            var totalBytes = 0
            var count = 0
            while (true) {
                val line = readBoundedAsciiLine(input, maxLineBytes) ?: break
                if (line.isEmpty()) break
                count++
                if (count > maxCount) {
                    throw HeaderLimitException("Header count exceeds $maxCount")
                }
                totalBytes += line.length + 2
                if (totalBytes > maxTotalBytes) {
                    throw HeaderLimitException("Total header bytes exceed $maxTotalBytes")
                }
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim().lowercase()
                    val v = line.substring(colon + 1).trim()
                    headers[k] = v
                }
            }
            return headers
        }
    }
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
    /** 按 16KB 分块动态增长读取请求体，避免按未验证的宣称 `Content-Length` 预先分配大数组 OOM。 */
    fun readBodyBytes(maxBytes: Int = 2 * 1024 * 1024): ByteArray {
        val cap = maxBytes.coerceAtLeast(0)
        if (cap == 0) return ByteArray(0)
        if (isChunked) {
            val out = ByteArrayOutputStream(minOf(cap, 4096))
            copyChunkedTo(out, cap.toLong())
            return out.toByteArray()
        }
        val target = contentLength.coerceIn(0L, cap.toLong()).toInt()
        if (target <= 0) return ByteArray(0)
        val out = ByteArrayOutputStream(minOf(target, 16 * 1024))
        val chunk = ByteArray(minOf(target, 16 * 1024))
        var remaining = target
        while (remaining > 0) {
            val n = bodyStream.read(chunk, 0, minOf(chunk.size, remaining))
            if (n <= 0) break
            out.write(chunk, 0, n)
            remaining -= n
        }
        return out.toByteArray()
    }

    fun readBodyUtf8(maxBytes: Int = 2 * 1024 * 1024): String =
        String(readBodyBytes(maxBytes), StandardCharsets.UTF_8)

    /** 将请求体流式写入 [OutputStream]，精确读取 [contentLength] 字节（支持 chunked 完整性校验与最大字节上限）。 */
    fun copyBodyTo(
        out: OutputStream,
        maxBytes: Long = Long.MAX_VALUE,
        onProgress: (Long) -> Unit = {},
    ): Long {
        if (isChunked) {
            return copyChunkedTo(out, maxBytes, onProgress)
        }
        if (contentLength < 0L) throw IOException("Invalid contentLength: $contentLength")
        if (contentLength > maxBytes) throw IOException("Body exceeds maxBytes limit ($maxBytes)")
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
        var sawTerminator = false
        while (true) {
            val sizeLine = readChunkLine()
                ?: throw EOFException("Truncated chunked body before chunk size")
            val hex = sizeLine.substringBefore(';').trim()
            val chunkSize = hex.toLongOrNull(16)
            if (chunkSize == null || chunkSize < 0L) {
                throw IOException("Invalid chunk size: $hex")
            }
            if (chunkSize == 0L) {
                // Consume trailing headers until empty line
                while (true) {
                    val t = readChunkLine() ?: break
                    if (t.isEmpty()) break
                }
                sawTerminator = true
                break
            }
            var rem = chunkSize
            while (rem > 0L) {
                val toRead = minOf(buf.size.toLong(), rem).toInt()
                val n = bodyStream.read(buf, 0, toRead)
                if (n <= 0) throw EOFException("Truncated chunk payload ($rem bytes remaining)")
                if (copied + n > maxBytes) {
                    throw IOException("Chunked body exceeds maxBytes limit ($maxBytes)")
                }
                out.write(buf, 0, n)
                copied += n
                onProgress(copied)
                rem -= n
            }
            // Consume CRLF after chunk data
            readChunkLine() ?: throw EOFException("Truncated chunk trailer CRLF")
        }
        if (!sawTerminator) {
            throw EOFException("Missing terminating zero-length chunk")
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
            if (b != '\r'.code) {
                if (baos.size() >= 4096) {
                    throw IOException("Chunk header line exceeds 4096 bytes")
                }
                baos.write(b)
            }
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

    /** 启动 Server-Sent Events 长连接响应头。 */
    fun startEventStream() {
        val sb = StringBuilder()
            .append("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: text/event-stream; charset=utf-8\r\n")
            .append("Cache-Control: no-cache, no-transform\r\n")
            .append("Connection: close\r\n")
            .append("X-Accel-Buffering: no\r\n")
        for ((k, v) in defaultHeaders) {
            val safeKey = sanitizeHeaderToken(k)
            if (safeKey.isNotEmpty()) {
                sb.append(safeKey).append(": ").append(sanitizeHeaderValue(v)).append("\r\n")
            }
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.ISO_8859_1))
        out.flush()
    }

    /** 写入一条原始 SSE 文本块；若对端已断开返回 false。 */
    fun sendSseRaw(chunk: String): Boolean = runCatching {
        val bytes = chunk.toByteArray(StandardCharsets.UTF_8)
        out.write(bytes)
        out.flush()
        true
    }.getOrDefault(false)

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
            val ascii = attachmentName
                .replace(Regex("""[^\x20-\x7E]"""), "_")
                .replace("\"", "_")
                .replace("\\", "_")
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
            304 -> "Not Modified"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            408 -> "Request Timeout"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            422 -> "Unprocessable Entity"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            507 -> "Insufficient Storage"
            else -> "Error"
        }
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
        sb.append("Content-Type: ").append(sanitizeHeaderValue(contentType)).append("\r\n")
        sb.append("Content-Length: ").append(contentLength).append("\r\n")
        if (extraHeaders.keys.none { it.equals("Cache-Control", ignoreCase = true) }) {
            sb.append("Cache-Control: no-store\r\n")
        }
        if (extraHeaders.keys.none { it.equals("X-Content-Type-Options", ignoreCase = true) }) {
            sb.append("X-Content-Type-Options: nosniff\r\n")
        }
        if (extraHeaders.keys.none { it.equals("X-Frame-Options", ignoreCase = true) }) {
            sb.append("X-Frame-Options: DENY\r\n")
        }
        if (extraHeaders.keys.none { it.equals("Referrer-Policy", ignoreCase = true) }) {
            sb.append("Referrer-Policy: no-referrer\r\n")
        }
        sb.append("Connection: close\r\n")
        for ((k, v) in defaultHeaders) {
            if (k !in extraHeaders) {
                val safeKey = sanitizeHeaderToken(k)
                if (safeKey.isNotEmpty()) {
                    sb.append(safeKey).append(": ").append(sanitizeHeaderValue(v)).append("\r\n")
                }
            }
        }
        for ((k, v) in extraHeaders) {
            val safeKey = sanitizeHeaderToken(k)
            if (safeKey.isNotEmpty()) {
                sb.append(safeKey).append(": ").append(sanitizeHeaderValue(v)).append("\r\n")
            }
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.US_ASCII))
    }

    companion object {
        /** 剥离 HTTP 响应头名称中的 CRLF/控制字符/冒号，阻断响应拆分注入。 */
        internal fun sanitizeHeaderToken(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (ch in raw) {
                if (ch.code in 0x21..0x7E && ch != ':') sb.append(ch)
            }
            return sb.toString()
        }

        /** 剥离 HTTP 响应头值中的 CR/LF/NUL，阻断 CRLF 响应拆分注入。 */
        internal fun sanitizeHeaderValue(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (ch in raw) {
                if (ch != '\r' && ch != '\n' && ch != '\u0000') sb.append(ch)
            }
            return sb.toString()
        }
    }
}

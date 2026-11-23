package com.pocketdeck.launcher.lan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 覆盖 [HttpMiniServer] / [HttpRequest] / [HttpResponseWriter] 协议边界、流式防 OOM 与抗畸形输入能力：
 * - Header 行长（8KB）/ 条数（64）/ 总字节（32KB）越界抛出 [HttpMiniServer.HeaderLimitException] 并返回 431
 * - 负数或非数字 Content-Length 返回 400
 * - 并发连接上限触发 503
 * - `readBodyBytes` 面对虚高 `Content-Length` 按 16KB 分块读取不发生预分配 OOM
 * - Chunked 传输：正常流、截断流抛 EOFException、负块长抛 IOException、超出 `maxBytes` 抛 IOException
 * - `HttpResponseWriter` 剥离头字段 CR/LF 注入、`sendFile` 转义反斜杠、HTTP Range 206 分段响应
 */
class HttpMiniServerParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `readBoundedAsciiLine rejects lines exceeding limit`() {
        val ok = ByteArrayInputStream("GET /ok HTTP/1.1\r\n".toByteArray(StandardCharsets.US_ASCII))
        assertEquals("GET /ok HTTP/1.1", HttpMiniServer.readBoundedAsciiLine(ok, 64))

        val huge = ByteArray(100) { 'A'.code.toByte() }
        try {
            HttpMiniServer.readBoundedAsciiLine(ByteArrayInputStream(huge), 64)
            fail("Expected HeaderLimitException for oversized line")
        } catch (_: HttpMiniServer.HeaderLimitException) {
            // expected
        }
    }

    @Test
    fun `readBoundedHeaders rejects excessive header count and total bytes`() {
        val tooMany = buildString {
            repeat(65) { i -> append("X-H-$i: v\r\n") }
            append("\r\n")
        }
        try {
            HttpMiniServer.readBoundedHeaders(ByteArrayInputStream(tooMany.toByteArray(StandardCharsets.US_ASCII)))
            fail("Expected HeaderLimitException when count > 64")
        } catch (_: HttpMiniServer.HeaderLimitException) {
            // expected
        }

        val tooLargeTotal = buildString {
            val valStr = "x".repeat(4000)
            repeat(10) { i -> append("X-Big-$i: $valStr\r\n") }
            append("\r\n")
        }
        try {
            HttpMiniServer.readBoundedHeaders(ByteArrayInputStream(tooLargeTotal.toByteArray(StandardCharsets.US_ASCII)))
            fail("Expected HeaderLimitException when total bytes > 32KB")
        } catch (_: HttpMiniServer.HeaderLimitException) {
            // expected
        }
    }

    @Test
    fun `readBodyBytes grows dynamically and does not preallocate claimed huge Content-Length`() {
        val payload = "tiny".toByteArray(StandardCharsets.UTF_8)
        val req = HttpRequest(
            method = "POST",
            path = "/upload",
            query = emptyMap(),
            headers = mapOf("content-length" to "2097152"),
            cookies = emptyMap(),
            contentLength = 2L * 1024 * 1024,
            bodyStream = ByteArrayInputStream(payload),
            remoteIp = "127.0.0.1",
        )
        assertArrayEquals(payload, req.readBodyBytes(maxBytes = 2 * 1024 * 1024))
    }

    @Test
    fun `copyBodyTo validates chunked framing truncation negative size and maxBytes`() {
        fun chunkedReq(raw: String): HttpRequest = HttpRequest(
            method = "POST",
            path = "/chunked",
            query = emptyMap(),
            headers = mapOf("transfer-encoding" to "chunked"),
            cookies = emptyMap(),
            contentLength = 0L,
            isChunked = true,
            bodyStream = ByteArrayInputStream(raw.toByteArray(StandardCharsets.UTF_8)),
            remoteIp = "127.0.0.1",
        )

        // 1. 合法 chunked 完整写入
        val validOut = ByteArrayOutputStream()
        val copied = chunkedReq("5\r\nHello\r\n6\r\n World\r\n0\r\n\r\n").copyBodyTo(validOut, maxBytes = 64L)
        assertEquals(11L, copied)
        assertEquals("Hello World", validOut.toString(StandardCharsets.UTF_8.name()))

        // 2. 缺少结束块 0\r\n\r\n 的截断流必须抛 EOFException
        try {
            chunkedReq("5\r\nHello\r\n").copyBodyTo(ByteArrayOutputStream(), maxBytes = 64L)
            fail("Expected EOFException on missing terminating 0 chunk")
        } catch (_: EOFException) {
            // expected
        }

        // 3. chunk 数据体中途截断抛 EOFException
        try {
            chunkedReq("a\r\n1234").copyBodyTo(ByteArrayOutputStream(), maxBytes = 64L)
            fail("Expected EOFException on truncated chunk payload")
        } catch (_: EOFException) {
            // expected
        }

        // 4. 负十六进制块长抛 IOException
        try {
            chunkedReq("-5\r\nHello\r\n0\r\n\r\n").copyBodyTo(ByteArrayOutputStream(), maxBytes = 64L)
            fail("Expected IOException on negative chunk size")
        } catch (_: IOException) {
            // expected
        }

        // 5. 超出 maxBytes 上限抛 IOException
        try {
            chunkedReq("5\r\nHello\r\n6\r\n World\r\n0\r\n\r\n").copyBodyTo(ByteArrayOutputStream(), maxBytes = 8L)
            fail("Expected IOException when chunked payload exceeds maxBytes")
        } catch (_: IOException) {
            // expected
        }

        // 6. 普通 Content-Length 超过 maxBytes 上限抛 IOException
        val fixedReq = HttpRequest(
            method = "POST",
            path = "/fixed",
            query = emptyMap(),
            headers = mapOf("content-length" to "10"),
            cookies = emptyMap(),
            contentLength = 10L,
            bodyStream = ByteArrayInputStream("0123456789".toByteArray(StandardCharsets.UTF_8)),
            remoteIp = "127.0.0.1",
        )
        try {
            fixedReq.copyBodyTo(ByteArrayOutputStream(), maxBytes = 5L)
            fail("Expected IOException when fixed body exceeds maxBytes")
        } catch (_: IOException) {
            // expected
        }
    }

    @Test
    fun `responseWriter strips CRLF injection escapes filename backslash and supports Range`() {
        val rawOut = ByteArrayOutputStream()
        val writer = HttpResponseWriter(rawOut)
        writer.sendBytes(
            status = 200,
            contentType = "text/plain\r\nX-Injected: evil",
            bytes = "ok".toByteArray(StandardCharsets.UTF_8),
            extraHeaders = mapOf("X-Safe\r\nBad" to "val1\r\nSet-Cookie: pwn=1"),
        )
        val respText = rawOut.toString(StandardCharsets.UTF_8.name())
        val headerBlock = respText.substringBefore("\r\n\r\n")
        assertFalse("Injected header line must not exist", headerBlock.contains("\r\nX-Injected:"))
        assertFalse("Injected Set-Cookie must not exist", headerBlock.contains("\r\nSet-Cookie:"))

        val file = tmp.newFile("sample.bin").apply { writeBytes("0123456789".toByteArray(StandardCharsets.US_ASCII)) }

        val fullOut = ByteArrayOutputStream()
        HttpResponseWriter(fullOut).sendFile(file, attachmentName = """bad\"name\test.bin""", rangeHeader = null)
        val fullHeaders = fullOut.toString(StandardCharsets.UTF_8.name()).substringBefore("\r\n\r\n")
        assertTrue(fullHeaders.contains("HTTP/1.1 200 OK"))
        assertTrue(fullHeaders.contains("""filename="bad__name_test.bin""""))

        val rangeOut = ByteArrayOutputStream()
        HttpResponseWriter(rangeOut).sendFile(file, attachmentName = "sample.bin", rangeHeader = "bytes=2-5")
        val rangeResp = rangeOut.toString(StandardCharsets.UTF_8.name())
        assertTrue(rangeResp.startsWith("HTTP/1.1 206 Partial Content\r\n"))
        assertTrue(rangeResp.contains("Content-Range: bytes 2-5/10\r\n"))
        assertTrue(rangeResp.endsWith("\r\n\r\n2345"))
    }

    @Test
    fun `loopback server rejects oversized headers with 431 malformed Content-Length with 400 and excess concurrency with 503`() {
        val loopback = InetAddress.getByName("127.0.0.1") as Inet4Address
        val holdEntered = CountDownLatch(1)
        val holdRelease = CountDownLatch(1)
        val server = HttpMiniServer(
            bindAddress = loopback,
            preferredPort = 39410,
            portScanCount = 30,
            maxConcurrentConnections = 1,
        ) { req, res ->
            if (req.path == "/hold") {
                holdEntered.countDown()
                holdRelease.await(5, TimeUnit.SECONDS)
                res.sendText(200, "released")
            } else {
                res.sendText(200, "ok")
            }
        }
        val port = server.start()
        try {
            // 1. 超长请求头返回 431
            val resp431 = sendRawHttp(port, "GET / HTTP/1.1\r\nX-Huge: ${"a".repeat(9000)}\r\n\r\n")
            assertTrue("Expected 431 response, got: $resp431", resp431.startsWith("HTTP/1.1 431 "))

            // 2. 负 Content-Length 返回 400
            val resp400Neg = sendRawHttp(port, "POST / HTTP/1.1\r\nContent-Length: -5\r\n\r\n")
            assertTrue("Expected 400 for negative Content-Length, got: $resp400Neg", resp400Neg.startsWith("HTTP/1.1 400 "))

            // 3. 非数字 Content-Length 返回 400
            val resp400Nan = sendRawHttp(port, "POST / HTTP/1.1\r\nContent-Length: abc\r\n\r\n")
            assertTrue("Expected 400 for invalid Content-Length, got: $resp400Nan", resp400Nan.startsWith("HTTP/1.1 400 "))

            // 4. 并发连接数超过 maxConcurrentConnections=1 时返回 503
            val holder = Socket("127.0.0.1", port).apply { soTimeout = 5000 }
            try {
                holder.getOutputStream().write("GET /hold HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                holder.getOutputStream().flush()
                assertTrue("First connection should enter handler", holdEntered.await(3, TimeUnit.SECONDS))

                val resp503 = sendRawHttp(port, "GET / HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n")
                assertTrue("Expected 503 when connection slots full, got: $resp503", resp503.startsWith("HTTP/1.1 503 "))
            } finally {
                holdRelease.countDown()
                runCatching { holder.close() }
            }
        } finally {
            server.stop()
        }
    }

    private fun sendRawHttp(port: Int, rawRequest: String): String {
        Socket("127.0.0.1", port).use { sock ->
            sock.soTimeout = 3000
            sock.getOutputStream().write(rawRequest.toByteArray(StandardCharsets.US_ASCII))
            sock.getOutputStream().flush()
            return readAllLimited(sock.getInputStream())
        }
    }

    private fun readAllLimited(input: InputStream, maxBytes: Int = 8192): String {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (baos.size() < maxBytes) {
            val n = runCatching { input.read(buf) }.getOrDefault(-1)
            if (n <= 0) break
            baos.write(buf, 0, n)
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }
}

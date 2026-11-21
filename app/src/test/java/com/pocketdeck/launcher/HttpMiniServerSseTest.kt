package com.pocketdeck.launcher

import com.pocketdeck.launcher.lan.HttpResponseWriter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class HttpMiniServerSseTest {

    @Test
    fun startEventStreamWritesSseHeadersAndRawEvents() {
        val baos = ByteArrayOutputStream()
        val writer = HttpResponseWriter(baos)
        writer.startEventStream()
        val ok = writer.sendSseRaw("event: state\ndata: {\"online\":true}\n\n")
        assertTrue(ok)

        val raw = baos.toString(StandardCharsets.UTF_8.name())
        assertTrue(raw.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(raw.contains("Content-Type: text/event-stream; charset=utf-8\r\n"))
        assertTrue(raw.contains("Cache-Control: no-cache, no-transform\r\n"))
        assertTrue(raw.contains("X-Accel-Buffering: no\r\n"))
        assertTrue(raw.contains("\r\n\r\nevent: state\ndata: {\"online\":true}\n\n"))
    }

    @Test
    fun sendSseRawReturnsFalseWhenPeerDisconnects() {
        val broken = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("Broken pipe")
            }
        }
        val writer = HttpResponseWriter(broken)
        assertFalse(writer.sendSseRaw("event: ping\ndata: {}\n\n"))
    }
}

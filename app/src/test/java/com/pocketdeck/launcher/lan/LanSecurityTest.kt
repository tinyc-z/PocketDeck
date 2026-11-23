package com.pocketdeck.launcher.lan

import android.content.Context
import android.content.ContextWrapper
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.LocalPrefsStore
import com.pocketdeck.launcher.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * 局域网安全回归测试（覆盖 WebManagerServer / WebFileSecurity / LanHttpClient / QuickShareService）：
 * - Host 与 POST Origin/Referer 校验（防 DNS Rebinding & CSRF，保留 adb forward 回环调试）
 * - 封面源 URL 白名单 & 公网 IP 校验（防内网/回环/元数据/CGNAT/IPv4-mapped SSRF）
 * - 目录边界 `isInsideDir` 与 `isSafePlatformId`
 * - `LanHttpClient.readBoundedLine` 阻断无换行流零点击 OOM
 * - `LanHttpClient.verifyPeerCertificateFingerprint` 真实回环 TLS 证书指纹校验
 * - `QuickShareService` 原生与 LocalSend HTTP 处理：禁 CORS/OPTIONS、Sender IP 校验、白名单文件名校验、
 *   总字节预算 413、LocalSend 空 sessionId 拒绝 400、fileToken 原子消费防重放、platformId 路径穿越拦截
 */
class LanSecurityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeDirContext(
        private val fakeFilesDir: File,
        private val fakeCacheDir: File,
    ) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = fakeFilesDir
        override fun getCacheDir(): File = fakeCacheDir
    }

    private fun newFakeContext(name: String): Context {
        val root = tmp.newFolder(name)
        val files = File(root, "files").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        return FakeDirContext(files, cache)
    }

    // ─────────────────── WebManagerServer Host / Origin 防护 ───────────────────

    @Test
    fun `webManager Host and POST Origin validation blocks DNS rebinding and CSRF`() {
        val lanIp = "192.168.1.42"
        val port = 8686

        // 正常同源访问
        assertTrue(WebManagerServer.isAllowedHostHeader("192.168.1.42:8686", lanIp, port, "192.168.1.10"))
        assertTrue(
            WebManagerServer.isAllowedPostOriginHeader(
                hostHeader = "192.168.1.42:8686",
                originHeader = "http://192.168.1.42:8686",
                refererHeader = "",
                boundIp = lanIp,
                boundPort = port,
                remoteIp = "192.168.1.10",
            ),
        )

        // DNS Rebinding / 空 Host / 错误端口全部拦截
        assertFalse(WebManagerServer.isAllowedHostHeader("evil.com:8686", lanIp, port, "192.168.1.10"))
        assertFalse(WebManagerServer.isAllowedHostHeader("", lanIp, port, "192.168.1.10"))
        assertFalse(WebManagerServer.isAllowedHostHeader("192.168.1.42:9999", lanIp, port, "192.168.1.10"))

        // 跨站 Origin 与 Origin: null 拦截
        assertFalse(
            WebManagerServer.isAllowedPostOriginHeader(
                hostHeader = "192.168.1.42:8686",
                originHeader = "http://evil.com",
                refererHeader = "",
                boundIp = lanIp,
                boundPort = port,
                remoteIp = "192.168.1.10",
            ),
        )
        assertFalse(
            WebManagerServer.isAllowedPostOriginHeader(
                hostHeader = "192.168.1.42:8686",
                originHeader = "null",
                refererHeader = "",
                boundIp = lanIp,
                boundPort = port,
                remoteIp = "192.168.1.10",
            ),
        )
        assertFalse(
            WebManagerServer.isAllowedPostOriginHeader(
                hostHeader = "192.168.1.42:8686",
                originHeader = "",
                refererHeader = "https://attacker.example/csrf",
                boundIp = lanIp,
                boundPort = port,
                remoteIp = "192.168.1.10",
            ),
        )

        // 回环客户端（adb forward）放行 127.0.0.1 / localhost，非回环客户端伪造 127.0.0.1 必须拦截
        assertTrue(WebManagerServer.isAllowedHostHeader("127.0.0.1:8686", lanIp, port, "127.0.0.1"))
        assertTrue(WebManagerServer.isAllowedHostHeader("localhost:18686", lanIp, port, "127.0.0.1"))
        assertFalse(WebManagerServer.isAllowedHostHeader("127.0.0.1:8686", lanIp, port, "192.168.1.10"))
        assertFalse(WebManagerServer.isAllowedHostHeader("localhost:8686", lanIp, port, "192.168.1.10"))
    }

    // ─────────────────── SSRF 封面源 URL 白名单与公网 IP 校验 ───────────────────

    @Test
    fun `webFileSecurity enforces HTTPS cover allowlist and blocks private IPs`() {
        // 合法封面 CDN 放行
        assertTrue(WebFileSecurity.isAllowedCoverSourceUrl("https://cdn.jsdelivr.net/gh/libretro-thumbnails/a.png"))
        assertTrue(WebFileSecurity.isAllowedCoverSourceUrl("https://thumbnails.libretro.com/Nintendo_-_GBA/Named_Boxarts/a.png"))
        assertTrue(WebFileSecurity.isAllowedCoverSourceUrl("https://cdn2.steamgriddb.com/thumb/abc.png"))
        assertTrue(WebFileSecurity.isAllowedCoverSourceUrl("https://images.igdb.com/igdb/image/upload/t_cover_big/co1.jpg"))
        assertTrue(WebFileSecurity.isAllowedCoverSourceUrl("https://art.gametdb.com/wii/cover/US/RMGE01.png"))
        assertTrue(WebFileSecurity.isAllowedCoverSourceUrl("https://upload.wikimedia.org/wikipedia/commons/a/ab/cover.png"))

        // 拦截 HTTP / 内网 IP / 任意外域 / 非默认端口 / userinfo 混淆
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("http://cdn.jsdelivr.net/gh/libretro-thumbnails/a.png"))
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("https://127.0.0.1/secret.png"))
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("https://192.168.1.1/admin"))
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("https://169.254.169.254/latest/meta-data/"))
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("https://evil.com/cover.png"))
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("https://evil-libretro.com/a.png"))
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("https://cdn.jsdelivr.net:8443/a.png"))
        assertFalse(WebFileSecurity.isAllowedCoverSourceUrl("https://user:pass@cdn.jsdelivr.net/a.png"))

        // 公网 IPv4 / IPv6 校验
        assertTrue(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("1.1.1.1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("10.0.0.1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("172.16.0.1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("192.168.1.1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("169.254.169.254")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("100.64.0.1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("0.0.0.0")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("::1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("fc00::1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("fe80::1")))
        assertFalse(WebFileSecurity.isPublicInetAddress(InetAddress.getByName("::ffff:127.0.0.1")))
    }

    @Test
    fun `webFileSecurity isInsideDir and isSafePlatformId block sibling prefix and traversal`() {
        val base = tmp.newFolder("custom_covers")
        val sibling = tmp.newFolder("custom_covers_evil")
        val child = File(base, "game.jpg").apply { writeBytes(byteArrayOf(1)) }
        val siblingFile = File(sibling, "steal.jpg").apply { writeBytes(byteArrayOf(2)) }
        val traversal = File(base, "../custom_covers_evil/steal.jpg")

        assertFalse("Directory itself is not strictly inside dir", WebFileSecurity.isInsideDir(base, base))
        assertTrue(WebFileSecurity.isInsideDir(child, base))
        assertFalse("Sibling prefix dir must NOT pass isInsideDir", WebFileSecurity.isInsideDir(siblingFile, base))
        assertFalse("Traversal must NOT pass isInsideDir", WebFileSecurity.isInsideDir(traversal, base))

        assertTrue(WebFileSecurity.isSafePlatformId("gba"))
        assertTrue(WebFileSecurity.isSafePlatformId("ps_2-custom"))
        assertFalse(WebFileSecurity.isSafePlatformId(""))
        assertFalse(WebFileSecurity.isSafePlatformId("../Download"))
        assertFalse(WebFileSecurity.isSafePlatformId("gba/sub"))
        assertFalse(WebFileSecurity.isSafePlatformId("a".repeat(41)))
    }

    // ─────────────────── LanHttpClient 抗 OOM 与 TLS 指纹校验 ───────────────────

    @Test
    fun `lanHttpClient rejects endless headerless stream and excessive headers without OOM`() {
        // 构造无限不换行字节流，若没有行长上限必然 OOM
        val endlessNoNewline = object : InputStream() {
            var count = 0
            override fun read(): Int {
                count++
                return 'Z'.code
            }
        }
        try {
            LanHttpClient.readBoundedLine(endlessNoNewline, maxBytes = 8 * 1024)
            fail("Expected IOException on endless line")
        } catch (_: IOException) {
            assertTrue("Should fail right after maxBytes without reading huge data", endlessNoNewline.count <= 8 * 1024 + 8)
        }

        // 超过 64 个响应头立即拒绝
        val manyHeaders = buildString {
            append("HTTP/1.1 200 OK\r\n")
            repeat(65) { i -> append("X-R-$i: 1\r\n") }
            append("\r\n")
        }
        try {
            LanHttpClient.readHttpResponse(ByteArrayInputStream(manyHeaders.toByteArray(StandardCharsets.US_ASCII)))
            fail("Expected IOException when response headers > 64")
        } catch (_: IOException) {
            // expected
        }
    }

    @Test
    fun `lanHttpClient verifies real loopback TLS peer certificate SHA-256 fingerprint`() {
        val ctx = newFakeContext("tls_cert")
        val id = LocalSendCert.getOrCreate(ctx)
        val server = (id.serverSslContext.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", 0))
            needClientAuth = false
            wantClientAuth = false
        }
        val port = server.localPort
        val acceptThread = Thread {
            repeat(2) {
                runCatching {
                    (server.accept() as SSLSocket).use { peer ->
                        peer.soTimeout = 3000
                        peer.startHandshake()
                        runCatching { peer.getInputStream().read() }
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        try {
            // 1. 匹配指纹（含大写冒号格式）应通过校验
            val colonUpperFp = id.fingerprint.uppercase().chunked(2).joinToString(":")
            (id.clientSslContext.socketFactory.createSocket("127.0.0.1", port) as SSLSocket).use { client ->
                client.soTimeout = 3000
                client.startHandshake()
                LanHttpClient.verifyPeerCertificateFingerprint(client, colonUpperFp)
            }

            // 2. 错误指纹、null 或畸形指纹均必须 fail-closed 抛出 SSLPeerUnverifiedException
            val wrongFp = "0".repeat(64)
            (id.clientSslContext.socketFactory.createSocket("127.0.0.1", port) as SSLSocket).use { client ->
                client.soTimeout = 3000
                client.startHandshake()
                try {
                    LanHttpClient.verifyPeerCertificateFingerprint(client, wrongFp)
                    fail("Expected SSLPeerUnverifiedException on mismatched fingerprint")
                } catch (_: SSLPeerUnverifiedException) {
                    // expected
                }
                for (badFp in listOf(null, "", "abc", "zz".repeat(32))) {
                    try {
                        LanHttpClient.verifyPeerCertificateFingerprint(client, badFp)
                        fail("Expected SSLPeerUnverifiedException on null/malformed fingerprint: $badFp")
                    } catch (_: SSLPeerUnverifiedException) {
                        // expected fail-closed
                    }
                }
            }
        } finally {
            runCatching { server.close() }
            acceptThread.join(2000)
        }
    }

    // ─────────────────── QuickShareService 协议与会话安全 ───────────────────

    @Test
    fun `quickShare native HTTP enforces no-CORS sender IP approved filename and byte budget`() = runBlocking {
        val ctx = newFakeContext("qs_native")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val targetDir = tmp.newFolder("qs_target")
        try {
            val prefs = LocalPrefsStore.forUserPrefs(ctx)
            LocalPrefsStore::class.java.getDeclaredField("cachedMap").apply { isAccessible = true }
                .set(prefs, mapOf("rom_root" to targetDir.absolutePath))
            val userStore = UserStore(ctx)
            val repo = GameRepository(ctx, userStore, scope)
            val svc = QuickShareService(ctx, repo)

            // 1. 携带 Origin 或 OPTIONS 预检返回 403 且不返回 ACAO:*
            val corsRes = dispatchNative(
                svc,
                method = "GET",
                path = "/api/share/ping",
                headers = mapOf("origin" to "https://evil.example"),
            )
            assertEquals(403, corsRes.first)
            assertFalse(corsRes.second.contains("Access-Control-Allow-Origin"))

            val optionsRes = dispatchNative(svc, method = "OPTIONS", path = "/api/share/prepare")
            assertEquals(403, optionsRes.first)

            // 2. 合法 prepare（申请 1 个文件 mario.gba，大小 10 字节）
            val prepBody = JSONObject().apply {
                put("sessionId", "sess-1")
                put("senderId", "sender-a")
                put("senderName", "HandheldA")
                put("fileName", "mario.gba")
                put("fileSize", 10L)
                put("platformId", "../evil")
                put("fileCount", 1)
                put("fileNames", listOf("mario.gba"))
            }.toString()
            val prepRes = dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/prepare",
                body = prepBody.toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.50",
            )
            assertEquals(202, prepRes.first)
            val prepJson = JSONObject(prepRes.second.substringAfter("\r\n\r\n"))
            val serverSessionId = prepJson.optString("sessionId", "")
            assertTrue("Server must issue a non-blank sessionId", serverSessionId.isNotBlank())
            assertFalse("Server must ignore client-supplied sessionId", serverSessionId == "sess-1")
            assertEquals("Traversal platformId must be stripped", "", svc.incomingRequest.value?.platformId)

            // 3. 第三方 IP 企图 cancel 必须返回 403
            val spoofCancel = dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/cancel",
                query = mapOf("sessionId" to serverSessionId),
                remoteIp = "192.168.1.99",
            )
            assertEquals(403, spoofCancel.first)

            // 用户同意接收
            svc.acceptIncoming(serverSessionId, targetDir)

            // 4. 第三方 IP 企图 upload 必须返回 403
            val spoofUpload = dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/upload",
                query = mapOf("sessionId" to serverSessionId, "name" to "mario.gba"),
                body = "0123456789".toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.99",
            )
            assertEquals(403, spoofUpload.first)

            // 5. 同 IP 但换掉包文件名 evil.apk 必须返回 403（且不消耗 mario.gba 配额）
            val wrongName = dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/upload",
                query = mapOf("sessionId" to serverSessionId, "name" to "evil.apk"),
                body = "0123456789".toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.50",
            )
            assertEquals(403, wrongName.first)

            // 6. 同 IP + 正确文件名，但体积超过 prepare 批准的 10 字节 -> 413
            val oversize = dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/upload",
                query = mapOf("sessionId" to serverSessionId, "name" to "mario.gba"),
                body = "0123456789-OVERFLOW".toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.50",
            )
            assertEquals(413, oversize.first)
            assertFalse("Oversize rejected upload must not create file", File(targetDir, "mario.gba").exists())

            // 6b. 声明 0 字节文件并尝试上传 1 字节必须返回 413（严禁回退为不限速/无限配额）
            dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/cancel",
                query = mapOf("sessionId" to serverSessionId),
                remoteIp = "192.168.1.50",
            )
            val zeroPrep = dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/prepare",
                body = JSONObject().apply {
                    put("senderId", "sender-a")
                    put("senderName", "HandheldA")
                    put("fileName", "empty.sav")
                    put("fileSize", 0L)
                    put("fileCount", 1)
                    put("fileNames", listOf("empty.sav"))
                }.toString().toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.50",
            )
            assertEquals(202, zeroPrep.first)
            val zeroSid = JSONObject(zeroPrep.second.substringAfter("\r\n\r\n")).getString("sessionId")
            svc.acceptIncoming(zeroSid, targetDir)
            val zeroOverflow = dispatchNative(
                svc,
                method = "POST",
                path = "/api/share/upload",
                query = mapOf("sessionId" to zeroSid, "name" to "empty.sav"),
                body = byteArrayOf(0x42),
                remoteIp = "192.168.1.50",
            )
            assertEquals("0-byte declared file must reject 1-byte payload with 413", 413, zeroOverflow.first)
            assertFalse(File(targetDir, "empty.sav").exists())

            // 7. buildCandidateDirs 对非法/穿越 platformId 不应逃出 ROM 根目录
            val romRoot = tmp.newFolder("roms_root")
            val dirs = svc.buildCandidateDirs(listOf(romRoot), "../../outside")
            assertTrue(dirs.none { it.canonicalPath.endsWith("outside") })

            // 8. 磁盘空间校验不因 Long.MAX_VALUE 溢出而误判为空间充足
            assertFalse(
                "Long.MAX_VALUE incoming size must not overflow disk check",
                WebManagerServer.hasEnoughDiskSpace(targetDir, Long.MAX_VALUE),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `quickShare LocalSend enforces non-blank cancel sessionId and single-use fileToken`() = runBlocking {
        val ctx = newFakeContext("qs_localsend")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val targetDir = tmp.newFolder("ls_target")
        try {
            val prefs = LocalPrefsStore.forUserPrefs(ctx)
            LocalPrefsStore::class.java.getDeclaredField("cachedMap").apply { isAccessible = true }
                .set(prefs, mapOf("rom_root" to targetDir.absolutePath))
            val userStore = UserStore(ctx)
            val repo = GameRepository(ctx, userStore, scope)
            val svc = QuickShareService(ctx, repo)

            // 1. LocalSend cancel 缺少 sessionId 返回 400
            val blankCancel = dispatchLocalSend(
                svc,
                method = "POST",
                path = "/api/localsend/v2/cancel",
                remoteIp = "192.168.1.60",
            )
            assertEquals(400, blankCancel.first)

            // 2. 启动 prepare-upload 长轮询并由接收端同意，拿到 sessionId 与一次性 token
            val preparePayload = JSONObject().apply {
                put("info", JSONObject().apply {
                    put("alias", "PeerA")
                    put("fingerprint", "a".repeat(64))
                })
                put("files", JSONObject().apply {
                    put("f1", JSONObject().apply {
                        put("id", "f1")
                        put("fileName", "zelda.gba")
                        put("size", 8L)
                    })
                })
            }.toString()
            val prepareDeferred = async {
                dispatchLocalSend(
                    svc,
                    method = "POST",
                    path = "/api/localsend/v2/prepare-upload",
                    body = preparePayload.toByteArray(StandardCharsets.UTF_8),
                    remoteIp = "192.168.1.60",
                )
            }
            var incomingId: String? = null
            repeat(40) {
                incomingId = svc.incomingRequest.value?.sessionId
                if (incomingId != null) return@repeat
                delay(25)
            }
            assertNotNull("Incoming LocalSend session should be queued", incomingId)
            svc.acceptIncoming(incomingId!!, targetDir)

            val prepResult = prepareDeferred.await()
            assertEquals(200, prepResult.first)
            val prepJson = JSONObject(prepResult.second.substringAfter("\r\n\r\n"))
            val sid = prepJson.getString("sessionId")
            val token = prepJson.getJSONObject("files").getString("f1")

            // 3. 第三方 IP 拿到了 token 也不允许上传 -> 403
            val wrongIpUpload = dispatchLocalSend(
                svc,
                method = "POST",
                path = "/api/localsend/v2/upload",
                query = mapOf("sessionId" to sid, "fileId" to "f1", "token" to token),
                body = "12345678".toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.99",
            )
            assertEquals(403, wrongIpUpload.first)

            // 4. 正确 sender IP + 正确 token 首次上传成功 200
            val firstUpload = dispatchLocalSend(
                svc,
                method = "POST",
                path = "/api/localsend/v2/upload",
                query = mapOf("sessionId" to sid, "fileId" to "f1", "token" to token),
                body = "12345678".toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.60",
            )
            assertEquals(200, firstUpload.first)
            assertTrue(File(targetDir, "zelda.gba").isFile)

            // 5. 重放同一 token 必须被拒绝 -> 403
            val replayUpload = dispatchLocalSend(
                svc,
                method = "POST",
                path = "/api/localsend/v2/upload",
                query = mapOf("sessionId" to sid, "fileId" to "f1", "token" to token),
                body = "12345678".toByteArray(StandardCharsets.UTF_8),
                remoteIp = "192.168.1.60",
            )
            assertEquals(403, replayUpload.first)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun dispatchNative(
        svc: QuickShareService,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        body: ByteArray = ByteArray(0),
        remoteIp: String = "192.168.1.50",
    ): Pair<Int, String> {
        val out = ByteArrayOutputStream()
        val req = HttpRequest(
            method = method,
            path = path,
            query = query,
            headers = headers,
            cookies = emptyMap(),
            contentLength = body.size.toLong(),
            bodyStream = ByteArrayInputStream(body),
            remoteIp = remoteIp,
        )
        svc.handleHttp(req, HttpResponseWriter(out))
        return parseResponse(out)
    }

    private suspend fun dispatchLocalSend(
        svc: QuickShareService,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        body: ByteArray = ByteArray(0),
        remoteIp: String = "192.168.1.60",
    ): Pair<Int, String> {
        val out = ByteArrayOutputStream()
        val req = HttpRequest(
            method = method,
            path = path,
            query = query,
            headers = headers,
            cookies = emptyMap(),
            contentLength = body.size.toLong(),
            bodyStream = ByteArrayInputStream(body),
            remoteIp = remoteIp,
        )
        svc.handleLocalSendHttp(req, HttpResponseWriter(out))
        return parseResponse(out)
    }

    private fun parseResponse(out: ByteArrayOutputStream): Pair<Int, String> {
        val raw = out.toString(StandardCharsets.UTF_8.name())
        val code = raw.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: -1
        return code to raw
    }
}

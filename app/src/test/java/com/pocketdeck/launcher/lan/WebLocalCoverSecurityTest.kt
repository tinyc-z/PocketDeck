package com.pocketdeck.launcher.lan

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.RomDir
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

class WebLocalCoverSecurityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newFakeContext(tag: String): Pair<Context, File> {
        val base = tmp.newFolder(tag)
        val filesDir = File(base, "files").apply { mkdirs() }
        val cacheDir = File(base, "cache").apply { mkdirs() }
        val prefsMap = HashMap<String, SharedPreferences>()

        fun makePrefs(): SharedPreferences {
            val data = HashMap<String, Any?>()
            lateinit var prefs: SharedPreferences
            prefs = Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getAll" -> HashMap(data)
                    "getInt" -> (data[args[0] as String] as? Int) ?: (args[1] as Int)
                    "getLong" -> (data[args[0] as String] as? Long) ?: (args[1] as Long)
                    "getString" -> (data[args[0] as String] as? String) ?: (args[1] as? String)
                    "getBoolean" -> (data[args[0] as String] as? Boolean) ?: (args[1] as Boolean)
                    "edit" -> Proxy.newProxyInstance(
                        SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java),
                    ) { proxy, em, eArgs ->
                        when (em.name) {
                            "putInt", "putLong", "putString", "putBoolean" -> {
                                data[eArgs[0] as String] = eArgs[1]
                                proxy
                            }
                            "remove" -> {
                                data.remove(eArgs[0] as String)
                                proxy
                            }
                            "clear" -> {
                                data.clear()
                                proxy
                            }
                            "commit" -> true
                            "apply" -> Unit
                            else -> proxy
                        }
                    }
                    else -> null
                }
            } as SharedPreferences
            return prefs
        }

        lateinit var ctx: Context
        ctx = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = ctx
            override fun getFilesDir(): File = filesDir
            override fun getCacheDir(): File = cacheDir
            override fun getPackageName(): String = "com.pocketdeck.launcher.test"
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                prefsMap.getOrPut(name.orEmpty()) { makePrefs() }
            override fun getDatabasePath(name: String?): File = File(base, name ?: "test.db")
        }
        return ctx to base
    }

    @Test
    fun resolveAllowedLocalCoverFileEnforcesLibraryAndRomMediaBoundaries() = runBlocking {
        val (ctx, _) = newFakeContext("web_local_sec")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val container = tmp.newFolder("storage")
            val romRoot = File(container, "ROMs").apply { mkdirs() }
            val nes = File(romRoot, "nes").apply { mkdirs() }
            val rom = File(nes, "Contra.nes").apply { writeText("rom") }
            val scannedCover = File(nes, "Imgs/Contra.png").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val siblingMedia = File(container, "covers/nes/Bonus.jpg").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(4, 5, 6))
            }
            val nonImageInsideRom = File(nes, "Imgs/readme.txt").apply { writeText("hi") }
            val outsideImg = File(tmp.newFolder("outside"), "secret.png").apply { writeBytes(byteArrayOf(9, 9)) }

            val lib = RomScanner.scanAll(listOf(romRoot))
            val userStore = UserStore(ctx)
            userStore.update { it.copy(romDirs = listOf(RomDir(romRoot.path))) }
            val repo = GameRepository(ctx, userStore, scope)
            val scannedField = GameRepository::class.java.getDeclaredField("scanned").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (scannedField.get(repo) as MutableStateFlow<Any?>).value = lib
            val deadline = System.currentTimeMillis() + 2_000L
            while (repo.fullLibrary.value == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertNotNull(repo.fullLibrary.value)

            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val metaRepo = MetadataRepository(ctx, scope, client)
            val server = WebManagerServer(ctx, repo, covers, metaRepo)

            // 1. 允许扫描记录内的本地封面与 ROM 根下图片
            assertEquals(scannedCover.canonicalPath, server.resolveAllowedLocalCoverFile(scannedCover.path)?.canonicalPath)
            // 2. 允许 sibling media dir（romRoot.parentFile/covers/...）下的图片
            assertEquals(siblingMedia.canonicalPath, server.resolveAllowedLocalCoverFile(siblingMedia.path)?.canonicalPath)
            // 3. 拦截非图片文件、系统路径与 ROM 根外任意路径
            assertNull(server.resolveAllowedLocalCoverFile(nonImageInsideRom.path))
            assertNull(server.resolveAllowedLocalCoverFile(outsideImg.path))
            assertNull(server.resolveAllowedLocalCoverFile("/etc/hosts"))
            assertNull(server.resolveAllowedLocalCoverFile(""))
            assertNotNull(lib.byId[rom.path])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun coverSourceServesLocalCoverBytesForWebAndRespectsManualOverride() = runBlocking {
        val (ctx, _) = newFakeContext("web_local_bytes")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val root = tmp.newFolder("web_roms")
            val nes = File(root, "nes").apply { mkdirs() }
            val rom = File(nes, "Contra.nes").apply { writeText("rom") }
            val coverBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
            val scannedCover = File(nes, "Imgs/Contra.png").apply {
                parentFile?.mkdirs()
                writeBytes(coverBytes)
            }

            val lib = RomScanner.scanAll(listOf(root))
            val game = lib.byId[rom.path]!!
            val covers = CoverSource(ctx, scope, ScraperClient(ctx))

            // 自动刮削链接存在也不覆盖 localCover
            covers.rememberSuccess(
                game.id,
                "https://thumbnails.libretro.com/Nintendo_-_NES/Named_Boxarts/Contra.png",
            )

            // 自动刮削链接存在也不暴露自动远程公网 URL，Web 端通过 /api/cover 直接读取本地封面字节
            assertNull(covers.publicCoverUrlForWeb(game))
            val resolved = covers.resolveWebCoverBytes(game)
            assertNotNull(resolved)
            assertEquals("image/png", resolved!!.first)
            assertArrayEquals(coverBytes, resolved.second)

            // 若用户手动指定远端封面，则 Web public URL 切换为该手动远端 URL
            val manualUrl = "https://cdn2.steamgriddb.com/thumb/contra_manual.png"
            covers.applyManualCover(game.id, manualUrl, ScraperCatalog.CoverIds.STEAMGRIDDB)
            assertEquals(manualUrl, covers.publicCoverUrlForWeb(game))
        } finally {
            scope.cancel()
        }
    }
}

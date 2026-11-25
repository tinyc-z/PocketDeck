package com.pocketdeck.launcher

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.pocketdeck.launcher.data.CoverMatchMode
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.UserStore
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

class CoverPriorityAndLocalPickerTest {

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

    private fun setNetworkOnline(online: Boolean) {
        val initField = NetworkStatus::class.java.getDeclaredField("initialized").apply { isAccessible = true }
        initField.setBoolean(NetworkStatus, true)
        val flowField = NetworkStatus::class.java.getDeclaredField("_online").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (flowField.get(NetworkStatus) as MutableStateFlow<Boolean>).value = online
    }

    private fun injectMockHttp(client: ScraperClient, http: OkHttpClient) {
        val field = ScraperClient::class.java.getDeclaredField("http\$delegate").apply { isAccessible = true }
        field.set(client, lazyOf(http))
    }

    @Test
    fun localCoverTakesStrictPriorityOverAutoScrapedRemoteAndSkipsNetworkDownload() = runBlocking {
        setNetworkOnline(true)
        val (ctx, base) = newFakeContext("cover_prio")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx)
            val httpHits = AtomicInteger(0)
            val mockHttp = OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        httpHits.incrementAndGet()
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ByteArray(16) { 1 }.toResponseBody("image/png".toMediaType()))
                            .build()
                    },
                )
                .build()
            injectMockHttp(client, mockHttp)
            val covers = CoverSource(ctx, scope, client)

            val localFile = File(base, "local_box.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val customFile = File(base, "custom_box.png").apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }
            val romFile = File(base, "Mario.nes").apply { writeText("rom") }
            val plat = Platform("nes", "NES", "Nintendo Entertainment System")

            // 先给游戏写入一个自动刮削远程 URL 记录，验证存在 localCover 时不会走远端
            val autoUrl = "https://thumbnails.libretro.com/Nintendo_-_NES/Named_Boxarts/Super%20Mario%20Bros.png"
            covers.rememberSuccess(romFile.path, autoUrl)

            val withLocal = Game(
                id = romFile.path,
                platformId = "nes",
                baseName = "Mario",
                fileName = romFile.name,
                title = "Mario",
                localCover = localFile.path,
            )

            // 1. 内存候选与 info 优先本地封面，且 O(1) hasLocalOrCachedCover 无需磁审为 true
            val fast = covers.fastMemoryCandidates(withLocal)
            assertTrue("fastMemoryCandidates 首位应为 localCover File", fast.firstOrNull() == localFile)
            val full = covers.candidates(withLocal, plat)
            assertEquals("candidates 首位应为 localCover File", localFile, full.firstOrNull())
            val infoLocal = covers.resolveCoverInfo(withLocal)
            assertEquals(CoverSource.LOCAL_COVER_PROVIDER, infoLocal?.providerId)
            assertEquals(CoverMatchMode.LocalFile, infoLocal?.matchMode)
            assertTrue(covers.hasLocalOrCachedCover(withLocal))
            assertNull("有 localCover 且未手动选远端时 Web 不返回自动刮削公网 URL（走 /api/cover 本地直传）", covers.publicCoverUrlForWeb(withLocal))

            // 2. fetchWebCoverNow 在存在 localCover 且 forceRemote=false 时不发任何 HTTP 请求
            assertFalse(covers.fetchWebCoverNow(withLocal, plat, null, forceRemote = false))
            assertEquals("有本地封面时不得下载远端封面", 0, httpHits.get())

            // 3. 用户手动选择远端封面后，手动选择覆盖本地默认封面
            val manualUrl = "https://cdn2.steamgriddb.com/thumb/mario_manual.png"
            covers.applyManualCover(withLocal.id, manualUrl, ScraperCatalog.CoverIds.STEAMGRIDDB)
            assertTrue(covers.isManualCover(withLocal.id))
            assertEquals(manualUrl, covers.candidates(withLocal, plat).firstOrNull())
            assertEquals(manualUrl, covers.publicCoverUrlForWeb(withLocal))
            val infoManual = covers.resolveCoverInfo(withLocal)
            assertEquals(ScraperCatalog.CoverIds.STEAMGRIDDB, infoManual?.providerId)

            // 4. 用户自定义封面（customCover）优先级高于 manual 与 localCover
            val withCustom = withLocal.copy(customCover = customFile.path)
            assertEquals(customFile, covers.candidates(withCustom, plat).firstOrNull())
            assertEquals(CoverMatchMode.CustomFile, covers.resolveCoverInfo(withCustom)?.matchMode)
            assertEquals("custom", covers.resolveCoverInfo(withCustom)?.providerId)
            assertNull("customCover 不暴露远端公网 URL", covers.publicCoverUrlForWeb(withCustom))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun hasLocalOrCachedCoverIsPureInMemoryForLocalCoverPath() {
        val (ctx, base) = newFakeContext("mem_only_check")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val covers = CoverSource(ctx, scope, ScraperClient(ctx))
            val nonexistentPath = File(base, "never_stat_me.png").path
            val gameWithLocal = Game("/roms/nes/a.nes", "nes", "A", "a.nes", "A", localCover = nonexistentPath)
            assertTrue("hasLocalOrCachedCover 应纯内存判断非空 localCover，不触发 File.isFile", covers.hasLocalOrCachedCover(gameWithLocal))

            val gameWithoutCover = Game("/roms/nes/b.nes", "nes", "B", "b.nes", "B")
            assertFalse(covers.hasLocalOrCachedCover(gameWithoutCover))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun manualSearchAndPickerIncludeDeduplicatedLocalCoversEvenWhenOffline() = runBlocking {
        setNetworkOnline(false)
        val (ctx, _) = newFakeContext("offline_local_picker")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val root = tmp.newFolder("roms_picker")
            val nes = File(root, "nes").apply { mkdirs() }
            val rom = File(nes, "Contra (USA).nes").apply { writeText("rom") }
            val box = File(nes, "media/box2dfront/Contra (USA).png").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(10, 20, 30, 40))
            }
            val mix = File(nes, "Imgs/Contra.jpg").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(50, 60, 70, 80))
            }
            val lib = RomScanner.scanAll(listOf(root))
            val game = lib.byId[rom.path]!!
            val plat = lib.platforms.first { it.id == "nes" }

            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val metaRepo = MetadataRepository(ctx, scope, client)

            // 1. CoverSource.scrapeAllForPicker 离线返回本地封面候选
            val pickerList = covers.scrapeAllForPicker(
                game = game,
                platform = plat,
                localCoverPaths = lib.localCovers[game.id].orEmpty(),
            )
            assertEquals(2, pickerList.size)
            assertTrue(pickerList.all { it.nodeId == CoverSource.LOCAL_COVER_PROVIDER })
            assertEquals(listOf(box.path, mix.path), pickerList.map { it.url })

            // 2. MetadataRepository.manualSearch 离线合并游戏本地封面 + 关键词匹配本地封面并去重
            val extraByKw = File(nes, "Imgs/Contra Bonus.png").apply { writeBytes(byteArrayOf(9, 8, 7)) }
            val manualList = metaRepo.manualSearch(
                keyword = "Contra",
                game = game,
                platform = plat,
                covers = covers,
                localCoverPaths = lib.localCovers[game.id].orEmpty(),
                localKeywordCoversProvider = {
                    listOf(box.path, extraByKw.path) // 包含重复 box.path，验证去重
                },
            )
            val localManual = manualList.filter { it.providerId == CoverSource.LOCAL_COVER_PROVIDER }
            assertEquals(listOf(box.path, mix.path, extraByKw.path), localManual.map { it.coverUrl })
            assertTrue(localManual.all { !it.matchedByHash && it.title.isNotBlank() })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun saveCustomCoverFromLocalFileUpdatesOrClearsCustomCoverCorrectly() = runBlocking {
        val (ctx, base) = newFakeContext("save_local_cover")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val userStore = UserStore(ctx)
            val repo = GameRepository(ctx, userStore, scope)
            val defaultLocal = File(base, "default.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val altLocal = File(base, "alt.png").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val game = Game("/roms/nes/mario.nes", "nes", "Mario", "mario.nes", "Mario", localCover = defaultLocal.path)

            // 选非主本地图（alt）写入 customCover
            val saved = repo.saveCustomCoverFromLocalFile(game, altLocal.path)
            assertNotNull(saved)
            assertEquals(saved, userStore.data.first().covers[game.id])

            // 选回游戏默认 localCover 时清空 customCover，回退到本地默认图
            val cleared = repo.saveCustomCoverFromLocalFile(game, defaultLocal.path)
            assertNull(cleared)
            assertFalse(userStore.data.first().covers.containsKey(game.id))
        } finally {
            scope.cancel()
        }
    }
}

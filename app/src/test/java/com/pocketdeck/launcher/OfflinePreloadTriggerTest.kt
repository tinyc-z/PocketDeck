package com.pocketdeck.launcher

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.OfflineGameDb
import com.pocketdeck.launcher.data.ScrapeDatabase
import com.pocketdeck.launcher.data.ScrapeHit
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 冷启动 offline_db 触发条件优化回归测试：
 * 1. 无元数据平台（M=0）或未匹配游戏落库持久化负缓存，二次冷启动 ensureLoaded / resolveFocusGame 零离线表加载；
 * 2. 临时网络错误（HTTP 5xx）不误写 7 天负缓存；配置变更/缓存过期后正确失效重试；冷启恢复历史官方名不误清封面负缓存；
 * 3. 封面持久负缓存：二次冷启动跳过 boxart 预载与滑动窗口刮削，knownIndex 立即返回 -1，跨平台索引加载与网络重连不误清持久负缓存；
 * 4. 多平台 Tab 焦点外扩排序、活跃平台保护上限（<=6）与离线表并发加载限流（<=2）。
 */
class OfflinePreloadTriggerTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    /** 纯内存 ScrapeDatabase 替身：在 JVM 单元测试中完整模拟跨冷启动的 SQLite 持久化读写。 */
    private class FakeScrapeDatabase(context: Context) : ScrapeDatabase(context) {
        val covers = ConcurrentHashMap<String, CoverRow>()
        val resolvedMeta = ConcurrentHashMap<String, ManualMetaRow>()
        val manualMeta = ConcurrentHashMap<String, ManualMetaRow>()
        val scrapeHits = ConcurrentHashMap<String, ScrapeCacheRow>()
        var onPutResolvedBatch: ((List<ManualMetaRow>) -> Unit)? = null

        override fun loadAllCovers(): List<CoverRow> = covers.values.toList()

        override fun putCoverSuccess(gameId: String, url: String, provider: String?, matchMode: String?) {
            covers[gameId] = CoverRow(gameId, url, provider, matchMode, 0L, System.currentTimeMillis())
        }

        override fun putCoverFailure(gameId: String, failedUntilMs: Long) {
            covers[gameId] = CoverRow(gameId, null, null, null, failedUntilMs, System.currentTimeMillis())
        }

        override fun deleteCover(gameId: String) {
            covers.remove(gameId)
        }

        override fun clearCoverFailures(gameIds: Collection<String>) {
            for (id in gameIds) {
                val cur = covers[id]
                if (cur != null && cur.url == null) {
                    covers[id] = cur.copy(failedUntilMs = 0L)
                }
            }
        }

        override fun clearAllCoverFailures() {
            covers.entries.removeIf { it.value.url == null }
        }

        override fun clearCovers() {
            covers.clear()
        }

        override fun getScrapeHit(providerId: String, gameId: String, nowMs: Long): ScrapeCacheRow? {
            val row = scrapeHits["$providerId::$gameId"] ?: return null
            if (row.expiresAtMs > 0L && row.expiresAtMs < nowMs) return null
            return row
        }

        override fun putScrapeHit(providerId: String, gameId: String, hit: ScrapeHit, status: Int, expiresAtMs: Long) {
            scrapeHits["$providerId::$gameId"] = ScrapeCacheRow(hit, status, expiresAtMs)
        }

        override fun putScrapeHitsBatch(entries: List<BatchScrapeEntry>) {
            for (e in entries) {
                scrapeHits["${e.providerId}::${e.gameId}"] = ScrapeCacheRow(e.hit, e.status, e.expiresAtMs)
            }
        }

        override fun putManualMetadata(
            gameId: String,
            providerId: String,
            officialName: String?,
            canonicalName: String?,
            meta: GameMetadata,
        ) {
            manualMeta[gameId] = ManualMetaRow(gameId, providerId, officialName, canonicalName, meta)
        }

        override fun putResolvedMetadataBatch(rows: List<ManualMetaRow>) {
            onPutResolvedBatch?.invoke(rows)
            for (r in rows) {
                resolvedMeta[r.gameId] = r
            }
        }

        override fun loadAllResolvedMetadata(nowMs: Long): List<ManualMetaRow> =
            resolvedMeta.values.filter { it.expiresAtMs == 0L || it.expiresAtMs >= nowMs }

        override fun loadAllManualMetadata(): List<ManualMetaRow> = manualMeta.values.toList()

        override fun clearResolvedMetadataMisses() {
            resolvedMeta.entries.removeIf { it.value.status != STATUS_HIT }
        }

        override fun clearScrapeCache() {
            resolvedMeta.clear()
            manualMeta.clear()
            scrapeHits.clear()
        }
    }

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
        val httpDelegateField = ScraperClient::class.java.getDeclaredField("http\$delegate").apply { isAccessible = true }
        httpDelegateField.set(client, lazyOf(http))
    }

    private fun newMockHttp(statusCode: Int = 200, body: String = """{"search":[]}""", counter: AtomicInteger? = null): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    counter?.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message(if (statusCode == 200) "OK" else "ERR")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()

    @Suppress("UNCHECKED_CAST")
    private fun getSystemTables(repo: MetadataRepository): Map<String, Any> {
        val f = MetadataRepository::class.java.getDeclaredField("systemTables").apply { isAccessible = true }
        return f.get(repo) as Map<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getSystemIndexes(covers: CoverSource): Map<String, Any> {
        val f = CoverSource::class.java.getDeclaredField("systemIndexes").apply { isAccessible = true }
        return f.get(covers) as Map<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun awaitRepoInit(repo: MetadataRepository) {
        val f = MetadataRepository::class.java.getDeclaredField("initialCacheReady").apply { isAccessible = true }
        (f.get(repo) as CompletableDeferred<Unit>).await()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun awaitCoversInit(covers: CoverSource) {
        val f = CoverSource::class.java.getDeclaredField("initialCoversReady").apply { isAccessible = true }
        (f.get(covers) as CompletableDeferred<Unit>).await()
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, cond: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!cond()) delay(15L)
        }
    }

    // ─────────────────── 1. 元数据负缓存：冷启动不反复解压 M=0 / 未匹配平台 ───────────────────

    @Test
    fun metadataNegativeCachePreventsReloadAcrossColdRestarts() = runBlocking {
        setNetworkOnline(true)
        val (ctx, base) = newFakeContext("meta_neg_cold")
        val db = FakeScrapeDatabase(ctx)
        val ps1Sys = "Sony - PlayStation"
        val ps1 = Platform("psx", "PS1", "PlayStation", 1f, libretroSystem = ps1Sys)
        val romDir = File(base, "roms").apply { mkdirs() }
        val g1 = Game(File(romDir, "HomebrewDemo.chd").apply { writeText("a") }.path, "psx", "HomebrewDemo", "HomebrewDemo.chd", "HomebrewDemo")
        val g2 = Game(File(romDir, "UnmatchedGame.chd").apply { writeText("b") }.path, "psx", "UnmatchedGame", "UnmatchedGame.chd", "UnmatchedGame")

        // 写入模拟缓存 TSV（存在表但不包含 g1/g2）以便 getOrFetchLibretroTable 有本地数据可加载
        val metaCacheDir = File(ctx.cacheDir, "metadata").apply { mkdirs() }
        File(metaCacheDir, "v3_${OfflineGameDb.sanitizeSystemName(ps1Sys)}.tsv").writeText(
            "11111111\t\tOther Game (USA)\t1998\tRPG\tSquare\tSony\n",
        )

        val httpCount = AtomicInteger(0)
        val mockHttp = newMockHttp(statusCode = 200, body = """{"search":[]}""", counter = httpCount)

        // ── 冷启动 1：首次加载，解析离线表 + 焦点走在线 API，均未命中并落库负缓存 ──
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client1 = ScraperClient(ctx, db)
            injectMockHttp(client1, mockHttp)
            val repo1 = MetadataRepository(ctx, scope1, client1)
            repo1.configure(
                providers = ScraperCatalog.defaultMetadataConfigs().map {
                    it.copy(enabled = it.id == ScraperCatalog.MetaIds.LIBRETRO_DB || it.id == ScraperCatalog.MetaIds.WIKIDATA)
                },
                cred = ScraperCredentials(),
            )
            awaitRepoInit(repo1)

            // 1) ensureLoaded 批量离线解析 g1 & g2
            repo1.ensureLoaded(listOf(g1, g2), focusIdx = 0) { ps1 }
            waitUntil { db.resolvedMeta.containsKey(g1.id) && db.resolvedMeta.containsKey(g2.id) }
            assertTrue("冷启动 1 应加载 PS1 离线元数据表", getSystemTables(repo1).containsKey(ps1Sys))
            assertEquals(ScrapeDatabase.STATUS_MISS, db.resolvedMeta[g2.id]?.status)
            assertFalse("ensureLoaded 离线未命中不应标记 focusChecked", db.resolvedMeta[g2.id]?.focusChecked == true)

            // 2) resolveFocusGame 对焦点 g1 跑完整在线 API 检索后仍为空，应升级为 focusChecked = true
            repo1.resolveFocusGame(g1, ps1)
            waitUntil { db.resolvedMeta[g1.id]?.focusChecked == true }
            assertEquals(ScrapeDatabase.STATUS_MISS, db.resolvedMeta[g1.id]?.status)
            assertTrue("焦点完整探测未命中后应写入 focusChecked=true", db.resolvedMeta[g1.id]?.focusChecked == true)
        } finally {
            scope1.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }

        // ── 冷启动 2：全新 MetadataRepository 实例，仅从 SQLite 恢复，零离线表加载、零 API 发包 ──
        val httpBeforeCold2 = httpCount.get()
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client2 = ScraperClient(ctx, db)
            injectMockHttp(client2, mockHttp)
            val repo2 = MetadataRepository(ctx, scope2, client2)
            repo2.configure(
                providers = ScraperCatalog.defaultMetadataConfigs().map {
                    it.copy(enabled = it.id == ScraperCatalog.MetaIds.LIBRETRO_DB || it.id == ScraperCatalog.MetaIds.WIKIDATA)
                },
                cred = ScraperCredentials(),
            )
            awaitRepoInit(repo2)

            // 冷启动恢复不应向 newlyResolvedNames 发射历史官方名
            val emittedNames = Collections.synchronizedList(ArrayList<Set<String>>())
            val collectJob = scope2.launch { repo2.newlyResolvedNames.collect { emittedNames += it } }

            repo2.ensureLoaded(listOf(g1, g2), focusIdx = 0) { ps1 }
            repo2.resolveFocusGame(g1, ps1)
            delay(150L)

            assertTrue("冷启动 2 ensureLoaded + resolveFocusGame(g1) 不应加载 PS1 离线表", getSystemTables(repo2).isEmpty())
            assertEquals("冷启动 2 焦点 g1 命中全源负缓存不应再发网络请求", httpBeforeCold2, httpCount.get())

            // 离线态聚焦仅 offlineMiss 的 g2 同样零离线表加载
            setNetworkOnline(false)
            repo2.resolveFocusGame(g2, ps1)
            delay(100L)
            assertTrue("离线态聚焦已 offlineMiss 的 g2 不应加载离线表", getSystemTables(repo2).isEmpty())
            assertTrue("冷启动恢复不应误触发 newlyResolvedNames", emittedNames.isEmpty())
            collectJob.cancel()

            // 配置变更后应清空负缓存，允许重新加载离线表
            setNetworkOnline(true)
            repo2.configure(
                providers = ScraperCatalog.defaultMetadataConfigs().map {
                    it.copy(enabled = it.id == ScraperCatalog.MetaIds.LIBRETRO_DB)
                },
                cred = ScraperCredentials(),
            )
            waitUntil { db.resolvedMeta.isEmpty() }
            repo2.ensureLoaded(listOf(g1, g2), focusIdx = 0) { ps1 }
            waitUntil { getSystemTables(repo2).containsKey(ps1Sys) }
            assertTrue("切换元数据数据源后应清空负缓存并重新加载离线表", getSystemTables(repo2).containsKey(ps1Sys))
        } finally {
            setNetworkOnline(false)
            scope2.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }
    }

    @Test
    fun legacyOfficialNameOnlyRowAndTransientApiErrorAreHandledSafely() = runBlocking {
        setNetworkOnline(true)
        val (ctx, base) = newFakeContext("meta_legacy_transient")
        val db = FakeScrapeDatabase(ctx)
        val ps1Sys = "Sony - PlayStation"
        val ps1 = Platform("psx", "PS1", "PlayStation", 1f, libretroSystem = ps1Sys)
        val romDir = File(base, "roms").apply { mkdirs() }
        val gLegacy = Game(File(romDir, "FF7.chd").apply { writeText("x") }.path, "psx", "FF7", "FF7.chd", "FF7")
        val gErr = Game(File(romDir, "ErrGame.chd").apply { writeText("y") }.path, "psx", "ErrGame", "ErrGame.chd", "ErrGame")

        // 模拟旧版 v0.6.7 落库的“仅有官方名、无元数据”记录（status=STATUS_HIT, expiresAtMs=0, meta=empty）
        db.resolvedMeta[gLegacy.id] = ScrapeDatabase.ManualMetaRow(
            gameId = gLegacy.id,
            providerId = ScraperCatalog.MetaIds.LIBRETRO_DB,
            officialName = "Final Fantasy VII (USA)",
            canonicalName = "Final Fantasy VII (USA)",
            meta = GameMetadata(),
            status = ScrapeDatabase.STATUS_HIT,
            expiresAtMs = 0L,
            focusChecked = false,
        )
        val metaCacheDir = File(ctx.cacheDir, "metadata").apply { mkdirs() }
        File(metaCacheDir, "v3_${OfflineGameDb.sanitizeSystemName(ps1Sys)}.tsv").writeText(
            "22222222\t\tOther (USA)\t1999\tAction\tCapcom\tCapcom\n",
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx, db)
            // HTTP 503 模拟临时服务故障
            injectMockHttp(client, newMockHttp(statusCode = 503, body = "unavailable"))
            val repo = MetadataRepository(ctx, scope, client)
            repo.configure(
                providers = ScraperCatalog.defaultMetadataConfigs().map {
                    it.copy(enabled = it.id == ScraperCatalog.MetaIds.LIBRETRO_DB || it.id == ScraperCatalog.MetaIds.WIKIDATA)
                },
                cred = ScraperCredentials(),
            )
            awaitRepoInit(repo)

            // 1) 旧版仅官方名记录：恢复 officialName，且 ensureLoaded 不应重复加载离线表
            assertEquals("Final Fantasy VII (USA)", repo.officialNames.value[gLegacy.id])
            repo.ensureLoaded(listOf(gLegacy), focusIdx = 0) { ps1 }
            delay(120L)
            assertTrue("兼容旧版仅官方名记录时 ensureLoaded 不应解压离线表", getSystemTables(repo).isEmpty())

            // 2) 临时 503 故障时，resolveFocusGame 不得将 gErr 误标为 7 天 focusChecked=true
            repo.resolveFocusGame(gErr, ps1)
            waitUntil { db.resolvedMeta.containsKey(gErr.id) }
            assertFalse(
                "API 临时 503 故障不得误写 focusChecked=true 7天全源负缓存",
                db.resolvedMeta[gErr.id]?.focusChecked == true,
            )
        } finally {
            setNetworkOnline(false)
            scope.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }
    }

    // ─────────────────── 2. 封面负缓存：冷启动跳过 boxart 预载与窗口刮削，且不被其他平台加载误清 ───────────────────

    @Test
    fun coverPersistedFailureSkipsColdPreloadAndSurvivesOtherSystemLoadAndReconnect() = runBlocking {
        setNetworkOnline(true)
        val (ctx, base) = newFakeContext("cover_neg_cold")
        val db = FakeScrapeDatabase(ctx)
        val gbaSys = "Nintendo - Game Boy Advance"
        val nesSys = "Nintendo - Nintendo Entertainment System"
        val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, libretroSystem = gbaSys)
        val nes = Platform("nes", "NES", "NES", 1f, libretroSystem = nesSys)

        val indexDir = File(ctx.cacheDir, "boxart_index").apply { mkdirs() }
        File(indexDir, "${OfflineGameDb.sanitizeSystemName(gbaSys)}.txt").writeText("Golden Sun (USA).png\n")
        File(indexDir, "${OfflineGameDb.sanitizeSystemName(nesSys)}.txt").writeText("Super Mario Bros. (World).png\n")

        val romDir = File(base, "roms").apply { mkdirs() }
        val homebrew = Game(
            File(romDir, "MyUniqueHomebrew2026.gba").apply { writeText("hb") }.path,
            "gba",
            "MyUniqueHomebrew2026",
            "MyUniqueHomebrew2026.gba",
            "MyUniqueHomebrew2026",
        )
        val nesNew = Game(
            File(romDir, "Super Mario Bros.nes").apply { writeText("mario") }.path,
            "nes",
            "Super Mario Bros",
            "Super Mario Bros.nes",
            "Super Mario Bros",
        )

        val onlyLibretroNodes = ScraperCatalog.defaultCoverConfigs().map {
            it.copy(enabled = it.id == ScraperCatalog.CoverIds.LIBRETRO_BOXARTS)
        }

        // ── 冷启动 1：首次失败经 windowScrapeQueue 完成离线哈希后，应晋升持久化 7 天失败冷却 ──
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client1 = ScraperClient(ctx, db)
            injectMockHttp(client1, newMockHttp(statusCode = 404, body = ""))
            val covers1 = CoverSource(ctx, scope1, client1)
            covers1.configure(onlyLibretroNodes, ScraperCredentials())
            awaitCoversInit(covers1)

            // 模拟卡片先于 windowScrapeQueue 报告失败（此时未跑离线哈希，仅内存短冷却不写 DB）
            covers1.ensureIndexLoadedAwait(gba)
            covers1.rememberFailure(homebrew.id, gba)
            delay(80L)
            assertNull("离线哈希尚未探测前不应提前写入 7 天持久化负缓存", db.covers[homebrew.id])

            // 随后窗口刮削跑完离线哈希仍无封面，应晋升写入持久化 7 天负缓存
            covers1.ensureWindowCoversScraped(listOf(homebrew), 0, { gba })
            waitUntil { (db.covers[homebrew.id]?.failedUntilMs ?: 0L) > System.currentTimeMillis() }
            assertNotNull("全源探测未命中后应写入 cover_cache 持久化冷却", db.covers[homebrew.id])
        } finally {
            scope1.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }

        // ── 冷启动 2：全新 CoverSource 实例，应跳过 GBA boxart 预载与窗口刮削 ──
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client2 = ScraperClient(ctx, db)
            injectMockHttp(client2, newMockHttp(statusCode = 404, body = ""))
            val covers2 = CoverSource(ctx, scope2, client2)
            covers2.configure(onlyLibretroNodes, ScraperCredentials())
            awaitCoversInit(covers2)

            assertEquals("冷启动 2 持久负缓存项 knownIndex 应立即返回 -1", -1, covers2.knownIndex(homebrew.id, gba))

            covers2.ensureIndexesLoadedIfNeeded(listOf(homebrew), focusIdx = 0) { gba }
            covers2.ensureWindowCoversScraped(listOf(homebrew), 0, { gba })
            delay(300L)
            assertFalse("冷却期无封面游戏不应触发 GBA boxart 索引加载", getSystemIndexes(covers2).containsKey(gbaSys))

            // 加载其他平台（NES）索引不应误清 GBA 的持久负缓存
            assertTrue(covers2.ensureIndexLoadedAwait(nes))
            assertTrue(getSystemIndexes(covers2).containsKey(nesSys))
            assertEquals("加载 NES 索引不得误清 GBA 持久负缓存", -1, covers2.knownIndex(homebrew.id, gba))

            // 网络断开重连也不应误清持久负缓存
            setNetworkOnline(false)
            delay(30L)
            setNetworkOnline(true)
            delay(50L)
            assertEquals("网络重连不得误清 7 天持久负缓存", -1, covers2.knownIndex(homebrew.id, gba))

            // 收到新解析官方名时必须解锁该游戏持久负缓存，允许重新加载索引
            covers2.onOfficialNamesResolved(listOf(homebrew.id))
            assertNull("新解析官方名后应解锁持久负缓存", covers2.knownIndex(homebrew.id, gba))
            covers2.ensureIndexesLoadedIfNeeded(listOf(homebrew), focusIdx = 0) { gba }
            waitUntil { getSystemIndexes(covers2).containsKey(gbaSys) }
            assertTrue("解锁后应按需加载 GBA boxart 索引", getSystemIndexes(covers2).containsKey(gbaSys))
        } finally {
            setNetworkOnline(false)
            scope2.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }
    }

    @Test
    fun transientApiErrorDoesNotPersistCoverFailure() = runBlocking {
        setNetworkOnline(true)
        val (ctx, base) = newFakeContext("cover_transient_err")
        val db = FakeScrapeDatabase(ctx)
        val gbaSys = "Nintendo - Game Boy Advance"
        val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, libretroSystem = gbaSys)
        val indexDir = File(ctx.cacheDir, "boxart_index").apply { mkdirs() }
        File(indexDir, "${OfflineGameDb.sanitizeSystemName(gbaSys)}.txt").writeText("Other (USA).png\n")
        val rom = File(base, "SomeGame.gba").apply { writeText("rom") }
        val game = Game(rom.path, "gba", "SomeGame", rom.name, "SomeGame")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx, db)
            injectMockHttp(client, newMockHttp(statusCode = 500, body = "server error"))
            val covers = CoverSource(ctx, scope, client)
            covers.configure(
                ScraperCatalog.defaultCoverConfigs().map {
                    it.copy(enabled = it.id == ScraperCatalog.CoverIds.LIBRETRO_BOXARTS || it.id == ScraperCatalog.CoverIds.HASHEOUS)
                },
                ScraperCredentials(),
            )
            awaitCoversInit(covers)
            covers.ensureIndexLoadedAwait(gba)

            // 模拟 API 临时 500 错误（STATUS_ERROR）后调用 rememberFailure
            client.queryProvider(ScraperCatalog.CoverIds.HASHEOUS, game, gba, ScraperCredentials())
            assertFalse("STATUS_ERROR 不应视作已确认缓存", client.hasConfirmedCacheInMemory(ScraperCatalog.CoverIds.HASHEOUS, game.id))

            covers.ensureWindowCoversScraped(listOf(game), 0, { gba })
            delay(350L)
            assertNull("API 临时 500 故障不得写入 7 天 cover_cache 负缓存", db.covers[game.id])
        } finally {
            setNetworkOnline(false)
            scope.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }
    }

    // ─────────────────── 3. 多平台受控并发上限与焦点外扩优先级 ───────────────────

    @Test
    fun focusOutwardAndActiveSystemsCapNearFocus() {
        val visited = ArrayList<Int>()
        OfflineGameDb.forEachFocusOutward(size = 6, focusIdx = 2) { visited += it }
        assertEquals(listOf(2, 3, 1, 4, 0, 5), visited)

        val systems = (0 until 12).map { "System_$it" }
        val active = OfflineGameDb.collectActiveSystemsNearFocus(
            items = systems,
            focusIdx = 5,
            maxSystems = OfflineGameDb.MAX_ACTIVE_PROTECTED_SYSTEMS,
            systemOf = { it },
        )
        assertEquals(OfflineGameDb.MAX_ACTIVE_PROTECTED_SYSTEMS, active.size)
        assertEquals(
            listOf("System_5", "System_6", "System_4", "System_7", "System_3", "System_8"),
            active.toList(),
        )
    }

    @Test
    fun multiPlatformMetadataPreloadRespectsConcurrencyCapAndFocusPriority() = runBlocking {
        setNetworkOnline(false)
        val (ctx, base) = newFakeContext("meta_conc_cap")
        val db = FakeScrapeDatabase(ctx)
        val romDir = File(base, "roms").apply { mkdirs() }
        val metaCacheDir = File(ctx.cacheDir, "metadata").apply { mkdirs() }

        val platformCount = 8
        val platforms = (0 until platformCount).map { idx ->
            val sys = "TestSys_$idx"
            File(metaCacheDir, "v3_${OfflineGameDb.sanitizeSystemName(sys)}.tsv").writeText(
                "0000000$idx\t\tSample $idx (USA)\t2000\tAction\tDev\tPub\n",
            )
            Platform("p$idx", "P$idx", "Plat $idx", 1f, libretroSystem = sys)
        }
        val games = platforms.mapIndexed { idx, p ->
            val f = File(romDir, "game_$idx.rom").apply { writeText("r$idx") }
            Game(f.path, p.id, "Game $idx", f.name, "Game $idx")
        }
        val platById = platforms.associateBy { it.id }

        val activeWorkers = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val executionOrder = Collections.synchronizedList(ArrayList<String>())
        db.onPutResolvedBatch = { rows ->
            val cur = activeWorkers.incrementAndGet()
            maxConcurrent.accumulateAndGet(cur, ::maxOf)
            rows.firstOrNull()?.let { executionOrder += it.gameId }
            Thread.sleep(60L)
            activeWorkers.decrementAndGet()
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx, db)
            val repo = MetadataRepository(ctx, scope, client)
            repo.configure(
                providers = ScraperCatalog.defaultMetadataConfigs().map {
                    it.copy(enabled = it.id == ScraperCatalog.MetaIds.LIBRETRO_DB)
                },
                cred = ScraperCredentials(),
            )
            awaitRepoInit(repo)

            // 焦点位于第 4 项：并发上限不得超过 MAX_CONCURRENT_PRELOAD_SYSTEMS(=2)，且焦点附近平台最先执行
            repo.ensureLoaded(games, focusIdx = 4) { g -> platById[g.platformId] }
            waitUntil(timeoutMs = 8_000L) { executionOrder.size == platformCount }

            assertTrue(
                "多平台并发解析数不应超过 ${MetadataRepository.MAX_CONCURRENT_PRELOAD_SYSTEMS}，实际=${maxConcurrent.get()}",
                maxConcurrent.get() in 1..MetadataRepository.MAX_CONCURRENT_PRELOAD_SYSTEMS,
            )
            val firstTwo = executionOrder.take(2).toSet()
            assertTrue(
                "最先启动的 2 个平台应包含焦点所在平台(${games[4].id})，实际=$firstTwo",
                games[4].id in firstTwo,
            )
        } finally {
            scope.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }
    }

    @Test
    fun multiPlatformBoxartPreloadPrioritizesFocusAndLoadsAllNeededSystems() = runBlocking {
        setNetworkOnline(false)
        val (ctx, base) = newFakeContext("boxart_conc_cap")
        val db = FakeScrapeDatabase(ctx)
        val romDir = File(base, "roms").apply { mkdirs() }
        val indexDir = File(ctx.cacheDir, "boxart_index").apply { mkdirs() }

        val platformCount = 6
        val platforms = (0 until platformCount).map { idx ->
            val sys = "BoxSys_$idx"
            File(indexDir, "${OfflineGameDb.sanitizeSystemName(sys)}.txt").writeText(
                (1..200).joinToString("\n") { n -> "Title_${idx}_$n (USA).png" },
            )
            Platform("b$idx", "B$idx", "BoxPlat $idx", 1f, libretroSystem = sys)
        }
        val games = platforms.mapIndexed { idx, p ->
            val f = File(romDir, "box_$idx.rom").apply { writeText("b$idx") }
            Game(f.path, p.id, "Box $idx", f.name, "Box $idx")
        }
        val platById = platforms.associateBy { it.id }

        // 标记已完成首次 matcher_ver 迁移（模拟非首装的常规冷启动）
        ctx.getSharedPreferences("cover_matcher_meta", Context.MODE_PRIVATE)
            .edit()
            .putInt("matcher_ver", CoverSource.MATCHER_VERSION)
            .apply()

        // 预设第 0、1 平台的游戏处于持久失败冷却期，验证仅加载其余 4 个平台
        val future = System.currentTimeMillis() + ScrapeDatabase.TTL_MISS_MS
        db.putCoverFailure(games[0].id, future)
        db.putCoverFailure(games[1].id, future)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx, db)
            val covers = CoverSource(ctx, scope, client)
            covers.configure(
                ScraperCatalog.defaultCoverConfigs().map {
                    it.copy(enabled = it.id == ScraperCatalog.CoverIds.LIBRETRO_BOXARTS)
                },
                ScraperCredentials(),
            )
            awaitCoversInit(covers)

            covers.ensureIndexesLoadedIfNeeded(games, focusIdx = 3) { g -> platById[g.platformId]!! }
            waitUntil(timeoutMs = 8_000L) {
                (2 until platformCount).all { idx -> getSystemIndexes(covers).containsKey("BoxSys_$idx") }
            }
            delay(100L)
            assertFalse("冷却期平台 BoxSys_0 不应被预载", getSystemIndexes(covers).containsKey("BoxSys_0"))
            assertFalse("冷却期平台 BoxSys_1 不应被预载", getSystemIndexes(covers).containsKey("BoxSys_1"))
            assertEquals(4, getSystemIndexes(covers).size)
        } finally {
            scope.cancel()
            OfflineGameDb.trimMemory(emptySet())
        }
    }
}

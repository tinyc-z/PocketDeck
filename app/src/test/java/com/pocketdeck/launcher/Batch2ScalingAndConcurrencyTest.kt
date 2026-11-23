package com.pocketdeck.launcher

import android.content.Context
import android.content.ContextWrapper
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameTdbClient
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.PlayStats
import com.pocketdeck.launcher.data.ReplaceableTaskQueue
import com.pocketdeck.launcher.data.RomIdentityExtractor
import com.pocketdeck.launcher.data.ScrapeDatabase
import com.pocketdeck.launcher.data.ScrapeHit
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.UserData
import com.pocketdeck.launcher.data.UserStore
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.background.BG_STEP_IDLE
import com.pocketdeck.launcher.ui.components.background.BG_STEP_SLOW
import com.pocketdeck.launcher.ui.components.background.ThrottledClockStepper
import com.pocketdeck.launcher.ui.settings.computeScrapeCacheBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 第 2 批（60/120fps 渲染流畅度与大库缩放优化）专项回归测试：
 * - P0-4：`CoverSource` 零 `stat()` 懒哈希发现离线封面、`pruneDirByLru` 磁盘上限淘汰、`computeScrapeCacheBytes` 含缩略图与 GameTDB、`ScrapeDatabase` `extra_json` 纯字符串快路径
 * - P1-1：`RomIdentityExtractor` `skipFullFileCrc` 对 16MB `.nds` 仅读头部 Serial 不读全文件、且不污染后续完整 CRC 提取；`scrapeMissing` 批量合库写事务
 * - P1-2：`GameTdbClient.lookupInMemory` UI 线程仅走 O(1) 精确查表不跑倒排模糊扫描
 * - P1-3：`ReplaceableTaskQueue` 闲置后 2 并发立即拉满与 Worker 异常自愈；`MetadataRepository.scrapeMissing` 互斥双启与取消重启 `scrapeRunId` 守卫
 * - P1-4：`UserStore` 四项路径迁移/清理同步覆盖 `playStats`；`CoverSource`/`MetadataRepository` 内存缓存改名与删除联动
 * - P1-5：`ThrottledClockStepper` 在 120Hz（8.333ms）下保持稳定 20Hz / 30Hz 推进不腰斩
 */
class Batch2ScalingAndConcurrencyTest {

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

    private fun newFakeContext(prefix: String): Pair<Context, Pair<File, File>> {
        val root = tmp.newFolder(prefix)
        val files = File(root, "files").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        return FakeDirContext(files, cache) to (files to cache)
    }

    @Suppress("UNCHECKED_CAST")
    private fun withNetworkOnline(online: Boolean, block: () -> Unit) {
        val initField = NetworkStatus::class.java.getDeclaredField("initialized").apply { isAccessible = true }
        val flowField = NetworkStatus::class.java.getDeclaredField("_online").apply { isAccessible = true }
        val prevInit = initField.getBoolean(NetworkStatus)
        val flow = flowField.get(NetworkStatus) as MutableStateFlow<Boolean>
        val prevOnline = flow.value
        try {
            initField.setBoolean(NetworkStatus, true)
            flow.value = online
            block()
        } finally {
            flow.value = prevOnline
            initField.setBoolean(NetworkStatus, prevInit)
        }
    }

    // ─────────────────── P0-4: 零 stat() 懒哈希、LRU 磁盘淘汰、extra_json 快路径 ───────────────────

    @Test
    fun `coverSource lazy web cache discovery LRU disk prune and cache size calculation`() = runBlocking {
        val (ctx, dirs) = newFakeContext("p0_4_cover_lru")
        val cacheDir = dirs.second
        val webDir = File(cacheDir, "web_covers").apply { mkdirs() }
        val thumbDir = File(cacheDir, "cover_thumbs_webp").apply { mkdirs() }
        val tdbDir = File(cacheDir, "gametdb").apply { mkdirs() }

        val gameId = "/roms/gba/zelda.gba"
        val preCachedImg = File(webDir, "${CoverSource.sha256HexFast(gameId)}.img").apply {
            writeBytes(ByteArray(128) { 7 })
        }
        File(thumbDir, "abc.webp").writeBytes(ByteArray(256) { 3 })
        File(tdbDir, "wiitdb_v2.tsv").writeBytes(ByteArray(512) { 5 })

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val covers = CoverSource(ctx, scope, ScraperClient(ctx))
            covers.awaitInitialReady()

            val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, "Nintendo - Game Boy Advance")
            val game = Game(
                id = gameId,
                platformId = "gba",
                baseName = "Zelda",
                fileName = "zelda.gba",
                title = "Zelda",
            )
            // 仅通过 dir.list() 懒哈希命中离线封面（启动零 stat() / 零全库预哈希）
            assertEquals(listOf<Any>(preCachedImg), covers.fastMemoryCandidates(game))
            assertEquals(listOf<Any>(preCachedImg), covers.candidates(game, gba))

            // 缓存体积统计必须包含 cover_thumbs_webp 与 gametdb
            assertEquals(128L + 256L + 512L, computeScrapeCacheBytes(cacheDir))

            // LRU 磁盘上限淘汰：4 × 400B = 1600B，上限 1000B（目标回收至 850B），删最旧 2 个 + 顺手删 .tmp
            val pruneDir = tmp.newFolder("lru_prune")
            val f1 = File(pruneDir, "old1.img").apply { writeBytes(ByteArray(400)); setLastModified(1_000L) }
            val f2 = File(pruneDir, "old2.img").apply { writeBytes(ByteArray(400)); setLastModified(2_000L) }
            val f3 = File(pruneDir, "new1.img").apply { writeBytes(ByteArray(400)); setLastModified(3_000L) }
            val f4 = File(pruneDir, "new2.img").apply { writeBytes(ByteArray(400)); setLastModified(4_000L) }
            val tmpLeftover = File(pruneDir, "partial.img.123.tmp").apply { writeBytes(ByteArray(50)) }

            val deletedNames = mutableListOf<String>()
            val removedCount = CoverSource.pruneDirByLru(pruneDir, maxBytes = 1_000L) { deletedNames += it }
            assertEquals(2, removedCount)
            assertEquals(listOf("old1.img", "old2.img"), deletedNames)
            assertFalse(f1.exists())
            assertFalse(f2.exists())
            assertTrue(f3.exists())
            assertTrue(f4.exists())
            assertFalse("Leftover .tmp file must be cleaned up", tmpLeftover.exists())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `scrapeDatabase extra_json uses plain provider string fast path and stays backward compatible`() {
        val (ctx, _) = newFakeContext("p0_4_extra_fast")
        val db = ScrapeDatabase(ctx)
        val simpleHit = ScrapeHit(officialName = "Mario", meta = GameMetadata(genre = "Platform"))

        val encodedSimple = db.encodeExtraForTest(simpleHit, manualProvider = "libretro_db", focusChecked = false)
        assertEquals("libretro_db", encodedSimple)
        assertEquals("libretro_db", db.decodeExtraProviderForTest(encodedSimple, "fallback"))

        // 兼容旧版 JSON 行
        assertEquals("igdb", db.decodeExtraProviderForTest("""{"manualProvider":"igdb"}""", "fallback"))

        // 多字段（含 canonicalName / focusChecked）仍序列化为 JSON
        val richHit = ScrapeHit(officialName = "马里奥", canonicalName = "Mario", meta = GameMetadata(description = "Classic"))
        val encodedRich = db.encodeExtraForTest(richHit, manualProvider = "wikidata", focusChecked = true)
        assertNotNull(encodedRich)
        assertTrue(encodedRich!!.startsWith("{"))
        assertEquals("wikidata", db.decodeExtraProviderForTest(encodedRich, "fallback"))
    }

    // ─────────────────── P1-1: 静默浏览 skipFullFileCrc 不读大文件体 ───────────────────

    @Test
    fun `romIdentityExtractor skipFullFileCrc reads 16MB nds header fast without polluting full CRC cache`() {
        val (ctx, _) = newFakeContext("p1_1_nds_fast")
        val db = ScrapeDatabase(ctx)
        val extractor = RomIdentityExtractor(db)

        // 构造 16MB 假 .nds ROM，仅在 0x0C 写入合法 4 字节 Game Code "ASME"
        val bigNds = File(tmp.root, "big_game.nds")
        RandomAccessFile(bigNds, "rw").use { raf ->
            raf.setLength(16L * 1024 * 1024)
            raf.seek(0x0CL)
            raf.write("ASME".toByteArray(Charsets.US_ASCII))
        }

        val t0 = System.nanoTime()
        val fastId = extractor.extract(bigNds, platformId = "nds", skipFullFileCrc = true)
        val fastMs = (System.nanoTime() - t0) / 1_000_000L
        assertEquals("ASME", fastId.serial)
        assertNull("skipFullFileCrc must skip 16MB CRC32 streaming", fastId.crc32)
        assertTrue("Fast header-only extract should finish quickly (took ${fastMs}ms)", fastMs < 500L)

        // 后续需要完整指纹（如手动/在线刮削）时，必须能正常算出完整 CRC32，不被快速缓存污染
        val fullId = extractor.extract(bigNds, platformId = "nds", skipFullFileCrc = false)
        assertEquals("ASME", fullId.serial)
        assertNotNull("Full extract must compute CRC32 after fast header-only extract", fullId.crc32)
        assertEquals(8, fullId.crc32!!.length)
    }

    // ─────────────────── P1-2: GameTdbClient.lookupInMemory UI 线程 O(1) ───────────────────

    @Test
    fun `gameTdbClient lookupInMemory uses O1 exact match only without UI thread fuzzy scan`() {
        val (ctx, _) = newFakeContext("p1_2_tdb_o1")
        val tdb = ScraperClient(ctx).gameTdb

        val entry = GameTdbClient.TdbEntry(
            id = "RMGE01",
            region = "USA",
            titleEn = "Super Mario Galaxy",
            titleZh = null,
            titleJa = null,
            developer = "Nintendo",
            publisher = "Nintendo",
            genre = "Platformer",
            releaseDate = "2007-11-12",
        )
        val tableClass = Class.forName("com.pocketdeck.launcher.data.GameTdbClient\$TdbTable")
        val ctor = tableClass.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            Map::class.java,
            Map::class.java,
            Map::class.java,
            Map::class.java,
        ).apply { isAccessible = true }
        val table = ctor.newInstance(
            "wiitdb",
            "wii",
            mapOf("RMGE01" to entry),
            mapOf("super mario galaxy" to entry),
            mapOf("supermariogalaxy" to entry),
            mapOf("super" to listOf(entry), "mario" to listOf(entry), "galaxy" to listOf(entry)),
        )
        val tablesCache = GameTdbClient::class.java.getDeclaredField("tables").apply { isAccessible = true }.get(tdb)!!
        tablesCache.javaClass.getMethod("set", Any::class.java, Any::class.java).invoke(tablesCache, "wiitdb", table)

        val wii = Platform("wii", "Wii", "Nintendo Wii", 0.7f, "Nintendo - Wii")
        val exactGame = Game("/roms/wii/smg.rvz", "wii", "Super Mario Galaxy (USA)", "smg.rvz", "Super Mario Galaxy")
        val fuzzyOnlyGame = Game("/roms/wii/smg_typo.rvz", "wii", "Super Mario Galxy", "smg_typo.rvz", "Super Mario Galxy")

        assertNotNull("Exact match must succeed O(1)", tdb.lookupInMemory(exactGame, wii))
        assertNull("Fuzzy-only typo must NOT run inverted token scan on UI thread", tdb.lookupInMemory(fuzzyOnlyGame, wii))
    }

    // ─────────────────── P1-3: ReplaceableTaskQueue 满并发唤醒与异常自愈 ───────────────────

    @Test
    fun `replaceableTaskQueue wakes second idle worker immediately and survives task exceptions`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val warmupDone = CountDownLatch(1)
            val bothEntered = CountDownLatch(2)
            val releaseBoth = CountDownLatch(1)
            val afterCrashDone = CountDownLatch(1)

            val queue = ReplaceableTaskQueue<String>(
                scope = scope,
                maxConcurrency = 2,
                taskTimeoutMs = 5_000L,
            ) { payload ->
                when (payload) {
                    "warmup" -> warmupDone.countDown()
                    "crash" -> throw IllegalStateException("simulated crash")
                    "after_crash" -> afterCrashDone.countDown()
                    else -> {
                        bothEntered.countDown()
                        releaseBoth.await(3, TimeUnit.SECONDS)
                    }
                }
            }

            // 1. 先跑 1 个任务让两个 Worker 启动并全部进入空闲等待态
            queue.replacePending(listOf("w" to "warmup"))
            assertTrue(warmupDone.await(2, TimeUnit.SECONDS))
            delay(60L)

            // 2. 从空闲态一次性入队 2 个阻塞任务：两个 Worker 必须立即并发进入（不等待第 1 个任务结束）
            queue.replacePending(listOf("a" to "block_a", "b" to "block_b"))
            assertTrue("Both workers must wake concurrently from idle", bothEntered.await(2, TimeUnit.SECONDS))
            releaseBoth.countDown()
            delay(50L)

            // 3. 任务抛出非取消异常后 Worker 不得死亡，后续任务继续正常执行
            queue.replacePending(listOf("c1" to "crash", "c2" to "after_crash"))
            assertTrue("Worker must survive task exception and execute subsequent task", afterCrashDone.await(2, TimeUnit.SECONDS))
        } finally {
            scope.cancel()
        }
    }

    // ─────────────────── P1-1 & P1-3: scrapeMissing 互斥、批量合库与 scrapeRunId 守卫 ───────────────────

    @Test
    fun `metadataRepository scrapeMissing batches DB writes and guards concurrent double launch`() = runBlocking {
        val (ctx, _) = newFakeContext("p1_3_scrape_batch")
        val batchCalls = AtomicInteger(0)
        val totalRowsPersisted = AtomicInteger(0)

        val countingDb = object : ScrapeDatabase(ctx) {
            override fun loadAllCovers(): List<CoverRow> = emptyList()
            override fun loadAllResolvedMetadata(nowMs: Long): List<ManualMetaRow> = emptyList()
            override fun loadAllManualMetadata(): List<ManualMetaRow> = emptyList()
            override fun putResolvedMetadataBatch(rows: List<ManualMetaRow>) {
                batchCalls.incrementAndGet()
                totalRowsPersisted.addAndGet(rows.size)
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx, countingDb)
            val covers = CoverSource(ctx, scope, client)
            val repo = MetadataRepository(ctx, scope, client)

            // 仅启用离线 Libretro DB 且在离线态运行，保证零网络访问且批量写入负缓存行
            val offlineOnly = ScraperCatalog.defaultMetadataConfigs().map {
                it.copy(enabled = it.id == ScraperCatalog.MetaIds.LIBRETRO_DB)
            }
            repo.configure(offlineOnly, ScraperCredentials())

            val customPlatform = Platform("custom", "CUS", "Custom", 1f, null)
            val games = (1..24).map { i ->
                Game(
                    id = "/roms/custom/game_$i.bin",
                    platformId = "custom",
                    baseName = "Game $i",
                    fileName = "game_$i.bin",
                    title = "Game $i",
                )
            }

            withNetworkOnline(false) {
                runBlocking {
                    // 并发双触发 scrapeMissing：Mutex 串行保护，返回相同 total 且不会启动双套 Worker 重复落库
                    val d1 = async { repo.scrapeMissing(games, { customPlatform }, covers) }
                    val d2 = async { repo.scrapeMissing(games, { customPlatform }, covers) }
                    val t1 = d1.await()
                    val t2 = d2.await()
                    assertEquals(24, t1)
                    assertEquals(24, t2)

                    repeat(100) {
                        if (!repo.scrapeProgress.value.running) return@repeat
                        delay(30L)
                    }
                    assertFalse(repo.scrapeProgress.value.running)
                    assertEquals(24, repo.scrapeProgress.value.done)
                    assertEquals(24, totalRowsPersisted.get())
                    assertTrue(
                        "24 games should be persisted in far fewer than 24 single-row DB batches (was ${batchCalls.get()})",
                        batchCalls.get() <= 4,
                    )
                }
            }
        } finally {
            scope.cancel()
        }
    }

    // ─────────────────── P1-4: UserStore playStats 迁移清理与内存缓存同步 ───────────────────

    @Test
    fun `userStore and scrape repos migrate and clean playStats and cached entries on rename or delete`() {
        val (ctx, _) = newFakeContext("p1_4_migrate")
        val userStore = UserStore(ctx)
        val oldId = "/roms/gba/old.gba"
        val newId = "/roms/gba/new.gba"
        val stat = PlayStats(playCount = 7, totalPlayMs = 123_456L, lastPlayedAt = 999L)
        val initial = UserData(
            favorites = setOf(oldId),
            recents = listOf(oldId),
            playStats = mapOf(oldId to stat),
        )

        val migrated = userStore.applyMigrateGameId(initial, oldId, newId)
        assertNull(migrated.playStats[oldId])
        assertEquals(stat, migrated.playStats[newId])

        val removed = userStore.applyRemoveGameData(migrated, newId)
        assertTrue(removed.playStats.isEmpty())

        val dirMigrated = userStore.applyMigratePathPrefix(initial, "/roms/gba", "/roms/gba_renamed")
        assertNull(dirMigrated.playStats[oldId])
        assertEquals(stat, dirMigrated.playStats["/roms/gba_renamed/old.gba"])

        val dirRemoved = userStore.applyRemovePathPrefix(dirMigrated, "/roms/gba_renamed")
        assertTrue(dirRemoved.playStats.isEmpty())

        // CoverSource & MetadataRepository 内存缓存改名/删除同步
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val metaRepo = MetadataRepository(ctx, scope, client)
            val url = "https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_GBA@master/Named_Boxarts/Old.png"
            val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, "Nintendo - Game Boy Advance")

            covers.rememberSuccess(oldId, url)
            metaRepo.seedLocalMetadata(mapOf(oldId to GameMetadata(releaseDate = "2003", genre = "RPG")))

            covers.migrateGameId(oldId, newId)
            metaRepo.migrateGameId(oldId, newId)

            val newGame = Game(newId, "gba", "New", "new.gba", "New")
            assertEquals(listOf<Any>(url), covers.candidates(newGame, gba))
            assertEquals("RPG", metaRepo.metadata.value[newId]?.genre)
            assertNull(metaRepo.metadata.value[oldId])

            covers.forget(newId)
            metaRepo.forgetGame(newId)
            assertNull(metaRepo.metadata.value[newId])
        } finally {
            scope.cancel()
        }
    }

    // ─────────────────── P1-5: 120Hz 节流时钟不掉半速 ───────────────────

    @Test
    fun `throttledClockStepper maintains 20Hz and 30Hz on 120Hz vsync timestamps`() {
        val frameNs = 8_333_333L // 120Hz = 8.333333ms/frame (6 帧 = 49.999998ms < 50ms)

        val stepper20 = ThrottledClockStepper(BG_STEP_SLOW)
        var ticks20 = 0
        var now = 1_000_000_000L
        repeat(121) {
            stepper20.onFrame(now) { ticks20++ }
            now += frameNs
        }
        assertTrue("20Hz clock on 120Hz screen should advance 19..20 times/sec, not 10 (got $ticks20)", ticks20 in 19..20)

        val stepper30 = ThrottledClockStepper(BG_STEP_IDLE)
        var ticks30 = 0
        now = 1_000_000_000L
        repeat(121) {
            stepper30.onFrame(now) { ticks30++ }
            now += frameNs
        }
        assertTrue("30Hz clock on 120Hz screen should advance 29..30 times/sec, not 15 (got $ticks30)", ticks30 in 29..30)
    }
}

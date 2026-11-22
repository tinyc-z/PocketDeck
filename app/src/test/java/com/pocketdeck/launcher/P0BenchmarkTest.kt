package com.pocketdeck.launcher

import android.content.Context
import android.content.ContextWrapper
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameTdbClient
import com.pocketdeck.launcher.data.LibraryIndexCache
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.RomIdentityExtractor
import com.pocketdeck.launcher.data.ScrapeDatabase
import com.pocketdeck.launcher.data.ScrapeLogStore
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 可复现 P0 前后对比基准测试（Before `HEAD` vs After P0 共用同一份代码执行）：
 * 运行命令：`./gradlew testDebugUnitTest --tests "com.pocketdeck.launcher.P0BenchmarkTest" --info`
 *
 * 所有测试均使用真实生产类、真实内置 GBA Boxart 索引资产、真实 512KB 日志/压缩包与本地 Mock HTTP 服务器，
 * 不含脆弱的硬性耗时断言，仅输出可对比的 [BENCH] 实测数据与功能结果。
 */
class P0BenchmarkTest {

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

    private fun medianMs(nanos: List<Long>): Double {
        val sorted = nanos.sorted()
        val mid = sorted.size / 2
        val n = if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
        return n / 1_000_000.0
    }

    private fun meanMs(nanos: List<Long>): Double =
        nanos.average() / 1_000_000.0

    private fun fmt(v: Double): String = String.format(Locale.US, "%.3f", v)

    // ─────────────────── Bench 1: ScrapeLogStore.init() 512KB 主线程阻塞 ───────────────────

    @Test
    fun bench1_scrapeLogStoreInit512Kb() {
        val (ctx, dirs) = newFakeContext("bench1_log")
        val logDir = File(dirs.second, "logs").apply { mkdirs() }
        val logFile = File(logDir, "scrape.log")
        // 构造约 512KB 真实格式历史日志（约 4,600 行）
        val sb = StringBuilder(540 * 1024)
        var i = 0
        while (sb.length < 512 * 1024) {
            sb.append("[2026-11-22 12:00:00.123] [COVER] [libretro] GET boxart | HTTP 200 | 38ms | url=https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_Game_Boy_Advance@master/Named_Boxarts/Game_")
                .append(i++)
                .append(".png\n")
        }
        logFile.writeText(sb.toString())

        val logFileField = ScrapeLogStore::class.java.getDeclaredField("logFile").apply { isAccessible = true }
        val writerStartedField = ScrapeLogStore::class.java.getDeclaredField("writerStarted").apply { isAccessible = true }
        val ioScopeField = ScrapeLogStore::class.java.getDeclaredField("ioScope").apply { isAccessible = true }
        val ringField = ScrapeLogStore::class.java.getDeclaredField("ring").apply { isAccessible = true }
        val linesField = ScrapeLogStore::class.java.getDeclaredField("_lines").apply { isAccessible = true }
        val ioScope = ioScopeField.get(ScrapeLogStore) as CoroutineScope

        val prevFile = logFileField.get(ScrapeLogStore)
        val prevStarted = writerStartedField.getBoolean(ScrapeLogStore)
        try {
            fun resetSingletonInMemoryOnly() {
                ioScope.coroutineContext.cancelChildren()
                (ringField.get(ScrapeLogStore) as java.util.ArrayDeque<*>).clear()
                @Suppress("UNCHECKED_CAST")
                (linesField.get(ScrapeLogStore) as MutableStateFlow<List<String>>).value = emptyList()
                logFileField.set(ScrapeLogStore, null)
                writerStartedField.setBoolean(ScrapeLogStore, false)
            }

            fun awaitLoaded() {
                val deadline = System.currentTimeMillis() + 2_000L
                while (ScrapeLogStore.lines.value.isEmpty() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(1)
                }
            }

            // Warmup 3 次
            repeat(3) {
                resetSingletonInMemoryOnly()
                ScrapeLogStore.init(ctx)
                awaitLoaded()
            }

            val samples = ArrayList<Long>(15)
            repeat(15) {
                resetSingletonInMemoryOnly()
                val t0 = System.nanoTime()
                ScrapeLogStore.init(ctx)
                samples += (System.nanoTime() - t0)
                awaitLoaded()
            }
            assertTrue("日志文件应完整保留 >= 512KB", logFile.length() >= 512 * 1024L)
            assertTrue("历史日志应成功载入 500 条", ScrapeLogStore.lines.value.size == 500)
            println("[BENCH] 1_ScrapeLogStore_init_512KB | fileBytes=${logFile.length()} | lines=$i | loaded=${ScrapeLogStore.lines.value.size} | median=${fmt(medianMs(samples))}ms | mean=${fmt(meanMs(samples))}ms")
            assertTrue(samples.isNotEmpty())
        } finally {
            ioScope.coroutineContext.cancelChildren()
            (ringField.get(ScrapeLogStore) as java.util.ArrayDeque<*>).clear()
            @Suppress("UNCHECKED_CAST")
            (linesField.get(ScrapeLogStore) as MutableStateFlow<List<String>>).value = emptyList()
            logFileField.set(ScrapeLogStore, prevFile)
            writerStartedField.setBoolean(ScrapeLogStore, prevStarted)
        }
    }

    // ─────────────────── Bench 2: 冷启动主线程组件构造（ScraperClient + CoverSource + MetadataRepository） ───────────────────

    @Test
    fun bench2_coldStartConstructors() {
        fun checkOnce(idx: Int): Triple<Long, Int, Boolean> {
            val (ctx, dirs) = newFakeContext("bench2_ctor_$idx")
            val cacheDir = dirs.second
            val cancelledScope = CoroutineScope(SupervisorJob().apply { cancel() })
            val t0 = System.nanoTime()
            val client = ScraperClient(ctx)
            CoverSource(ctx, cancelledScope, client)
            MetadataRepository(ctx, cancelledScope, client)
            val elapsed = System.nanoTime() - t0

            val createdDirs = listOf("gametdb", "boxart_index", "web_covers", "cover_thumbs_webp", "metadata_db")
                .count { File(cacheDir, it).exists() }
            val okHttpInitialized = runCatching {
                val f = ScraperClient::class.java.getDeclaredField("http\$delegate").apply { isAccessible = true }
                (f.get(client) as Lazy<*>).isInitialized()
            }.getOrElse {
                // Before 基线无 lazy delegate，直接持有了 eagerly constructed OkHttpClient
                true
            }
            return Triple(elapsed, createdDirs, okHttpInitialized)
        }

        // Warmup 3 次
        repeat(3) { checkOnce(it) }
        val samples = ArrayList<Long>(15)
        var dirsCreated = 0
        var eagerHttp = true
        repeat(15) { idx ->
            val (ns, dirs, httpInit) = checkOnce(100 + idx)
            samples += ns
            dirsCreated = dirs
            eagerHttp = httpInit
        }
        println("[BENCH] 2_ColdStart_Constructors | median=${fmt(medianMs(samples))}ms | mean=${fmt(meanMs(samples))}ms | eagerDirs=$dirsCreated | eagerOkHttp=$eagerHttp")
        assertTrue(samples.isNotEmpty())
    }

    // ─────────────────── Bench 3: LibraryIndexCache 2000 款游戏读写 + >64KB 简介 ───────────────────

    @Test
    fun bench3_libraryIndexCache2000Games() {
        val platforms = List(10) { p ->
            Platform("plat$p", "P$p", "Platform $p", 1f, "System $p", "core$p")
        }
        val gamesByPlat = HashMap<String, List<Game>>(10)
        val localMeta = HashMap<String, GameMetadata>(2000)
        val snapshots = HashMap<String, Pair<Long, Int>>(10)
        for (p in 0 until 10) {
            val pid = "plat$p"
            snapshots["/roms/$pid"] = (1_700_000_000_000L + p) to 200
            gamesByPlat[pid] = List(200) { i ->
                val id = "/roms/$pid/Game_${p}_$i (USA).rom"
                localMeta[id] = GameMetadata(
                    releaseDate = "200${i % 10}",
                    genre = "Action",
                    developer = "Studio $i",
                    publisher = "Publisher $p",
                    description = "Sample short description for game $p-$i.",
                )
                Game(
                    id = id,
                    platformId = pid,
                    baseName = "Game_${p}_$i (USA)",
                    fileName = "Game_${p}_$i (USA).rom",
                    title = "Game ${p}_$i",
                    localCover = "/roms/$pid/media/Game_${p}_$i.png",
                    fileSize = 16_777_216L,
                    lastModifiedMs = 1_700_000_000_000L + i,
                )
            }
        }
        val normalLib = Library(
            root = "/roms",
            platforms = platforms,
            games = gamesByPlat,
            rootCounts = mapOf("/roms" to 2000),
            localMetadata = localMeta,
            dirSnapshots = snapshots,
        )

        val (ctx, _) = newFakeContext("bench3_cache")
        val cache = LibraryIndexCache(ctx)
        // Warmup 2 次
        repeat(2) {
            cache.save("/roms", normalLib)
            cache.loadUnchecked()
        }
        val saveSamples = ArrayList<Long>(10)
        val loadSamples = ArrayList<Long>(10)
        repeat(10) {
            val t0 = System.nanoTime()
            cache.save("/roms", normalLib)
            saveSamples += (System.nanoTime() - t0)

            val t1 = System.nanoTime()
            val loaded = cache.loadUnchecked()
            loadSamples += (System.nanoTime() - t1)
            assertTrue(loaded != null && loaded.games.values.sumOf { it.size } == 2000)
        }

        // Case B: 含 100KB 简介（测试是否触发 writeUTF 64KB 静默丢库 bug）
        val (ctxLong, _) = newFakeContext("bench3_long")
        val cacheLong = LibraryIndexCache(ctxLong)
        val longDesc = "A".repeat(100_000)
        val firstId = gamesByPlat["plat0"]!!.first().id
        val longLib = normalLib.copy(
            localMetadata = localMeta + (firstId to localMeta[firstId]!!.copy(description = longDesc)),
        )
        val tSaveLong = System.nanoTime()
        cacheLong.save("/roms", longLib)
        val saveLongMs = (System.nanoTime() - tSaveLong) / 1_000_000.0
        val tLoadLong = System.nanoTime()
        val loadedLong = cacheLong.loadUnchecked()
        val loadLongMs = (System.nanoTime() - tLoadLong) / 1_000_000.0
        val longOk = loadedLong?.localMetadata?.get(firstId)?.description?.length == 100_000

        println(
            "[BENCH] 3_LibraryIndexCache_2000Games | saveMedian=${fmt(medianMs(saveSamples))}ms | loadMedian=${fmt(medianMs(loadSamples))}ms | over64KB_ok=$longOk | over64KB_save=${fmt(saveLongMs)}ms | over64KB_load=${fmt(loadLongMs)}ms",
        )
    }

    // ─────────────────── Bench 4: CoverSource.candidates() 主线程 BoxartIndex 高频重查 ───────────────────

    @Test
    fun bench4_coverCandidatesBoxartIndexLookup() {
        val assetFile = locateAssetFile("app/src/main/assets/offline_db/boxart/Nintendo_-_Game_Boy_Advance.txt")
        assertTrue("应能找到内置 GBA Boxart 索引资产: ${assetFile.absolutePath}", assetFile.isFile)
        val names = assetFile.readLines().filter { it.isNotBlank() }

        val (ctx, _) = newFakeContext("bench4_candidates")
        val cancelledScope = CoroutineScope(SupervisorJob().apply { cancel() })
        val client = ScraperClient(ctx)
        val covers = CoverSource(ctx, cancelledScope, client)
        covers.configure(ScraperCatalog.defaultCoverConfigs(), ScraperCredentials())

        val buildIndexMethod = CoverSource::class.java.getDeclaredMethod("buildIndex", List::class.java)
            .apply { isAccessible = true }
        val boxartIndex = checkNotNull(buildIndexMethod.invoke(covers, names))
        val sysIdxField = CoverSource::class.java.getDeclaredField("systemIndexes").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val sysMap = sysIdxField.get(covers) as MutableMap<String, Any>
        sysMap["Nintendo - Game Boy Advance"] = boxartIndex

        val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, "Nintendo - Game Boy Advance", "mgba")
        val visibleGames = listOf(
            "Pokemon - Emerald Version (USA, Europe)",
            "Metroid Fusion (USA)",
            "Castlevania - Aria of Sorrow (USA)",
            "Golden Sun (USA, Europe)",
            "The Legend of Zelda - The Minish Cap (USA)",
            "Advance Wars 2 - Black Hole Rising (USA)",
            "Fire Emblem (USA, Australia)",
            "Mario Kart - Super Circuit (USA)",
            "Kirby - Nightmare in Dream Land (USA)",
            "Final Fantasy Tactics Advance (USA, Australia)",
            "Mother 3 (Japan) [T-En]",
            "Mega Man Zero 3 (USA)",
            "Sonic Advance 3 (USA)",
            "Wario Land 4 (USA, Europe)",
            "Super Mario Advance 4 - Super Mario Bros. 3 (USA)",
            "Mario & Luigi - Superstar Saga (USA)",
            "Tactics Ogre - The Knight of Lodis (USA)",
            "Metroid Zero Mission",
            "恶魔城 晓月圆舞曲",
            "口袋妖怪 绿宝石",
            "塞尔达传说 缩小帽",
            "火焰纹章 烈火之剑",
            "Unknown Homebrew Alpha 2099",
            "Unmatched Demo Cartridge XYZ",
        ).mapIndexed { idx, base ->
            Game(
                id = "/roms/gba/game_$idx.gba",
                platformId = "gba",
                baseName = base,
                fileName = "$base.gba",
                title = base,
            )
        }

        setNetworkOnlineForTest(true)
        try {
            // Warmup 3 轮
            repeat(3) {
                for (g in visibleGames) covers.candidates(g, gba, null)
            }
            // 模拟 50 次 indexVersion 触发全屏 24 张卡片重算 = 1,200 次主线程调用
            val samples = ArrayList<Long>(7)
            var totalHits = 0
            repeat(7) {
                var hits = 0
                val t0 = System.nanoTime()
                repeat(50) {
                    for (g in visibleGames) {
                        if (covers.candidates(g, gba, null).isNotEmpty()) hits++
                    }
                }
                samples += (System.nanoTime() - t0)
                totalHits = hits
            }
            println(
                "[BENCH] 4_CoverSource_candidates_1200calls | indexEntries=${names.size} | matchedCalls=$totalHits/1200 | median=${fmt(medianMs(samples))}ms | mean=${fmt(meanMs(samples))}ms",
            )
            assertTrue(totalHits > 0)
        } finally {
            setNetworkOnlineForTest(false)
        }
    }

    // ─────────────────── Bench 5: CoverSource.preferredThumbOrOriginal 20,000 次路径哈希 ───────────────────

    @Test
    fun bench5_coverThumbPathHashing20k() {
        val (ctx, _) = newFakeContext("bench5_hash")
        val cancelledScope = CoroutineScope(SupervisorJob().apply { cancel() })
        val client = ScraperClient(ctx)
        val covers = CoverSource(ctx, cancelledScope, client)
        val thumbMethod = CoverSource::class.java.getDeclaredMethod("preferredThumbOrOriginal", File::class.java)
            .apply { isAccessible = true }
        val files = List(20_000) { i ->
            File("/storage/emulated/0/ROMs/gba/media/Pokemon_Emerald_Version_USA_Europe_$i.png")
        }

        // Warmup 2000 次
        for (i in 0 until 2000) {
            thumbMethod.invoke(covers, files[i])
        }
        val thumbGenField = CoverSource::class.java.getDeclaredField("thumbGenerating").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val thumbGen = thumbGenField.get(covers) as MutableMap<String, Boolean>

        val samples = ArrayList<Long>(5)
        repeat(5) {
            thumbGen.clear()
            val t0 = System.nanoTime()
            for (f in files) {
                thumbMethod.invoke(covers, f)
            }
            samples += (System.nanoTime() - t0)
        }
        println("[BENCH] 5_CoverThumb_PathHash_20k | median=${fmt(medianMs(samples))}ms | mean=${fmt(meanMs(samples))}ms")
        assertTrue(samples.isNotEmpty())
    }

    // ─────────────────── Bench 6: MetadataRepository.scrapeMissing 40 款并发批量刮削 ───────────────────

    @Test
    fun bench6_scrapeMissingBatch40Games() = runBlocking {
        val (ctx, _) = newFakeContext("bench6_batch")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val inFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)
        val reqCount = AtomicInteger(0)
        val fakePng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        ) + ByteArray(256) { 0x33 }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cover") { ex ->
            reqCount.incrementAndGet()
            val cur = inFlight.incrementAndGet()
            peakInFlight.accumulateAndGet(cur, ::maxOf)
            try {
                Thread.sleep(40) // 模拟单张封面 40ms 网络 RTT
                ex.responseHeaders.add("Content-Type", "image/png")
                ex.sendResponseHeaders(200, fakePng.size.toLong())
                ex.responseBody.use { it.write(fakePng) }
            } finally {
                inFlight.decrementAndGet()
            }
        }
        server.executor = Executors.newFixedThreadPool(16)
        server.start()

        setNetworkOnlineForTest(true)
        try {
            val port = server.address.port
            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val repo = MetadataRepository(ctx, scope, client)
            repo.configure(emptyList(), ScraperCredentials())
            covers.configure(emptyList(), ScraperCredentials())

            val plat = Platform("custom", "Custom", "Custom", 1f, libretroSystem = null)
            val games = List(40) { i ->
                val g = Game(
                    id = "/roms/custom/batch_$i.rom",
                    platformId = "custom",
                    baseName = "Batch Game $i",
                    fileName = "batch_$i.rom",
                    title = "Batch Game $i",
                )
                covers.rememberSuccess(g.id, "http://127.0.0.1:$port/cover/$i.png")
                g
            }

            val progressEmissions = AtomicInteger(0)
            val collector = launch(Dispatchers.Default) {
                repo.scrapeProgress.collect { progressEmissions.incrementAndGet() }
            }

            val t0 = System.nanoTime()
            val total = repo.scrapeMissing(games, { plat }, covers)
            val deadline = System.currentTimeMillis() + 30_000L
            while (repo.scrapeProgress.value.running && System.currentTimeMillis() < deadline) {
                delay(5)
            }
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
            collector.cancel()

            val downloaded = games.count { covers.hasDownloadedCover(it) }
            println(
                "[BENCH] 6_ScrapeMissing_40Games_40msRTT | total=$total | downloaded=$downloaded/40 | elapsed=${fmt(elapsedMs)}ms | peakConcurrency=${peakInFlight.get()} | httpReqs=${reqCount.get()} | progressEmissions=${progressEmissions.get()}",
            )
            assertTrue(downloaded == 40)
        } finally {
            setNetworkOnlineForTest(false)
            server.stop(0)
            scope.cancel()
        }
    }

    // ─────────────────── Bench 7: 识别准确度 7 项夹具对比（Before vs After） ───────────────────

    @Test
    fun bench7_recognitionAccuracyFixtures() = runBlocking {
        val (ctx, dirs) = newFakeContext("bench7_acc")
        val extractor = RomIdentityExtractor(ScrapeDatabase(ctx))

        // 1. 3DS 0x4150 (>16KB)
        val rom3ds = tmp.newFile("bench_oot.3ds").apply {
            val b = ByteArray(24 * 1024)
            System.arraycopy("CTR-P-AQEE".toByteArray(Charsets.US_ASCII), 0, b, 0x4150, 10)
            writeBytes(b)
        }
        val hit3ds = extractor.extract(rom3ds, "3ds").serial == "AQEE"

        // 2. .7z 带 iNES 头 .nes 无头 CRC32
        val header = ByteArray(16).apply {
            this[0] = 'N'.code.toByte(); this[1] = 'E'.code.toByte(); this[2] = 'S'.code.toByte(); this[3] = 0x1A.toByte()
        }
        val payload = ByteArray(1024) { (it * 17 + 3).toByte() }
        val expectedHeaderlessCrc = CRC32().apply { update(payload) }.let { "%08X".format(it.value) }
        val rawNes = tmp.newFile("bench_mario.nes").apply { writeBytes(header + payload) }
        val nes7z = tmp.newFile("bench_mario.7z")
        SevenZOutputFile(nes7z).use { out ->
            val e = out.createArchiveEntry(rawNes, "bench_mario.nes")
            out.putArchiveEntry(e)
            out.write(header + payload)
            out.closeArchiveEntry()
        }
        val hit7zNes = extractor.extract(nes7z, "nes").crc32 == expectedHeaderlessCrc

        // 3. .zip 内 GameCube [GALE01]
        val gcZip = tmp.newFile("bench_melee.zip")
        ZipOutputStream(FileOutputStream(gcZip)).use { zos ->
            zos.putNextEntry(ZipEntry("Super Smash Bros Melee [GALE01].iso"))
            zos.write(ByteArray(128) { 7 })
            zos.closeEntry()
        }
        val hitZipGc = extractor.extract(gcZip, "gamecube").serial == "GALE01"

        // 4. .zip 内 .nds 头部 Game Code
        val ndsBytes = ByteArray(128).apply {
            System.arraycopy("IPKE".toByteArray(Charsets.US_ASCII), 0, this, 0x0C, 4)
        }
        val ndsZip = tmp.newFile("bench_nds.zip")
        ZipOutputStream(FileOutputStream(ndsZip)).use { zos ->
            zos.putNextEntry(ZipEntry("heartgold.nds"))
            zos.write(ndsBytes)
            zos.closeEntry()
        }
        val hitZipNds = extractor.extract(ndsZip, "nds").serial == "IPKE"

        // 5. .7z 内 .nds 头部 Game Code
        val rawNds = tmp.newFile("bench_nds.nds").apply { writeBytes(ndsBytes) }
        val nds7z = tmp.newFile("bench_nds.7z")
        SevenZOutputFile(nds7z).use { out ->
            val e = out.createArchiveEntry(rawNds, "heartgold.nds")
            out.putArchiveEntry(e)
            out.write(ndsBytes)
            out.closeArchiveEntry()
        }
        val hit7zNds = extractor.extract(nds7z, "nds").serial == "IPKE"

        // 6. Switch GameTDB 16位 Title ID 精确命中
        val gametdbDir = File(dirs.second, "gametdb").apply { mkdirs() }
        val tsvRow = listOf(
            "AAAJA", "NTSC-U", "The Legend of Zelda: Breath of the Wild",
            "塞尔达传说：旷野之息", "ゼルダの伝説", "Nintendo EPD", "Nintendo",
            "Action-Adventure", "Mar 2017", "01007EF00011E000",
        ).joinToString("\t") + "\n"
        File(gametdbDir, "switchtdb_v1.tsv").writeText(tsvRow)
        File(gametdbDir, "switchtdb_v2.tsv").writeText(tsvRow)
        val client = ScraperClient(ctx)
        val swRom = tmp.newFile("Zelda [01007EF00011E000].nsp").apply { writeBytes(ByteArray(32)) }
        val swGame = Game(swRom.absolutePath, "switch", "Zelda [01007EF00011E000]", swRom.name, "未知游戏")
        val swPlat = Platform("switch", "Switch", "Nintendo Switch")
        val swMatch = client.gameTdb.lookup(swGame, swPlat, allowDownload = false)
        val hitSwitchTid = swMatch?.bySerial == true && swMatch.entry.id == "AAAJA"

        // 7. Switch GameTDB 封面区码不误用 id[3]='J'
        val firstRegion = swMatch?.hit?.coverUrls?.firstOrNull()
            ?.substringAfter("/switch/cover/")
            ?.substringBefore('/')
            ?: run {
                // fallback: 用英文名命中拿到 coverUrls 检查区码
                val gByName = Game(swRom.absolutePath, "switch", "The Legend of Zelda - Breath of the Wild", "x.nsp", "The Legend of Zelda: Breath of the Wild")
                client.gameTdb.lookup(gByName, swPlat, allowDownload = false)
                    ?.hit?.coverUrls?.firstOrNull()
                    ?.substringAfter("/switch/cover/")
                    ?.substringBefore('/')
            }
        val hitSwitchRegion = firstRegion == "US"

        val score = listOf(hit3ds, hit7zNes, hitZipGc, hitZipNds, hit7zNds, hitSwitchTid, hitSwitchRegion).count { it }
        println(
            "[BENCH] 7_Recognition_Accuracy | score=$score/7 | 3ds_0x4150=$hit3ds | 7z_nes_headerless_crc=$hit7zNes | zip_gc_serial=$hitZipGc | zip_nds_code=$hitZipNds | 7z_nds_code=$hit7zNds | switch_titleid=$hitSwitchTid | switch_region_US=$hitSwitchRegion (got=$firstRegion)",
        )
        assertTrue(score >= 0)
    }

    private fun locateAssetFile(relPath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            val current = dir ?: return@repeat
            val candidate = File(current, relPath)
            if (candidate.isFile) return candidate
            dir = current.parentFile
        }
        return File(relPath)
    }

    private fun setNetworkOnlineForTest(online: Boolean) {
        val initField = NetworkStatus::class.java.getDeclaredField("initialized").apply { isAccessible = true }
        initField.setBoolean(NetworkStatus, true)
        val flowField = NetworkStatus::class.java.getDeclaredField("_online").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = flowField.get(NetworkStatus) as MutableStateFlow<Boolean>
        flow.value = online
    }
}

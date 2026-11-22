package com.pocketdeck.launcher

import android.content.Context
import android.content.ContextWrapper
import com.pocketdeck.launcher.data.CachedAndroidApp
import com.pocketdeck.launcher.data.CoverMatchMode
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameTdbClient
import com.pocketdeck.launcher.data.LibraryIndexCache
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.RomIdentityExtractor
import com.pocketdeck.launcher.data.ScrapeDatabase
import com.pocketdeck.launcher.data.ScrapeHit
import com.pocketdeck.launcher.data.ScrapeLogStore
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.SourceItemConfig
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * P0 修复功能回归测试：覆盖启动懒加载、LibraryIndexCache v3/v2 兼容、ROM 指纹识别、
 * GameTDB Switch 支持、TGDB/IGDB 相似度门控、CoverSource 优先级/哈希/配置保留、
 * TopBar 并发时间格式化以及并发批量刮削链路。
 */
class P0RegressionTest {

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

    private fun newFakeContext(prefix: String = "ctx"): Pair<Context, Pair<File, File>> {
        val root = tmp.newFolder(prefix)
        val files = File(root, "files").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        return FakeDirContext(files, cache) to (files to cache)
    }

    // ─────────────────── P0-1: 懒初始化，不阻塞启动主线程 ───────────────────

    @Test
    fun `p0_1_lazyInitDoesNotEagerlyCreateOkHttpOrDiskDirs`() {
        val (ctx, dirs) = newFakeContext("p0_1_lazy")
        val cacheDir = dirs.second
        // 使用已取消的 scope（使 init 中 launch(Dispatchers.IO) 不立即执行），严格验证构造函数本身在调用线程零 mkdirs / 零 OkHttp 初始化
        val cancelledJob = SupervisorJob().apply { cancel() }
        val scope = CoroutineScope(cancelledJob)
        val client = ScraperClient(ctx)
        val httpDelegate = ScraperClient::class.java.getDeclaredField("http\$delegate")
            .apply { isAccessible = true }
            .get(client) as Lazy<*>
        assertFalse("OkHttpClient 不应在构造期初始化", httpDelegate.isInitialized())
        assertFalse("gametdb 目录不应在构造期 eager mkdirs", File(cacheDir, "gametdb").exists())

        val covers = CoverSource(ctx, scope, client)
        assertFalse("boxart_index 目录不应在构造期同步 mkdirs", File(cacheDir, "boxart_index").exists())
        assertFalse("web_covers 目录不应在构造期同步 mkdirs", File(cacheDir, "web_covers").exists())
        assertFalse("cover_thumbs_webp 目录不应在构造期同步 mkdirs", File(cacheDir, "cover_thumbs_webp").exists())

        MetadataRepository(ctx, scope, client)
        assertFalse("metadata_db 目录不应在构造期同步 mkdirs", File(cacheDir, "metadata_db").exists())
        assertFalse("MetadataRepository 构造期也不应触发 OkHttp 初始化", httpDelegate.isInitialized())
        assertNotNull(covers)
    }

    @Test
    fun `p0_1_scrapeLogStoreInitLoadsHistoryAsync`() = runBlocking {
        val (ctx, dirs) = newFakeContext("p0_1_logstore")
        val logDir = File(dirs.second, "logs").apply { mkdirs() }
        val logFile = File(logDir, "scrape.log")
        val totalLines = 600
        logFile.writeText((1..totalLines).joinToString("\n") { i -> "[2026-01-01 00:00:00.000] [COVER] [libretro] line-$i" } + "\n")

        val logFileField = ScrapeLogStore::class.java.getDeclaredField("logFile").apply { isAccessible = true }
        val writerStartedField = ScrapeLogStore::class.java.getDeclaredField("writerStarted").apply { isAccessible = true }
        val prevFile = logFileField.get(ScrapeLogStore)
        val prevStarted = writerStartedField.getBoolean(ScrapeLogStore)
        try {
            ScrapeLogStore.clear()
            logFileField.set(ScrapeLogStore, null)
            writerStartedField.setBoolean(ScrapeLogStore, false)

            ScrapeLogStore.init(ctx)

            val deadline = System.currentTimeMillis() + 5_000L
            while (ScrapeLogStore.lines.value.isEmpty() && System.currentTimeMillis() < deadline) {
                delay(10)
            }
            val loaded = ScrapeLogStore.lines.value
            assertEquals("后台应异步预载最近 500 条日志", 500, loaded.size)
            assertTrue("最新日志应排在首位", loaded.first().endsWith("line-600"))
        } finally {
            ScrapeLogStore.clear()
            logFileField.set(ScrapeLogStore, prevFile)
            writerStartedField.setBoolean(ScrapeLogStore, prevStarted)
        }
    }

    // ─────────────────── P0-2: LibraryIndexCache v3 & >64KB 描述 & v2 兼容 ───────────────────

    @Test
    fun `p0_2_libraryIndexCacheV3HandlesOver64KbDescriptionAndInstalledApps`() {
        val (ctx, _) = newFakeContext("p0_2_v3")
        val cache = LibraryIndexCache(ctx)
        val longDesc = buildString(100_500) {
            repeat(10_000) { append("勇者斗恶龙简介测试AB") }
        }
        assertTrue("描述 UTF-8 字节数需大于 64KB", longDesc.toByteArray(Charsets.UTF_8).size > 65_535)

        val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, "Nintendo - Game Boy Advance", "mgba")
        val game = Game(
            id = "/roms/gba/Mother 3 (Japan).gba",
            platformId = "gba",
            baseName = "Mother 3 (Japan)",
            fileName = "Mother 3 (Japan).gba",
            title = "Mother 3",
            localCover = "/roms/gba/media/Mother 3.png",
            fileSize = 33_554_432L,
            lastModifiedMs = 1_700_000_000_000L,
            discFiles = listOf("/roms/gba/Mother 3 (Japan).gba"),
        )
        val lib = Library(
            root = "/roms",
            platforms = listOf(gba),
            games = mapOf("gba" to listOf(game)),
            rootCounts = mapOf("/roms" to 1),
            localMetadata = mapOf(
                game.id to GameMetadata(
                    releaseDate = "2006",
                    genre = "RPG",
                    developer = "Brownie Brown",
                    publisher = "Nintendo",
                    description = longDesc,
                ),
            ),
            dirSnapshots = mapOf("/roms/gba" to (1_700_000_000_000L to 12)),
        )
        val apps = List(50) { i ->
            CachedAndroidApp(
                packageName = "com.example.game$i",
                label = "Android Game $i",
                isAutoGame = i % 2 == 0,
                apkSize = 10_000_000L + i,
                lastModifiedMs = 1_700_000_000_000L + i,
            )
        }

        cache.save("/roms", lib, apps)

        val snap = cache.loadUncheckedSnapshot()
        assertNotNull("v3 快照应成功读取", snap)
        assertEquals(longDesc, snap!!.library.localMetadata[game.id]?.description)
        assertEquals(mapOf("/roms/gba" to (1_700_000_000_000L to 12)), snap.library.dirSnapshots)
        assertEquals(apps, snap.installedApps)
        assertEquals(lib.games["gba"], snap.library.games["gba"])
    }

    @Test
    fun `p0_2_libraryIndexCacheBackwardCompatibleWithV2`() {
        val (ctx, dirs) = newFakeContext("p0_2_v2_compat")
        val cacheFile = File(dirs.first, "library_index_v1.bin")
        DataOutputStream(BufferedOutputStream(FileOutputStream(cacheFile))).use { out ->
            out.writeInt(0x50444958) // MAGIC
            out.writeInt(2) // VERSION_V2
            out.writeUTF("/roms")
            out.writeInt(1)
            out.writeUTF("/roms")
            out.writeInt(1)
            out.writeInt(1) // platforms
            out.writeUTF("nes")
            out.writeUTF("NES")
            out.writeUTF("Nintendo Entertainment System")
            out.writeFloat(0.72f)
            out.writeBoolean(true); out.writeUTF("Nintendo - Nintendo Entertainment System")
            out.writeBoolean(true); out.writeUTF("fceumm")
            out.writeInt(1) // buckets
            out.writeUTF("nes")
            out.writeInt(1)
            out.writeUTF("/roms/nes/mario.nes")
            out.writeUTF("nes")
            out.writeUTF("mario")
            out.writeUTF("mario.nes")
            out.writeUTF("Mario")
            out.writeBoolean(false)
            out.writeBoolean(false)
            out.writeLong(40976L)
            out.writeLong(123456L)
            out.writeInt(0)
            out.writeInt(1) // localMetadata
            out.writeUTF("/roms/nes/mario.nes")
            out.writeBoolean(true); out.writeUTF("1985")
            out.writeBoolean(false)
            out.writeBoolean(false)
            out.writeBoolean(false)
            out.writeBoolean(true); out.writeUTF("Classic platformer")
            out.writeInt(1) // dirSnapshots
            out.writeUTF("/roms/nes")
            out.writeLong(999L)
            out.writeInt(3)
        }

        val snap = LibraryIndexCache(ctx).loadUncheckedSnapshot()
        assertNotNull("旧版 v2 快照应兼容读取", snap)
        assertEquals("Classic platformer", snap!!.library.localMetadata["/roms/nes/mario.nes"]?.description)
        assertNull("v2 快照不包含 installedApps", snap.installedApps)
    }

    // ─────────────────── P0-3: Library 过滤保留 localMetadata 与 dirSnapshots ───────────────────

    @Test
    fun `p0_3_libraryCopyPreservesLocalMetadataAndDirSnapshots`() {
        val g1 = Game("/roms/gba/a.gba", "gba", "a", "a.gba", "A")
        val g2 = Game("/roms/gba/b.gba", "gba", "b", "b.gba", "B")
        val gba = Platform("gba", "GBA", "Game Boy Advance")
        val full = Library(
            root = "/roms",
            platforms = listOf(gba),
            games = mapOf("gba" to listOf(g1, g2)),
            localMetadata = mapOf(g1.id to GameMetadata(developer = "Nintendo")),
            dirSnapshots = mapOf("/roms/gba" to (1234L to 2)),
        )
        val filtered = full.copy(
            platforms = listOf(gba),
            games = mapOf("gba" to listOf(g1)),
        )
        assertEquals(full.localMetadata, filtered.localMetadata)
        assertEquals(full.dirSnapshots, filtered.dirSnapshots)
    }

    // ─────────────────── P0-4: DateTimeFormatter 并发安全 ───────────────────

    @Test
    fun `p0_4_dateTimeFormatterIsThreadSafeUnderHighConcurrency`() {
        val fmt24 = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val fmt12 = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = List(8) { threadIdx ->
                Callable {
                    val sample = LocalTime.of((threadIdx * 3) % 24, (threadIdx * 7) % 60)
                    repeat(1500) {
                        val s24 = sample.format(fmt24)
                        val s12 = sample.format(fmt12)
                        assertTrue("24h 格式异常: $s24", s24.matches(Regex("""\d{2}:\d{2}""")))
                        assertTrue("12h 格式异常: $s12", s12.contains(":"))
                    }
                    true
                }
            }
            val results = pool.invokeAll(tasks)
            results.forEach { assertTrue(it.get()) }
        } finally {
            pool.shutdownNow()
        }
    }

    // ─────────────────── P0-7: RomIdentityExtractor 修复回归 ───────────────────

    @Test
    fun `p0_7_3dsHeaderSerialAtOffset0x4150IsExtracted`() {
        val (ctx, _) = newFakeContext("p0_7_3ds")
        val extractor = RomIdentityExtractor(ScrapeDatabase(ctx))
        val rom = tmp.newFile("zelda_oot.3ds")
        val bytes = ByteArray(24 * 1024)
        val tag = "CTR-P-AQEE".toByteArray(Charsets.US_ASCII)
        System.arraycopy(tag, 0, bytes, 0x4150, tag.size)
        rom.writeBytes(bytes)

        val id = extractor.extract(rom, "3ds")
        assertEquals("应读满 32KB 提取 0x4150 处的 3DS 产品码", "AQEE", id.serial)
        assertTrue(extractor.hasFastFingerprint(rom, "3ds"))
    }

    @Test
    fun `p0_7_sevenZipHeaderedNesComputesHeaderlessCrc32`() {
        val (ctx, _) = newFakeContext("p0_7_7z_nes")
        val extractor = RomIdentityExtractor(ScrapeDatabase(ctx))
        val header = ByteArray(16).apply {
            this[0] = 'N'.code.toByte()
            this[1] = 'E'.code.toByte()
            this[2] = 'S'.code.toByte()
            this[3] = 0x1A.toByte()
            this[4] = 2
            this[5] = 1
        }
        val payload = ByteArray(1024) { (it * 31 + 7).toByte() }
        val expectedHeaderlessCrc = CRC32().apply { update(payload) }.let { "%08X".format(it.value) }
        val fullBytes = header + payload
        val headeredCrc = CRC32().apply { update(fullBytes) }.let { "%08X".format(it.value) }
        assertTrue(expectedHeaderlessCrc != headeredCrc)

        val rawNes = tmp.newFile("Super Mario Bros.nes").apply { writeBytes(fullBytes) }
        val szFile = tmp.newFile("mario.7z")
        SevenZOutputFile(szFile).use { out ->
            val entry = out.createArchiveEntry(rawNes, "Super Mario Bros.nes")
            out.putArchiveEntry(entry)
            out.write(fullBytes)
            out.closeArchiveEntry()
        }

        val id = extractor.extract(szFile, "nes")
        assertEquals("7z 内 iNES ROM 应剥离 16B 头计算无头 CRC32", expectedHeaderlessCrc, id.crc32)
        assertEquals(fullBytes.size.toLong(), id.romSize)
    }

    @Test
    fun `p0_7_zipAndSevenZipExtractPlatformSerialAndNdsGameCode`() {
        val (ctx, _) = newFakeContext("p0_7_archive_serial")
        val extractor = RomIdentityExtractor(ScrapeDatabase(ctx))

        // 1) .zip 内含 GameCube [GALE01]
        val gcZip = tmp.newFile("melee.zip")
        ZipOutputStream(FileOutputStream(gcZip)).use { zos ->
            zos.putNextEntry(ZipEntry("Super Smash Bros Melee [GALE01].iso"))
            zos.write(ByteArray(256) { 1 })
            zos.closeEntry()
        }
        val gcId = extractor.extract(gcZip, "gamecube")
        assertEquals("GALE01", gcId.serial)
        assertEquals(256L, gcId.romSize)

        // 2) .zip 内含 .nds 头部 Game Code（0x0C..0x0F）
        val ndsBytes = ByteArray(128).apply {
            System.arraycopy("IPKE".toByteArray(Charsets.US_ASCII), 0, this, 0x0C, 4)
        }
        val ndsZip = tmp.newFile("heartgold.zip")
        ZipOutputStream(FileOutputStream(ndsZip)).use { zos ->
            zos.putNextEntry(ZipEntry("heartgold.nds"))
            zos.write(ndsBytes)
            zos.closeEntry()
        }
        assertEquals("IPKE", extractor.extract(ndsZip, "nds").serial)

        // 3) .7z 内含 .nds 头部 Game Code
        val rawNds = tmp.newFile("platinum.nds").apply { writeBytes(ndsBytes) }
        val nds7z = tmp.newFile("platinum.7z")
        SevenZOutputFile(nds7z).use { out ->
            val e = out.createArchiveEntry(rawNds, "platinum.nds")
            out.putArchiveEntry(e)
            out.write(ndsBytes)
            out.closeArchiveEntry()
        }
        assertEquals("IPKE", extractor.extract(nds7z, "nds").serial)
    }

    @Test
    fun `p0_7_archiveCandidateSelectionDoesNotOverflowAbove2Gb`() {
        val (ctx, _) = newFakeContext("p0_7_overflow")
        val extractor = RomIdentityExtractor(ScrapeDatabase(ctx))
        // 4.5GB ISO > Int.MAX_VALUE (~2.15GB)
        val hugeIsoSize = 4_500_000_000L
        val smallBinSize = 10_000_000L
        assertTrue(
            "4.5GB 已知 ROM 应胜过 10MB 已知 ROM（不发生 Int 溢出）",
            extractor.isBetterArchiveCandidate(
                knownRom = true,
                size = hugeIsoSize,
                bestKnownRom = true,
                bestSize = smallBinSize,
            ),
        )
        assertFalse(
            "10MB 已知 ROM 不应胜过 4.5GB 已知 ROM",
            extractor.isBetterArchiveCandidate(
                knownRom = true,
                size = smallBinSize,
                bestKnownRom = true,
                bestSize = hugeIsoSize,
            ),
        )
        assertTrue(
            "已知 ROM 应优先于更大的未知扩展名文件",
            extractor.isBetterArchiveCandidate(
                knownRom = true,
                size = 1024L,
                bestKnownRom = false,
                bestSize = hugeIsoSize,
            ),
        )
    }

    // ─────────────────── P0-8: GameTDB Switch TitleID、区码与双语 canonicalName ───────────────────

    @Test
    fun `p0_8_gameTdbSwitchTitleIdLookupRegionAndCanonicalName`() = runBlocking {
        val (ctx, dirs) = newFakeContext("p0_8_switch")
        val cacheDir = dirs.second
        val gametdbDir = File(cacheDir, "gametdb").apply { mkdirs() }
        // 故意让 Switch 5 位码第 4 位为 'J'（AAAJA），验证不再套用 Wii/DS id[3]=='J'->JA 规则
        val tsv = File(gametdbDir, "switchtdb_v2.tsv")
        tsv.writeText(
            listOf(
                "AAAJA",
                "NTSC-U",
                "The Legend of Zelda: Breath of the Wild",
                "塞尔达传说：旷野之息",
                "ゼルダの伝説 ブレス オブ ザ ワイルド",
                "Nintendo EPD",
                "Nintendo",
                "Action-Adventure",
                "Mar 2017",
                "01007EF00011E000",
            ).joinToString("\t") + "\n",
        )

        val client = ScraperClient(ctx)
        val switchPlat = Platform("switch", "Switch", "Nintendo Switch")
        val romFile = tmp.newFile("Zelda BOTW [01007EF00011E000].nsp").apply { writeBytes(ByteArray(32)) }
        val game = Game(
            id = romFile.absolutePath,
            platformId = "switch",
            baseName = "Zelda BOTW [01007EF00011E000]",
            fileName = romFile.name,
            title = "塞尔达",
        )

        val match = client.gameTdb.lookup(game, switchPlat, allowDownload = false)
        assertNotNull("Switch 16位 Title ID 应通过 switchtdb_v2.tsv 精确命中", match)
        assertTrue("应标记为 bySerial 命中", match!!.bySerial)
        assertEquals("AAAJA", match.entry.id)

        // 验证 Switch 区码不误用 id[3]
        val switchRegions = GameTdbClient.coverRegionCodes(match.entry, artSystem = "switch")
        assertEquals("Switch 应优先按 region=NTSC-U 生成 US，而非误读 id[3]='J' 生成 JA", "US", switchRegions.first())

        // 验证 DS 仍保持原有 id[3] 规则不受影响
        val dsEntry = GameTdbClient.TdbEntry("ADAJ", "NTSC-U", "Pokemon Diamond", null, null, null, null, null, null)
        assertEquals("DS 仍应保留 id[3]='J' -> JA 区码推断", "JA", GameTdbClient.coverRegionCodes(dsEntry, artSystem = "ds").first())

        // 验证中英分离：preferZh=true 时 officialName 为中文，canonicalName 与 effectiveSearchName 为英文标准名
        val hitZh = GameTdbClient.entryToHit(match.entry, artSystem = "switch", preferZh = true)
        assertEquals("塞尔达传说：旷野之息", hitZh.officialName)
        assertEquals("The Legend of Zelda: Breath of the Wild", hitZh.canonicalName)
        assertEquals("The Legend of Zelda: Breath of the Wild", hitZh.effectiveSearchName)
    }

    // ─────────────────── P0-9: TGDB/IGDB 相似度门控 ───────────────────

    @Test
    fun `p0_9_tgdbAndIgdbRejectUnrelatedLowSimilarityResults`() {
        val unrelated = JSONArray(
            listOf(
                JSONObject(mapOf("id" to 1, "game_title" to "Barbie Horse Adventures", "name" to "Barbie Horse Adventures")),
                JSONObject(mapOf("id" to 2, "game_title" to "Cooking Mama", "name" to "Cooking Mama")),
            ),
        )
        assertNull(
            "无达标候选时绝不盲兜底第 0 条无关游戏",
            ScraperClient.selectBestTitleCandidate("Chrono Trigger", unrelated, "game_title"),
        )
        assertNull(
            "IGDB 字段同样拒绝无关第 0 条",
            ScraperClient.selectBestTitleCandidate("Chrono Trigger", unrelated, "name"),
        )

        val withMatch = JSONArray(
            listOf(
                JSONObject(mapOf("id" to 1, "game_title" to "Unrelated Title")),
                JSONObject(mapOf("id" to 2, "game_title" to "Chrono Trigger")),
            ),
        )
        val picked = ScraperClient.selectBestTitleCandidate("Chrono Trigger", withMatch, "game_title")
        assertNotNull(picked)
        assertEquals(2, picked!!.getInt("id"))
    }

    // ─────────────────── P0-10 & P0-14: CoverSource 哈希、优先级与 configure 保留 ───────────────────

    @Test
    fun `p0_14_fastSha1AndSha256MatchStandardMessageDigest`() {
        val samples = listOf(
            "",
            "/storage/emulated/0/ROMs/gba/Pokemon - Emerald Version (USA, Europe).gba",
            "/storage/emulated/0/ROMs/switch/塞尔达传说：王国之泪 [0100F2C0115B6000].nsp",
            "https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_Game_Boy_Advance@master/Named_Boxarts/Metroid%20Fusion%20(USA).png",
        )
        for (s in samples) {
            val stdSha1 = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val stdSha256 = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals(stdSha1, CoverSource.sha1HexFast(s))
            assertEquals(stdSha256, CoverSource.sha256HexFast(s))
        }
    }

    @Test
    fun `p0_10_coverPriorityAndConfigureRetainsEnabledNodeCovers`() {
        val (ctx, dirs) = newFakeContext("p0_10_priority")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val localFile = tmp.newFile("local.png").apply { writeBytes(ByteArray(64) { 1 }) }
            val customFile = tmp.newFile("custom.png").apply { writeBytes(ByteArray(64) { 2 }) }
            val game = Game(
                id = "/roms/gba/metroid.gba",
                platformId = "gba",
                baseName = "Metroid Fusion (USA)",
                fileName = "metroid.gba",
                title = "Metroid Fusion",
                localCover = localFile.absolutePath,
            )
            val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, "Nintendo - Game Boy Advance")

            // 初始启用默认节点，模拟已解析出 Libretro 在线封面
            val baseNodes = ScraperCatalog.defaultCoverConfigs()
            covers.configure(baseNodes, ScraperCredentials())
            val webUrl = "https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_Game_Boy_Advance@master/Named_Boxarts/Metroid%20Fusion%20(USA).png"
            covers.rememberSuccess(game.id, webUrl)

            // 验证优先级：cachedUrl > localCover（保证手动刮削/在线命中可覆盖 localCover；快滑路径保留 localCover 兜底）
            val candsWithWeb = covers.candidates(game, gba)
            assertEquals(listOf<Any>(webUrl), candsWithWeb)
            assertEquals(listOf<Any>(webUrl, localFile), covers.fastMemoryCandidates(game))

            // 验证优先级：customCover > cachedUrl > localCover
            val withCustom = game.copy(customCover = customFile.absolutePath)
            assertEquals(customFile.absolutePath, (covers.candidates(withCustom, gba).first() as File).absolutePath)

            // 配置微调（仅填凭证或新增启用备用节点）：已解析的 Libretro 封面应完整保留
            val expandedNodes = baseNodes.map {
                if (it.id == ScraperCatalog.CoverIds.STEAMGRIDDB) it.copy(enabled = true) else it
            }
            covers.configure(expandedNodes, ScraperCredentials(sgdbKey = "demo_key"))
            assertEquals("新增备用节点不应清空已有封面", webUrl, covers.fastMemoryCandidates(game).first())

            // 明确禁用 Libretro 节点：仅清除来自该被禁用节点的封面
            val disabledLibretro = expandedNodes.map {
                if (it.id == ScraperCatalog.CoverIds.LIBRETRO_BOXARTS) it.copy(enabled = false) else it
            }
            covers.configure(disabledLibretro, ScraperCredentials(sgdbKey = "demo_key"))
            assertEquals("禁用原来源节点后应清除对应缓存并回落 localCover", listOf<Any>(localFile), covers.fastMemoryCandidates(game))
            assertTrue(dirs.second.exists())
        } finally {
            scope.cancel()
        }
    }

    // ─────────────────── P0-11: 并发批量刮削端到端功能回归 ───────────────────

    @Test
    fun `p0_11_scrapeMissingConcurrentlyDownloadsAllCoversWithoutLoss`() = runBlocking {
        val (ctx, _) = newFakeContext("p0_11_batch")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val hits = AtomicInteger(0)
        // 最小合法 PNG 头字节（>32 字节）
        val fakePng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        ) + ByteArray(128) { 0x42 }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cover") { ex ->
            hits.incrementAndGet()
            Thread.sleep(20)
            ex.responseHeaders.add("Content-Type", "image/png")
            ex.sendResponseHeaders(200, fakePng.size.toLong())
            ex.responseBody.use { it.write(fakePng) }
        }
        server.executor = Executors.newFixedThreadPool(8)
        server.start()

        setNetworkOnlineForTest(true)
        try {
            val port = server.address.port
            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val repo = MetadataRepository(ctx, scope, client)
            repo.configure(emptyList(), ScraperCredentials())
            covers.configure(emptyList(), ScraperCredentials())

            val plat = Platform("custom", "Custom", "Custom Platform", 1f, libretroSystem = null)
            val games = List(16) { i ->
                val g = Game(
                    id = "/roms/custom/game_$i.rom",
                    platformId = "custom",
                    baseName = "Game $i",
                    fileName = "game_$i.rom",
                    title = "Game $i",
                )
                covers.rememberSuccess(g.id, "http://127.0.0.1:$port/cover/$i.png")
                g
            }

            val total = repo.scrapeMissing(games, { plat }, covers)
            assertEquals(16, total)

            // 等待批量任务完成
            val deadline = System.currentTimeMillis() + 10_000L
            while (repo.scrapeProgress.value.running && System.currentTimeMillis() < deadline) {
                delay(20)
            }
            val finalProg = repo.scrapeProgress.value
            assertFalse("批量刮削应正常结束", finalProg.running)
            assertEquals(16, finalProg.done)
            assertEquals(16, finalProg.updatedCount)
            assertEquals(0, finalProg.failedCount)
            assertEquals(16, hits.get())
            games.forEach { g ->
                assertTrue("每个游戏都应已落盘本地封面: ${g.id}", covers.hasDownloadedCover(g))
            }
        } finally {
            setNetworkOnlineForTest(false)
            server.stop(0)
            scope.cancel()
        }
    }

    @Test
    fun `p0_11_offlineEnsureLoadedDoesNotClobberScrapeProgress`() = runBlocking {
        val (ctx, _) = newFakeContext("p0_11_progress")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx)
            val repo = MetadataRepository(ctx, scope, client)
            repo.configure(ScraperCatalog.defaultMetadataConfigs(), ScraperCredentials())
            val plat = Platform("custom", "Custom", "Custom Platform", 1f, libretroSystem = null)
            val games = List(5) { i ->
                Game("/roms/custom/g$i.rom", "custom", "G$i", "g$i.rom", "G$i")
            }
            // 调用离线 ensureLoaded 不得污染 scrapeProgress（避免主页加载冲掉全量刮削进度条）
            repo.ensureLoaded(games) { plat }
            delay(100)
            assertFalse("离线预加载不应将 scrapeProgress.running 置为 true", repo.scrapeProgress.value.running)
            assertEquals(0, repo.scrapeProgress.value.total)
        } finally {
            scope.cancel()
        }
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

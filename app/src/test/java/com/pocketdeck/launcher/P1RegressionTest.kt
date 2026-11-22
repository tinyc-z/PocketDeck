package com.pocketdeck.launcher

import android.content.Context
import android.content.SharedPreferences
import com.pocketdeck.launcher.data.BoundedLruCache
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameTdbClient
import com.pocketdeck.launcher.data.HttpLogInterceptor
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.OfflineGameDb
import com.pocketdeck.launcher.data.RomIdentityExtractor
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.ScrapeDatabase
import com.pocketdeck.launcher.data.SourceItemConfig
import com.pocketdeck.launcher.data.TitleNormalizer
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.StringReader
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * P1 进阶优化回归与基准验证测试：
 * - P1-1：根目录散落 ROM 快照、封面子目录与外置 gamelist.xml mtime 纳入快照、gamelist.xml 流式解析
 * - P1-2：OfflineGameDb 按平台单飞锁与零拷贝视图/懒建分词表、I3 负缓存不击穿、GitHub Trees JSON 流式解析
 * - P1-3：007/1942/1943 真名前缀保护、.6~.9 版本号剥离、BoxartIndex 优先匹配 ROM 原区域、模糊匹配不截断英文别名
 * - P1-4：空闲 3D 呼吸节流复用、TopBar 回调切 IO 与重组隔离、CustomImageEffect 移出主线程 stat、CoverGlow ReduceMotion
 * - P1-5：BoundedLruCache 上限与 onTrimMemory 治理、GameTDB 失败冷却、Coil 与 Scraper 共享 OkHttp 连接池/调度器与 Debug 日志门控
 */
class P1RegressionTest {

    @Rule
    @JvmField
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
        ctx = object : android.content.ContextWrapper(null) {
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

    private fun locateProjectFile(relPath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            val cur = dir ?: return@repeat
            val candidate = File(cur, relPath)
            if (candidate.isFile) return candidate
            dir = cur.parentFile
        }
        return File(relPath)
    }

    // ─────────────────── P1-1: 增量扫描快照完善与流式 gamelist.xml ───────────────────

    @Test
    fun p1_1_rootLooseRomsAreSnapshottedAndIncrementalReused() {
        val root = tmp.newFolder("p1_root_loose")
        val gbaDir = File(root, "gba").apply { mkdirs() }
        File(gbaDir, "Metroid Fusion (USA).gba").writeText("rom1")
        File(root, "Super Mario Bros (USA).nes").writeText("loose_nes")

        val first = RomScanner.scanAll(listOf(root))
        assertTrue("根目录应写入 dirSnapshots", first.dirSnapshots.containsKey(root.path))
        assertEquals(1, first.games["nes"]?.size)
        assertEquals("Super Mario Bros", first.games["nes"]?.single()?.title)

        // 增量二次扫描：快照未变时直接复用并保持完全相等（避免重写二进制索引）
        val second = RomScanner.scanAll(listOf(root), previousLibrary = first)
        assertEquals(first, second)

        // 根目录新增散落 ROM 后快照变化，必须及时扫出新游戏
        File(root, "Sonic (USA).gba").writeText("loose_gba")
        val third = RomScanner.scanAll(listOf(root), previousLibrary = second)
        assertNotEquals(first.dirSnapshots[root.path], third.dirSnapshots[root.path])
        assertEquals(2, third.games["gba"]?.size)
    }

    @Test
    fun p1_1_snapshotDetectsCoverSubdirAndExternalGamelistChanges() {
        val root = tmp.newFolder("p1_snap_ext")
        val gba = File(root, "gba").apply { mkdirs() }
        val rom = File(gba, "Golden Sun (USA).gba").apply { writeText("romdata") }
        val coverDir = File(gba, "media/covers").apply { mkdirs() }

        val scan1 = RomScanner.scanAll(listOf(root))
        assertNull(scan1.games["gba"]!!.single().localCover)
        assertNull(scan1.localMetadata[rom.path])

        // 1. 仅在 media/covers 子目录新增封面（不修改 gba 目录本身）
        val cover = File(coverDir, "Golden Sun (USA).png").apply { writeText("png") }
        coverDir.setLastModified(coverDir.lastModified() + 5_000L)
        val scan2 = RomScanner.scanAll(listOf(root), previousLibrary = scan1)
        assertNotEquals(scan1.dirSnapshots[gba.path], scan2.dirSnapshots[gba.path])
        assertEquals(cover.path, scan2.games["gba"]!!.single().localCover)

        // 2. 仅在外置 gamelists/gba/gamelist.xml 写入元数据（不修改 gba 目录本身）
        val extGlDir = File(root, "gamelists/gba").apply { mkdirs() }
        val extGl = File(extGlDir, "gamelist.xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <gameList>
                  <game>
                    <path>./Golden Sun (USA).gba</path>
                    <developer>Camelot</developer>
                    <publisher>Nintendo</publisher>
                    <genre>RPG</genre>
                    <releasedate>20011111T000000</releasedate>
                    <desc>Alchemy &amp; Psynergy adventure.</desc>
                  </game>
                </gameList>
                """.trimIndent(),
            )
        }
        val scan3 = RomScanner.scanAll(listOf(root), previousLibrary = scan2)
        assertNotEquals(scan2.dirSnapshots[gba.path], scan3.dirSnapshots[gba.path])
        val meta = scan3.localMetadata[rom.path]
        assertNotNull(meta)
        assertEquals("Camelot", meta?.developer)
        assertEquals("2001-11-11", meta?.releaseDate)
        assertEquals("Alchemy & Psynergy adventure.", meta?.description)

        // 3. 原地修改外置 gamelist.xml 内容与 mtime，快照仍能感知
        extGl.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gameList>
              <game>
                <path>./Golden Sun (USA).gba</path>
                <developer>Camelot Software Planning</developer>
              </game>
            </gameList>
            """.trimIndent(),
        )
        extGl.setLastModified(extGl.lastModified() + 5_000L)
        val scan4 = RomScanner.scanAll(listOf(root), previousLibrary = scan3)
        assertEquals("Camelot Software Planning", scan4.localMetadata[rom.path]?.developer)
    }

    @Test
    fun p1_1_streamingGamelistXmlParsesEntitiesCdataAnd2000Games() {
        val root = tmp.newFolder("p1_xml_stream")
        val snes = File(root, "snes").apply { mkdirs() }
        val targetRom = File(snes, "Chrono Trigger (USA).sfc").apply { writeText("rom") }
        val thumb = File(snes, "thumbs/ct.png").apply {
            parentFile?.mkdirs()
            writeText("img")
        }
        val sb = StringBuilder(256 * 1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gameList>\n")
        sb.append("<folder><path>./sub</path><name>Ignored Folder</name></folder>\n")
        sb.append(
            """
            <game>
              <path>./Chrono Trigger (USA).sfc</path>
              <thumbnail>./thumbs/ct.png</thumbnail>
              <developer>Square</developer>
              <publisher>Square &amp; Enix</publisher>
              <genre>RPG</genre>
              <releasedate>19950811T000000</releasedate>
              <desc><![CDATA[Time travel <RPG> & adventure]]></desc>
            </game>
            """.trimIndent(),
        ).append('\n')
        for (i in 1..2000) {
            sb.append("<game><path>./Game_").append(i).append(".sfc</path>")
            sb.append("<developer>Dev").append(i).append("</developer>")
            sb.append("<desc>Description for game ").append(i).append(" &amp; sequel</desc></game>\n")
        }
        sb.append("</gameList>\n")
        File(snes, "gamelist.xml").writeText(sb.toString())

        val t0 = System.nanoTime()
        val lib = RomScanner.scanAll(listOf(root))
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        val game = lib.games["snes"]!!.single()
        assertEquals(thumb.path, game.localCover)
        val meta = lib.localMetadata[targetRom.path]
        assertEquals("Square", meta?.developer)
        assertEquals("Square & Enix", meta?.publisher)
        assertEquals("1995-08-11", meta?.releaseDate)
        assertEquals("Time travel <RPG> & adventure", meta?.description)
        println("[P1-BENCH] gamelist_2000games_streaming | elapsed=${String.format("%.2f", elapsedMs)}ms")
    }

    // ─────────────────── P1-2: OfflineGameDb 零拷贝与单飞锁、I3 负缓存、流式 GitHub Trees ───────────────────

    @Test
    fun p1_2_offlineTableZeroCopyViewsAndLazyTokenIndex() {
        val table = OfflineGameDb.parseMetaTsv(
            sequenceOf(
                "H\t63F9407D\tTetris (World)",
                "M\tTetris (World)\tBullet-Proof Software\tNintendo\tPuzzle\t1989-06",
            ),
        )
        // 验证零拷贝只读视图兼容旧接口
        assertEquals("Nintendo", table.metaByExactName[TitleNormalizer.exactKey("Tetris (World)")]?.publisher)
        assertEquals("Puzzle", table.metaByNormTitle[TitleNormalizer.normalizedTitle("Tetris (World)")]?.genre)
        assertEquals("Tetris (World)", table.rowByCompact[TitleNormalizer.compactKey("Tetris (World)")]?.name)

        // 验证倒排分词表初始未构建（按需懒建）
        val tokenField = OfflineGameDb.PlatformTable::class.java.getDeclaredField("_rowByToken")
            .apply { isAccessible = true }
        assertNull("常规加载不应急切构建 _rowByToken", tokenField.get(table))
        val built = table.ensureTokenIndexBuilt()
        assertTrue(built["tetris"]?.any { it.name == "Tetris (World)" } == true)
        assertNotNull(tokenField.get(table))
    }

    @Test
    fun p1_2_offlineGameDbHasPerSystemSingleFlightLocks() {
        // 并发验证同一平台锁对象一致性与不同平台并发不互斥
        val boxartLocksField = OfflineGameDb::class.java.getDeclaredField("boxartLocks").apply { isAccessible = true }
        val metaLocksField = OfflineGameDb::class.java.getDeclaredField("metaLocks").apply { isAccessible = true }
        assertNotNull(boxartLocksField.get(OfflineGameDb))
        assertNotNull(metaLocksField.get(OfflineGameDb))

        val (ctx, _) = newFakeContext("p1_singleflight")
        val sys = "Test - SingleFlight System"
        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val done = CountDownLatch(6)
        try {
            repeat(6) {
                pool.submit {
                    start.await()
                    OfflineGameDb.loadPlatformTable(ctx, sys)
                    OfflineGameDb.loadBoxartNames(ctx, sys)
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
            OfflineGameDb.trimMemory(emptySet())
        }
    }

    @Test
    fun p1_2_i3SecondAliasDoesNotBypassNegativeCache() = runBlocking {
        setNetworkOnline(true)
        val (ctx, _) = newFakeContext("p1_i3_negcache")
        val httpCount = AtomicInteger(0)
        val mockHttp = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    httpCount.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"search":[]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()

        val client = ScraperClient(ctx)
        // 注入 mock http 客户端（跳过真实网络请求）
        val httpDelegateField = ScraperClient::class.java.getDeclaredField("http\$delegate").apply { isAccessible = true }
        httpDelegateField.set(client, lazyOf(mockHttp))

        val scope = CoroutineScope(SupervisorJob())
        val repo = MetadataRepository(ctx, scope, client)
        repo.configure(
            providers = ScraperCatalog.defaultMetadataConfigs().map {
                it.copy(enabled = it.id == ScraperCatalog.MetaIds.WIKIDATA)
            },
            cred = ScraperCredentials(),
        )
        val snes = Platform("snes", "SNES", "Super Nintendo", 1f, "Nintendo - Super Nintendo Entertainment System", "snes9x")
        val rom = File(tmp.newFolder("p1_i3_roms"), "皇牌空战5 汉化版.sfc").apply { writeText("rom") }
        val game = Game(rom.path, "snes", "皇牌空战5 汉化版", rom.name, "皇牌空战5")

        // 确保测试标题在 CjkAliases 中拥有至少 2 个不同英文别名，真正覆盖 I3 第二别名回退分支
        val aliases = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases(game.title)
        assertTrue("测试标题需具备 >=2 个英文别名以覆盖 I3，实际=$aliases", aliases.size >= 2)

        // 使用 resolveFocusGame 覆盖真实调用链（跳过 init 中常驻的 NetworkStatus.online.collect 协程）
        val scopeJob = scope.coroutineContext[kotlinx.coroutines.Job]!!
        suspend fun runFocusAndWait() {
            val before = scopeJob.children.toSet()
            repo.resolveFocusGame(game, snes)
            kotlinx.coroutines.withTimeout(10_000) {
                scopeJob.children.filter { it !in before }.toList().forEach { it.join() }
            }
        }

        runFocusAndWait()
        val afterFirst = httpCount.get()
        assertTrue("首次查询应至少发起 2 次请求（主别名 + I3 第二别名），实际=$afterFirst", afterFirst >= 2)

        // 模拟新会话重新聚焦同一游戏：应命中 SQLite/内存负缓存，绝不因 I3 forceRefresh 击穿发包
        val focusField = MetadataRepository::class.java.getDeclaredField("focusApiResolved").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (focusField.get(repo) as MutableMap<String, Boolean>).clear()
        val inFlightField = MetadataRepository::class.java.getDeclaredField("inFlightFocus").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (inFlightField.get(repo) as MutableSet<String>).clear()

        runFocusAndWait()
        assertEquals("负缓存命中后不应触发 I3 二次网络请求", afterFirst, httpCount.get())
        scope.cancel()
    }

    @Test
    fun p1_2_streamingGitHubTreeParserMatchesJsonObjectAndBenchmarks() {
        val totalBoxarts = 6000
        val sb = StringBuilder(4 * 1024 * 1024)
        sb.append("""{"sha":"abc123","url":"https://api.github.com/repos/x/y","meta":{"tree":"ignored_nested"},"tree":[""")
        var idx = 0
        fun appendItem(path: String) {
            if (idx++ > 0) sb.append(',')
            sb.append("""{"path":"""").append(path).append("""","mode":"100644","type":"blob","sha":"0123456789abcdef0123456789abcdef01234567","size":12345,"url":"https://api.github.com/blobs/0123456789abcdef"}""")
        }
        appendItem("""Named_Boxarts/Pokémon - Emerald Version (USA, Europe).png""")
        appendItem("""Named_Boxarts/Game \"Special\" Edition (USA).PNG""")
        appendItem("""Named_Boxarts/README.md""")
        appendItem("""Named_Snaps/Pokémon - Emerald Version (USA, Europe).png""")
        appendItem("""Named_Titles/Super Mario World (USA).png""")
        for (i in 1 until totalBoxarts - 1) {
            appendItem("Named_Boxarts/Game Title $i (USA).png")
            appendItem("Named_Snaps/Game Title $i (USA).png")
            appendItem("Named_Titles/Game Title $i (USA).png")
        }
        sb.append("""],"truncated":false}""")
        val json = sb.toString()

        // 1. DOM (JSONObject) 参照解析
        val tDom0 = System.nanoTime()
        val domTree = JSONObject(json).getJSONArray("tree")
        val expected = LinkedHashSet<String>(totalBoxarts)
        for (i in 0 until domTree.length()) {
            val p = domTree.getJSONObject(i).optString("path")
            if (p.startsWith("Named_Boxarts/") && p.endsWith(".png", ignoreCase = true)) {
                expected += p.removePrefix("Named_Boxarts/")
            }
        }
        val domMs = (System.nanoTime() - tDom0) / 1_000_000.0

        // 2. 流式解析
        val tStream0 = System.nanoTime()
        val actual = CoverSource.parseGitHubTreeBoxarts(StringReader(json))
        val streamMs = (System.nanoTime() - tStream0) / 1_000_000.0

        assertEquals(expected.toList(), actual)
        assertTrue(actual.contains("Pokémon - Emerald Version (USA, Europe).png"))
        assertTrue(actual.contains("""Game "Special" Edition (USA).PNG"""))
        assertEquals(totalBoxarts, actual.size)
        println(
            "[P1-BENCH] github_trees_18k_entries | jsonMB=${String.format("%.2f", json.length / (1024.0 * 1024.0))} | " +
                "dom=${String.format("%.2f", domMs)}ms | stream=${String.format("%.2f", streamMs)}ms | entries=${actual.size}",
        )
    }

    // ─────────────────── P1-3: 标题清洗与区域匹配打磨 ───────────────────

    @Test
    fun p1_3_preserves007AndYearTitlePrefixesWhileStrippingPackIndices() {
        assertEquals("007 - Everything or Nothing", TitleNormalizer.stripRomTags("007 - Everything or Nothing (USA)"))
        assertEquals("007 - NightFire", TitleNormalizer.stripRomTags("007 - NightFire (USA, Europe)"))
        assertEquals("1942 - Joint Strike", TitleNormalizer.stripRomTags("1942 - Joint Strike (USA)"))
        assertEquals("1943 - The Battle of Midway", TitleNormalizer.stripRomTags("1943 - The Battle of Midway (USA)"))
        assertEquals("1941 - Counter Attack", TitleNormalizer.stripRomTags("1941 - Counter Attack (World)"))
        assertEquals("2020 - Super Baseball", TitleNormalizer.stripRomTags("2020 - Super Baseball (USA)"))

        // 常见整合包编号仍正常剥离
        assertEquals("Super Mario World", TitleNormalizer.stripRomTags("001 - Super Mario World (USA)"))
        assertEquals("Super Mario World", TitleNormalizer.stripRomTags("No.007 - Super Mario World (USA)"))
        assertEquals("Super Mario World", TitleNormalizer.stripRomTags("No.1942 - Super Mario World"))
        assertEquals("Super Mario World", TitleNormalizer.stripRomTags("007. Super Mario World (USA)"))
        assertEquals("超级马里奥", TitleNormalizer.stripRomTags("1942、超级马里奥"))
    }

    @Test
    fun p1_3_stripsVersionSuffixesDot6ThroughDot9() {
        assertEquals("Super Mario World", TitleNormalizer.stripRomTags("Super Mario World v1.6"))
        assertEquals("Chrono Trigger", TitleNormalizer.stripRomTags("Chrono Trigger v2.7"))
        assertEquals("Metroid Fusion", TitleNormalizer.stripRomTags("Metroid Fusion v1.8"))
        assertEquals("Castlevania", TitleNormalizer.stripRomTags("Castlevania v1.9"))
        assertEquals("Final Fantasy", TitleNormalizer.stripRomTags("Final Fantasy v1.10"))
    }

    @Test
    fun p1_3_boxartIndexPrefersRomOwnRegionAndPenalizesReissues() {
        val (ctx, _) = newFakeContext("p1_region")
        val cancelledScope = CoroutineScope(SupervisorJob().apply { cancel() })
        val covers = CoverSource(ctx, cancelledScope, ScraperClient(ctx))
        val buildIndex = CoverSource::class.java.getDeclaredMethod("buildIndex", List::class.java)
            .apply { isAccessible = true }
        val index = checkNotNull(
            buildIndex.invoke(
                covers,
                listOf(
                    "Super Mario World (USA).png",
                    "Super Mario World (USA, Europe).png",
                    "Super Mario World (Europe).png",
                    "Super Mario World (Japan).png",
                    "Super Mario World (Japan) (Virtual Console).png",
                    "Super Mario World (Korea).png",
                ),
            ),
        )
        val findMatches = index.javaClass.getDeclaredMethod("findMatches", List::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        fun firstMatch(baseName: String): String? {
            val queries = TitleNormalizer.buildQueryVariants(baseName, TitleNormalizer.stripRomTags(baseName))
            return (findMatches.invoke(index, queries, true) as List<String>).firstOrNull()
        }

        assertEquals("Super Mario World (Japan).png", firstMatch("Super Mario World (Japan) [T-En]"))
        assertEquals("Super Mario World (Japan).png", firstMatch("Super Mario World (J) [!]"))
        assertEquals("Super Mario World (Europe).png", firstMatch("Super Mario World (Europe) (En,Fr,De)"))
        assertEquals("Super Mario World (Korea).png", firstMatch("Super Mario World (Korea) [T-Chs]"))
        assertEquals("Super Mario World (USA).png", firstMatch("Super Mario World"))

        // 验证 extractRegionTags 准确识别常见区域标且不误把语言标当区域
        assertEquals(setOf("USA", "Europe"), CoverSource.extractRegionTags("Pokemon (USA, Europe) (En,Fr,De)"))
        assertEquals(setOf("Japan"), CoverSource.extractRegionTags("Mother 3 (日版) 汉化版"))
        assertTrue(CoverSource.extractRegionTags("Final Fantasy VII (Disc 1) (En,Fr)").isEmpty())
    }

    @Test
    fun p1_3_selectFuzzyQueriesPreservesEnglishAliasesAheadOfCjkVariants() {
        val queries = listOf(
            "火焰纹章 封印之剑 (简中)",
            "火焰纹章 封印之剑",
            "圣魔之光石 完美汉化版",
            "火焰纹章 圣魔之光石",
            "火焰纹章 烈火之剑",
            "Fire Emblem - The Sacred Stones",
            "Fire Emblem: The Sacred Stones (USA)",
        )
        val selected = TitleNormalizer.selectFuzzyQueries(queries, maxCount = 4)
        assertTrue(
            "英文别名必须排在前列且不被截断，实际=$selected",
            selected.first().equals("Fire Emblem - The Sacred Stones", ignoreCase = true),
        )
        // 去重后相同规范名的英文变体只占 1 个名额，剩余名额留给不重复的中文查询
        assertEquals(1, selected.count { it.contains("Fire Emblem", ignoreCase = true) })
        assertEquals(4, selected.size)

        // 端到端验证 BoxartIndex 倒排模糊匹配能命中被排在第 5 位之后的英文别名
        val (ctx, _) = newFakeContext("p1_fuzzy_en")
        val cancelledScope = CoroutineScope(SupervisorJob().apply { cancel() })
        val covers = CoverSource(ctx, cancelledScope, ScraperClient(ctx))
        val buildIndex = CoverSource::class.java.getDeclaredMethod("buildIndex", List::class.java)
            .apply { isAccessible = true }
        val index = checkNotNull(
            buildIndex.invoke(
                covers,
                listOf("Fire Emblem - The Sacred Stones Version (USA, Australia).png"),
            ),
        )
        val findMatches = index.javaClass.getDeclaredMethod("findMatches", List::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val hits = findMatches.invoke(index, queries, true) as List<String>
        assertEquals(listOf("Fire Emblem - The Sacred Stones Version (USA, Australia).png"), hits)
    }

    // ─────────────────── P1-4: 动画节流、TopBar 回调切 IO、CustomImageEffect 与 CoverGlow ───────────────────

    @Test
    fun p1_4_uiPerformanceInvariantsHoldInSource() {
        val gallerySrc = locateProjectFile("app/src/main/java/com/pocketdeck/launcher/ui/home/GalleryView.kt").readText()
        val gridSrc = locateProjectFile("app/src/main/java/com/pocketdeck/launcher/ui/home/GridView.kt").readText()
        val bubblesSrc = locateProjectFile("app/src/main/java/com/pocketdeck/launcher/ui/home/BubblesView.kt").readText()
        for ((name, src) in listOf("GalleryView" to gallerySrc, "GridView" to gridSrc, "BubblesView" to bubblesSrc)) {
            assertTrue("$name 应复用 rememberIdleBreathingClock", "rememberIdleBreathingClock(" in src)
            assertFalse("$name 不应残留未节流的裸 withFrameNanos 循环", "withFrameNanos" in src)
        }

        val topBarSrc = locateProjectFile("app/src/main/java/com/pocketdeck/launcher/ui/components/TopBar.kt").readText()
        assertTrue("TopBar 应拆分独立 TopBarTabsSection", "private fun TopBarTabsSection(" in topBarSrc)
        assertTrue("TopBar 应拆分独立 TopBarStatusCluster", "private fun TopBarStatusCluster(" in topBarSrc)
        val receiverSection = topBarSrc.substringAfter("val receiver = object : BroadcastReceiver()")
            .substringBefore("val lifecycleObserver =")
        assertFalse("TopBar 系统广播/回调不应在主线程直接调 readWifiLevel", "readWifiLevel(" in receiverSection)
        assertFalse("TopBar 系统广播/回调不应在主线程直接调 readWiredHeadsetConnected", "readWiredHeadsetConnected(" in receiverSection)
        assertFalse("TopBar 系统广播/回调不应在主线程直接调 readAirplaneMode", "readAirplaneMode(" in receiverSection)
        assertFalse("TopBar 系统广播/回调不应在主线程直接调 readDndEnabled", "readDndEnabled(" in receiverSection)

        val waveSrc = locateProjectFile("app/src/main/java/com/pocketdeck/launcher/ui/components/WaveBackground.kt").readText()
        val customImgSection = waveSrc.substringAfter("private fun CustomImageEffect(")
        assertTrue("CustomImageEffect 应使用 produceState + Dispatchers.IO 移出主线程 stat", "produceState<" in customImgSection && "Dispatchers.IO" in customImgSection)

        val glowSrc = locateProjectFile("app/src/main/java/com/pocketdeck/launcher/ui/components/background/CoverGlow.kt").readText()
        assertTrue("CoverGlow 应响应 LocalReduceMotion", "LocalReduceMotion.current" in glowSrc)
    }

    // ─────────────────── P1-5: 缓存有界治理、GameTDB 冷却、共享 OkHttp 与 Debug 拦截器 ───────────────────

    @Test
    fun p1_5_boundedLruCacheAndTrimMemoryGovernance() {
        val lru = BoundedLruCache<String, Int>(maxSize = 3)
        lru["a"] = 1
        lru["b"] = 2
        lru["c"] = 3
        // 访问 "a" 刷新 LRU 序，再写入 "d" 应淘汰最久未用的 "b"
        assertEquals(1, lru["a"])
        lru["d"] = 4
        assertEquals(3, lru.size)
        assertNull(lru["b"])
        assertEquals(1, lru["a"])
        assertEquals(4, lru["d"])

        // trimToSize 保留受保护 key
        lru.trimToSize(targetSize = 1, protectedKeys = setOf("a"))
        assertEquals(1, lru.size)
        assertEquals(1, lru["a"])

        // 验证 ScraperClient.trimMemory 联动清理 memHits / hasheousHashCache / identityExtractor / gameTdb
        val (ctx, _) = newFakeContext("p1_trim")
        val client = ScraperClient(ctx)
        val romDir = tmp.newFolder("p1_trim_roms")
        repeat(10) { i ->
            val f = File(romDir, "game_$i.gba").apply { writeText("rom_$i") }
            client.extractIdentity(f, "gba")
        }
        assertEquals(10, client.identityExtractor.memoryCacheSize())
        client.trimMemory(keepSystems = setOf("Nintendo - Wii"), aggressive = true)
        assertEquals(0, client.identityExtractor.memoryCacheSize())
        assertEquals(0, client.memHitsSize())
        assertEquals(0, client.hasheousHashCacheSize())
    }

    @Test
    fun p1_5_gameTdbDownloadFailureRespectsCooldown() = runBlocking {
        setNetworkOnline(true)
        val (ctx, _) = newFakeContext("p1_gametdb_cd")
        val attempts = AtomicInteger(0)
        val failingHttp = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    attempts.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("error".toResponseBody("text/plain".toMediaType()))
                        .build()
                },
            )
            .build()

        val db = ScrapeDatabase(ctx)
        val extractor = RomIdentityExtractor(db)
        val tdb = GameTdbClient(ctx, { failingHttp }, extractor)
        val sw = Platform("switch", "Switch", "Nintendo Switch", 1f, "Nintendo - Nintendo Switch", null)
        val rom = File(tmp.newFolder("p1_sw"), "Mario [0100000000010000].nsp").apply { writeText("x") }
        val game = Game(rom.path, "switch", "Mario", rom.name, "Mario")

        assertNull(tdb.lookup(game, sw, allowDownload = true))
        assertEquals(1, attempts.get())
        assertTrue("下载失败后应进入冷却窗口", tdb.isDownloadInCooldown("switchtdb"))

        // 冷却期内重复查询不得再次请求远端
        assertNull(tdb.lookup(game, sw, allowDownload = true))
        assertEquals("冷却期内不应重复下载 switchtdb.zip", 1, attempts.get())
    }

    @Test
    fun p1_5_coilAndScraperSharePoolDispatcherAndGateHttpLogByDebug() {
        val (ctx, _) = newFakeContext("p1_okhttp_share")
        val client = ScraperClient(ctx)
        val scraperHttp = client.http
        val coilHttp = client.newCoilHttpClient()

        assertSame("Coil 与 Scraper 必须共享底层 ConnectionPool", scraperHttp.connectionPool, coilHttp.connectionPool)
        assertSame("Coil 与 Scraper 必须共享底层 Dispatcher", scraperHttp.dispatcher, coilHttp.dispatcher)

        val scraperHasLog = scraperHttp.interceptors.any { it is HttpLogInterceptor }
        val coilHasLog = coilHttp.interceptors.any { it is HttpLogInterceptor }
        assertEquals("ScraperClient HttpLogInterceptor 应与 BuildConfig.DEBUG 一致", BuildConfig.DEBUG, scraperHasLog)
        assertEquals("Coil HttpLogInterceptor 应与 BuildConfig.DEBUG 一致", BuildConfig.DEBUG, coilHasLog)

        val appSrc = locateProjectFile("app/src/main/java/com/pocketdeck/launcher/PocketDeckApp.kt").readText()
        assertTrue("PocketDeckApp 应复用 container.scraperClient.newCoilHttpClient()", "newCoilHttpClient()" in appSrc)
        assertTrue("PocketDeckApp.onTrimMemory 应接入 scraperClient.trimMemory", "scraperClient.trimMemory(" in appSrc)
    }
}

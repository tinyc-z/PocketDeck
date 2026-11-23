package com.pocketdeck.launcher

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color
import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameTdbClient
import com.pocketdeck.launcher.data.ManualScrapeCandidate
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperClient
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.DefaultCoverArtCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 第 1 批可靠性与内存底线回归测试：
 * - 离线/弱网下 `CoverSource.rememberFailure` 不抹除已解析 URL 与手动封面，坏离线缓存文件仅摘除坏文件
 * - `MetadataRepository.configure` 区分封面专属凭证（如 `sgdbKey`），并保留手动刮削（`manualMetaIds`）与本地 `gamelist.xml`
 * - `GameTdbClient.trimMemory(aggressive = true)` 后台回收全量释放；`DefaultCoverArtCache` 按描边密度区分键并支持 `trimMemory`
 * - `ArchiveManager` 压缩跳过符号链接与防循环、解压拦截超限目录炸弹；`RomScanner` 拦截 `.m3u` 跨目录穿越
 */
class ScrapeReliabilityTest {

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

    @Test
    fun `coverSource rememberFailure preserves URLs offline protects manual covers online and evicts broken web cache file`() {
        val (ctx, dirs) = newFakeContext("cover_reliability")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val gba = Platform("gba", "GBA", "Game Boy Advance", 1f, "Nintendo - Game Boy Advance")
            val autoGame = Game(
                id = "/roms/gba/auto.gba",
                platformId = "gba",
                baseName = "Auto Game",
                fileName = "auto.gba",
                title = "Auto Game",
            )
            val manualGame = Game(
                id = "/roms/gba/manual.gba",
                platformId = "gba",
                baseName = "Manual Game",
                fileName = "manual.gba",
                title = "Manual Game",
            )
            val autoUrl = "https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_Game_Boy_Advance@master/Named_Boxarts/Auto.png"
            val manualUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co_manual.jpg"

            covers.configure(ScraperCatalog.defaultCoverConfigs(), ScraperCredentials())
            covers.rememberSuccess(autoGame.id, autoUrl)
            covers.applyManualCover(manualGame.id, manualUrl, ScraperCatalog.CoverIds.IGDB)

            // 1. 离线状态下 rememberFailure 不得抹除自动或手动封面 URL
            withNetworkOnline(false) {
                covers.rememberFailure(autoGame.id, gba)
                covers.rememberFailure(manualGame.id, gba)
                assertEquals(listOf<Any>(autoUrl), covers.candidates(autoGame, gba))
                assertEquals(listOf<Any>(manualUrl), covers.candidates(manualGame, gba))
            }

            // 2. 在线状态下 rememberFailure 对手动封面豁免（不删库），对损坏的 web_covers 文件仅摘除坏文件并保留 URL
            withNetworkOnline(true) {
                covers.rememberFailure(manualGame.id, gba)
                assertEquals("Manual cover must survive online transient failure", listOf<Any>(manualUrl), covers.candidates(manualGame, gba))

                // 模拟 autoGame 存在一个已损坏的 web_covers 文件
                val webDir = File(dirs.second, "web_covers").apply { mkdirs() }
                val brokenFile = File(webDir, "${CoverSource.sha1HexFast(autoGame.id)}.img").apply { writeBytes(byteArrayOf(0, 1, 2)) }
                @Suppress("UNCHECKED_CAST")
                val webMap = CoverSource::class.java.getDeclaredField("webCacheFilesById").apply { isAccessible = true }
                    .get(covers) as ConcurrentHashMap<String, File>
                webMap[autoGame.id] = brokenFile

                assertTrue(covers.isWebCacheFile(autoGame.id, brokenFile))
                covers.invalidateWebCacheFile(autoGame.id, brokenFile)
                assertFalse(covers.isWebCacheFile(autoGame.id, brokenFile))
                assertEquals("URL fallback should remain intact after broken web cache eviction", listOf<Any>(autoUrl), covers.candidates(autoGame, gba))

                // 再次模拟仅通过 rememberFailure 触发损坏文件清理：第一拍应只清坏文件而不删 URL
                brokenFile.writeBytes(byteArrayOf(3, 4, 5))
                webMap[autoGame.id] = brokenFile
                covers.rememberFailure(autoGame.id, gba)
                assertFalse(covers.isWebCacheFile(autoGame.id, brokenFile))
                assertEquals("First failure on broken web file must keep URL", listOf<Any>(autoUrl), covers.candidates(autoGame, gba))

                // 此时禁用 IGDB 节点：manualGame 的手动封面应继续保留
                val disabledIgdb = ScraperCatalog.defaultCoverConfigs().map {
                    if (it.id == ScraperCatalog.CoverIds.IGDB) it.copy(enabled = false) else it
                }
                covers.configure(disabledIgdb, ScraperCredentials())
                assertEquals("Manual cover must survive provider disable", listOf<Any>(manualUrl), covers.candidates(manualGame, gba))
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `metadataRepository configure ignores cover-only sgdbKey and retains manual plus local metadata`() {
        val (ctx, _) = newFakeContext("meta_configure")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ScraperClient(ctx)
            val covers = CoverSource(ctx, scope, client)
            val repo = MetadataRepository(ctx, scope, client)

            val baseMetaNodes = ScraperCatalog.defaultMetadataConfigs()
            repo.configure(baseMetaNodes, ScraperCredentials())

            val manualId = "/roms/gba/manual.gba"
            val localId = "/roms/gba/local.gba"
            val localMeta = GameMetadata(releaseDate = "2002", genre = "Action", developer = "Nintendo")
            repo.seedLocalMetadata(mapOf(localId to localMeta))
            repo.applyManualScrape(
                gameId = manualId,
                candidate = ManualScrapeCandidate(
                    title = "Metroid Fusion",
                    providerId = ScraperCatalog.MetaIds.IGDB,
                    coverUrl = null,
                    metadata = GameMetadata(releaseDate = "2002", genre = "Metroidvania", developer = "Nintendo R&D1"),
                ),
                covers = covers,
            )

            // 1. 仅变更封面专用 sgdbKey 不应清空元数据
            repo.configure(baseMetaNodes, ScraperCredentials(sgdbKey = "cover-only-key"))
            assertNotNull(repo.metadata.value[manualId])
            assertEquals("Metroid Fusion", repo.officialNames.value[manualId])
            assertEquals(localMeta, repo.metadata.value[localId])

            // 2. 禁用全部在线元数据节点：手动刮削条目与本地 gamelist.xml 条目均应完整保留
            val allDisabled = baseMetaNodes.map { it.copy(enabled = false) }
            repo.configure(allDisabled, ScraperCredentials(sgdbKey = "cover-only-key"))
            assertEquals("Metroidvania", repo.metadata.value[manualId]?.genre)
            assertEquals("Metroid Fusion", repo.officialNames.value[manualId])
            assertEquals(localMeta, repo.metadata.value[localId])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `gameTdbClient aggressive trimMemory clears all tables and defaultCoverArtCache distinguishes strokeDensity`() {
        val (ctx, _) = newFakeContext("trim_mem")
        val tdb = ScraperClient(ctx).gameTdb
        val tablesField = GameTdbClient::class.java.getDeclaredField("tables").apply { isAccessible = true }
        val tablesCache = tablesField.get(tdb)!!
        val setMethod = tablesCache.javaClass.getMethod("set", Any::class.java, Any::class.java)
        val tableClass = Class.forName("com.pocketdeck.launcher.data.GameTdbClient\$TdbTable")
        val ctor = tableClass.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            Map::class.java,
            Map::class.java,
            Map::class.java,
            Map::class.java,
        ).apply { isAccessible = true }
        val dummyTable = ctor.newInstance("wiitdb", "wii", emptyMap<String, Any>(), emptyMap<String, Any>(), emptyMap<String, Any>(), emptyMap<String, Any>())
        setMethod.invoke(tablesCache, "wiitdb", dummyTable)
        setMethod.invoke(tablesCache, "dstdb", dummyTable)
        assertEquals(2, tdb.cachedTableCount())

        // 普通回收仅保留 keepDbKeys
        tdb.trimMemory(keepDbKeys = setOf("wiitdb"), aggressive = false)
        assertEquals(1, tdb.cachedTableCount())

        // aggressive=true（退后台/启动模拟器）即使传入 keepDbKeys 也全量清空
        tdb.trimMemory(keepDbKeys = setOf("wiitdb"), aggressive = true)
        assertEquals(0, tdb.cachedTableCount())

        // DefaultCoverArtCache 键区分 strokeDensity 桶且 trimMemory 归零
        val k1 = DefaultCoverArtCache.buildKey(Color.Red, Color.Blue, Color.Green, 128, 128, false, false, false, true, 1.5f)
        val k2 = DefaultCoverArtCache.buildKey(Color.Red, Color.Blue, Color.Green, 128, 128, false, false, false, true, 3.0f)
        assertNotEquals("Different strokeDensity must yield distinct cache keys", k1, k2)
        DefaultCoverArtCache.trimMemory()
        assertEquals(0, DefaultCoverArtCache.cachedSizeBytes())
    }

    @Test
    fun `archiveManager skips symlinks rejects directory bomb and romScanner blocks m3u traversal`() = runBlocking {
        // 1. ArchiveManager.compressToZip 跳过指向外部的符号链接及自环目录符号链接
        val srcDir = tmp.newFolder("zip_src")
        val secretOutside = tmp.newFile("secret_outside.txt").apply { writeText("TOP_SECRET") }
        File(srcDir, "normal.txt").writeText("hello")
        val fileLink = File(srcDir, "escape_link.txt").toPath()
        val dirLoop = File(srcDir, "self_loop").toPath()
        val symlinksSupported = runCatching {
            Files.createSymbolicLink(fileLink, secretOutside.toPath())
            Files.createSymbolicLink(dirLoop, srcDir.toPath())
            true
        }.getOrDefault(false)
        if (symlinksSupported) {
            val outZip = File(tmp.root, "safe.zip")
            assertTrue(ArchiveManager.compressToZip(listOf(srcDir), outZip).isSuccess)
            ZipFile(outZip).use { zf ->
                val names = zf.entries().asSequence().map { it.name }.toList()
                assertTrue(names.any { it.endsWith("normal.txt") })
                assertFalse("Symlink to outside file must not be archived", names.any { it.contains("escape_link") })
                assertFalse("Recursive dir symlink must not be archived", names.any { it.contains("self_loop") })
            }
        }

        // 2. ArchiveManager.extract 对超过 10,000 个目录项的目录炸弹返回错误（不耗尽 inode）
        val dirBombZip = File(tmp.root, "dir_bomb.zip")
        ZipOutputStream(FileOutputStream(dirBombZip)).use { zos ->
            for (i in 0..10_005) {
                zos.putNextEntry(ZipEntry("d$i/"))
                zos.closeEntry()
            }
        }
        val extractDst = tmp.newFolder("extract_dst")
        val extractRes = ArchiveManager.extract(dirBombZip, extractDst)
        assertTrue("Directory-entry ZIP bomb over 10,000 entries must fail", extractRes.isFailure)

        // 3. RomScanner 拦截 .m3u 中包含 ../ 跨目录路径穿越的条目
        val romsRoot = tmp.newFolder("m3u_roms")
        val psxDir = File(romsRoot, "psx").apply { mkdirs() }
        val outsideDisc = File(romsRoot, "secret.chd").apply { writeBytes(ByteArray(32)) }
        val insideDisc = File(psxDir, "disc1.chd").apply { writeBytes(ByteArray(32)) }
        val evilM3u = File(psxDir, "EvilGame.m3u").apply {
            writeText("../${outsideDisc.name}\n")
        }
        val goodM3u = File(psxDir, "GoodGame.m3u").apply {
            writeText("${insideDisc.name}\n")
        }
        val lib = RomScanner.scan(romsRoot)
        val psxGames = lib.games["psx"].orEmpty()
        assertNull("Traversal m3u must be ignored", psxGames.find { it.id == evilM3u.absolutePath })
        assertNotNull("Valid local-dir m3u must be scanned", psxGames.find { it.id == goodM3u.absolutePath })
    }

    @Test
    fun `tarGz inspect enforces 64MB decompression cap even across TarArchiveInputStream skip`() = runBlocking {
        // 构造一个解压后 70MB（压缩后仅几十 KB）的全零 .tar.gz 炸弹，验证 bounded FilterInputStream.skip() 拦截生效
        val tarGz = File(tmp.root, "bomb.tar.gz")
        val payloadSize = 70L * 1024L * 1024L
        org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(
            java.util.zip.GZIPOutputStream(FileOutputStream(tarGz)),
        ).use { tos ->
            val e1 = org.apache.commons.compress.archivers.tar.TarArchiveEntry("huge.bin").apply { size = payloadSize }
            tos.putArchiveEntry(e1)
            val zeros = ByteArray(64 * 1024)
            var rem = payloadSize
            while (rem > 0L) {
                val n = minOf(rem, zeros.size.toLong()).toInt()
                tos.write(zeros, 0, n)
                rem -= n
            }
            tos.closeArchiveEntry()
            val e2 = org.apache.commons.compress.archivers.tar.TarArchiveEntry("second.txt").apply { size = 2L }
            tos.putArchiveEntry(e2)
            tos.write(byteArrayOf(0x61, 0x62))
            tos.closeArchiveEntry()
        }
        val res = ArchiveManager.inspect(tarGz)
        assertTrue("70MB .tar.gz preview should truncate cleanly instead of OOM/crash", res.isSuccess)
        val names = res.getOrThrow().map { it.name }
        assertEquals(
            "TarArchiveInputStream.skip() must stop at 64MB cap before reaching second.txt",
            listOf("huge.bin"),
            names,
        )
    }

    @Test
    fun `coverSource pruneDirByLru keeps fresh tmp files cleans stale tmp and offlineDb caps active systems`() {
        // 1. OfflineGameDb 活跃系统保护数不超过 GameTdbClient.MAX_CACHED_TABLES (3)
        val maxActive = com.pocketdeck.launcher.data.OfflineGameDb::class.java
            .getDeclaredField("MAX_ACTIVE_PROTECTED_SYSTEMS")
            .apply { isAccessible = true }
            .getInt(null)
        assertTrue("MAX_ACTIVE_PROTECTED_SYSTEMS ($maxActive) must be <= 3", maxActive <= 3)

        // 2. pruneDirByLru 仅清理 >5 分钟的陈旧 .tmp 文件，不误删正在并发写入的新鲜 .tmp
        val dir = tmp.newFolder("lru_tmp_test")
        val freshTmp = File(dir, "inflight.tmp").apply { writeBytes(ByteArray(16)) }
        val staleTmp = File(dir, "abandoned.tmp").apply {
            writeBytes(ByteArray(16))
            setLastModified(System.currentTimeMillis() - 10 * 60_000L)
        }
        CoverSource.pruneDirByLru(dir, maxBytes = 1024L * 1024L)

        assertTrue("Fresh in-flight .tmp file must NOT be deleted by pruneDirByLru", freshTmp.exists())
        assertFalse("Stale >5min .tmp file must be cleaned by pruneDirByLru", staleTmp.exists())
    }
}

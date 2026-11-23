package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.PlayStats
import com.pocketdeck.launcher.data.SystemTabConfig
import com.pocketdeck.launcher.data.UserData
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.home.HomeLibraryReducer
import com.pocketdeck.launcher.ui.home.TabInfo
import com.pocketdeck.launcher.ui.home.TabType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UT-23 · HomeLibraryReducer 纯计算归约器回归测试（P2-6 / P2-7）：
 * - buildManageTabs：自定义排序、系统标签计数与隐藏状态、未归类 OTHER 排除已归类平台/单款游戏
 * - sortCategoryGames & parseReleaseKey：发行日期（新到旧/旧到新、含英文月份与缺失日期置尾）、体积与游玩时长
 * - computeTabListSignature：同容量 metaMap 原地更新某游戏 releaseDate 必须刷新缓存签名
 * - searchAllCategories：10,000 ROM 万级大库先过滤后排序语义正确且剔除已隐藏条目
 */
class HomeLibraryReducerTest {

    private fun game(
        id: String,
        title: String,
        platformId: String = "nes",
        fileSize: Long = 1024L,
        lastModifiedMs: Long = 1000L,
    ) = Game(
        id = id,
        title = title,
        platformId = platformId,
        baseName = title,
        fileName = "$title.rom",
        fileSize = fileSize,
        lastModifiedMs = lastModifiedMs,
    )

    @Test
    fun buildManageTabsComputesCountsAndRespectsTabOrder() {
        val gNes = game("/roms/nes/mario.nes", "Super Mario Bros", "nes")
        val gSnes = game("/roms/snes/zelda.sfc", "A Link to the Past", "snes")
        val gGba = game("/roms/gba/metroid.gba", "Metroid Fusion", "gba")
        val lib = Library(
            root = "/roms",
            platforms = listOf(
                Platform("nes", "NES", "NES", 1f),
                Platform("snes", "SNES", "SNES", 1f),
                Platform("gba", "GBA", "GBA", 1f),
            ),
            games = mapOf("nes" to listOf(gNes), "snes" to listOf(gSnes), "gba" to listOf(gGba)),
        )
        val user = UserData(
            recents = listOf(gNes.id, "/roms/deleted.nes"),
            favorites = setOf(gSnes.id),
            categories = listOf(
                Category(id = "cat_nintendo", name = "Nintendo", sources = listOf("nes"), games = setOf(gSnes.id)),
            ),
            nowTab = true,
            favsTab = false,
            hiddenTabs = setOf("other"),
            tabOrder = listOf("cat_nintendo", "now", "other", "favs"),
            systemTabs = mapOf("now" to SystemTabConfig(customName = "Recent", followSystemLanguage = false)),
        )

        val tabs = HomeLibraryReducer.buildManageTabs(lib, user)
        assertEquals(listOf("cat_nintendo", "now", "other", "favs"), tabs.map { it.id })
        assertEquals(2, tabs[0].gameCount) // gNes by platform + gSnes by explicit game
        assertEquals(1, tabs[1].gameCount) // deleted recent ignored
        assertEquals("Recent", tabs[1].label)
        assertFalse(tabs[1].hidden)
        assertEquals(1, tabs[2].gameCount) // only gGba is uncategorized
        assertTrue(tabs[2].hidden)
        assertEquals(1, tabs[3].gameCount)
        assertTrue(tabs[3].hidden)
    }

    @Test
    fun sortCategoryGamesOrdersByReleaseDateAndPlayStats() {
        val gOld = game("/roms/nes/1985.nes", "Alpha", fileSize = 200L)
        val gMid = game("/roms/nes/1991.nes", "Beta", fileSize = 500L)
        val gNew = game("/roms/nes/1998.nes", "Gamma", fileSize = 100L)
        val gNoDate = game("/roms/nes/nodate.nes", "Aardvark", fileSize = 0L)
        val games = listOf(gNoDate, gMid, gOld, gNew)

        val meta = mapOf(
            gOld.id to GameMetadata(releaseDate = "1985-09-13"),
            gMid.id to GameMetadata(releaseDate = "Nov 21, 1991"),
            gNew.id to GameMetadata(releaseDate = "1998"),
        )

        val newest = HomeLibraryReducer.sortCategoryGames(games, CategorySortOrder.ReleaseNewest, meta)
        assertEquals(listOf(gNew.id, gMid.id, gOld.id, gNoDate.id), newest.map { it.id })

        val oldest = HomeLibraryReducer.sortCategoryGames(games, CategorySortOrder.ReleaseOldest, meta)
        assertEquals(listOf(gOld.id, gMid.id, gNew.id, gNoDate.id), oldest.map { it.id })

        val bySize = HomeLibraryReducer.sortCategoryGames(games, CategorySortOrder.SizeDesc, meta)
        assertEquals(listOf(gMid.id, gOld.id, gNew.id, gNoDate.id), bySize.map { it.id })

        val stats = mapOf(
            gOld.id to PlayStats(totalPlayMs = 60_000L, lastPlayedAt = 200L),
            gNew.id to PlayStats(totalPlayMs = 120_000L, lastPlayedAt = 100L),
        )
        val byPlayTime = HomeLibraryReducer.sortCategoryGames(games, CategorySortOrder.PlayTimeDesc, meta, stats)
        assertEquals(listOf(gNew.id, gOld.id, gNoDate.id, gMid.id), byPlayTime.map { it.id })
    }

    @Test
    fun computeTabListSignatureRefreshesWhenSameSizeMetaMapUpdatesReleaseDate() {
        val tab = TabInfo(id = "cat_1", label = "RPG", type = TabType.Custom)
        val user = UserData(
            categories = listOf(
                Category(id = "cat_1", name = "RPG", sources = listOf("snes"), sortOrder = CategorySortOrder.ReleaseNewest),
            ),
        )
        val meta1 = mapOf(
            "g1" to GameMetadata(releaseDate = "1991-01-01"),
            "g2" to GameMetadata(releaseDate = "1992-01-01"),
        )
        // 新不可变快照大小完全相同（仍为 2），仅原地修改 g1 的发行日
        val meta2 = mapOf(
            "g1" to GameMetadata(releaseDate = "1999-12-31"),
            "g2" to GameMetadata(releaseDate = "1992-01-01"),
        )
        assertEquals(meta1.size, meta2.size)

        val sig1 = HomeLibraryReducer.computeTabListSignature(tab, user, CategorySortOrder.ReleaseNewest, meta1)
        val sig2 = HomeLibraryReducer.computeTabListSignature(tab, user, CategorySortOrder.ReleaseNewest, meta2)
        assertNotEquals("Same-size metaMap snapshot update must invalidate release-sorted tab cache", sig1, sig2)

        // 按标题排序时无需依赖 metaMap，签名应稳定一致
        val titleSig1 = HomeLibraryReducer.computeTabListSignature(tab, user, CategorySortOrder.TitleAsc, meta1)
        val titleSig2 = HomeLibraryReducer.computeTabListSignature(tab, user, CategorySortOrder.TitleAsc, meta2)
        assertEquals(titleSig1, titleSig2)
    }

    @Test
    fun searchAllCategoriesHandlesTenThousandRomsWithFilterBeforeSort() {
        val games = List(10_000) { idx ->
            val name = when (idx) {
                7777 -> "Chrono Trigger"
                3333 -> "Chrono Cross"
                1111 -> "Hidden Chrono"
                else -> "Retro Game #$idx"
            }
            game("/roms/snes/g_$idx.sfc", name, "snes")
        }
        val lib = Library(
            root = "/roms",
            platforms = listOf(Platform("snes", "SNES", "SNES", 1f)),
            games = mapOf("snes" to games),
        )
        val hidden = setOf("/roms/snes/g_1111.sfc")

        val hits = HomeLibraryReducer.searchAllCategories(lib, hidden, "  chrono ")
        assertEquals(listOf("Chrono Cross", "Chrono Trigger"), hits.map { it.title })
        assertTrue(HomeLibraryReducer.searchAllCategories(lib, hidden, "   ").isEmpty())
    }

    @Test
    fun resolveInitialTabSelectionRestoresSavedTabWaitsDuringScanAndFallsBackCleanly() {
        val tabs = listOf(
            TabInfo("now", "NOW", TabType.Now),
            TabInfo("favs", "FAVS", TabType.Favs),
            TabInfo("cat:nes", "NES", TabType.Custom),
            TabInfo("cat:android", "ANDROID", TabType.Custom),
        )

        // 1. 命中有游戏的上次分类 -> 立即恢复并锁定
        val hit = HomeLibraryReducer.resolveInitialTabSelection(
            tabs = tabs,
            currentTabIndex = 0,
            savedTabId = "cat:nes",
            scanning = true,
            hasAnyGamesInLibrary = true,
            tabHasGames = { it.id == "cat:nes" },
        )
        assertEquals(2, hit.tabIndex)
        assertTrue(hit.finalized)
        assertTrue(hit.matchedSavedTab)

        // 2. 扫描中且上次分类（如 ANDROID）暂未扫完 -> 先停留在目标标签等待，不提前回退
        val waiting = HomeLibraryReducer.resolveInitialTabSelection(
            tabs = tabs,
            currentTabIndex = 0,
            savedTabId = "cat:android",
            scanning = true,
            hasAnyGamesInLibrary = true,
            tabHasGames = { it.id == "cat:nes" },
        )
        assertEquals(3, waiting.tabIndex)
        assertFalse(waiting.finalized)
        assertTrue(waiting.matchedSavedTab)

        // 3. 扫描结束后上次分类仍为空，存在其它非空分类 -> 回退到首个非空分类
        val emptyFallback = HomeLibraryReducer.resolveInitialTabSelection(
            tabs = tabs,
            currentTabIndex = 3,
            savedTabId = "cat:android",
            scanning = false,
            hasAnyGamesInLibrary = true,
            tabHasGames = { it.id == "cat:nes" },
        )
        assertEquals(2, emptyFallback.tabIndex)
        assertTrue(emptyFallback.finalized)
        assertFalse(emptyFallback.matchedSavedTab)

        // 4. 上次分类已被删除或隐藏 -> 回退到首个非空分类
        val deletedFallback = HomeLibraryReducer.resolveInitialTabSelection(
            tabs = tabs,
            currentTabIndex = 0,
            savedTabId = "cat:deleted",
            scanning = false,
            hasAnyGamesInLibrary = true,
            tabHasGames = { it.id == "favs" || it.id == "cat:nes" },
        )
        assertEquals(1, deletedFallback.tabIndex)
        assertTrue(deletedFallback.finalized)
        assertFalse(deletedFallback.matchedSavedTab)

        // 5. 首次安装（无保存记录）-> 保持原逻辑跳过空的 NOW，跳到首个非空标签
        val firstRun = HomeLibraryReducer.resolveInitialTabSelection(
            tabs = tabs,
            currentTabIndex = 0,
            savedTabId = "",
            scanning = false,
            hasAnyGamesInLibrary = true,
            tabHasGames = { it.id == "cat:nes" },
        )
        assertEquals(2, firstRun.tabIndex)
        assertTrue(firstRun.finalized)
    }

    @Test
    fun userStoreMigratesAndClearsLastSelectedGameIdOnFileOps() {
        val base = UserData(
            lastSelectedTabId = "cat:nes",
            lastSelectedGameId = "/roms/nes/mario.nes",
        )
        // 使用空 Context 仅调用纯数据迁移方法
        val fakeCtx = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): java.io.File = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "pd_last_sel_test")
        }
        val store = com.pocketdeck.launcher.data.UserStore(fakeCtx)

        val renamed = store.applyMigrateGameId(base, "/roms/nes/mario.nes", "/roms/nes/super_mario.nes")
        assertEquals("/roms/nes/super_mario.nes", renamed.lastSelectedGameId)
        assertEquals("cat:nes", renamed.lastSelectedTabId)

        val dirMoved = store.applyMigratePathPrefix(renamed, "/roms/nes", "/roms/fc")
        assertEquals("/roms/fc/super_mario.nes", dirMoved.lastSelectedGameId)

        val deleted = store.applyRemoveGameData(dirMoved, "/roms/fc/super_mario.nes")
        assertEquals("", deleted.lastSelectedGameId)

        val dirRemoved = store.applyRemovePathPrefix(dirMoved, "/roms/fc")
        assertEquals("", dirRemoved.lastSelectedGameId)
    }
}

package com.pocketdeck.launcher.ui.home

import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.PlayStats
import com.pocketdeck.launcher.data.SystemTabConfig
import com.pocketdeck.launcher.data.UserData
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library

/**
 * 首页游戏库纯计算归约器：
 * 从 HomeViewModel 剥离无副作用的 Tab 列表构建、缓存签名计算、分类排序、发行日期解析与搜索过滤逻辑，
 * 便于独立单元测试并减轻 ViewModel 的职责耦合（P2-6）。
 */
internal object HomeLibraryReducer {
    private val RELEASE_YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val RELEASE_NUM_REGEX = Regex("""\d{1,2}""")
    private val RELEASE_MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun buildManageTabs(lib: Library, user: UserData): List<TabEntry> {
        val categorizedPlatforms = user.categories.flatMap { it.sources }.toSet()
        val categorizedGames = user.categories.flatMap { it.games }.toSet()
        val nowCfg = user.systemTabs["now"] ?: SystemTabConfig()
        val favsCfg = user.systemTabs["favs"] ?: SystemTabConfig()
        val otherCfg = user.systemTabs["other"] ?: SystemTabConfig()

        val nowCount = user.recents.count { it in lib.byId }
        val favsCount = user.favorites.count { it in lib.byId }
        val otherCount = lib.byId.values.count {
            it.platformId !in categorizedPlatforms && it.id !in categorizedGames
        }

        val all = buildList {
            add(
                TabEntry(
                    id = "now",
                    label = nowCfg.customName.ifBlank { "NOW" },
                    type = TabType.Now,
                    hidden = !user.nowTab,
                    gameCount = nowCount,
                    followSystemLanguage = nowCfg.followSystemLanguage,
                ),
            )
            add(
                TabEntry(
                    id = "favs",
                    label = favsCfg.customName.ifBlank { "FAVS" },
                    type = TabType.Favs,
                    hidden = !user.favsTab,
                    gameCount = favsCount,
                    followSystemLanguage = favsCfg.followSystemLanguage,
                ),
            )
            user.categories.forEach { c ->
                val names = c.sources.map { sid -> lib.platform(sid)?.shortName ?: PlatformCatalog.forFolder(sid).shortName }
                val catCount = (c.sources.flatMap { lib.games[it].orEmpty() } + c.games.mapNotNull { lib.byId[it] })
                    .distinctBy { it.id }
                    .size
                add(
                    TabEntry(
                        id = c.id,
                        label = c.name,
                        type = TabType.Custom,
                        hidden = c.id in user.hiddenTabs,
                        includes = names,
                        gameCount = catCount,
                        followSystemLanguage = false,
                    ),
                )
            }
            add(
                TabEntry(
                    id = "other",
                    label = otherCfg.customName.ifBlank { "OTHER" },
                    type = TabType.Other,
                    hidden = "other" in user.hiddenTabs,
                    gameCount = otherCount,
                    followSystemLanguage = otherCfg.followSystemLanguage,
                ),
            )
        }
        val order = user.tabOrder.withIndex().associate { it.value to it.index }
        return all.withIndex()
            .sortedBy { (i, t) -> order[t.id] ?: (100_000 + i) }
            .map { it.value }
    }

    /**
     * 计算 Tab 排序结果的缓存签名。
     * 注意：`metaMap` 在仅修改已有条目 releaseDate（size 不变）时仍会生成新不可变 Map 实例，
     * 因此使用 `System.identityHashCode(metaMap)` 确保原地更新也能立刻失效缓存。
     */
    fun computeTabListSignature(
        tab: TabInfo,
        user: UserData,
        sortOrder: CategorySortOrder?,
        metaMap: Map<String, GameMetadata>,
    ): Int {
        val needsMeta = sortOrder == CategorySortOrder.ReleaseNewest || sortOrder == CategorySortOrder.ReleaseOldest
        val needsPlayStats = tab.type == TabType.Now ||
            sortOrder == CategorySortOrder.LastPlayedDesc ||
            sortOrder == CategorySortOrder.PlayTimeDesc
        return when (tab.type) {
            TabType.Now -> user.recents.hashCode()
            TabType.Favs -> user.favorites.hashCode()
            TabType.Custom -> user.categories.firstOrNull { it.id == tab.id }.hashCode()
            TabType.Other -> user.categories.hashCode()
        } * 31 + (if (needsMeta) System.identityHashCode(metaMap) else 0) + (if (needsPlayStats) user.playStats.hashCode() else 0)
    }

    fun sortCategoryGames(
        games: List<Game>,
        order: CategorySortOrder,
        metaMap: Map<String, GameMetadata>,
        playStats: Map<String, PlayStats> = emptyMap(),
    ): List<Game> = when (order) {
        CategorySortOrder.TitleAsc -> games.sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title) }
        CategorySortOrder.TitleDesc -> games.sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(b.title, a.title) }
        CategorySortOrder.ReleaseNewest -> {
            val keys = HashMap<String, Int>(games.size * 2)
            for (g in games) {
                parseReleaseKey(metaMap[g.id]?.releaseDate)?.let { keys[g.id] = it }
            }
            games.sortedWith { a, b ->
                val ka = keys[a.id]
                val kb = keys[b.id]
                when {
                    ka == null && kb == null -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                    ka == null -> 1
                    kb == null -> -1
                    ka != kb -> kb.compareTo(ka)
                    else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                }
            }
        }
        CategorySortOrder.ReleaseOldest -> {
            val keys = HashMap<String, Int>(games.size * 2)
            for (g in games) {
                parseReleaseKey(metaMap[g.id]?.releaseDate)?.let { keys[g.id] = it }
            }
            games.sortedWith { a, b ->
                val ka = keys[a.id]
                val kb = keys[b.id]
                when {
                    ka == null && kb == null -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                    ka == null -> 1
                    kb == null -> -1
                    ka != kb -> ka.compareTo(kb)
                    else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                }
            }
        }
        CategorySortOrder.ModifiedNewest -> games.sortedWith { a, b ->
            val cmp = b.lastModifiedMs.compareTo(a.lastModifiedMs)
            if (cmp != 0) cmp else String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
        }
        CategorySortOrder.SizeDesc -> games.sortedWith { a, b ->
            val emptyA = a.fileSize <= 0L
            val emptyB = b.fileSize <= 0L
            when {
                emptyA != emptyB -> if (emptyA) 1 else -1
                a.fileSize != b.fileSize -> b.fileSize.compareTo(a.fileSize)
                else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
            }
        }
        CategorySortOrder.SizeAsc -> games.sortedWith { a, b ->
            val emptyA = a.fileSize <= 0L
            val emptyB = b.fileSize <= 0L
            when {
                emptyA != emptyB -> if (emptyA) 1 else -1
                a.fileSize != b.fileSize -> a.fileSize.compareTo(b.fileSize)
                else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
            }
        }
        CategorySortOrder.LastPlayedDesc -> games.sortedWith { a, b ->
            val ta = playStats[a.id]?.lastPlayedAt ?: 0L
            val tb = playStats[b.id]?.lastPlayedAt ?: 0L
            if (ta != tb) tb.compareTo(ta) else 0
        }
        CategorySortOrder.PlayTimeDesc -> games.sortedWith { a, b ->
            val da = playStats[a.id]?.totalPlayMs ?: 0L
            val db = playStats[b.id]?.totalPlayMs ?: 0L
            when {
                da != db -> db.compareTo(da)
                else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
            }
        }
    }

    fun parseReleaseKey(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val yMatch = RELEASE_YEAR_REGEX.find(raw) ?: return null
        val year = yMatch.groupValues[1].toIntOrNull() ?: return null
        val afterYear = raw.substring(yMatch.range.last + 1)
        var month = 0
        var day = 0
        var found = 0
        for (m in RELEASE_NUM_REGEX.findAll(afterYear)) {
            val v = m.value.toIntOrNull() ?: continue
            if (found == 0) {
                if (v in 1..12) month = v
                found++
            } else if (found == 1) {
                if (v in 1..31) day = v
                break
            }
        }
        if (month == 0) month = monthNameToNumber(raw) ?: 0
        return year * 10000 + month * 100 + day
    }

    private fun monthNameToNumber(raw: String): Int? {
        val low = raw.lowercase()
        val idx = RELEASE_MONTHS.indexOfFirst { it in low }
        return if (idx >= 0) idx + 1 else null
    }

    fun applySearch(games: List<Game>, query: String): List<Game> {
        if (query.isBlank()) return games
        val q = query.trim().lowercase()
        return games.filter { it.title.lowercase().contains(q) }
    }

    /**
     * 全分类搜索：先过滤隐藏项与关键词，再仅对命中子集排序，
     * 避免万级游戏库每次输入都做 O(N log N) 全量排序。
     */
    fun searchAllCategories(lib: Library, hiddenIds: Set<String>, query: String): List<Game> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val matched = ArrayList<Game>()
        for (g in lib.byId.values) {
            if (g.id !in hiddenIds && g.title.lowercase().contains(q)) {
                matched.add(g)
            }
        }
        matched.sortWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title) }
        return matched
    }
}

package com.pocketdeck.launcher.data

import android.content.Context
import com.pocketdeck.launcher.input.KeyAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** 一个 ROM 根目录（其下每个子文件夹是一个平台）。 */
data class RomDir(val path: String, val enabled: Boolean = true)

/** 分类内游戏的显示排序方式。 */
enum class CategorySortOrder {
    TitleAsc,       // 名称 A -> Z
    TitleDesc,      // 名称 Z -> A
    ReleaseNewest,  // 发行时间 新 -> 旧
    ReleaseOldest,  // 发行时间 旧 -> 新
    ModifiedNewest, // 文件修改时间 新 -> 旧
    SizeDesc,       // 游戏大小 大 -> 小
    SizeAsc,        // 游戏大小 小 -> 大
    LastPlayedDesc, // 最近游玩 新 -> 旧
    PlayTimeDesc,   // 游玩最久 多 -> 少
}

/** 单游戏游玩统计（启动次数、累计游玩时长、最近游玩时间戳）。 */
data class PlayStats(
    val playCount: Int = 0,
    val totalPlayMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
)

/**
 * 自定义分类（标签）。
 * @param sources 合并进来的平台 id：这些平台的全部游戏都属于该分类，且平台自身的标签不再单独显示。
 * @param games 手动加入的游戏 id。
 * @param sortOrder 分类内游戏排序方式。
 */
data class Category(
    val id: String,
    val name: String,
    val sources: List<String> = emptyList(),
    val games: Set<String> = emptySet(),
    val sortOrder: CategorySortOrder = CategorySortOrder.TitleAsc,
)

enum class ViewMode { Gallery, List, Grid, Bubbles, CoverFlow, Hero, Wheel, Shelf }
/** Gallery 模式下小卡相对焦点卡的垂直对齐 */
enum class GalleryAlign { Top, Center, Bottom }
/** 顶部分类栏与底部操作栏的配套样式（默认无底透明 / 悬浮磨砂岛）。 */
enum class NavBarStyle { Classic, GlassDock }

enum class BackgroundStyle {
    SunsetFlow,  // 品牌渐变流光（默认）
    PixelDrift,  // 像素游戏元素漂浮
    RetroGrid,   // 合成波透视网格
    CoverGlow,   // 焦点封面氛围光
    Aurora,      // 极光丝带
    Starfield,   // 星海漫游（视差星场 + 星云 + 流星）
    Image,       // 自定义静态图片
    None;        // 纯色

    companion object {
        /** 兼容旧版本存储的背景名，自动迁移到最接近的新背景。 */
        fun fromName(name: String?): BackgroundStyle = when (name) {
            null -> SunsetFlow
            "Waves", "Gradient", "Static", "Topo" -> SunsetFlow
            "Particles", "DotMatrix" -> PixelDrift
            "CoverBlur" -> CoverGlow
            else -> entries.firstOrNull { it.name == name } ?: SunsetFlow
        }
    }
}

/**
 * 系统特殊标签配置（NOW / FAVS / OTHER）。
 * @param customName 自定义显示名（留空时回退系统翻译）。
 * @param followSystemLanguage 是否跟随系统语言自动翻译。
 * @param sortOrder 该系统分类的游戏排序方式；NOW 留空表示按最近游玩顺序，FAVS/OTHER 留空按 TitleAsc。
 */
data class SystemTabConfig(
    val customName: String = "",
    val followSystemLanguage: Boolean = true,
    val sortOrder: CategorySortOrder? = null,
)

/**
 * 用户保存的可复用自定义启动器配置。
 */
data class CustomLauncherPreset(
    val id: String,
    val name: String,
    val launcherId: String,
)

/**
 * 12 种低疲劳主题色（覆盖标题、选中态、波浪/极光/等高线/点阵背景等全链路视觉）。
 */
/**
 * 8 套双色渐变主题。gradStart→gradMid→gradEnd 为品牌渐变（选中态、主按钮、开关、焦点环），
 * lightPrimary / darkPrimary 为对比度足够的文字强调色。
 */
enum class ThemeAccent(
    val gradStart: Long,
    val gradMid: Long,
    val gradEnd: Long,
    val lightPrimary: Long,
    val darkPrimary: Long,
) {
    Sunset(0xFFFF9F43, 0xFFFF5A5F, 0xFFE9327A, 0xFFE03A66, 0xFFFF6F91),
    Berry(0xFFFF6FA5, 0xFFC94FD8, 0xFF8B5CF6, 0xFFB0358A, 0xFFF07CC8),
    Ocean(0xFF38BDF8, 0xFF3B82F6, 0xFF6366F1, 0xFF2563EB, 0xFF6FA8FF),
    Mint(0xFF34D399, 0xFF14B8A6, 0xFF0E9AA7, 0xFF0F8579, 0xFF4FD6BE),
    Lemon(0xFFFFD84D, 0xFFFFA62B, 0xFFF97316, 0xFFC2570A, 0xFFFFB547),
    Sakura(0xFFFFB3C7, 0xFFFF7AA8, 0xFFF0568C, 0xFFD43A72, 0xFFFF8DB4),
    Grape(0xFFA78BFA, 0xFF8B5CF6, 0xFF6D28D9, 0xFF6F3CD6, 0xFFB497FF),
    Graphite(0xFF9AA3B2, 0xFF6B7280, 0xFF3F4652, 0xFF4A5160, 0xFFC4CBD6);

    companion object {
        /** 兼容旧版 12 色主题名。 */
        fun fromName(name: String?): ThemeAccent = when (name) {
            null, "Ruby", "Terracotta" -> Sunset
            "Violet" -> Grape
            "Emerald", "Sage", "Cyan" -> Mint
            "Slate" -> Ocean
            "Gold" -> Lemon
            else -> entries.firstOrNull { it.name == name } ?: Sunset
        }
    }
}

object DefaultCategories {
    fun build(): List<Category> = listOf(
        Category(id = "cat:default_nes", name = "NES", sources = listOf("nes")),
        Category(id = "cat:default_snes", name = "SNES", sources = listOf("snes", "sfc")),
        Category(id = "cat:default_gb", name = "GB", sources = listOf("gb", "gba", "gbc")),
        Category(id = "cat:default_android", name = "ANDROID", sources = listOf(PlatformCatalog.ANDROID_ID)),
    )
}

/** 封面下方展示哪些元数据字段（文件大小默认关闭）。 */
data class MetadataDisplayConfig(
    val releaseDate: Boolean = true,
    val genre: Boolean = true,
    val developer: Boolean = true,
    val publisher: Boolean = true,
    val fileSize: Boolean = false,
)

data class UserData(
    val favorites: Set<String> = emptySet(),
    /** 最近游玩，最新的在前。 */
    val recents: List<String> = emptyList(),
    val renames: Map<String, String> = emptyMap(),
    val covers: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.Gallery,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.SunsetFlow,
    val showMetadata: Boolean = true,
    val bottomBar: Boolean = true,
    val navBarStyle: NavBarStyle = NavBarStyle.Classic,
    val nowTab: Boolean = true,
    val favsTab: Boolean = true,
    val clock12h: Boolean = false,
    val swapAbxy: Boolean = false,
    val romRoot: String? = null,
    /** 状态栏图标显示控制 */
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
    val showMobileSignal: Boolean = true,
    val showHeadset: Boolean = true,
    val showAirplane: Boolean = true,
    val showDnd: Boolean = true,
    /** 每平台首选启动器（platformId → emulatorId）。 */
    val platformLaunchers: Map<String, String> = emptyMap(),
    val galleryAlign: GalleryAlign = GalleryAlign.Top,
    /** 用户配置的 ROM 目录；为空时自动检测。 */
    val romDirs: List<RomDir> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** 标签顺序（标签 id）；不在列表里的标签按默认顺序排在后面。 */
    val tabOrder: List<String> = emptyList(),
    /** 被隐藏的平台标签 id。 */
    val hiddenTabs: Set<String> = emptySet(),
    /** 主题模式：跟随系统 / 浅色 / 深色。 */
    val theme: AppTheme = AppTheme.System,
    /** 封面抓取节点（按优先级排序）。 */
    val coverSources: List<SourceItemConfig> = ScraperCatalog.defaultCoverConfigs(),
    /** 游戏元数据服务商（按优先级排序）。 */
    val metadataSources: List<SourceItemConfig> = ScraperCatalog.defaultMetadataConfigs(),
    /** 第三方刮削服务可选账号/密钥。 */
    val scraperCredentials: ScraperCredentials = ScraperCredentials(),
    /** 封面下方展示哪些元数据字段。 */
    val metaDisplay: MetadataDisplayConfig = MetadataDisplayConfig(),
    /** 单游戏自定义启动器覆盖（gameId → launcherId）。 */
    val gameLaunchers: Map<String, String> = emptyMap(),
    /** 是否显示原生 Android 游戏标签。 */
    val showAndroidGames: Boolean = true,
    /** 手动加入游戏库的 Android 应用包名。 */
    val androidGamePackages: Set<String> = emptySet(),
    /** 从自动发现的 Android 游戏中排除的包名。 */
    val excludedAndroidPackages: Set<String> = emptySet(),
    /** 自定义静态背景图片路径。 */
    val customBackground: String? = null,
    /** 自定义按键绑定（Action → Android keyCode，-1 表示清除，缺失表示跟随默认）。 */
    val keyBindings: Map<KeyAction, Int> = emptyMap(),
    /** 按键音开关（默认开启）。 */
    val keySoundEnabled: Boolean = true,
    /** 减少动态效果：为 true 时背景动画暂停。 */
    val reduceMotion: Boolean = false,
    /** 主题色（8 种预设）。 */
    val themeAccent: ThemeAccent = ThemeAccent.Sunset,
    /** 字体 ID（"default" / "system_sans" / "system_serif" / "system_mono" / "file:<path>"）。 */
    val fontId: String = "default",
    /** 每平台封面比例覆盖（platformId → aspect）。 */
    val platformAspects: Map<String, Float> = emptyMap(),
    /** 用户保存的自定义启动器预设。 */
    val customLaunchers: List<CustomLauncherPreset> = emptyList(),
    /** 系统特殊标签配置（now / favs / other）。 */
    val systemTabs: Map<String, SystemTabConfig> = emptyMap(),
    /** 是否已初始化过默认分类（用户删空后不再自动补回）。 */
    val categoriesInitialized: Boolean = false,
    /** 局域网设备 ID（用于区分同网段多个客户端）。 */
    val deviceId: String = "",
    /** 局域网设备昵称（默认自动生成可爱名字，可随时修改）。 */
    val deviceName: String = "",
    /** 游戏快传是否兼容 LocalSend v2 协议（默认开启）。 */
    val localSendCompat: Boolean = true,
    /** 单游戏游玩统计（gameId -> PlayStats）。 */
    val playStats: Map<String, PlayStats> = emptyMap(),
    /** 实验性功能：掌机快捷 HUD 浮层开关（默认关闭）。 */
    val quickHudEnabled: Boolean = false,
    val searchAllCategories: Boolean = true,
)

/** 用户数据（收藏、最近、改名、偏好设置）。轻量级 AtomicFile JSON 单文件，读写均在 IO 线程。 */
class UserStore(context: Context) {

    private val store = LocalPrefsStore.forUserPrefs(context)

    private object K {
        val favorites = stringSetPrefsKey("favorites")
        val recents = stringPrefsKey("recents")
        val renames = stringMapPrefsKey("renames")
        val covers = stringMapPrefsKey("covers")
        val hidden = stringSetPrefsKey("hidden")
        val viewMode = stringPrefsKey("view_mode")
        val background = stringPrefsKey("background_style")
        val showMetadata = booleanPrefsKey("show_metadata")
        val bottomBar = booleanPrefsKey("bottom_bar")
        val navBarStyle = stringPrefsKey("nav_bar_style")
        val nowTab = booleanPrefsKey("now_tab")
        val favsTab = booleanPrefsKey("favs_tab")
        val clock12h = booleanPrefsKey("clock_12h")
        val swapAbxy = booleanPrefsKey("swap_abxy")
        val romRoot = stringPrefsKey("rom_root")
        val showClock = booleanPrefsKey("show_clock")
        val showBattery = booleanPrefsKey("show_battery")
        val showWifi = booleanPrefsKey("show_wifi")
        val showBluetooth = booleanPrefsKey("show_bluetooth")
        val showMobileSignal = booleanPrefsKey("show_mobile_signal")
        val showHeadset = booleanPrefsKey("show_headset")
        val showAirplane = booleanPrefsKey("show_airplane")
        val showDnd = booleanPrefsKey("show_dnd")
        val platformLaunchers = stringMapPrefsKey("platform_launchers")
        val galleryAlign = stringPrefsKey("gallery_align")
        val romDirs = stringPrefsKey("rom_dirs")
        val categories = stringPrefsKey("categories")
        val tabOrder = stringPrefsKey("tab_order")
        val hiddenTabs = stringSetPrefsKey("hidden_tabs")
        val theme = stringPrefsKey("theme")
        val coverSources = stringPrefsKey("cover_sources")
        val metadataSources = stringPrefsKey("metadata_sources")
        val scraperCredentials = stringPrefsKey("scraper_credentials")
        val metaShowReleaseDate = booleanPrefsKey("meta_show_release_date")
        val metaShowGenre = booleanPrefsKey("meta_show_genre")
        val metaShowDeveloper = booleanPrefsKey("meta_show_developer")
        val metaShowPublisher = booleanPrefsKey("meta_show_publisher")
        val metaShowFileSize = booleanPrefsKey("meta_show_file_size")
        val gameLaunchers = stringMapPrefsKey("game_launchers")
        val showAndroidGames = booleanPrefsKey("show_android_games")
        val androidGamePackages = stringSetPrefsKey("android_game_packages")
        val excludedAndroidPackages = stringSetPrefsKey("excluded_android_packages")
        val customBackground = stringPrefsKey("custom_background")
        val keyBindings = stringMapPrefsKey("key_bindings")
        val keySoundEnabled = booleanPrefsKey("key_sound_enabled")
        val reduceMotion = booleanPrefsKey("reduce_motion")
        val themeAccent = stringPrefsKey("theme_accent")
        val fontId = stringPrefsKey("font_id")
        val platformAspects = stringMapPrefsKey("platform_aspects")
        val customLaunchers = stringPrefsKey("custom_launchers")
        val systemTabs = stringPrefsKey("system_tabs")
        val categoriesInitialized = booleanPrefsKey("categories_initialized")
        val deviceId = stringPrefsKey("device_id")
        val deviceName = stringPrefsKey("device_name")
        val localSendCompat = booleanPrefsKey("local_send_compat")
        val playStats = stringPrefsKey("play_stats")
        val quickHudEnabled = booleanPrefsKey("quick_hud_enabled")
        val searchAllCategories = booleanPrefsKey("search_all_categories")
    }

    private companion object {
        const val MAX_RECENTS = 30

        fun decodeDirs(s: String?): List<RomDir> = s.orEmpty().split('\n').mapNotNull { line ->
            if (line.length < 3 || line[1] != '\t') null else RomDir(line.substring(2), line[0] == '1')
        }

        fun encodeDirs(dirs: List<RomDir>): String =
            dirs.joinToString("\n") { (if (it.enabled) "1" else "0") + "\t" + it.path }

        fun decodeCategories(s: String?): List<Category> = runCatching {
            val arr = JSONArray(s ?: return emptyList())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val src = o.optJSONArray("sources") ?: JSONArray()
                val gs = o.optJSONArray("games") ?: JSONArray()
                Category(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    sources = (0 until src.length()).map { src.getString(it) },
                    games = (0 until gs.length()).map { gs.getString(it) }.toSet(),
                    sortOrder = enumOf(o.optString("sortOrder"), CategorySortOrder.TitleAsc),
                )
            }
        }.getOrDefault(emptyList())

        fun encodeCategories(list: List<Category>): String = JSONArray().apply {
            list.forEach { c ->
                put(
                    JSONObject()
                        .put("id", c.id)
                        .put("name", c.name)
                        .put("sources", JSONArray(c.sources))
                        .put("games", JSONArray(c.games.toList()))
                        .put("sortOrder", c.sortOrder.name),
                )
            }
        }.toString()

        inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default

        fun decodeSources(s: String?): List<SourceItemConfig> = runCatching {
            if (s.isNullOrBlank()) return emptyList()
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) null else SourceItemConfig(id, o.optBoolean("enabled", true))
            }
        }.getOrDefault(emptyList())

        fun encodeSources(list: List<SourceItemConfig>): String = JSONArray().apply {
            list.forEach { item ->
                put(JSONObject().put("id", item.id).put("enabled", item.enabled))
            }
        }.toString()

        fun decodeCredentials(s: String?): ScraperCredentials = runCatching {
            if (s.isNullOrBlank()) return ScraperCredentials()
            val o = JSONObject(s)
            ScraperCredentials(
                ssUser = o.optString("ssUser", ""),
                ssPass = o.optString("ssPass", ""),
                tgdbKey = o.optString("tgdbKey", ""),
                igdbClientId = o.optString("igdbClientId", ""),
                igdbClientSecret = o.optString("igdbClientSecret", ""),
                sgdbKey = o.optString("sgdbKey", ""),
            )
        }.getOrDefault(ScraperCredentials())

        fun encodeCredentials(c: ScraperCredentials): String = JSONObject()
            .put("ssUser", c.ssUser)
            .put("ssPass", c.ssPass)
            .put("tgdbKey", c.tgdbKey)
            .put("igdbClientId", c.igdbClientId)
            .put("igdbClientSecret", c.igdbClientSecret)
            .put("sgdbKey", c.sgdbKey)
            .toString()

        fun decodeKeyBindings(map: Map<String, String>?): Map<KeyAction, Int> =
            map.orEmpty().mapNotNull { (k, v) ->
                val action = runCatching { KeyAction.valueOf(k) }.getOrNull() ?: return@mapNotNull null
                val code = v.toIntOrNull() ?: return@mapNotNull null
                action to code
            }.toMap()

        fun encodeKeyBindings(map: Map<KeyAction, Int>): Map<String, String> =
            map.entries.associate { it.key.name to it.value.toString() }

        fun decodeFloatMap(map: Map<String, String>?): Map<String, Float> =
            map.orEmpty().mapNotNull { (k, v) -> v.toFloatOrNull()?.let { k to it } }.toMap()

        fun encodeFloatMap(map: Map<String, Float>): Map<String, String> =
            map.mapValues { it.value.toString() }

        fun decodeCustomLaunchers(s: String?): List<CustomLauncherPreset> = runCatching {
            if (s.isNullOrBlank()) return emptyList()
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val name = o.optString("name")
                val lid = o.optString("launcherId")
                if (id.isBlank() || lid.isBlank()) null else CustomLauncherPreset(id, name.ifBlank { lid }, lid)
            }
        }.getOrDefault(emptyList())

        fun encodeCustomLaunchers(list: List<CustomLauncherPreset>): String = JSONArray().apply {
            list.forEach { p ->
                put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("launcherId", p.launcherId),
                )
            }
        }.toString()

        fun decodeSystemTabs(s: String?): Map<String, SystemTabConfig> = runCatching {
            if (s.isNullOrBlank()) return emptyMap()
            val obj = JSONObject(s)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = obj.optJSONObject(k) ?: continue
                    put(
                        k,
                        SystemTabConfig(
                            customName = o.optString("customName", ""),
                            followSystemLanguage = o.optBoolean("followSystemLanguage", true),
                            sortOrder = o.optString("sortOrder", "").takeIf { it.isNotBlank() }?.let { raw ->
                                CategorySortOrder.entries.firstOrNull { it.name == raw }
                            },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())

        fun encodeSystemTabs(map: Map<String, SystemTabConfig>): String = JSONObject().apply {
            map.forEach { (k, v) ->
                put(
                    k,
                    JSONObject()
                        .put("customName", v.customName)
                        .put("followSystemLanguage", v.followSystemLanguage)
                        .apply { v.sortOrder?.let { put("sortOrder", it.name) } },
                )
            }
        }.toString()
    }

    val data: Flow<UserData> = store.data.map { p -> p.toUserData() }

    private fun PrefsSnapshot.toUserData(): UserData {
        val rawCategories = decodeCategories(this[K.categories])
        val initialized = this[K.categoriesInitialized] ?: false
        val effectiveCategories = if (!initialized && rawCategories.isEmpty()) DefaultCategories.build() else rawCategories
        return UserData(
            favorites = this[K.favorites].orEmpty(),
            recents = this[K.recents]?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
            renames = this[K.renames].orEmpty(),
            covers = this[K.covers].orEmpty(),
            hidden = this[K.hidden].orEmpty(),
            viewMode = enumOf(this[K.viewMode], ViewMode.Gallery),
            backgroundStyle = BackgroundStyle.fromName(this[K.background]),
            showMetadata = this[K.showMetadata] ?: true,
            bottomBar = this[K.bottomBar] ?: true,
            navBarStyle = enumOf(this[K.navBarStyle], NavBarStyle.Classic),
            nowTab = this[K.nowTab] ?: true,
            favsTab = this[K.favsTab] ?: true,
            clock12h = this[K.clock12h] ?: false,
            swapAbxy = this[K.swapAbxy] ?: false,
            romRoot = this[K.romRoot],
            showClock = this[K.showClock] ?: true,
            showBattery = this[K.showBattery] ?: true,
            showWifi = this[K.showWifi] ?: true,
            showBluetooth = this[K.showBluetooth] ?: true,
            showMobileSignal = this[K.showMobileSignal] ?: true,
            showHeadset = this[K.showHeadset] ?: true,
            showAirplane = this[K.showAirplane] ?: true,
            showDnd = this[K.showDnd] ?: true,
            platformLaunchers = this[K.platformLaunchers].orEmpty(),
            galleryAlign = enumOf(this[K.galleryAlign], GalleryAlign.Top),
            romDirs = decodeDirs(this[K.romDirs]),
            categories = effectiveCategories,
            tabOrder = this[K.tabOrder]?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
            hiddenTabs = this[K.hiddenTabs].orEmpty(),
            theme = enumOf(this[K.theme], AppTheme.System),
            coverSources = ScraperCatalog.normalizeCoverConfigs(decodeSources(this[K.coverSources])),
            metadataSources = ScraperCatalog.normalizeMetadataConfigs(decodeSources(this[K.metadataSources])),
            scraperCredentials = decodeCredentials(this[K.scraperCredentials]),
            metaDisplay = MetadataDisplayConfig(
                releaseDate = this[K.metaShowReleaseDate] ?: true,
                genre = this[K.metaShowGenre] ?: true,
                developer = this[K.metaShowDeveloper] ?: true,
                publisher = this[K.metaShowPublisher] ?: true,
                fileSize = this[K.metaShowFileSize] ?: false,
            ),
            gameLaunchers = this[K.gameLaunchers].orEmpty(),
            showAndroidGames = this[K.showAndroidGames] ?: true,
            androidGamePackages = this[K.androidGamePackages].orEmpty(),
            excludedAndroidPackages = this[K.excludedAndroidPackages].orEmpty(),
            customBackground = this[K.customBackground],
            keyBindings = decodeKeyBindings(this[K.keyBindings]),
            keySoundEnabled = this[K.keySoundEnabled] ?: true,
            reduceMotion = this[K.reduceMotion] ?: false,
            themeAccent = ThemeAccent.fromName(this[K.themeAccent]),
            fontId = this[K.fontId] ?: "default",
            platformAspects = decodeFloatMap(this[K.platformAspects]),
            customLaunchers = decodeCustomLaunchers(this[K.customLaunchers]),
            systemTabs = decodeSystemTabs(this[K.systemTabs]),
            categoriesInitialized = initialized || rawCategories.isNotEmpty(),
            deviceId = this[K.deviceId].orEmpty(),
            deviceName = this[K.deviceName].orEmpty(),
            localSendCompat = this[K.localSendCompat] ?: true,
            playStats = PlayStatsCodec.decodePlayStats(this[K.playStats]),
            quickHudEnabled = this[K.quickHudEnabled] ?: false,
            searchAllCategories = this[K.searchAllCategories] ?: true,
        )
    }

    suspend fun toggleFavorite(id: String) = store.edit { p ->
        val cur = p[K.favorites].orEmpty()
        p[K.favorites] = if (id in cur) cur - id else cur + id
    }

    suspend fun addRecent(id: String) = recordGameLaunch(id)

    suspend fun recordGameLaunch(id: String, nowMs: Long = System.currentTimeMillis()) = store.edit { p ->
        val cur = p[K.recents]?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        p[K.recents] = (listOf(id) + (cur - id)).take(MAX_RECENTS).joinToString("\n")
        val stats = PlayStatsCodec.decodePlayStats(p[K.playStats]).toMutableMap()
        val prev = stats[id] ?: PlayStats()
        stats[id] = prev.copy(
            playCount = prev.playCount + 1,
            lastPlayedAt = nowMs,
        )
        p[K.playStats] = PlayStatsCodec.encodePlayStats(stats)
    }

    suspend fun addPlayDuration(id: String, deltaMs: Long) = store.edit { p ->
        if (deltaMs <= 0L) return@edit
        val stats = PlayStatsCodec.decodePlayStats(p[K.playStats]).toMutableMap()
        val prev = stats[id] ?: PlayStats()
        stats[id] = prev.copy(
            totalPlayMs = prev.totalPlayMs + deltaMs,
            lastPlayedAt = maxOf(prev.lastPlayedAt, System.currentTimeMillis()),
        )
        p[K.playStats] = PlayStatsCodec.encodePlayStats(stats)
    }

    suspend fun saveGameEdits(id: String, name: String?, cover: String?) = store.edit { p ->
        val renames = p[K.renames].orEmpty().toMutableMap()
        if (name.isNullOrBlank()) renames.remove(id) else renames[id] = name.trim()
        p[K.renames] = renames
        val covers = p[K.covers].orEmpty().toMutableMap()
        if (cover == null) covers.remove(id) else covers[id] = cover
        p[K.covers] = covers
    }

    suspend fun hide(id: String) = store.edit { p ->
        p[K.hidden] = p[K.hidden].orEmpty() + id
    }

    suspend fun unhide(id: String) = store.edit { p ->
        p[K.hidden] = p[K.hidden].orEmpty() - id
    }

    /** ROM 文件重命名/移动后，把用户数据中关联的旧路径迁移到新路径。 */
    suspend fun migrateGameId(oldId: String, newId: String) {
        if (oldId == newId) return
        update { u ->
            val favs = if (oldId in u.favorites) u.favorites - oldId + newId else u.favorites
            val recents = u.recents.map { if (it == oldId) newId else it }
            val renames = u.renames.toMutableMap().apply { remove(oldId)?.let { put(newId, it) } }
            val covers = u.covers.toMutableMap().apply { remove(oldId)?.let { put(newId, it) } }
            val hidden = if (oldId in u.hidden) u.hidden - oldId + newId else u.hidden
            val launchers = u.gameLaunchers.toMutableMap().apply { remove(oldId)?.let { put(newId, it) } }
            val cats = u.categories.map { c ->
                if (oldId in c.games) c.copy(games = c.games.map { if (it == oldId) newId else it }.toSet())
                else c
            }
            u.copy(
                favorites = favs,
                recents = recents,
                renames = renames,
                covers = covers,
                hidden = hidden,
                gameLaunchers = launchers,
                categories = cats,
            )
        }
    }

    /** ROM 文件被永久删除后，清理其关联的收藏、最近、改名、封面、隐藏与分类条目。 */
    suspend fun removeGameData(id: String) {
        update { u ->
            u.copy(
                favorites = u.favorites - id,
                recents = u.recents.filter { it != id },
                renames = u.renames - id,
                covers = u.covers - id,
                hidden = u.hidden - id,
                gameLaunchers = u.gameLaunchers - id,
                categories = u.categories.map { c ->
                    if (id in c.games) c.copy(games = c.games - id) else c
                },
            )
        }
    }

    /** 文件夹重命名或移动后，将目录下所有游戏的关联路径前缀从 [oldDir] 迁移到 [newDir]。 */
    suspend fun migratePathPrefix(oldDir: String, newDir: String) {
        val oldPrefix = if (oldDir.endsWith("/")) oldDir else "$oldDir/"
        val newPrefix = if (newDir.endsWith("/")) newDir else "$newDir/"
        if (oldPrefix == newPrefix) return
        fun remap(id: String): String =
            if (id.startsWith(oldPrefix)) newPrefix + id.removePrefix(oldPrefix) else id
        update { u ->
            u.copy(
                favorites = u.favorites.map(::remap).toSet(),
                recents = u.recents.map(::remap),
                renames = u.renames.entries.associate { (k, v) -> remap(k) to v },
                covers = u.covers.entries.associate { (k, v) -> remap(k) to v },
                hidden = u.hidden.map(::remap).toSet(),
                gameLaunchers = u.gameLaunchers.entries.associate { (k, v) -> remap(k) to v },
                categories = u.categories.map { c ->
                    if (c.games.any { it.startsWith(oldPrefix) }) {
                        c.copy(games = c.games.map(::remap).toSet())
                    } else c
                },
            )
        }
    }

    /** 文件夹被删除后，清理该目录下所有游戏的关联用户数据。 */
    suspend fun removePathPrefix(dirPath: String) {
        val prefix = if (dirPath.endsWith("/")) dirPath else "$dirPath/"
        update { u ->
            u.copy(
                favorites = u.favorites.filterNot { it.startsWith(prefix) }.toSet(),
                recents = u.recents.filterNot { it.startsWith(prefix) },
                renames = u.renames.filterKeys { !it.startsWith(prefix) },
                covers = u.covers.filterKeys { !it.startsWith(prefix) },
                hidden = u.hidden.filterNot { it.startsWith(prefix) }.toSet(),
                gameLaunchers = u.gameLaunchers.filterKeys { !it.startsWith(prefix) },
                categories = u.categories.map { c ->
                    if (c.games.any { it.startsWith(prefix) }) {
                        c.copy(games = c.games.filterNot { it.startsWith(prefix) }.toSet())
                    } else c
                },
            )
        }
    }

    suspend fun update(transform: (UserData) -> UserData) = store.edit { p ->
        val old = p.toSnapshot().toUserData()
        val new = transform(old)
        p[K.viewMode] = new.viewMode.name
        p[K.favorites] = new.favorites
        p[K.recents] = new.recents.joinToString("\n")
        p[K.renames] = new.renames
        p[K.covers] = new.covers
        p[K.hidden] = new.hidden
        p[K.background] = new.backgroundStyle.name
        p[K.showMetadata] = new.showMetadata
        p[K.bottomBar] = new.bottomBar
        p[K.navBarStyle] = new.navBarStyle.name
        p[K.nowTab] = new.nowTab
        p[K.favsTab] = new.favsTab
        p[K.clock12h] = new.clock12h
        p[K.swapAbxy] = new.swapAbxy
        if (new.romRoot == null) p.remove(K.romRoot) else p[K.romRoot] = new.romRoot
        p[K.showClock] = new.showClock
        p[K.showBattery] = new.showBattery
        p[K.showWifi] = new.showWifi
        p[K.showBluetooth] = new.showBluetooth
        p[K.showMobileSignal] = new.showMobileSignal
        p[K.showHeadset] = new.showHeadset
        p[K.showAirplane] = new.showAirplane
        p[K.showDnd] = new.showDnd
        p[K.platformLaunchers] = new.platformLaunchers
        p[K.galleryAlign] = new.galleryAlign.name
        p[K.romDirs] = encodeDirs(new.romDirs)
        p[K.categories] = encodeCategories(new.categories)
        p[K.tabOrder] = new.tabOrder.joinToString("\n")
        p[K.hiddenTabs] = new.hiddenTabs
        p[K.theme] = new.theme.name
        p[K.coverSources] = encodeSources(new.coverSources)
        p[K.metadataSources] = encodeSources(new.metadataSources)
        p[K.scraperCredentials] = encodeCredentials(new.scraperCredentials)
        p[K.metaShowReleaseDate] = new.metaDisplay.releaseDate
        p[K.metaShowGenre] = new.metaDisplay.genre
        p[K.metaShowDeveloper] = new.metaDisplay.developer
        p[K.metaShowPublisher] = new.metaDisplay.publisher
        p[K.metaShowFileSize] = new.metaDisplay.fileSize
        p[K.gameLaunchers] = new.gameLaunchers
        p[K.showAndroidGames] = new.showAndroidGames
        p[K.androidGamePackages] = new.androidGamePackages
        p[K.excludedAndroidPackages] = new.excludedAndroidPackages
        if (new.customBackground == null) p.remove(K.customBackground) else p[K.customBackground] = new.customBackground
        p[K.keyBindings] = encodeKeyBindings(new.keyBindings)
        p[K.keySoundEnabled] = new.keySoundEnabled
        p[K.reduceMotion] = new.reduceMotion
        p[K.themeAccent] = new.themeAccent.name
        p[K.fontId] = new.fontId
        p[K.platformAspects] = encodeFloatMap(new.platformAspects)
        p[K.customLaunchers] = encodeCustomLaunchers(new.customLaunchers)
        p[K.systemTabs] = encodeSystemTabs(new.systemTabs)
        p[K.categoriesInitialized] = true
        if (new.deviceId.isNotBlank()) p[K.deviceId] = new.deviceId
        if (new.deviceName.isNotBlank()) p[K.deviceName] = new.deviceName
        p[K.localSendCompat] = new.localSendCompat
        p[K.playStats] = PlayStatsCodec.encodePlayStats(new.playStats)
        p[K.quickHudEnabled] = new.quickHudEnabled
        p[K.searchAllCategories] = new.searchAllCategories
    }

    object PlayStatsCodec {
        fun decodePlayStats(s: String?): Map<String, PlayStats> = runCatching {
            if (s.isNullOrBlank()) return emptyMap()
            val obj = JSONObject(s)
            val out = LinkedHashMap<String, PlayStats>(obj.length() * 2)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = obj.optJSONObject(id) ?: continue
                out[id] = PlayStats(
                    playCount = item.optInt("c", 0).coerceAtLeast(0),
                    totalPlayMs = item.optLong("t", 0L).coerceAtLeast(0L),
                    lastPlayedAt = item.optLong("l", 0L).coerceAtLeast(0L),
                )
            }
            out
        }.getOrDefault(emptyMap())

        fun encodePlayStats(map: Map<String, PlayStats>): String = JSONObject().apply {
            for ((id, st) in map) {
                put(
                    id,
                    JSONObject().apply {
                        put("c", st.playCount)
                        put("t", st.totalPlayMs)
                        put("l", st.lastPlayedAt)
                    },
                )
            }
        }.toString()
    }
}

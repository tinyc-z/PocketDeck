package com.pocketdeck.launcher.data.model

/**
 * 一个平台（对应 ROM 根目录下的一个子文件夹）。
 *
 * @param coverAspect 封面宽高比（宽 / 高），决定画廊卡片形状。
 * @param libretroSystem libretro-thumbnails 仓库使用的系统名，用于拉取封面。
 * @param core RetroArch 默认核心名（不含 `_libretro_android.so`）。
 */
data class Platform(
    val id: String,
    val shortName: String,
    val fullName: String,
    val coverAspect: Float = 1f,
    val libretroSystem: String? = null,
    val core: String? = null,
)

data class Game(
    /** ROM 的绝对路径，同时作为唯一 ID。 */
    val id: String,
    val platformId: String,
    /** 不含扩展名的文件名（也是封面匹配用的名字）。 */
    val baseName: String,
    val fileName: String,
    /** 界面上显示的名字（已清理区域/版本标签；用户改名后为改后的名字）。 */
    val title: String,
    /** ROM 目录中找到的同名本地封面。 */
    val localCover: String? = null,
    /** 用户在 Edit 页面选的封面（应用私有目录中的文件）。 */
    val customCover: String? = null,
    /** ROM 文件大小（字节）。 */
    val fileSize: Long = 0L,
    /** 文件最后修改时间戳（毫秒）。 */
    val lastModifiedMs: Long = 0L,
)

data class GameMetadata(
    val releaseDate: String? = null,
    val genre: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
) {
    val isEmpty: Boolean
        get() = releaseDate == null && genre == null && developer == null && publisher == null
}

data class Library(
    val root: String,
    val platforms: List<Platform>,
    val games: Map<String, List<Game>>,
    /** 每个扫描根目录找到的游戏数。 */
    val rootCounts: Map<String, Int> = emptyMap(),
) {
    val byId: Map<String, Game> by lazy { games.values.flatten().associateBy { it.id } }
    private val platformById by lazy { platforms.associateBy { it.id } }

    fun platform(id: String): Platform? = platformById[id]

    companion object {
        val Empty = Library("", emptyList(), emptyMap())
    }
}

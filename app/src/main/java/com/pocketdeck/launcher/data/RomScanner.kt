package com.pocketdeck.launcher.data

import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Library
import java.io.File

/** 扫描 ROM 根目录：每个非空子文件夹是一个平台。只做一层目录遍历，几百个游戏几十毫秒。 */
object RomScanner {

    private val IGNORED_EXT = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "txt", "md", "nfo", "xml", "json", "cfg", "ini",
        "dat", "db", "srm", "sav", "state", "auto", "bak", "log", "pdf", "mp4", "mkv", "ips", "bps",
        "ups", "sub", "sbi", "rtc", "nomedia", "keep", "lpl",
    )
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")
    private val COVER_DIRS = listOf("images", "covers", "boxart", "media/covers", "media/box2dfront", "media/images")
    private val DISC_INDEX_EXT = setOf("cue", "gdi", "m3u", "ccd")
    private val DISC_TRACK_EXT = setOf("bin", "img", "iso")

    /** 常见的 ROM 根目录，按顺序取第一个存在的。 */
    fun defaultRoots(sd: File): List<File> = listOf(
        File(sd, "PocketGames/roms"),
        File(sd, "ROMs"),
        File(sd, "Roms"),
        File(sd, "roms"),
    )

    /** 扫描多个根目录并合并：同一平台出现在多个目录时游戏合并到一个标签。 */
    fun scanAll(roots: List<File>): Library {
        val merged = HashMap<String, MutableMap<String, Game>>()
        val platforms = LinkedHashMap<String, com.pocketdeck.launcher.data.model.Platform>()
        val counts = LinkedHashMap<String, Int>()
        for (root in roots) {
            val lib = scan(root)
            counts[root.path] = lib.games.values.sumOf { it.size }
            lib.platforms.forEach { platforms.putIfAbsent(it.id, it) }
            lib.games.forEach { (pid, list) ->
                val m = merged.getOrPut(pid) { LinkedHashMap() }
                list.forEach { m.putIfAbsent(it.id, it) }
            }
        }
        val games = merged.mapValues { (_, m) -> m.values.sortedBy { it.title.lowercase() } }
        val sorted = platforms.values.sortedWith(compareBy({ PlatformCatalog.sortKey(it.id) }, { it.id }))
        return Library(roots.joinToString(";") { it.path }, sorted, games, counts)
    }

    fun scan(root: File): Library {
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return Library.Empty.copy(root = root.path)
        if (dirs.isEmpty() && !PlatformCatalog.isKnown(root.name)) return Library.Empty.copy(root = root.path)
        val games = HashMap<String, List<Game>>()
        val platforms = dirs.mapNotNull { dir ->
            val platform = PlatformCatalog.forFolder(dir.name)
            val list = scanPlatform(dir, platform.id)
            if (list.isEmpty()) null else {
                games[platform.id] = list
                platform
            }
        }.toMutableList()
        // 根目录本身就是一个平台文件夹（如直接添加 /ROMs/gba）
        if (PlatformCatalog.isKnown(root.name) && platforms.isEmpty()) {
            val platform = PlatformCatalog.forFolder(root.name)
            val list = scanPlatform(root, platform.id)
            if (list.isNotEmpty()) {
                games[platform.id] = list
                platforms += platform
            }
        }
        platforms.sortWith(compareBy({ PlatformCatalog.sortKey(it.id) }, { it.id }))
        return Library(root.path, platforms, games)
    }

    private fun scanPlatform(dir: File, platformId: String): List<Game> {
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: return emptyList()
        val covers = HashMap<String, String>()
        fun collectCovers(d: File) = d.listFiles()?.forEach { f ->
            if (f.isFile && f.extension.lowercase() in IMAGE_EXT) covers.putIfAbsent(f.nameWithoutExtension.lowercase(), f.path)
        }
        COVER_DIRS.forEach { collectCovers(File(dir, it)) }
        collectCovers(dir)

        var roms = files.filter { it.extension.isNotEmpty() && it.extension.lowercase() !in IGNORED_EXT }
        // 光盘游戏：有 cue/gdi/m3u 时，隐藏对应的轨道文件
        if (roms.any { it.extension.lowercase() in DISC_INDEX_EXT }) {
            roms = roms.filter { it.extension.lowercase() !in DISC_TRACK_EXT }
        }
        return roms.map { f ->
            val base = f.nameWithoutExtension
            Game(
                id = f.path,
                platformId = platformId,
                baseName = base,
                fileName = f.name,
                title = cleanTitle(base),
                localCover = covers[base.lowercase()],
                fileSize = f.length(),
                lastModifiedMs = f.lastModified(),
            )
        }.sortedBy { it.title.lowercase() }
    }

    private val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val ARTICLE = Regex("""^(.+?), (The|A|An)(\s+-\s+.*|\s*:.*)?$""")
    private val SPACES = Regex("""\s+""")

    /** "Legend of Zelda, The - Oracle of Ages (USA, Australia)" → "The Legend of Zelda - Oracle of Ages" */
    fun cleanTitle(base: String): String {
        var s = base.replace('_', ' ').replace(TAGS, "").trim()
        ARTICLE.matchEntire(s)?.let { m ->
            s = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
        }
        s = s.replace(SPACES, " ").trim()
        return s.ifEmpty { base }
    }
}

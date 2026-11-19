package com.pocketdeck.launcher.data

import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import java.io.File

/** 扫描 ROM 根目录：每个非空子文件夹是一个平台。只做一层目录遍历，几百个游戏几十毫秒。 */
object RomScanner {

    private val IGNORED_PLATFORM_DIRS = setOf(
        "images", "covers", "boxart", "media", "bios", "saves", "states", "cheats",
        "screenshots", "thumbnails", "themes", "fonts", "shaders", "overlays",
        "system", "config", "tmp", "temp", "cache", "downloads", "download",
    )
    private val IGNORED_EXT = setOf(
        // 图片
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "svg", "heic", "heif", "tiff", "tif", "psd", "raw",
        // 音频
        "mp3", "wav", "ogg", "flac", "aac", "m4a", "opus", "wma", "mid", "midi", "amr", "aiff", "ape", "alac",
        // 视频与字幕
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpg", "mpeg", "rmvb", "vob",
        "srt", "ass", "ssa", "vtt", "lrc",
        // 字体
        "ttf", "otf", "ttc", "woff", "woff2", "eot", "fon",
        // 文档、电子书与办公表格
        "txt", "md", "nfo", "pdf", "doc", "docx", "xls", "xlsx", "xlsm", "xlsb", "ppt", "pptx",
        "csv", "tsv", "rtf", "odt", "ods", "odp", "epub", "mobi", "azw3", "chm", "pages", "numbers", "key",
        // 脚本、代码、网页、配置与数据日志
        "js", "mjs", "cjs", "jsx", "ts", "tsx", "css", "scss", "less", "html", "htm", "xhtml", "php",
        "py", "pyc", "java", "kt", "kts", "c", "cpp", "cc", "h", "hpp", "rs", "go", "rb", "swift",
        "sh", "bash", "zsh", "bat", "cmd", "ps1", "xml", "json", "json5", "yaml", "yml", "toml",
        "cfg", "ini", "conf", "properties", "plist", "sql", "sqlite", "sqlite3", "db", "dat", "log",
        "bak", "old", "tmp", "temp", "part", "crdownload", "download", "lpl", "opt", "cht",
        "glsl", "slang", "glslp", "slangp", "inf", "url", "lnk", "desktop", "ds_store", "nomedia", "keep",
        // 安装包、系统镜像、证书校验与非 ROM 归档
        "dmg", "msi", "msp", "msix", "deb", "rpm", "appimage", "ipa", "xapk", "apks", "apk", "cab", "crx", "xpi",
        "pem", "crt", "cer", "der", "p12", "pfx", "pub", "asc", "sig", "sha1", "sha256", "md5", "sfv",
        "torrent", "m3u8", "pls", "xspf",
        "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "rar", "cbz", "cbr", "lzma", "zst",
        // 存档、补丁与光盘辅助文件
        "srm", "sav", "state", "auto", "mcr", "mcd", "vmp", "gci",
        "ips", "bps", "ups", "xdelta", "ppf", "sub", "sbi", "rtc",
    )
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")
    private val COVER_DIRS = listOf("images", "covers", "boxart", "media/covers", "media/box2dfront", "media/images")
    private val DISC_INDEX_EXT = setOf("cue", "gdi", "m3u", "ccd")
    private val DISC_TRACK_EXT = setOf("bin", "img", "iso")
    private val DISC_PLATFORMS = setOf(
        "psx", "ps2", "psp", "saturn", "dreamcast", "segacd", "pcenginecd", "neogeocd",
        "gamecube", "wii", "wiiu", "3do", "jaguarcd", "amigacd32", "pcfx", "cdi", "fmtowns",
    )

    fun isPotentialRomFile(file: File, platformId: String? = null): Boolean {
        if (!file.isFile || file.name.startsWith(".")) return false
        val ext = file.extension.lowercase()
        if (ext.isEmpty() || ext in IGNORED_EXT) return false
        if (ext == "m3u") {
            if (platformId != null && platformId !in DISC_PLATFORMS) return false
            return isValidDiscM3u(file)
        }
        return true
    }

    private fun isValidDiscM3u(file: File): Boolean {
        val parent = file.parentFile ?: return false
        val sample = runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(4096)
                val n = input.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            }
        }.getOrDefault("")
        if (sample.isBlank()) return false
        val lower = sample.lowercase()
        if ("#extm3u" in lower || "#extinf:" in lower ||
            "http://" in lower || "https://" in lower || "rtmp://" in lower || "rtsp://" in lower
        ) {
            return false
        }
        return sample.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .any { line -> File(parent, line).isFile }
    }

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
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty()
        val gamesByPlat = HashMap<String, LinkedHashMap<String, Game>>()
        val platformsById = LinkedHashMap<String, Platform>()
        fun addGames(plat: Platform, list: List<Game>) {
            if (list.isEmpty()) return
            val bucket = gamesByPlat.getOrPut(plat.id) { LinkedHashMap() }
            for (g in list) bucket.putIfAbsent(g.id, g)
            platformsById.putIfAbsent(plat.id, plat)
        }
        for (dir in dirs) {
            if (dir.name.lowercase() in IGNORED_PLATFORM_DIRS) continue
            val platform = PlatformCatalog.forFolder(dir.name)
            addGames(platform, scanPlatform(dir, platform.id))
        }
        // 根目录本身就是一个平台文件夹（如直接添加 /ROMs/gba）
        if (PlatformCatalog.isKnown(root.name)) {
            val platform = PlatformCatalog.forFolder(root.name)
            addGames(platform, scanPlatform(root, platform.id))
        } else {
            // 根目录（如 ~/PocketGames/roms）下直接放置的 ROM 文件也纳入扫描，按扩展名推断平台
            val rootGames = scanPlatform(root, "misc")
            for (g in rootGames) {
                val ext = g.fileName.substringAfterLast('.', "").lowercase()
                val inferredId = inferPlatformByExt(ext) ?: "misc"
                val plat = PlatformCatalog.forFolder(inferredId)
                val patched = if (g.platformId == plat.id) g else g.copy(platformId = plat.id)
                gamesByPlat.getOrPut(plat.id) { LinkedHashMap() }.putIfAbsent(patched.id, patched)
                platformsById.putIfAbsent(plat.id, plat)
            }
        }
        if (platformsById.isEmpty()) return Library.Empty.copy(root = root.path)
        val games = HashMap<String, List<Game>>(gamesByPlat.size * 2)
        for ((pid, map) in gamesByPlat) {
            games[pid] = map.values.sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title) }
        }
        val platforms = platformsById.values.sortedWith(compareBy({ PlatformCatalog.sortKey(it.id) }, { it.id }))
        return Library(root.path, platforms, games)
    }

    private fun inferPlatformByExt(ext: String): String? = when (ext) {
        "nes", "unf", "unif" -> "nes"
        "fds" -> "fds"
        "sfc", "smc", "fig", "swc" -> "snes"
        "gb" -> "gb"
        "gbc" -> "gbc"
        "gba" -> "gba"
        "nds" -> "nds"
        "n64", "z64", "v64" -> "n64"
        "md", "gen", "smd" -> "megadrive"
        "sms" -> "mastersystem"
        "gg" -> "gamegear"
        "pce" -> "pcengine"
        "3ds", "cia" -> "3ds"
        "nsp", "xci" -> "switch"
        "gcz", "rvz" -> "gamecube"
        "cso", "pbp" -> "psp"
        "ngp" -> "ngp"
        "ngc" -> "ngpc"
        "ws" -> "wswan"
        "wsc" -> "wswanc"
        else -> null
    }

    private fun scanPlatform(dir: File, platformId: String): List<Game> {
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: return emptyList()
        val covers = HashMap<String, String>()
        fun collectCovers(d: File) = d.listFiles()?.forEach { f ->
            if (f.isFile && f.extension.lowercase() in IMAGE_EXT) covers.putIfAbsent(f.nameWithoutExtension.lowercase(), f.path)
        }
        COVER_DIRS.forEach { collectCovers(File(dir, it)) }
        collectCovers(dir)

        var roms = files.filter { isPotentialRomFile(it, platformId) }
        // 光盘游戏：有 cue/gdi/m3u 时，仅隐藏被索引文件引用或同名前缀的轨道文件，避免误杀其他独立 iso/bin
        val indexFiles = roms.filter { it.extension.lowercase() in DISC_INDEX_EXT }
        if (indexFiles.isNotEmpty()) {
            val referencedNames = HashSet<String>()
            val indexBases = HashSet<String>()
            for (idxFile in indexFiles) {
                indexBases += idxFile.nameWithoutExtension.lowercase()
                runCatching {
                    idxFile.useLines { seq ->
                        seq.take(256).forEach { rawLine ->
                            val trimmed = rawLine.trim().removePrefix("\uFEFF")
                            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                            val quoted = Regex("\"([^\"]+)\"").find(trimmed)?.groupValues?.getOrNull(1)
                            val candidate = (quoted ?: trimmed.substringAfterLast(' '))
                                .replace('\\', '/')
                                .substringAfterLast('/')
                                .trim()
                            if (candidate.isNotEmpty()) referencedNames += candidate.lowercase()
                        }
                    }
                }
            }
            roms = roms.filter { f ->
                val ext = f.extension.lowercase()
                if (ext !in DISC_TRACK_EXT) return@filter true
                val lowerName = f.name.lowercase()
                if (lowerName in referencedNames) return@filter false
                val lowerBase = f.nameWithoutExtension.lowercase()
                val sameBase = indexBases.any { base ->
                    lowerBase == base || lowerBase.startsWith("$base (track") || lowerBase.startsWith("${base}_track")
                }
                !sameBase
            }
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

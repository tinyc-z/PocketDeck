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
    fun scanAll(roots: List<File>): Library = scanAllProgressive(roots, emptySet(), null)

    /**
     * 渐进式扫描多个根目录：
     * 1. 优先扫描 [priorityPlatformIds]（首个可见分类对应的平台目录），扫完后立即通过 [onProgress] 推送首屏结果；
     * 2. 其余平台目录在后台分批扫描并增量合并推送，大幅缩短海量游戏首次冷启动白屏时间。
     */
    fun scanAllProgressive(
        roots: List<File>,
        priorityPlatformIds: Set<String> = emptySet(),
        onProgress: ((Library) -> Unit)? = null,
    ): Library {
        val rootsKey = roots.joinToString(";") { it.path }
        val merged = HashMap<String, LinkedHashMap<String, Game>>()
        val platforms = LinkedHashMap<String, Platform>()
        val counts = LinkedHashMap<String, Int>()
        roots.forEach { counts[it.path] = 0 }

        fun addScanned(rootPath: String, plat: Platform, list: List<Game>) {
            if (list.isEmpty()) return
            val bucket = merged.getOrPut(plat.id) { LinkedHashMap() }
            var added = 0
            for (g in list) {
                if (bucket.putIfAbsent(g.id, g) == null) added++
            }
            platforms.putIfAbsent(plat.id, plat)
            counts[rootPath] = (counts[rootPath] ?: 0) + added
        }

        fun snapshot(): Library {
            val games = HashMap<String, List<Game>>(merged.size * 2)
            for ((pid, m) in merged) {
                games[pid] = m.values.sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title) }
            }
            val sortedPlats = platforms.values.sortedWith(compareBy({ PlatformCatalog.sortKey(it.id) }, { it.id }))
            return Library(rootsKey, sortedPlats, games, LinkedHashMap(counts))
        }

        // 先轻量枚举各根目录下的平台子文件夹，按优先级分桶
        val priorityTasks = ArrayList<Triple<File, File, Platform>>()
        val normalTasks = ArrayList<Triple<File, File, Platform>>()
        val rootMiscRoots = ArrayList<File>()

        for (root in roots) {
            val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty()
            for (dir in dirs) {
                if (dir.name.lowercase() in IGNORED_PLATFORM_DIRS) continue
                val plat = PlatformCatalog.forFolder(dir.name)
                val task = Triple(root, dir, plat)
                if (plat.id in priorityPlatformIds) priorityTasks += task else normalTasks += task
            }
            if (PlatformCatalog.isKnown(root.name)) {
                val plat = PlatformCatalog.forFolder(root.name)
                val task = Triple(root, root, plat)
                if (plat.id in priorityPlatformIds) priorityTasks += task else normalTasks += task
            } else {
                rootMiscRoots += root
            }
        }

        // 阶段 1：优先扫完首个标签页对应的平台目录，立刻上屏
        if (priorityTasks.isNotEmpty()) {
            for ((root, dir, plat) in priorityTasks) {
                addScanned(root.path, plat, scanPlatform(dir, plat.id))
            }
            if (onProgress != null && merged.isNotEmpty()) {
                onProgress(snapshot())
            }
        }

        // 阶段 2：其余平台目录按顺序分批扫描，每 4 个平台推送一次进度
        var batchCount = 0
        for ((root, dir, plat) in normalTasks) {
            addScanned(root.path, plat, scanPlatform(dir, plat.id))
            batchCount++
            if (onProgress != null && batchCount % 4 == 0 && merged.isNotEmpty()) {
                onProgress(snapshot())
            }
        }

        // 阶段 3：扫描直接散落在根目录下的 ROM 文件
        for (root in rootMiscRoots) {
            val rootGames = scanPlatform(root, "misc")
            for (g in rootGames) {
                val ext = g.fileName.substringAfterLast('.', "").lowercase()
                val inferredId = inferPlatformByExt(ext) ?: "misc"
                val plat = PlatformCatalog.forFolder(inferredId)
                val patched = if (g.platformId == plat.id) g else g.copy(platformId = plat.id)
                addScanned(root.path, plat, listOf(patched))
            }
        }

        return snapshot()
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

    /** "Legend of Zelda, The - Oracle of Ages (USA, Australia)" → "The Legend of Zelda - Oracle of Ages" */
    fun cleanTitle(base: String): String = TitleNormalizer.stripRomTags(base)
}

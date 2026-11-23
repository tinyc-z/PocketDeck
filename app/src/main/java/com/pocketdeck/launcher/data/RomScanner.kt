package com.pocketdeck.launcher.data

import android.util.Xml
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.lan.WebFileSecurity
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import javax.xml.parsers.SAXParserFactory

/** 扫描 ROM 根目录：支持多盘自动折叠、`.m3u` 解析、ES-DE/Daijisho/Pegasus 本地媒体与 `gamelist.xml` 导入及目录级增量复用。 */
object RomScanner {

    private val IGNORED_PLATFORM_DIRS = setOf(
        "images", "covers", "boxart", "media", "bios", "saves", "states", "cheats",
        "screenshots", "thumbnails", "themes", "fonts", "shaders", "overlays",
        "system", "config", "tmp", "temp", "cache", "downloads", "download",
        "downloaded_images", "downloaded_media", "gamelists",
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
    private val COVER_DIRS = listOf(
        "media/covers", "media/box2dfront", "media/images",
        "downloaded_images", "images", "covers", "boxart",
    )
    private val DISC_INDEX_EXT = setOf("cue", "gdi", "m3u", "ccd")
    private val DISC_TRACK_EXT = setOf("bin", "img", "iso")
    private val DISC_PLATFORMS = setOf(
        "psx", "ps2", "psp", "saturn", "dreamcast", "segacd", "pcenginecd", "neogeocd",
        "gamecube", "wii", "wiiu", "3do", "jaguarcd", "amigacd32", "pcfx", "cdi", "fmtowns",
    )

    private val DISC_TAG_REGEX = Regex(
        """(?i)[\s._-]*(?:\(\s*(?:dis[ck]|cd)\s*([0-9]+|[a-d])\s*(?:of\s*\d+)?\s*\)|\[\s*(?:dis[ck]|cd)\s*([0-9]+|[a-d])\s*\]|(?:dis[ck]|cd)\s*([0-9]+)(?=$|[\s._\-\(\[])|(?:光盘|碟|盘)\s*([0-9]+))""",
    )
    private val ILLEGAL_FILE_CHARS_REGEX = Regex("""[\\/:*?"<>|]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val QUOTED_PATH_REGEX = Regex("\"([^\"]+)\"")
    private val PARENT_MEDIA_TOP_DIRS = setOf("media", "downloaded_media", "images", "boxart", "covers", "gamelists")

    private data class LocalGamelistEntry(
        val coverPath: String? = null,
        val metadata: GameMetadata? = null,
    )

    private data class PlatformScanResult(
        val games: List<Game>,
        val localMetadata: Map<String, GameMetadata>,
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

    /** 为多盘游戏在同目录生成 `.m3u` 换盘索引文件（供 RetroArch / DuckStation 等模拟器无缝换盘）。 */
    fun generateM3uForGame(game: Game): Result<File> = runCatching {
        val discs = game.discFiles.ifEmpty { listOf(game.id) }
        require(discs.size > 1) { "Not a multi-disc game" }
        val firstFile = File(discs.first())
        val parent = firstFile.parentFile ?: error("Missing parent directory")
        val base = extractDiscInfo(firstFile.nameWithoutExtension)?.first?.takeIf { it.isNotBlank() }
            ?: game.title.replace(ILLEGAL_FILE_CHARS_REGEX, "_").trim().ifEmpty { firstFile.nameWithoutExtension }
        val m3uFile = File(parent, "$base.m3u")
        val lines = discs.map { path ->
            val f = File(path)
            if (f.parentFile?.path == parent.path) f.name else f.path
        }
        m3uFile.writeText(lines.joinToString("\n") + "\n")
        m3uFile
    }

    /** 从文件名（不含扩展名）提取 `(baseWithoutDiscTag, discOrder)`；非多盘返回 null。 */
    internal fun extractDiscInfo(baseName: String): Pair<String, Int>? {
        val m = DISC_TAG_REGEX.find(baseName) ?: return null
        val rawNum = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
        val order = rawNum.toIntOrNull() ?: when (rawNum.lowercase()) {
            "a" -> 1
            "b" -> 2
            "c" -> 3
            "d" -> 4
            else -> return null
        }
        val stripped = (baseName.substring(0, m.range.first) + baseName.substring(m.range.last + 1))
            .replace(WHITESPACE_REGEX, " ")
            .trim(' ', '-', '_', '.')
        if (stripped.isEmpty()) return null
        return stripped to order
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
            .any { line ->
                val target = File(parent, line)
                target.isFile && WebFileSecurity.isInsideDir(target, parent)
            }
    }

    /** 常见的 ROM 根目录，按顺序取第一个存在的。 */
    fun defaultRoots(sd: File): List<File> = listOf(
        File(sd, "PocketGames/roms"),
        File(sd, "ROMs"),
        File(sd, "Roms"),
        File(sd, "roms"),
    )

    /** 扫描多个根目录并合并：同一平台出现在多个目录时游戏合并到一个标签。 */
    fun scanAll(roots: List<File>, previousLibrary: Library? = null): Library =
        scanAllProgressive(roots, emptySet(), previousLibrary, null)

    /**
     * 渐进式 + 目录级增量扫描多个根目录：
     * 1. 未变化的平台目录（`lastModified` 与文件数一致）直接复用 [previousLibrary] 缓存，极大加速大库刷新；
     * 2. 优先扫描 [priorityPlatformIds]（首个可见分类对应的平台目录），扫完后立即通过 [onProgress] 推送首屏结果；
     * 3. 其余平台目录在后台分批扫描并增量合并推送。
     */
    fun scanAllProgressive(
        roots: List<File>,
        priorityPlatformIds: Set<String> = emptySet(),
        previousLibrary: Library? = null,
        onProgress: ((Library) -> Unit)? = null,
    ): Library {
        val rootsKey = roots.joinToString(";") { it.path }
        val merged = HashMap<String, LinkedHashMap<String, Game>>()
        val platforms = LinkedHashMap<String, Platform>()
        val counts = LinkedHashMap<String, Int>()
        val localMeta = HashMap<String, GameMetadata>()
        val dirSnapshots = HashMap<String, Pair<Long, Int>>()
        val parentEntriesCache = HashMap<String, Set<String>>()
        roots.forEach { counts[it.path] = 0 }

        fun addScanned(rootPath: String, plat: Platform, res: PlatformScanResult) {
            if (res.games.isEmpty()) return
            val bucket = merged.getOrPut(plat.id) { LinkedHashMap() }
            var added = 0
            for (g in res.games) {
                if (bucket.putIfAbsent(g.id, g) == null) added++
            }
            localMeta.putAll(res.localMetadata)
            platforms.putIfAbsent(plat.id, plat)
            counts[rootPath] = (counts[rootPath] ?: 0) + added
        }

        fun scanOrReuse(dir: File, plat: Platform): PlatformScanResult {
            val snap = computeDirSnapshot(dir, parentEntriesCache)
            dirSnapshots[dir.path] = snap
            if (previousLibrary != null && previousLibrary.dirSnapshots[dir.path] == snap) {
                val prefix = dir.path + File.separator
                val cachedGames = previousLibrary.games[plat.id].orEmpty()
                    .filter { it.id.startsWith(prefix) && it.id.indexOf(File.separatorChar, prefix.length) < 0 }
                val cachedMeta = HashMap<String, GameMetadata>(cachedGames.size)
                for (g in cachedGames) {
                    previousLibrary.localMetadata[g.id]?.let { cachedMeta[g.id] = it }
                }
                return PlatformScanResult(cachedGames, cachedMeta)
            }
            return scanPlatform(dir, plat.id)
        }

        fun snapshot(): Library {
            val games = HashMap<String, List<Game>>(merged.size * 2)
            val gameOrder = Comparator<Game> { a, b ->
                val cmp = String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                if (cmp != 0) cmp else a.id.compareTo(b.id)
            }
            for ((pid, m) in merged) {
                games[pid] = m.values.sortedWith(gameOrder)
            }
            val sortedPlats = platforms.values.sortedWith(compareBy({ PlatformCatalog.sortKey(it.id) }, { it.id }))
            return Library(
                root = rootsKey,
                platforms = sortedPlats,
                games = games,
                rootCounts = LinkedHashMap(counts),
                localMetadata = HashMap(localMeta),
                dirSnapshots = HashMap(dirSnapshots),
            )
        }

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
                addScanned(root.path, plat, scanOrReuse(dir, plat))
            }
            if (onProgress != null && merged.isNotEmpty()) {
                onProgress(snapshot())
            }
        }

        // 阶段 2：其余平台目录按顺序分批扫描，每 4 个平台推送一次进度
        var batchCount = 0
        for ((root, dir, plat) in normalTasks) {
            addScanned(root.path, plat, scanOrReuse(dir, plat))
            batchCount++
            if (onProgress != null && batchCount % 4 == 0 && merged.isNotEmpty()) {
                onProgress(snapshot())
            }
        }

        // 阶段 3：扫描直接散落在根目录下的 ROM 文件（同样纳入目录快照以支持增量复用）
        for (root in rootMiscRoots) {
            val snap = computeDirSnapshot(root, parentEntriesCache)
            dirSnapshots[root.path] = snap
            if (previousLibrary != null && previousLibrary.dirSnapshots[root.path] == snap) {
                val prefix = root.path + File.separator
                for ((pid, cachedList) in previousLibrary.games) {
                    val directGames = cachedList.filter {
                        it.id.startsWith(prefix) && it.id.indexOf(File.separatorChar, prefix.length) < 0
                    }
                    if (directGames.isEmpty()) continue
                    val plat = previousLibrary.platforms.firstOrNull { it.id == pid }
                        ?: PlatformCatalog.forFolder(pid)
                    val metaMap = HashMap<String, GameMetadata>(directGames.size)
                    for (g in directGames) {
                        previousLibrary.localMetadata[g.id]?.let { metaMap[g.id] = it }
                    }
                    addScanned(root.path, plat, PlatformScanResult(directGames, metaMap))
                }
                continue
            }
            val rootRes = scanPlatform(root, "misc")
            for (g in rootRes.games) {
                val ext = g.fileName.substringAfterLast('.', "").lowercase()
                val inferredId = inferPlatformByExt(ext) ?: "misc"
                val plat = PlatformCatalog.forFolder(inferredId)
                val patched = if (g.platformId == plat.id) g else g.copy(platformId = plat.id)
                val metaMap = rootRes.localMetadata[g.id]?.let { mapOf(patched.id to it) }.orEmpty()
                addScanned(root.path, plat, PlatformScanResult(listOf(patched), metaMap))
            }
        }

        return snapshot()
    }

    fun scan(root: File): Library = scanAll(listOf(root))

    /**
     * 计算目录增量快照：覆盖主目录本身、内置/外置 `gamelist.xml` 以及全部本地与上级封面子目录的 mtime 与条目数，
     * 避免新增封面或更新外置 `gamelist.xml` 时因主目录 mtime 不变而被误判为未变更。
     */
    internal fun computeDirSnapshot(
        dir: File,
        parentEntriesCache: MutableMap<String, Set<String>> = HashMap(),
    ): Pair<Long, Int> {
        val entries = dir.list()
        val count = entries?.size ?: 0
        var hash = 17L
        fun mix(value: Long) {
            hash = hash * 31L + value
        }
        fun mixFile(f: File, tag: Long) {
            if (f.isFile) {
                mix(tag)
                mix(f.lastModified())
                mix(f.length())
            }
        }
        fun mixCoverDir(d: File, tag: Long) {
            if (d.isDirectory) {
                mix(tag)
                mix(d.lastModified())
                mix((d.list()?.size ?: 0).toLong())
            }
        }

        mix(dir.lastModified())
        mix(count.toLong())

        val entrySet = if (!entries.isNullOrEmpty()) entries.mapTo(HashSet(entries.size)) { it.lowercase() } else emptySet()
        if ("gamelist.xml" in entrySet) {
            mixFile(File(dir, "gamelist.xml"), 101L)
        }
        if ("media" in entrySet) {
            mixCoverDir(File(dir, "media/covers"), 201L)
            mixCoverDir(File(dir, "media/box2dfront"), 202L)
            mixCoverDir(File(dir, "media/images"), 203L)
        }
        if ("downloaded_images" in entrySet) mixCoverDir(File(dir, "downloaded_images"), 204L)
        if ("images" in entrySet) mixCoverDir(File(dir, "images"), 205L)
        if ("covers" in entrySet) mixCoverDir(File(dir, "covers"), 206L)
        if ("boxart" in entrySet) mixCoverDir(File(dir, "boxart"), 207L)

        val parent = dir.parentFile
        if (parent != null) {
            val parentPresent = parentEntriesCache.getOrPut(parent.path) {
                parent.list()?.asSequence()
                    ?.map { it.lowercase() }
                    ?.filterTo(HashSet()) { it in PARENT_MEDIA_TOP_DIRS }
                    ?: emptySet()
            }
            if (parentPresent.isNotEmpty()) {
                val folderName = dir.name
                if ("gamelists" in parentPresent) {
                    mixFile(File(parent, "gamelists/$folderName/gamelist.xml"), 102L)
                }
                if ("media" in parentPresent) {
                    mixCoverDir(File(parent, "media/$folderName/covers"), 301L)
                    mixCoverDir(File(parent, "media/$folderName/box2dfront"), 302L)
                }
                if ("downloaded_media" in parentPresent) {
                    mixCoverDir(File(parent, "downloaded_media/$folderName/covers"), 303L)
                    mixCoverDir(File(parent, "downloaded_media/$folderName/box2dfront"), 304L)
                }
                if ("images" in parentPresent) mixCoverDir(File(parent, "images/$folderName"), 305L)
                if ("boxart" in parentPresent) mixCoverDir(File(parent, "boxart/$folderName"), 306L)
                if ("covers" in parentPresent) mixCoverDir(File(parent, "covers/$folderName"), 307L)
            }
        }
        return hash to count
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

    private fun scanPlatform(dir: File, platformId: String): PlatformScanResult {
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: return PlatformScanResult(emptyList(), emptyMap())
        val covers = HashMap<String, String>()
        fun collectCovers(d: File) {
            if (!d.isDirectory) return
            d.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() in IMAGE_EXT) {
                    val key = f.nameWithoutExtension.lowercase()
                    covers.putIfAbsent(key, f.path)
                    // 兼容 ES-DE / Daijisho `-image` / `-thumb` / `-boxart` 后缀命名
                    for (suffix in listOf("-image", "-thumb", "-boxart", "-cover")) {
                        if (key.endsWith(suffix)) {
                            covers.putIfAbsent(key.removeSuffix(suffix), f.path)
                        }
                    }
                }
            }
        }
        // 本目录内常见媒体目录（ES-DE / Daijisho / Pegasus / RetroArch）
        COVER_DIRS.forEach { collectCovers(File(dir, it)) }
        // 上级/同级 ES-DE / Daijisho 媒体目录
        dir.parentFile?.let { parent ->
            val folderName = dir.name
            listOf(
                File(parent, "media/$folderName/covers"),
                File(parent, "media/$folderName/box2dfront"),
                File(parent, "downloaded_media/$folderName/covers"),
                File(parent, "downloaded_media/$folderName/box2dfront"),
                File(parent, "images/$folderName"),
                File(parent, "boxart/$folderName"),
                File(parent, "covers/$folderName"),
            ).forEach { collectCovers(it) }
        }
        collectCovers(dir)

        // 解析 ES-DE / EmulationStation `gamelist.xml`
        val gamelistEntries = parseGamelistXml(dir)

        var roms = files.filter { isPotentialRomFile(it, platformId) }

        // 1) `.m3u` 多盘索引：记录每个 `.m3u` 引用的光盘文件并隐藏所有被引用的子盘文件（无论扩展名）
        val m3uFiles = roms.filter { it.extension.lowercase() == "m3u" }
        val m3uDiscMap = HashMap<String, List<String>>()
        if (m3uFiles.isNotEmpty()) {
            val m3uReferencedNames = HashSet<String>()
            for (m3u in m3uFiles) {
                val refs = ArrayList<String>()
                runCatching {
                    m3u.useLines { seq ->
                        seq.take(128).forEach { rawLine ->
                            val line = rawLine.trim().removePrefix("\uFEFF")
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val target = File(dir, line)
                            if (!WebFileSecurity.isInsideDir(target, dir)) return@forEach
                            if (target.isFile) {
                                refs += target.path
                                m3uReferencedNames += target.name.lowercase()
                            } else {
                                m3uReferencedNames += line.replace('\\', '/').substringAfterLast('/').lowercase()
                            }
                        }
                    }
                }
                if (refs.size > 1) {
                    m3uDiscMap[m3u.path] = refs
                }
            }
            if (m3uReferencedNames.isNotEmpty()) {
                roms = roms.filter { f ->
                    f.extension.lowercase() == "m3u" || f.name.lowercase() !in m3uReferencedNames
                }
            }
        }

        // 2) 光盘轨道文件：有 cue/gdi/m3u/ccd 时，隐藏被引用的 bin/img/iso 轨道文件
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
                            val quoted = QUOTED_PATH_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
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

        // 3) 多盘自动折叠：同基名 + 不同盘号（Disc 1/2, CD1/2, 光盘1/2 等）自动折叠为单条 Game
        val multiDiscGroups = LinkedHashMap<String, MutableList<Pair<Int, File>>>()
        val singles = ArrayList<File>()
        for (f in roms) {
            if (f.extension.lowercase() == "m3u") {
                singles += f
                continue
            }
            val discInfo = extractDiscInfo(f.nameWithoutExtension)
            if (discInfo != null) {
                val key = "${discInfo.first.lowercase()}|${f.extension.lowercase()}"
                multiDiscGroups.getOrPut(key) { ArrayList() }.add(discInfo.second to f)
            } else {
                singles += f
            }
        }

        val outGames = ArrayList<Game>(roms.size)
        val outLocalMeta = HashMap<String, GameMetadata>()

        fun buildGame(primary: File, displayBase: String, discs: List<String>) {
            val glEntry = gamelistEntries[primary.name.lowercase()]
                ?: gamelistEntries[displayBase.lowercase()]
            val cover = glEntry?.coverPath
                ?: covers[displayBase.lowercase()]
                ?: covers[primary.nameWithoutExtension.lowercase()]
            val totalSize = if (discs.size > 1) {
                discs.sumOf { File(it).length() }.takeIf { it > 0L } ?: primary.length()
            } else {
                primary.length()
            }
            val game = Game(
                id = primary.path,
                platformId = platformId,
                baseName = displayBase,
                fileName = primary.name,
                title = cleanTitle(displayBase),
                localCover = cover,
                fileSize = totalSize,
                lastModifiedMs = primary.lastModified(),
                discFiles = discs,
            )
            outGames += game
            glEntry?.metadata?.takeIf { !it.isEmpty }?.let { outLocalMeta[game.id] = it }
        }

        for (f in singles) {
            val discs = m3uDiscMap[f.path].orEmpty()
            buildGame(f, f.nameWithoutExtension, discs)
        }
        for ((_, group) in multiDiscGroups) {
            if (group.size >= 2 && group.map { it.first }.toSet().size >= 2) {
                val sorted = group.sortedBy { it.first }
                val primary = sorted.first().second
                val cleanBase = extractDiscInfo(primary.nameWithoutExtension)?.first ?: primary.nameWithoutExtension
                buildGame(primary, cleanBase, sorted.map { it.second.path })
            } else {
                for ((_, f) in group) {
                    buildGame(f, f.nameWithoutExtension, emptyList())
                }
            }
        }

        return PlatformScanResult(
            games = outGames.sortedBy { it.title.lowercase() },
            localMetadata = outLocalMeta,
        )
    }

    /** 流式解析 ES-DE / EmulationStation 的 `gamelist.xml`（零 DOM 建树），返回 `fileNameLower -> LocalGamelistEntry`。 */
    private fun parseGamelistXml(dir: File): Map<String, LocalGamelistEntry> {
        val candidates = listOfNotNull(
            File(dir, "gamelist.xml"),
            dir.parentFile?.let { File(it, "gamelists/${dir.name}/gamelist.xml") },
        )
        val xmlFile = candidates.firstOrNull { it.isFile && it.length() in 1L..16L * 1024 * 1024 }
            ?: return emptyMap()

        val result = HashMap<String, LocalGamelistEntry>()
        fun recordGame(
            rawPath: String?,
            image: String?,
            thumbnail: String?,
            releaseRaw: String?,
            genre: String?,
            developer: String?,
            publisher: String?,
            desc: String?,
        ) {
            val fileName = rawPath?.replace('\\', '/')?.substringAfterLast('/')?.trim() ?: return
            if (fileName.isEmpty()) return
            val imgRel = image ?: thumbnail
            val resolvedCover = imgRel?.let { rel ->
                val cleaned = rel.removePrefix("./").replace('\\', '/')
                val f = if (File(cleaned).isAbsolute) File(cleaned) else File(dir, cleaned)
                f.takeIf { it.isFile }?.path
            }
            val meta = GameMetadata(
                releaseDate = formatGamelistDate(releaseRaw),
                genre = genre,
                developer = developer,
                publisher = publisher,
                description = desc,
            )
            val entry = LocalGamelistEntry(
                coverPath = resolvedCover,
                metadata = meta.takeIf { !it.isEmpty },
            )
            result[fileName.lowercase()] = entry
            val noExt = fileName.substringBeforeLast('.', "").lowercase()
            if (noExt.isNotEmpty()) {
                result.putIfAbsent(noExt, entry)
            }
        }

        val pullParser = createXmlPullParser()
        if (pullParser != null) {
            runCatching {
                BufferedInputStream(xmlFile.inputStream(), 16 * 1024).use { input ->
                    pullParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    runCatching { pullParser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
                    pullParser.setInput(input, "UTF-8")

                    var inGame = false
                    var gameDepth = 0
                    var activeField: String? = null
                    val buf = StringBuilder(128)
                    var rawPath: String? = null
                    var image: String? = null
                    var thumbnail: String? = null
                    var releaseRaw: String? = null
                    var genre: String? = null
                    var developer: String? = null
                    var publisher: String? = null
                    var desc: String? = null

                    var event = pullParser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                val tag = pullParser.name
                                if (!inGame) {
                                    if (tag.equals("game", ignoreCase = true)) {
                                        inGame = true
                                        gameDepth = 0
                                        activeField = null
                                        rawPath = null
                                        image = null
                                        thumbnail = null
                                        releaseRaw = null
                                        genre = null
                                        developer = null
                                        publisher = null
                                        desc = null
                                    }
                                } else {
                                    gameDepth++
                                    if (gameDepth == 1) {
                                        val lower = tag.lowercase()
                                        if (lower in GAMELIST_FIELDS) {
                                            activeField = lower
                                            buf.setLength(0)
                                        }
                                    }
                                }
                            }
                            XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                                if (inGame && gameDepth == 1 && activeField != null) {
                                    pullParser.text?.let { buf.append(it) }
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (inGame) {
                                    if (gameDepth == 0) {
                                        recordGame(rawPath, image, thumbnail, releaseRaw, genre, developer, publisher, desc)
                                        inGame = false
                                    } else {
                                        if (gameDepth == 1 && activeField != null) {
                                            val value = buf.toString().trim().takeIf { it.isNotEmpty() }
                                            when (activeField) {
                                                "path" -> if (rawPath == null) rawPath = value
                                                "image" -> if (image == null) image = value
                                                "thumbnail" -> if (thumbnail == null) thumbnail = value
                                                "releasedate" -> if (releaseRaw == null) releaseRaw = value
                                                "genre" -> if (genre == null) genre = value
                                                "developer" -> if (developer == null) developer = value
                                                "publisher" -> if (publisher == null) publisher = value
                                                "desc" -> if (desc == null) desc = value
                                            }
                                            activeField = null
                                        }
                                        gameDepth--
                                    }
                                }
                            }
                        }
                        event = pullParser.next()
                    }
                }
            }
            return result
        }

        // 纯 JVM 单元测试环境无 Android XmlPullParser 实现时退回同样零 DOM 的 SAX 流式解析
        runCatching {
            val saxFactory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            BufferedInputStream(xmlFile.inputStream(), 16 * 1024).use { input ->
                saxFactory.newSAXParser().parse(
                    input,
                    object : DefaultHandler() {
                        private var inGame = false
                        private var gameDepth = 0
                        private var activeField: String? = null
                        private val buf = StringBuilder(128)
                        private var rawPath: String? = null
                        private var image: String? = null
                        private var thumbnail: String? = null
                        private var releaseRaw: String? = null
                        private var genre: String? = null
                        private var developer: String? = null
                        private var publisher: String? = null
                        private var desc: String? = null

                        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                            val tag = qName.orEmpty()
                            if (!inGame) {
                                if (tag.equals("game", ignoreCase = true)) {
                                    inGame = true
                                    gameDepth = 0
                                    activeField = null
                                    rawPath = null
                                    image = null
                                    thumbnail = null
                                    releaseRaw = null
                                    genre = null
                                    developer = null
                                    publisher = null
                                    desc = null
                                }
                            } else {
                                gameDepth++
                                if (gameDepth == 1) {
                                    val lower = tag.lowercase()
                                    if (lower in GAMELIST_FIELDS) {
                                        activeField = lower
                                        buf.setLength(0)
                                    }
                                }
                            }
                        }

                        override fun characters(ch: CharArray?, start: Int, length: Int) {
                            if (inGame && gameDepth == 1 && activeField != null && ch != null && length > 0) {
                                buf.append(ch, start, length)
                            }
                        }

                        override fun endElement(uri: String?, localName: String?, qName: String?) {
                            if (!inGame) return
                            if (gameDepth == 0) {
                                recordGame(rawPath, image, thumbnail, releaseRaw, genre, developer, publisher, desc)
                                inGame = false
                            } else {
                                if (gameDepth == 1 && activeField != null) {
                                    val value = buf.toString().trim().takeIf { it.isNotEmpty() }
                                    when (activeField) {
                                        "path" -> if (rawPath == null) rawPath = value
                                        "image" -> if (image == null) image = value
                                        "thumbnail" -> if (thumbnail == null) thumbnail = value
                                        "releasedate" -> if (releaseRaw == null) releaseRaw = value
                                        "genre" -> if (genre == null) genre = value
                                        "developer" -> if (developer == null) developer = value
                                        "publisher" -> if (publisher == null) publisher = value
                                        "desc" -> if (desc == null) desc = value
                                    }
                                    activeField = null
                                }
                                gameDepth--
                            }
                        }
                    },
                )
            }
        }
        return result
    }

    private fun createXmlPullParser(): XmlPullParser? =
        runCatching { Xml.newPullParser() }.getOrNull()
            ?: runCatching { XmlPullParserFactory.newInstance().newPullParser() }.getOrNull()

    private val GAMELIST_FIELDS = setOf(
        "path", "image", "thumbnail", "releasedate", "genre", "developer", "publisher", "desc",
    )

    private fun formatGamelistDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.takeWhile { it.isDigit() }
        return when {
            digits.length >= 8 -> "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
            digits.length >= 4 -> digits.substring(0, 4)
            else -> raw
        }
    }

    /** "Legend of Zelda, The - Oracle of Ages (USA, Australia)" → "The Legend of Zelda - Oracle of Ages" */
    fun cleanTitle(base: String): String = TitleNormalizer.stripRomTags(base)
}

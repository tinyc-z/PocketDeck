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

    private const val MAX_SUBDIR_DEPTH = 3
    private const val MAX_SNAPSHOT_SUBDIRS = 256
    private const val SNAPSHOT_HASH_SEED = 37L

    private val IGNORED_PLATFORM_DIRS = setOf(
        "images", "imgs", "img", "covers", "boxart", "box2dfront", "boxfront", "box3d", "3dboxes",
        "miximages", "mix", "media", "bios", "saves", "states", "cheats",
        "screenshots", "thumbnails", "themes", "fonts", "shaders", "overlays",
        "videos", "video", "manuals", "manual", "marquees", "marquee", "fanart",
        "titles", "snaps", "wheel", "logos", "artwork", "art",
        "system", "config", "tmp", "temp", "cache", "downloads", "download",
        "downloaded_images", "downloaded_media", "gamelists",
        "__macosx", "lost+found", "android",
    )
    private val NON_RECURSIVE_PLATFORMS = setOf(
        "dos", "scummvm", "easyrpg", "ps3", "wiiu", "winlator", "ports", "openbor",
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
        // 存档、补丁与光盘/自制程序辅助文件
        "srm", "sav", "state", "auto", "mcr", "mcd", "vmp", "gci",
        "ips", "bps", "ups", "xdelta", "ppf", "sub", "sbi", "rtc", "csz",
    )
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")
    private val BOXART_COVER_DIRS = listOf(
        "media/covers", "media/box2dfront", "media/boxfront", "media/boxart", "media/3dboxes",
        "covers", "boxart", "imgs", "images", "downloaded_images", "media/images",
    )
    private val FALLBACK_COVER_DIRS = listOf(
        "media/miximages", "media/mix", "media/screenshots", "screenshots",
    )
    private val COVER_SUFFIXES = listOf(
        "-image", "-thumb", "-boxart", "-cover", "-mix", "-3dbox", "-box2d", "-boxfront", "-screenshot",
    )
    private val PEGASUS_MEDIA_BUCKET_DIRS = setOf(
        "covers", "box2dfront", "boxfront", "boxart", "3dboxes", "box3d", "images",
        "miximages", "mix", "screenshots", "videos", "video", "marquees", "marquee",
        "fanart", "titles", "snaps", "wheel", "manuals", "manual", "logos",
    )
    private val SINGLE_GAME_BOXART_NAMES = listOf(
        "boxfront", "box_front", "box2dfront", "boxart", "cover", "front", "box", "folder",
    )
    private val SINGLE_GAME_FALLBACK_NAMES = listOf(
        "mix", "miximage", "screenshot", "titlescreen", "title", "logo",
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
    private val PARENT_MEDIA_TOP_DIRS = setOf(
        "media", "downloaded_media", "images", "imgs", "boxart", "covers", "screenshots", "gamelists",
    )
    private val FAST_SKIP_DIR_CHECK_EXT = setOf(
        "nes", "unf", "unif", "fds", "sfc", "smc", "fig", "swc", "gb", "gbc", "gba",
        "nds", "dsi", "3ds", "cia", "n64", "z64", "v64", "md", "gen", "smd", "sms", "gg",
        "pce", "ngp", "ngc", "ws", "wsc", "chd", "iso", "cso", "pbp", "cue", "bin", "img",
        "gdi", "ccd", "m3u", "rvz", "gcz", "wbfs", "nsp", "xci", "zip", "7z", "rar",
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "mp4", "mkv", "mp3", "wav", "ogg",
        "sav", "srm", "state", "xml", "txt", "json", "cfg", "ini", "log", "dat", "bak",
        "dmg", "apk", "pdf",
    )

    private data class PlatformScanResult(
        val games: List<Game>,
        val localMetadata: Map<String, GameMetadata>,
    )

    fun isPotentialRomFile(file: File, platformId: String? = null): Boolean =
        isPotentialRomFileInternal(file, platformId, file.parentFile)

    private fun isPotentialRomFileInternal(
        file: File,
        platformId: String? = null,
        m3uBoundaryDir: File? = file.parentFile,
    ): Boolean {
        if (!file.isFile || file.name.startsWith(".")) return false
        val ext = file.extension.lowercase()
        if (ext.isEmpty() || ext in IGNORED_EXT) return false
        if (ext == "m3u") {
            if (platformId != null && platformId !in DISC_PLATFORMS) return false
            return isValidDiscM3u(file, m3uBoundaryDir)
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

    private fun isValidDiscM3u(file: File, boundaryDir: File? = file.parentFile): Boolean {
        val parent = file.parentFile ?: return false
        val boundary = boundaryDir ?: parent
        val sample = runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(4096)
                val n = input.read(buf)
                if (n <= 0) "" else ArchiveManager.decodeTextBytes(buf, n, truncated = true).text
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
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .any { line ->
                val target = File(parent, line)
                target.isFile && WebFileSecurity.isInsideDir(target, boundary)
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
        val parentEntriesCache = HashMap<String, Map<String, String>>()
        val prevAllGames = previousLibrary?.games?.values?.flatten().orEmpty()
        roots.forEach { counts[it.path] = 0 }

        fun addScanned(rootPath: String, defaultPlat: Platform, res: PlatformScanResult) {
            if (res.games.isEmpty()) return
            var added = 0
            for (g in res.games) {
                val gPlat = if (g.platformId == defaultPlat.id) defaultPlat else PlatformCatalog.forFolder(g.platformId)
                val bucket = merged.getOrPut(gPlat.id) { LinkedHashMap() }
                if (bucket.putIfAbsent(g.id, g) == null) added++
                platforms.putIfAbsent(gPlat.id, gPlat)
            }
            localMeta.putAll(res.localMetadata)
            counts[rootPath] = (counts[rootPath] ?: 0) + added
        }

        fun scanOrReuse(dir: File, plat: Platform): PlatformScanResult {
            val allowSubdirs = plat.id !in NON_RECURSIVE_PLATFORMS
            val snap = computeDirSnapshot(dir, parentEntriesCache, allowSubdirs = allowSubdirs)
            dirSnapshots[dir.path] = snap
            if (previousLibrary != null && previousLibrary.dirSnapshots[dir.path] == snap) {
                val prefix = dir.path + File.separator
                val cachedGames = prevAllGames.filter { game ->
                    if (allowSubdirs) {
                        game.id.startsWith(prefix)
                    } else {
                        game.id.startsWith(prefix) && game.id.indexOf(File.separatorChar, prefix.length) < 0
                    }
                }
                val cachedMeta = HashMap<String, GameMetadata>(cachedGames.size)
                for (g in cachedGames) {
                    previousLibrary.localMetadata[g.id]?.let { cachedMeta[g.id] = it }
                }
                return PlatformScanResult(cachedGames, cachedMeta)
            }
            val maxDepth = if (allowSubdirs) MAX_SUBDIR_DEPTH else 0
            return scanPlatform(dir, plat.id, maxDepth = maxDepth)
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
            val snap = computeDirSnapshot(root, parentEntriesCache, allowSubdirs = false)
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
            val rootRes = scanPlatform(root, "misc", maxDepth = 0)
            for (g in rootRes.games) {
                val ext = g.fileName.substringAfterLast('.', "").lowercase()
                val inferredId = if (ext == "pbp" && PbpHeaderParser.parse(File(g.id))?.isPs1Classics == true) {
                    "psx"
                } else {
                    inferPlatformByExt(ext) ?: "misc"
                }
                val plat = PlatformCatalog.forFolder(inferredId)
                val patched = if (g.platformId == plat.id) g else g.copy(platformId = plat.id)
                val metaMap = rootRes.localMetadata[g.id]?.let { mapOf(patched.id to it) }.orEmpty()
                addScanned(root.path, plat, PlatformScanResult(listOf(patched), metaMap))
            }
        }

        return snapshot()
    }

    fun scan(root: File): Library = scanAll(listOf(root))

    private fun canonicalKey(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun buildLowerNameMap(names: Array<String>?): Map<String, String> {
        if (names.isNullOrEmpty()) return emptyMap()
        val map = HashMap<String, String>(names.size * 2)
        for (n in names) {
            map.putIfAbsent(n.lowercase(), n)
        }
        return map
    }

    private fun findChildIgnoreCase(
        parent: File,
        lowerName: String,
        lowerToActual: Map<String, String>? = null,
    ): File? {
        if (lowerToActual != null) {
            val actual = lowerToActual[lowerName] ?: return null
            return File(parent, actual)
        }
        val direct = File(parent, lowerName)
        if (direct.exists()) return direct
        val actual = parent.list()?.firstOrNull { it.equals(lowerName, ignoreCase = true) } ?: return null
        return File(parent, actual)
    }

    private fun resolveRelativeDirIgnoreCase(
        base: File,
        relPath: String,
        baseEntries: Map<String, String>? = null,
    ): File? {
        var cur = base
        var first = true
        for (seg in relPath.split('/')) {
            if (seg.isEmpty()) continue
            val map = if (first) {
                first = false
                baseEntries
            } else {
                null
            }
            val next = findChildIgnoreCase(cur, seg.lowercase(), map) ?: return null
            if (!next.isDirectory) return null
            cur = next
        }
        return cur
    }

    private fun isFastSkipNonDirFile(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return false
        return name.substring(dot + 1).lowercase() in FAST_SKIP_DIR_CHECK_EXT
    }

    /**
     * 计算目录增量快照：覆盖主目录、最多 3 层合法子目录、内置/外置 `gamelist.xml` / `metadata.pegasus.txt`
     * 以及全部本地/上级/同级封面目录的 mtime 与条目数，避免新增封面或子目录 ROM 时漏刷。
     */
    internal fun computeDirSnapshot(
        dir: File,
        parentEntriesCache: MutableMap<String, Map<String, String>> = HashMap(),
        allowSubdirs: Boolean = true,
    ): Pair<Long, Int> {
        val entries = dir.list()
        val rootCount = entries?.size ?: 0
        var totalCount = rootCount
        var hash = SNAPSHOT_HASH_SEED
        fun mix(value: Long) {
            hash = hash * 31L + value
        }
        fun mixFile(f: File?, tag: Long) {
            if (f != null && f.isFile) {
                mix(tag)
                mix(f.lastModified())
                mix(f.length())
            }
        }
        fun mixCoverDir(d: File?, tag: Long) {
            if (d != null && d.isDirectory) {
                mix(tag)
                mix(d.lastModified())
                mix((d.list()?.size ?: 0).toLong())
            }
        }
        fun mixMetaAndMedia(targetDir: File, entryMap: Map<String, String>) {
            if ("gamelist.xml" in entryMap) mixFile(findChildIgnoreCase(targetDir, "gamelist.xml", entryMap), 101L)
            if ("metadata.pegasus.txt" in entryMap) mixFile(findChildIgnoreCase(targetDir, "metadata.pegasus.txt", entryMap), 103L)
            if ("metadata.txt" in entryMap) mixFile(findChildIgnoreCase(targetDir, "metadata.txt", entryMap), 104L)

            if ("media" in entryMap) {
                val mediaDir = findChildIgnoreCase(targetDir, "media", entryMap)
                if (mediaDir != null && mediaDir.isDirectory) {
                    mixCoverDir(mediaDir, 200L)
                    val mediaEntries = buildLowerNameMap(mediaDir.list())
                    for ((idx, sub) in listOf(
                        "covers", "box2dfront", "boxfront", "boxart", "3dboxes",
                        "images", "miximages", "mix", "screenshots",
                    ).withIndex()) {
                        if (sub in mediaEntries) {
                            mixCoverDir(findChildIgnoreCase(mediaDir, sub, mediaEntries), 201L + idx)
                        }
                    }
                }
            }
            for ((idx, sub) in listOf(
                "downloaded_images", "images", "imgs", "img", "covers", "boxart", "screenshots",
            ).withIndex()) {
                if (sub in entryMap) {
                    mixCoverDir(findChildIgnoreCase(targetDir, sub, entryMap), 220L + idx)
                }
            }
        }

        mix(dir.lastModified())
        mix(rootCount.toLong())

        val rootEntryMap = buildLowerNameMap(entries)
        mixMetaAndMedia(dir, rootEntryMap)

        fun cachedMediaTopEntries(container: File): Map<String, String> =
            parentEntriesCache.getOrPut(container.path) {
                val list = container.list() ?: return@getOrPut emptyMap()
                val map = HashMap<String, String>()
                for (name in list) {
                    val lower = name.lowercase()
                    if (lower in PARENT_MEDIA_TOP_DIRS) {
                        map.putIfAbsent(lower, name)
                    }
                }
                map
            }

        fun mixExternalMirror(container: File, folderName: String, tagOffset: Long) {
            val present = cachedMediaTopEntries(container)
            if (present.isEmpty()) return
            if ("gamelists" in present) {
                resolveRelativeDirIgnoreCase(container, "gamelists/$folderName", present)?.let { glDir ->
                    mixFile(File(glDir, "gamelist.xml"), 102L + tagOffset)
                }
            }
            if ("media" in present) {
                resolveRelativeDirIgnoreCase(container, "media/$folderName", present)?.let { mDir ->
                    for ((idx, sub) in listOf(
                        "covers", "box2dfront", "boxfront", "boxart", "3dboxes", "miximages", "screenshots",
                    ).withIndex()) {
                        mixCoverDir(findChildIgnoreCase(mDir, sub), 301L + tagOffset + idx)
                    }
                }
            }
            if ("downloaded_media" in present) {
                resolveRelativeDirIgnoreCase(container, "downloaded_media/$folderName", present)?.let { dmDir ->
                    for ((idx, sub) in listOf(
                        "covers", "box2dfront", "3dboxes", "miximages", "screenshots",
                    ).withIndex()) {
                        mixCoverDir(findChildIgnoreCase(dmDir, sub), 320L + tagOffset + idx)
                    }
                }
            }
            for ((idx, top) in listOf("images", "imgs", "boxart", "covers", "screenshots").withIndex()) {
                if (top in present) {
                    mixCoverDir(resolveRelativeDirIgnoreCase(container, "$top/$folderName", present), 340L + tagOffset + idx)
                }
            }
        }

        val parent = dir.parentFile
        if (parent != null) {
            val folderName = dir.name
            mixExternalMirror(parent, folderName, tagOffset = 0L)
            val grandParent = parent.parentFile
            if (grandParent != null && dir.path != parent.path) {
                mixExternalMirror(grandParent, folderName, tagOffset = 100L)
            }
        }

        if (allowSubdirs && !entries.isNullOrEmpty()) {
            val visitedCanonical = HashSet<String>(16)
            visitedCanonical += canonicalKey(dir)
            var visitedSubdirs = 0

            fun walkSnapshot(curDir: File, curEntries: Array<String>, relPrefix: String, depth: Int) {
                if (depth > MAX_SUBDIR_DEPTH || visitedSubdirs >= MAX_SNAPSHOT_SUBDIRS) return
                for (name in curEntries) {
                    if (visitedSubdirs >= MAX_SNAPSHOT_SUBDIRS) break
                    if (name.startsWith(".")) continue
                    val lower = name.lowercase()
                    if (lower in IGNORED_PLATFORM_DIRS) continue
                    if (isFastSkipNonDirFile(name)) continue
                    val child = File(curDir, name)
                    if (!child.isDirectory) continue
                    val canon = canonicalKey(child)
                    if (!visitedCanonical.add(canon)) continue
                    val childEntries = child.list() ?: continue
                    val childMap = buildLowerNameMap(childEntries)
                    if (".nomedia" in childMap || ".pocketdeckignore" in childMap) continue

                    visitedSubdirs++
                    val relPath = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                    totalCount += childEntries.size
                    mix(relPath.hashCode().toLong())
                    mix(child.lastModified())
                    mix(childEntries.size.toLong())
                    mixMetaAndMedia(child, childMap)

                    if (depth < MAX_SUBDIR_DEPTH) {
                        walkSnapshot(child, childEntries, relPath, depth + 1)
                    }
                }
            }

            walkSnapshot(dir, entries, "", 1)
        }

        return hash to totalCount
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

    private fun mergeLocalMediaMaps(
        primary: Map<String, LocalMediaEntry>,
        secondary: Map<String, LocalMediaEntry>,
    ): Map<String, LocalMediaEntry> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val merged = HashMap<String, LocalMediaEntry>(primary.size + secondary.size)
        merged.putAll(secondary)
        for ((k, pEntry) in primary) {
            val sEntry = merged[k]
            merged[k] = if (sEntry == null) {
                pEntry
            } else {
                val pMeta = pEntry.metadata
                val sMeta = sEntry.metadata
                val combinedMeta = when {
                    pMeta == null -> sMeta
                    sMeta == null -> pMeta
                    else -> GameMetadata(
                        releaseDate = pMeta.releaseDate ?: sMeta.releaseDate,
                        genre = pMeta.genre ?: sMeta.genre,
                        developer = pMeta.developer ?: sMeta.developer,
                        publisher = pMeta.publisher ?: sMeta.publisher,
                        description = pMeta.description ?: sMeta.description,
                    ).takeIf { !it.isEmpty }
                }
                LocalMediaEntry(
                    coverPath = pEntry.coverPath ?: sEntry.coverPath,
                    metadata = combinedMeta,
                )
            }
        }
        return merged
    }

    private fun parseLocalMetadataForDir(dir: File, includeParentGamelists: Boolean): Map<String, LocalMediaEntry> {
        val gamelist = parseGamelistXml(dir, includeParentGamelists)
        val pegasusFile = findChildIgnoreCase(dir, "metadata.pegasus.txt")?.takeIf { it.isFile }
            ?: findChildIgnoreCase(dir, "metadata.txt")?.takeIf { it.isFile }
        val pegasus = if (pegasusFile != null) PegasusMetadataParser.parse(dir, pegasusFile) else emptyMap()
        return mergeLocalMediaMaps(gamelist, pegasus)
    }

    private fun collectCoversFromDir(
        d: File?,
        target: MutableMap<String, String>,
        relPrefix: String = "",
        depth: Int = 0,
        maxSubdirDepth: Int = 0,
    ) {
        if (d == null || !d.isDirectory) return
        val entries = d.listFiles() ?: return
        for (f in entries) {
            if (f.isFile) {
                if (f.extension.lowercase() !in IMAGE_EXT) continue
                val key = f.nameWithoutExtension.lowercase()
                val relKey = if (relPrefix.isEmpty()) key else "$relPrefix/$key"
                target.putIfAbsent(relKey, f.path)
                if (relPrefix.isNotEmpty()) {
                    target.putIfAbsent(key, f.path)
                }
                for (suffix in COVER_SUFFIXES) {
                    if (key.endsWith(suffix)) {
                        val stripped = key.removeSuffix(suffix)
                        val relStripped = if (relPrefix.isEmpty()) stripped else "$relPrefix/$stripped"
                        target.putIfAbsent(relStripped, f.path)
                        if (relPrefix.isNotEmpty()) {
                            target.putIfAbsent(stripped, f.path)
                        }
                    }
                }
            } else if (f.isDirectory && depth < maxSubdirDepth && !f.name.startsWith(".")) {
                val nextRel = if (relPrefix.isEmpty()) f.name.lowercase() else "$relPrefix/${f.name.lowercase()}"
                collectCoversFromDir(f, target, nextRel, depth + 1, maxSubdirDepth)
            }
        }
    }

    private fun collectPegasusPerGameMedia(
        mediaDir: File?,
        boxartOut: MutableMap<String, String>,
        fallbackOut: MutableMap<String, String>,
    ) {
        if (mediaDir == null || !mediaDir.isDirectory) return
        val subDirs = mediaDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty()
        for (gameDir in subDirs) {
            val lowerName = gameDir.name.lowercase()
            if (lowerName in PEGASUS_MEDIA_BUCKET_DIRS) continue
            val imgs = gameDir.listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXT }.orEmpty()
            if (imgs.isEmpty()) continue
            val byBase = HashMap<String, String>(imgs.size * 2)
            for (img in imgs) {
                byBase.putIfAbsent(img.nameWithoutExtension.lowercase(), img.path)
            }
            for (cand in SINGLE_GAME_BOXART_NAMES) {
                val hit = byBase[cand]
                if (hit != null) {
                    boxartOut.putIfAbsent(lowerName, hit)
                    break
                }
            }
            for (cand in SINGLE_GAME_FALLBACK_NAMES) {
                val hit = byBase[cand]
                if (hit != null) {
                    fallbackOut.putIfAbsent(lowerName, hit)
                    break
                }
            }
        }
    }

    private fun collectMediaForScope(
        scopeDir: File,
        boxartOut: MutableMap<String, String>,
        fallbackOut: MutableMap<String, String>,
        allowMirrorSubdirs: Boolean,
    ) {
        val maxMirrorDepth = if (allowMirrorSubdirs) MAX_SUBDIR_DEPTH else 0
        val dirEntries = buildLowerNameMap(scopeDir.list())
        for (relDir in BOXART_COVER_DIRS) {
            collectCoversFromDir(
                resolveRelativeDirIgnoreCase(scopeDir, relDir, dirEntries),
                boxartOut,
                maxSubdirDepth = maxMirrorDepth,
            )
        }
        if ("media" in dirEntries) {
            collectPegasusPerGameMedia(findChildIgnoreCase(scopeDir, "media", dirEntries), boxartOut, fallbackOut)
        }
        for (relDir in FALLBACK_COVER_DIRS) {
            collectCoversFromDir(
                resolveRelativeDirIgnoreCase(scopeDir, relDir, dirEntries),
                fallbackOut,
                maxSubdirDepth = maxMirrorDepth,
            )
        }
        collectCoversFromDir(scopeDir, boxartOut, maxSubdirDepth = 0)
    }

    private data class ScannedDirBucket(
        val dir: File,
        val relPrefix: String,
        val files: List<File>,
    )

    private fun scanPlatform(
        dir: File,
        platformId: String,
        maxDepth: Int = if (platformId in NON_RECURSIVE_PLATFORMS) 0 else MAX_SUBDIR_DEPTH,
    ): PlatformScanResult {
        if (!dir.isDirectory) return PlatformScanResult(emptyList(), emptyMap())

        val platformBoxart = HashMap<String, String>()
        val platformFallback = HashMap<String, String>()
        collectMediaForScope(dir, platformBoxart, platformFallback, allowMirrorSubdirs = maxDepth > 0)

        fun collectExternalMirrors(container: File, folderName: String, includeMediaFolder: Boolean) {
            val topMap = buildLowerNameMap(container.list())
            val mirrorDepth = if (maxDepth > 0) MAX_SUBDIR_DEPTH else 0
            if (includeMediaFolder && "media" in topMap) {
                for (sub in listOf("covers", "box2dfront", "boxfront", "boxart", "3dboxes")) {
                    collectCoversFromDir(
                        resolveRelativeDirIgnoreCase(container, "media/$folderName/$sub", topMap),
                        platformBoxart,
                        maxSubdirDepth = mirrorDepth,
                    )
                }
                for (sub in listOf("miximages", "mix", "screenshots")) {
                    collectCoversFromDir(
                        resolveRelativeDirIgnoreCase(container, "media/$folderName/$sub", topMap),
                        platformFallback,
                        maxSubdirDepth = mirrorDepth,
                    )
                }
            }
            if ("downloaded_media" in topMap) {
                for (sub in listOf("covers", "box2dfront", "3dboxes")) {
                    collectCoversFromDir(
                        resolveRelativeDirIgnoreCase(container, "downloaded_media/$folderName/$sub", topMap),
                        platformBoxart,
                        maxSubdirDepth = mirrorDepth,
                    )
                }
                for (sub in listOf("miximages", "screenshots")) {
                    collectCoversFromDir(
                        resolveRelativeDirIgnoreCase(container, "downloaded_media/$folderName/$sub", topMap),
                        platformFallback,
                        maxSubdirDepth = mirrorDepth,
                    )
                }
            }
            for (top in listOf("images", "imgs", "boxart", "covers")) {
                if (top in topMap) {
                    collectCoversFromDir(
                        resolveRelativeDirIgnoreCase(container, "$top/$folderName", topMap),
                        platformBoxart,
                        maxSubdirDepth = mirrorDepth,
                    )
                }
            }
            if ("screenshots" in topMap) {
                collectCoversFromDir(
                    resolveRelativeDirIgnoreCase(container, "screenshots/$folderName", topMap),
                    platformFallback,
                    maxSubdirDepth = mirrorDepth,
                )
            }
        }

        dir.parentFile?.let { parent ->
            val folderName = dir.name
            collectExternalMirrors(parent, folderName, includeMediaFolder = true)
            parent.parentFile?.let { grandParent ->
                if (dir.path != parent.path) {
                    collectExternalMirrors(grandParent, folderName, includeMediaFolder = false)
                }
            }
        }

        val platformMetaEntries = parseLocalMetadataForDir(dir, includeParentGamelists = true)

        val dirBuckets = ArrayList<ScannedDirBucket>()
        val visitedCanonical = HashSet<String>(16)

        fun collectDirs(curDir: File, relPrefix: String, depth: Int) {
            val canon = canonicalKey(curDir)
            if (!visitedCanonical.add(canon)) return
            val children = curDir.listFiles() ?: return
            val files = ArrayList<File>()
            var hasIgnoreMarker = false
            val subDirs = if (depth < maxDepth) ArrayList<File>() else null
            for (f in children) {
                val name = f.name
                if (name.startsWith(".")) {
                    val lower = name.lowercase()
                    if (lower == ".nomedia" || lower == ".pocketdeckignore") {
                        hasIgnoreMarker = true
                    }
                    continue
                }
                if (f.isFile) {
                    files += f
                } else if (subDirs != null && f.isDirectory && name.lowercase() !in IGNORED_PLATFORM_DIRS) {
                    subDirs += f
                }
            }
            if (hasIgnoreMarker && depth > 0) return
            dirBuckets += ScannedDirBucket(curDir, relPrefix, files)
            if (subDirs != null) {
                for (sub in subDirs) {
                    val nextRel = if (relPrefix.isEmpty()) sub.name else "$relPrefix/${sub.name}"
                    collectDirs(sub, nextRel, depth + 1)
                }
            }
        }

        collectDirs(dir, "", 0)

        // 1) `.m3u` 多盘索引：记录每个 `.m3u` 引用的光盘文件并隐藏所有被引用的子盘文件（支持跨子目录引用）
        val m3uDiscMap = HashMap<String, List<String>>()
        val m3uReferencedPaths = HashSet<String>()
        if (platformId in DISC_PLATFORMS || platformId == "misc") {
            for (bucket in dirBuckets) {
                for (f in bucket.files) {
                    if (!f.extension.equals("m3u", ignoreCase = true)) continue
                    if (!isPotentialRomFileInternal(f, platformId, m3uBoundaryDir = dir)) continue
                    val refs = ArrayList<String>()
                    runCatching {
                        f.useLines { seq ->
                            seq.take(128).forEach { rawLine ->
                                val line = rawLine.trim().removePrefix("\uFEFF")
                                if (line.isEmpty() || line.startsWith("#")) return@forEach
                                val target = File(bucket.dir, line)
                                if (!WebFileSecurity.isInsideDir(target, dir)) return@forEach
                                m3uReferencedPaths += target.path
                                m3uReferencedPaths += canonicalKey(target)
                                if (target.isFile) {
                                    refs += target.path
                                }
                            }
                        }
                    }
                    if (refs.size > 1) {
                        m3uDiscMap[f.path] = refs
                    }
                }
            }
        }

        val outGames = ArrayList<Game>()
        val outLocalMeta = HashMap<String, GameMetadata>()

        // 2) 按子目录独立结算轨道隐藏与多盘折叠，防止跨目录同名 Disc 误合并
        for (bucket in dirBuckets) {
            val subDir = bucket.dir
            val isSubDir = subDir.path != dir.path
            var roms = bucket.files.filter { f ->
                if (!isPotentialRomFileInternal(f, platformId, m3uBoundaryDir = dir)) return@filter false
                if (f.extension.equals("m3u", ignoreCase = true)) return@filter true
                f.path !in m3uReferencedPaths && canonicalKey(f) !in m3uReferencedPaths
            }
            if (roms.isEmpty()) continue

            // 光盘轨道文件：有 cue/gdi/m3u/ccd 时，隐藏本目录内被引用的 bin/img/iso 轨道文件
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
            if (roms.isEmpty()) continue

            // 多盘自动折叠：同基名 + 不同盘号（Disc 1/2, CD1/2, 光盘1/2 等）自动折叠为单条 Game
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

            val gameCountInDir = singles.size + multiDiscGroups.values.sumOf { grp ->
                if (grp.size >= 2 && grp.map { it.first }.toSet().size >= 2) 1 else grp.size
            }
            val singleGameSubdir = isSubDir && gameCountInDir == 1

            val subBoxart: Map<String, String>
            val subFallback: Map<String, String>
            val subMetaEntries: Map<String, LocalMediaEntry>
            var singleGenericBoxart: String? = null
            var singleGenericFallback: String? = null
            if (isSubDir) {
                val sb = HashMap<String, String>()
                val sf = HashMap<String, String>()
                collectMediaForScope(subDir, sb, sf, allowMirrorSubdirs = false)
                subBoxart = sb
                subFallback = sf
                subMetaEntries = parseLocalMetadataForDir(subDir, includeParentGamelists = false)
                if (singleGameSubdir) {
                    singleGenericBoxart = SINGLE_GAME_BOXART_NAMES.firstNotNullOfOrNull { sb[it] }
                    singleGenericFallback = SINGLE_GAME_FALLBACK_NAMES.firstNotNullOfOrNull { sb[it] ?: sf[it] }
                }
            } else {
                subBoxart = emptyMap()
                subFallback = emptyMap()
                subMetaEntries = emptyMap()
            }

            fun buildGame(primary: File, displayBase: String, discs: List<String>) {
                val pbpInfo = if (primary.extension.equals("pbp", ignoreCase = true)) PbpHeaderParser.parse(primary) else null
                val effectivePlatformId = if (platformId == "psp" && pbpInfo?.isPs1Classics == true) "psx" else platformId
                val pbpTitle = pbpInfo?.title?.takeIf { it.isNotBlank() }
                val isGenericEboot = displayBase.equals("EBOOT", ignoreCase = true) ||
                    displayBase.equals("PBOOT", ignoreCase = true)
                val resolvedTitleBase = when {
                    pbpTitle != null && isGenericEboot -> pbpTitle
                    isSubDir && isGenericEboot -> subDir.name
                    else -> displayBase
                }

                val relPrefixLower = bucket.relPrefix.lowercase()
                val fileLower = primary.name.lowercase()
                val fileNoExtLower = primary.nameWithoutExtension.lowercase()
                val baseLower = displayBase.lowercase()
                val titleLower = resolvedTitleBase.lowercase()
                val pbpLower = pbpTitle?.lowercase()
                val discIdLower = pbpInfo?.discId?.lowercase()
                val relFileLower = if (relPrefixLower.isEmpty()) fileLower else "$relPrefixLower/$fileLower"
                val relBaseLower = if (relPrefixLower.isEmpty()) baseLower else "$relPrefixLower/$baseLower"

                fun lookupMetaEntry(map: Map<String, LocalMediaEntry>): LocalMediaEntry? {
                    if (map.isEmpty()) return null
                    if (relPrefixLower.isNotEmpty()) {
                        map[relFileLower]?.let { return it }
                        map[relBaseLower]?.let { return it }
                    }
                    return map[fileLower]
                        ?: map[baseLower]
                        ?: map[fileNoExtLower]
                        ?: titleLower.takeIf { it != baseLower }?.let { map[it] }
                        ?: pbpLower?.let { map[it] }
                }

                fun lookupCoverMap(map: Map<String, String>, checkRel: Boolean): String? {
                    if (map.isEmpty()) return null
                    if (checkRel && relPrefixLower.isNotEmpty()) {
                        map[relFileLower]?.let { return it }
                        map[relBaseLower]?.let { return it }
                        map["$relPrefixLower/$fileNoExtLower"]?.let { return it }
                    }
                    return map[baseLower]
                        ?: map[fileLower]
                        ?: map[fileNoExtLower]
                        ?: titleLower.takeIf { it != baseLower }?.let { map[it] }
                        ?: pbpLower?.let { map[it] }
                        ?: discIdLower?.let { map[it] }
                }

                val subEntry = lookupMetaEntry(subMetaEntries)
                val platEntry = lookupMetaEntry(platformMetaEntries)
                val cover = subEntry?.coverPath
                    ?: platEntry?.coverPath
                    ?: lookupCoverMap(subBoxart, checkRel = false)
                    ?: singleGenericBoxart
                    ?: lookupCoverMap(platformBoxart, checkRel = true)
                    ?: lookupCoverMap(subFallback, checkRel = false)
                    ?: singleGenericFallback
                    ?: lookupCoverMap(platformFallback, checkRel = true)

                val meta = subEntry?.metadata ?: platEntry?.metadata
                val totalSize = if (discs.size > 1) {
                    discs.sumOf { File(it).length() }.takeIf { it > 0L } ?: primary.length()
                } else {
                    primary.length()
                }
                val game = Game(
                    id = primary.path,
                    platformId = effectivePlatformId,
                    baseName = resolvedTitleBase,
                    fileName = primary.name,
                    title = cleanTitle(resolvedTitleBase),
                    localCover = cover,
                    fileSize = totalSize,
                    lastModifiedMs = primary.lastModified(),
                    discFiles = discs,
                )
                outGames += game
                meta?.takeIf { !it.isEmpty }?.let { outLocalMeta[game.id] = it }
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
        }

        return PlatformScanResult(
            games = outGames.sortedBy { it.title.lowercase() },
            localMetadata = outLocalMeta,
        )
    }

    /** 流式解析 ES-DE / EmulationStation 的 `gamelist.xml`（零 DOM 建树），支持相对路径与文件名键。 */
    private fun parseGamelistXml(
        dir: File,
        includeParentGamelists: Boolean = true,
    ): Map<String, LocalMediaEntry> {
        val candidates = buildList {
            add(File(dir, "gamelist.xml"))
            if (includeParentGamelists) {
                dir.parentFile?.let { parent ->
                    add(File(parent, "gamelists/${dir.name}/gamelist.xml"))
                    parent.parentFile?.let { grandParent ->
                        if (dir.path != parent.path) {
                            add(File(grandParent, "gamelists/${dir.name}/gamelist.xml"))
                        }
                    }
                }
            }
        }
        val xmlFile = candidates.firstOrNull { it.isFile && it.length() in 1L..16L * 1024 * 1024 }
            ?: return emptyMap()

        val result = HashMap<String, LocalMediaEntry>()
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
            val normRel = rawPath?.replace('\\', '/')?.removePrefix("./")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return
            val fileName = normRel.substringAfterLast('/')
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
            val entry = LocalMediaEntry(
                coverPath = resolvedCover,
                metadata = meta.takeIf { !it.isEmpty },
            )
            val relLower = normRel.lowercase()
            result[relLower] = entry
            if ('.' in fileName) {
                val relNoExt = normRel.substringBeforeLast('.', "").lowercase()
                if (relNoExt.isNotEmpty()) result[relNoExt] = entry
            }
            result.putIfAbsent(fileName.lowercase(), entry)
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

    internal fun formatGamelistDate(raw: String?): String? {
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

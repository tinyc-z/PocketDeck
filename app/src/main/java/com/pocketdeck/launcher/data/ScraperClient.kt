package com.pocketdeck.launcher.data

import android.content.Context
import android.net.Uri
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class ScrapeHit(
    val officialName: String? = null,
    val meta: GameMetadata = GameMetadata(),
    val coverUrls: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = officialName == null && meta.isEmpty && coverUrls.isEmpty()
}

/**
 * 共享的多源刮削客户端（Hasheous / ArcadeItalia / Wikidata / ScreenScraper / TheGamesDB / IGDB）。
 * 负责 ROM CRC32 计算、HTTP 请求与按游戏落盘缓存，避免 CoverSource 和 MetadataRepository 重复查询。
 */
class ScraperClient(context: Context) {

    private val appCtx = context.applicationContext.also { ScrapeLogStore.init(it) }
    private val apiCacheDir = File(appCtx.cacheDir, "scraper_api").apply { mkdirs() }
    private val crcCache = ConcurrentHashMap<String, String>()
    private val memHits = ConcurrentHashMap<String, Any>()

    @Volatile
    private var igdbToken: String? = null

    @Volatile
    private var igdbTokenExpireMs: Long = 0L

    /** 仅查询内存命中的刮削缓存（O(1)，无任何磁盘 I/O，可安全在主线程调用）。 */
    fun cachedHitInMemory(providerId: String, gameId: String): ScrapeHit? =
        memHits["$providerId::$gameId"] as? ScrapeHit

    fun cachedHit(providerId: String, gameId: String): ScrapeHit? {
        val key = "$providerId::$gameId"
        val cached = memHits[key]
        if (cached === MISS_SENTINEL) return null
        if (cached is ScrapeHit) return cached
        val file = cacheFile(providerId, gameId)
        if (!file.isFile) {
            memHits[key] = MISS_SENTINEL
            return null
        }
        val hit = readCachedHit(file)
        if (hit == null) {
            memHits[key] = MISS_SENTINEL
            return null
        }
        memHits[key] = hit
        return hit
    }

    fun clearApiCache() {
        memHits.clear()
        apiCacheDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun queryProvider(
        providerId: String,
        game: Game,
        platform: Platform?,
        cred: ScraperCredentials,
        forceRefresh: Boolean = false,
    ): ScrapeHit? {
        if (!forceRefresh) {
            cachedHit(providerId, game.id)?.let { return it }
        }

        val hit = when (providerId) {
            ScraperCatalog.CoverIds.HASHEOUS -> queryHasheous(game)
            ScraperCatalog.CoverIds.ARCADEITALIA -> queryArcadeItalia(game, platform)
            ScraperCatalog.MetaIds.WIKIDATA -> queryWikidata(game)
            ScraperCatalog.CoverIds.SCREENSCRAPER -> queryScreenScraper(game, platform, cred)
            ScraperCatalog.CoverIds.THEGAMESDB -> queryTheGamesDb(game, cred)
            ScraperCatalog.CoverIds.IGDB -> queryIgdb(game, cred)
            else -> null
        } ?: return null

        // 强制刷新时若远端未命中，保留本地已有有效缓存，避免覆盖掉有效元数据
        if (forceRefresh && hit.isEmpty && cachedHit(providerId, game.id)?.isEmpty == false) {
            return hit
        }

        val key = "$providerId::${game.id}"
        memHits[key] = hit
        writeCachedHit(cacheFile(providerId, game.id), hit)
        return hit
    }

    fun computeRomCrc32(file: File): String? {
        crcCache[file.path]?.let { return it }
        if (!file.isFile) return null

        val result = runCatching {
            if (file.extension.equals("zip", ignoreCase = true)) {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (!e.isDirectory && e.crc >= 0) {
                            return@runCatching "%08X".format(e.crc)
                        }
                    }
                    null
                }
            } else {
                if (file.length() > MAX_CRC_SIZE) return@runCatching null
                val crc = CRC32()
                FileInputStream(file).use { input ->
                    val buf = ByteArray(16 * 1024)
                    var first = true
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        if (first) {
                            first = false
                            val isInes = file.extension.equals("nes", ignoreCase = true) &&
                                read >= 16 && buf[0] == 'N'.code.toByte() && buf[1] == 'E'.code.toByte() &&
                                buf[2] == 'S'.code.toByte() && buf[3] == 0x1A.toByte()
                            if (isInes) {
                                crc.update(buf, 16, read - 16)
                                continue
                            }
                        }
                        crc.update(buf, 0, read)
                    }
                }
                "%08X".format(crc.value)
            }
        }.getOrNull()

        if (result != null) crcCache[file.path] = result
        return result
    }

    // ───────────────────────── Hasheous ─────────────────────────

    private fun queryHasheous(game: Game): ScrapeHit? {
        val crc = computeRomCrc32(File(game.id)) ?: return null
        val body = httpGet("https://hasheous.org/api/v1/Lookup/ByHash/crc/$crc?returnAllMetadata=true")
            ?: return null
        return runCatching {
            val o = JSONObject(body)
            val name = o.optString("name").takeIf { it.isNotBlank() }
            val sigGame = o.optJSONObject("signature")?.optJSONObject("game")
            val year = sigGame?.optString("year")?.takeIf { it.isNotBlank() }
            val publisher = o.optJSONObject("publisher")?.optString("name")?.takeIf { it.isNotBlank() }
                ?: sigGame?.optString("publisher")?.takeIf { it.isNotBlank() }

            val urls = LinkedHashSet<String>()
            val attrs = o.optJSONArray("attributes")
            if (attrs != null) {
                for (i in 0 until attrs.length()) {
                    val a = attrs.optJSONObject(i) ?: continue
                    val type = a.optString("attributeType")
                    val attrName = a.optString("attributeName")
                    val link = a.optString("link")
                    if ((type == "ImageId" || attrName == "Logo" || attrName == "BoxArt") && link.startsWith("/")) {
                        urls += "https://hasheous.org$link"
                    }
                }
            }
            ScrapeHit(
                officialName = name,
                meta = GameMetadata(releaseDate = year, publisher = publisher),
                coverUrls = urls.toList(),
            )
        }.getOrNull()
    }

    // ───────────────────────── ArcadeItalia ─────────────────────────

    private fun queryArcadeItalia(game: Game, platform: Platform?): ScrapeHit? {
        val pid = platform?.id?.lowercase().orEmpty()
        if (pid !in ARCADE_PLATFORMS) return ScrapeHit()
        val romName = Uri.encode(game.baseName)
        val body = httpGet("https://adb.arcadeitalia.net/service_scraper.php?ajax=query_mame&game_name=$romName")
            ?: return null
        return runCatching {
            val arr = JSONObject(body).optJSONArray("result") ?: return@runCatching ScrapeHit()
            if (arr.length() == 0) return@runCatching ScrapeHit()
            val item = arr.getJSONObject(0)
            val title = item.optString("title").takeIf { it.isNotBlank() }
            val year = item.optString("year").takeIf { it.isNotBlank() }
            val genre = item.optString("genre").takeIf { it.isNotBlank() }
            val maker = item.optString("manufacturer").takeIf { it.isNotBlank() }
            val urls = listOf(
                item.optString("url_image_flyer"),
                item.optString("url_image_title"),
                item.optString("url_image_cabinet"),
                item.optString("url_image_ingame"),
            ).filter { it.startsWith("http") }

            ScrapeHit(
                officialName = title,
                meta = GameMetadata(
                    releaseDate = year,
                    genre = genre,
                    developer = maker,
                    publisher = maker,
                ),
                coverUrls = urls,
            )
        }.getOrNull()
    }

    // ───────────────────────── Wikidata ─────────────────────────

    private fun queryWikidata(game: Game): ScrapeHit? {
        val clean = cleanTitle(game.title.ifBlank { game.baseName })
        if (clean.length < 2) return ScrapeHit()
        val searchUrl = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${Uri.encode(clean)}&language=en&format=json&limit=5"
        val searchBody = httpGet(searchUrl) ?: return null
        return runCatching {
            val results = JSONObject(searchBody).optJSONArray("search") ?: return@runCatching ScrapeHit()
            var qid: String? = null
            var label: String? = null
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val desc = r.optString("description").lowercase()
                if ("video game" in desc || "arcade game" in desc || "computer game" in desc) {
                    qid = r.optString("id")
                    label = r.optString("label").takeIf { it.isNotBlank() }
                    break
                }
            }
            if (qid == null) return@runCatching ScrapeHit()

            val entBody = httpGet("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$qid&props=claims&format=json")
                ?: return@runCatching null
            val claims = JSONObject(entBody).optJSONObject("entities")
                ?.optJSONObject(qid)?.optJSONObject("claims") ?: return@runCatching ScrapeHit()

            val devId = firstClaimEntityId(claims, "P178")
            val pubId = firstClaimEntityId(claims, "P123")
            val genreId = firstClaimEntityId(claims, "P136")
            val release = firstClaimTime(claims, "P577")

            val idsToResolve = listOfNotNull(devId, pubId, genreId).distinct()
            val labels = if (idsToResolve.isEmpty()) emptyMap() else resolveWikidataLabels(idsToResolve)

            ScrapeHit(
                officialName = label,
                meta = GameMetadata(
                    releaseDate = release,
                    genre = genreId?.let { labels[it] },
                    developer = devId?.let { labels[it] },
                    publisher = pubId?.let { labels[it] },
                ),
            )
        }.getOrNull()
    }

    private fun resolveWikidataLabels(ids: List<String>): Map<String, String> {
        val body = httpGet(
            "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${ids.joinToString("|")}&props=labels&languages=en&format=json",
        ) ?: return emptyMap()
        return runCatching {
            val entities = JSONObject(body).optJSONObject("entities") ?: return@runCatching emptyMap()
            val map = HashMap<String, String>(ids.size)
            for (id in ids) {
                val v = entities.optJSONObject(id)?.optJSONObject("labels")?.optJSONObject("en")?.optString("value")
                if (!v.isNullOrBlank()) map[id] = v
            }
            map
        }.getOrDefault(emptyMap())
    }

    // ───────────────────────── ScreenScraper.fr ─────────────────────────

    private fun queryScreenScraper(
        game: Game,
        platform: Platform?,
        cred: ScraperCredentials,
    ): ScrapeHit? {
        if (cred.ssUser.isBlank() || cred.ssPass.isBlank()) return null
        val crc = computeRomCrc32(File(game.id))
        val sysId = SCREENSCRAPER_SYSTEM_IDS[platform?.id?.lowercase()]
        val sb = StringBuilder("https://api.screenscraper.fr/api2/jeuInfos.php")
            .append("?ssid=").append(Uri.encode(cred.ssUser))
            .append("&sspassword=").append(Uri.encode(cred.ssPass))
            .append("&softname=PocketDeck")
            .append("&output=json")
            .append("&romtype=rom")
            .append("&romnom=").append(Uri.encode(game.fileName))
        if (crc != null) sb.append("&crc=").append(crc)
        if (sysId != null) sb.append("&systemeid=").append(sysId)

        val body = httpGet(sb.toString()) ?: return null
        return runCatching {
            val jeu = JSONObject(body).optJSONObject("response")?.optJSONObject("jeu")
                ?: return@runCatching ScrapeHit()
            val name = extractSsText(jeu.optJSONArray("noms"))
            val date = jeu.optJSONArray("dates")?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
            val genre = jeu.optJSONArray("genres")?.optJSONObject(0)?.optJSONArray("noms")?.let(::extractSsText)
            val dev = jeu.optJSONObject("developpeur")?.optString("text")?.takeIf { it.isNotBlank() }
            val pub = jeu.optJSONObject("editeur")?.optString("text")?.takeIf { it.isNotBlank() }

            val urls = LinkedHashSet<String>()
            val medias = jeu.optJSONArray("medias")
            if (medias != null) {
                val preferred = listOf("box-2D", "box-2D-front", "box-3D", "ss", "sstitle")
                for (type in preferred) {
                    for (i in 0 until medias.length()) {
                        val m = medias.optJSONObject(i) ?: continue
                        if (m.optString("type") == type) {
                            val u = m.optString("url")
                            if (u.startsWith("http")) urls += u
                        }
                    }
                }
            }
            ScrapeHit(
                officialName = name,
                meta = GameMetadata(releaseDate = date, genre = genre, developer = dev, publisher = pub),
                coverUrls = urls.toList(),
            )
        }.getOrNull()
    }

    // ───────────────────────── TheGamesDB.net ─────────────────────────

    private fun queryTheGamesDb(game: Game, cred: ScraperCredentials): ScrapeHit? {
        if (cred.tgdbKey.isBlank()) return null
        val q = cleanTitle(game.title.ifBlank { game.baseName })
        val url = "https://api.thegamesdb.net/v1/Games/ByGameName?apikey=${Uri.encode(cred.tgdbKey)}&name=${Uri.encode(q)}&fields=publishers,genres,developers&include=boxart"
        val body = httpGet(url) ?: return null
        return runCatching {
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: return@runCatching ScrapeHit()
            val gamesArr = data.optJSONArray("games") ?: return@runCatching ScrapeHit()
            if (gamesArr.length() == 0) return@runCatching ScrapeHit()
            val g = gamesArr.getJSONObject(0)
            val gid = g.optInt("id").toString()
            val title = g.optString("game_title").takeIf { it.isNotBlank() }
            val rel = g.optString("release_date").takeIf { it.isNotBlank() }?.take(4)

            val include = root.optJSONObject("include")
            val boxart = include?.optJSONObject("boxart")
            val baseImg = boxart?.optJSONObject("base_url")?.optString("medium")
                ?: boxart?.optJSONObject("base_url")?.optString("thumb")
                ?: ""
            val urls = LinkedHashSet<String>()
            val imgArr = boxart?.optJSONObject("data")?.optJSONArray(gid)
            if (imgArr != null && baseImg.isNotBlank()) {
                for (i in 0 until imgArr.length()) {
                    val img = imgArr.optJSONObject(i) ?: continue
                    if (img.optString("type") == "boxart") {
                        val fn = img.optString("filename")
                        if (fn.isNotBlank()) urls += baseImg + fn
                    }
                }
            }
            ScrapeHit(
                officialName = title,
                meta = GameMetadata(releaseDate = rel),
                coverUrls = urls.toList(),
            )
        }.getOrNull()
    }

    // ───────────────────────── IGDB.com ─────────────────────────

    private fun queryIgdb(game: Game, cred: ScraperCredentials): ScrapeHit? {
        if (cred.igdbClientId.isBlank() || cred.igdbClientSecret.isBlank()) return null
        val token = ensureIgdbToken(cred) ?: return null
        val q = cleanTitle(game.title.ifBlank { game.baseName }).replace("\"", "")
        val query = "search \"$q\"; fields name,first_release_date,genres.name,involved_companies.company.name,involved_companies.developer,involved_companies.publisher,cover.image_id; limit 3;"
        val body = httpPost(
            urlStr = "https://api.igdb.com/v4/games",
            body = query,
            headers = mapOf(
                "Client-ID" to cred.igdbClientId,
                "Authorization" to "Bearer $token",
                "Accept" to "application/json",
            ),
        ) ?: return null
        return runCatching {
            val arr = JSONArray(body)
            if (arr.length() == 0) return@runCatching ScrapeHit()
            val g = arr.getJSONObject(0)
            val name = g.optString("name").takeIf { it.isNotBlank() }
            val ts = g.optLong("first_release_date", 0L)
            val year = if (ts > 0) {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = ts * 1000L
                cal.get(java.util.Calendar.YEAR).toString()
            } else null

            val genre = g.optJSONArray("genres")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
            var dev: String? = null
            var pub: String? = null
            val companies = g.optJSONArray("involved_companies")
            if (companies != null) {
                for (i in 0 until companies.length()) {
                    val c = companies.optJSONObject(i) ?: continue
                    val cname = c.optJSONObject("company")?.optString("name")?.takeIf { it.isNotBlank() } ?: continue
                    if (dev == null && c.optBoolean("developer")) dev = cname
                    if (pub == null && c.optBoolean("publisher")) pub = cname
                }
            }
            val imageId = g.optJSONObject("cover")?.optString("image_id")?.takeIf { it.isNotBlank() }
            val urls = if (imageId != null) {
                listOf("https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg")
            } else emptyList()

            ScrapeHit(
                officialName = name,
                meta = GameMetadata(releaseDate = year, genre = genre, developer = dev, publisher = pub),
                coverUrls = urls,
            )
        }.getOrNull()
    }

    private fun ensureIgdbToken(cred: ScraperCredentials): String? {
        val now = System.currentTimeMillis()
        igdbToken?.let { if (now < igdbTokenExpireMs) return it }
        val url = "https://id.twitch.tv/oauth2/token?client_id=${Uri.encode(cred.igdbClientId)}&client_secret=${Uri.encode(cred.igdbClientSecret)}&grant_type=client_credentials"
        val body = httpPost(url, "", emptyMap()) ?: return null
        return runCatching {
            val o = JSONObject(body)
            val token = o.optString("access_token").takeIf { it.isNotBlank() } ?: return@runCatching null
            val expires = o.optLong("expires_in", 3600L)
            igdbToken = token
            igdbTokenExpireMs = now + (expires - 60).coerceAtLeast(60) * 1000L
            token
        }.getOrNull()
    }

    // ───────────────────────── HTTP & Cache Helpers ─────────────────────────

    private fun httpGet(urlStr: String): String? {
        if (!NetworkStatus.isOnline(appCtx)) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET", url = urlStr, error = "offline")
            return null
        }
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                code = conn.responseCode
                val elapsed = System.currentTimeMillis() - t0
                if (code !in 200..299) {
                    val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText()?.take(140) }.getOrNull()
                    ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET", url = urlStr, status = code, elapsedMs = elapsed, error = errBody ?: "non-2xx")
                    return null
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET", url = urlStr, status = code, elapsedMs = elapsed, result = "${text.length}B ${text.take(120)}")
                text
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun httpPost(urlStr: String, body: String, headers: Map<String, String>): String? {
        if (!NetworkStatus.isOnline(appCtx)) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "POST", url = urlStr, error = "offline")
            return null
        }
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (body.isNotEmpty()) {
                    conn.outputStream.bufferedWriter().use { it.write(body) }
                }
                code = conn.responseCode
                val elapsed = System.currentTimeMillis() - t0
                if (code !in 200..299) {
                    val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText()?.take(140) }.getOrNull()
                    ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "POST", url = urlStr, status = code, elapsedMs = elapsed, error = errBody ?: "non-2xx")
                    return null
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "POST", url = urlStr, status = code, elapsedMs = elapsed, result = "${text.length}B ${text.take(120)}")
                text
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "POST", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun cacheFile(providerId: String, gameId: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(gameId.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return File(apiCacheDir, "${providerId}_$digest.json")
    }

    private fun readCachedHit(file: File): ScrapeHit? = runCatching {
        val o = JSONObject(file.readText())
        val urls = ArrayList<String>()
        val arr = o.optJSONArray("coverUrls")
        if (arr != null) {
            for (i in 0 until arr.length()) urls += arr.getString(i)
        }
        ScrapeHit(
            officialName = o.optString("officialName").takeIf { it.isNotEmpty() },
            meta = GameMetadata(
                releaseDate = o.optString("releaseDate").takeIf { it.isNotEmpty() },
                genre = o.optString("genre").takeIf { it.isNotEmpty() },
                developer = o.optString("developer").takeIf { it.isNotEmpty() },
                publisher = o.optString("publisher").takeIf { it.isNotEmpty() },
            ),
            coverUrls = urls,
        )
    }.getOrNull()

    private fun writeCachedHit(file: File, hit: ScrapeHit) {
        runCatching {
            val o = JSONObject()
                .put("officialName", hit.officialName.orEmpty())
                .put("releaseDate", hit.meta.releaseDate.orEmpty())
                .put("genre", hit.meta.genre.orEmpty())
                .put("developer", hit.meta.developer.orEmpty())
                .put("publisher", hit.meta.publisher.orEmpty())
                .put("coverUrls", JSONArray(hit.coverUrls))
            val tmp = File(apiCacheDir, "${file.name}.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    private companion object {
        val MISS_SENTINEL = Any()
        const val USER_AGENT = "PocketDeck/0.3 (Android Handheld Launcher)"
        const val MAX_CRC_SIZE = 32L * 1024 * 1024
        val TAGS_REGEX = Regex("""\s*[(\[][^)\]]*[)\]]""")
        val ARCADE_PLATFORMS = setOf("arcade", "fbneo", "mame", "cps1", "cps2", "cps3", "neogeo")

        val SCREENSCRAPER_SYSTEM_IDS = mapOf(
            "nes" to 3, "fds" to 106, "snes" to 4, "sfc" to 4, "n64" to 14,
            "gb" to 9, "gbc" to 10, "gba" to 12, "nds" to 15, "gamecube" to 13, "wii" to 16,
            "mastersystem" to 2, "megadrive" to 1, "genesis" to 1, "gamegear" to 21,
            "saturn" to 22, "dreamcast" to 23, "psx" to 57, "psp" to 61, "ps2" to 58,
            "pcengine" to 31, "neogeo" to 142, "arcade" to 75, "fbneo" to 75, "mame" to 75,
        )

        fun cleanTitle(s: String): String =
            s.replace('_', ' ').replace(TAGS_REGEX, "").replace(Regex("""\s+"""), " ").trim()

        fun firstClaimEntityId(claims: JSONObject, prop: String): String? =
            claims.optJSONArray(prop)?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")
                ?.takeIf { it.isNotBlank() }

        fun firstClaimTime(claims: JSONObject, prop: String): String? {
            val t = claims.optJSONArray(prop)?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("time")
                ?: return null
            // "+1995-03-11T00:00:00Z" -> "1995"
            val m = Regex("""[+-]?(\d{4})""").find(t) ?: return null
            return m.groupValues[1]
        }

        fun extractSsText(arr: JSONArray?): String? {
            if (arr == null) return null
            for (lang in listOf("en", "us", "wor", "jp", "fr")) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("langue") == lang) {
                        val t = o.optString("text").takeIf { it.isNotBlank() }
                        if (t != null) return t
                    }
                }
            }
            return arr.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
        }
    }
}

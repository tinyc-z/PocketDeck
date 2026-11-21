package com.pocketdeck.launcher.data

import android.content.Context
import com.pocketdeck.launcher.data.model.GameMetadata

/**
 * APK 内置的只读离线数据库（打包在 `assets/offline_db/` 下，清理缓存时不会被删除）。
 *
 * 包含两部分按平台分片的纯文本索引（由 APK ZIP 原生压缩，运行时按需懒加载单平台）：
 * 1. `offline_db/boxart/<safeSystem>.txt`：libretro-thumbnails `Named_Boxarts` 完整 PNG 文件名清单；
 * 2. `offline_db/meta/<safeSystem>.tsv`：libretro-database（No-Intro / Redump / FBNeo / MAME / TOSEC）
 *    的 `CRC32 -> 标准英文名`（`H` 行）与 `标准名 -> 开发商/发行商/类型/发行日`（`M` 行）。
 *
 * 合并策略：
 * - 运行时优先秒读内置底座，不阻塞界面与封面加载；
 * - 远端 `api.github.com` / `libretro-database` 作为后台异步更新拉取后与内置底座合并写缓存，
 *   远端 403/断网时无任何影响；
 * - 用户点击「清理缓存」只清 `cacheDir` 和在线 SQLite 缓存，内置底座永久保留并在下次访问时直接生效；
 * - 内存按 LRU 最多常驻近期使用的少量平台，并在 `onTrimMemory` 时只释放非当前活跃平台。
 */
object OfflineGameDb {

    private const val MAX_CACHED_PLATFORMS = 4

    /** 当前前台列表正在使用的 `libretroSystem` 集合：LRU 淘汰与 `onTrimMemory` 均受保护，不会被释放。 */
    @Volatile
    var activeSystems: Set<String> = emptySet()

    data class PlatformTable(
        val hashToName: Map<String, String>,
        val metaByExactName: Map<String, GameMetadata>,
        val metaByNormTitle: Map<String, GameMetadata>,
        val metaByRawName: Map<String, GameMetadata> = emptyMap(),
    ) {
        val isEmpty: Boolean
            get() = hashToName.isEmpty() && metaByExactName.isEmpty()

        companion object {
            val EMPTY = PlatformTable(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        }
    }

    private val cacheLock = Any()
    private val boxartCache = LinkedHashMap<String, List<String>>(8, 0.75f, true)
    private val metaCache = LinkedHashMap<String, PlatformTable>(8, 0.75f, true)

    fun sanitizeSystemName(system: String): String = sanitizeFileKey(system)

    /** 释放非活跃平台的内存副本（保留 [keepSystems] 中当前正在使用的平台）。 */
    fun trimMemory(keepSystems: Set<String> = activeSystems) {
        synchronized(cacheLock) {
            boxartCache.keys.retainAll(keepSystems)
            metaCache.keys.retainAll(keepSystems)
        }
    }

    private fun <V> evictOldestInactive(map: LinkedHashMap<String, V>, maxSize: Int) {
        if (map.size <= maxSize) return
        val keep = activeSystems
        val it = map.entries.iterator()
        while (map.size > maxSize && it.hasNext()) {
            val entry = it.next()
            if (entry.key !in keep) {
                it.remove()
            }
        }
    }

    /** 读取指定平台的内置封面文件名清单（不存在时返回空列表）。 */
    fun loadBoxartNames(context: Context, system: String): List<String> {
        synchronized(cacheLock) {
            boxartCache[system]?.let { return it }
        }
        val assetPath = "offline_db/boxart/${sanitizeSystemName(system)}.txt"
        val loaded = runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        }.getOrDefault(emptyList())
        synchronized(cacheLock) {
            boxartCache[system] = loaded
            evictOldestInactive(boxartCache, MAX_CACHED_PLATFORMS)
        }
        return loaded
    }

    /** 读取指定平台的内置哈希与元数据表（不存在时返回 [PlatformTable.EMPTY]）。 */
    fun loadPlatformTable(context: Context, system: String): PlatformTable {
        synchronized(cacheLock) {
            metaCache[system]?.let { return it }
        }
        val assetPath = "offline_db/meta/${sanitizeSystemName(system)}.tsv"
        val loaded = runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                parseMetaTsv(lines)
            }
        }.getOrDefault(PlatformTable.EMPTY)
        synchronized(cacheLock) {
            metaCache[system] = loaded
            evictOldestInactive(metaCache, MAX_CACHED_PLATFORMS)
        }
        return loaded
    }

    /** 通过 ROM 的 CRC32（8 位十六进制）在内置库中查询官方标准名。 */
    fun lookupCanonicalNameByCrc(context: Context, system: String?, crc32: String?): String? {
        if (system.isNullOrBlank() || crc32.isNullOrBlank()) return null
        val key = crc32.trim().uppercase()
        return loadPlatformTable(context, system).hashToName[key]
    }

    /** 纯函数解析 `meta/<system>.tsv`，便于 JVM 单元测试直接验证。 */
    fun parseMetaTsv(lines: Sequence<String>): PlatformTable {
        val hashToName = HashMap<String, String>(2048)
        val metaByRaw = HashMap<String, GameMetadata>(1024)
        val metaByExact = HashMap<String, GameMetadata>(1024)
        val metaByNorm = HashMap<String, GameMetadata>(1024)

        for (raw in lines) {
            if (raw.length < 3) continue
            val parts = raw.split('\t')
            when (parts[0]) {
                "H" -> if (parts.size >= 3) {
                    val crc = parts[1].trim().uppercase()
                    val name = parts[2].trim()
                    if (crc.isNotEmpty() && name.isNotEmpty()) {
                        hashToName.putIfAbsent(crc, name)
                    }
                }
                "M" -> if (parts.size >= 6) {
                    val name = parts[1].trim()
                    if (name.isEmpty()) continue
                    val meta = GameMetadata(
                        developer = parts[2].trim().takeIf { it.isNotEmpty() },
                        publisher = parts[3].trim().takeIf { it.isNotEmpty() },
                        genre = parts[4].trim().takeIf { it.isNotEmpty() },
                        releaseDate = parts[5].trim().takeIf { it.isNotEmpty() },
                    )
                    if (meta.isEmpty) continue
                    metaByRaw[name] = metaByRaw[name]?.let { mergeMeta(it, meta) } ?: meta
                    val exactKey = TitleNormalizer.exactKey(name)
                    if (exactKey.isNotEmpty()) {
                        metaByExact[exactKey] = metaByExact[exactKey]?.let { mergeMeta(it, meta) } ?: meta
                    }
                    val normKey = TitleNormalizer.normalizedTitle(name)
                    if (normKey.isNotEmpty()) {
                        metaByNorm[normKey] = metaByNorm[normKey]?.let { mergeMeta(it, meta) } ?: meta
                    }
                }
            }
        }
        return PlatformTable(hashToName, metaByExact, metaByNorm, metaByRaw)
    }

    fun mergeMeta(primary: GameMetadata, fallback: GameMetadata): GameMetadata = GameMetadata(
        releaseDate = primary.releaseDate ?: fallback.releaseDate,
        developer = primary.developer ?: fallback.developer,
        publisher = primary.publisher ?: fallback.publisher,
        genre = primary.genre ?: fallback.genre,
        description = primary.description ?: fallback.description,
    )

    /** 合并两个封面文件名列表（保留顺序去重），用于把远端增量更新合并到内置清单上。 */
    fun mergeBoxartNames(bundled: List<String>, remote: List<String>): List<String> {
        if (remote.isEmpty()) return bundled
        if (bundled.isEmpty()) return remote
        val merged = LinkedHashSet<String>(bundled.size + remote.size)
        merged.addAll(bundled)
        merged.addAll(remote)
        return merged.toList()
    }
}

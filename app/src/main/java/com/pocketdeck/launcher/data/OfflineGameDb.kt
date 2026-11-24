package com.pocketdeck.launcher.data

import android.content.Context
import com.pocketdeck.launcher.data.model.GameMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * APK 内置的只读离线数据库（打包在 `assets/offline_db/` 下，清理缓存时不会被删除）。
 *
 * 包含两部分按平台分片的纯文本索引（由 APK ZIP 原生压缩，运行时按需懒加载单平台）：
 * 1. `offline_db/boxart/<safeSystem>.txt`：libretro-thumbnails `Named_Boxarts` 完整 PNG 文件名清单；
 * 2. `offline_db/meta/<safeSystem>.tsv`：libretro-database（No-Intro / Redump / FBNeo / MAME / TOSEC）
 *    的 `CRC32 -> 标准英文名`（`H` 行）与 `标准名 -> 开发商/发行商/类型/发行日`（`M` 行）。
 *
 * 合并策略：
 * - 按平台单飞锁加载，并发调用同一平台只读盘解析一次；
 * - [PlatformTable] 直接持有共享索引行并在 `MetadataRepository` 零拷贝复用，倒排分词索引按需懒建；
 * - 远端 `api.github.com` / `libretro-database` 作为后台异步更新叠加在内置底座上，不重复拷贝底座；
 * - 内存按 LRU 最多常驻近期使用的少量平台，并在 `onTrimMemory` 时只释放非当前活跃平台。
 */
object OfflineGameDb {

    private const val MAX_CACHED_PLATFORMS = 4
    const val MAX_ACTIVE_PROTECTED_SYSTEMS = 3

    /** 当前前台列表焦点附近正在使用的 `libretroSystem` 集合：LRU 淘汰与 `onTrimMemory` 均受保护，不会被释放。 */
    @Volatile
    var activeSystems: Set<String> = emptySet()

    /** 按焦点为中心向两侧交替遍历列表下标（focus, focus+1, focus-1, ...），优先处理首屏可见项。 */
    inline fun forEachFocusOutward(size: Int, focusIdx: Int, action: (index: Int) -> Unit) {
        if (size <= 0) return
        val center = focusIdx.coerceIn(0, size - 1)
        action(center)
        var offset = 1
        while (center + offset < size || center - offset >= 0) {
            if (center + offset < size) action(center + offset)
            if (center - offset >= 0) action(center - offset)
            offset++
        }
    }

    /**
     * 提取距当前焦点最近的最多 [maxSystems] 个活跃平台（按焦点外扩顺序收集），
     * 防止多平台聚合 Tab（如 NOW/OTHER）把十余个平台全部钉死在内存里绕过 LRU 淘汰。
     */
    fun <T> collectActiveSystemsNearFocus(
        items: List<T>,
        focusIdx: Int,
        maxSystems: Int = MAX_ACTIVE_PROTECTED_SYSTEMS,
        systemOf: (T) -> String?,
    ): Set<String> {
        if (items.isEmpty() || maxSystems <= 0) return emptySet()
        val out = LinkedHashSet<String>(maxSystems)
        forEachFocusOutward(items.size, focusIdx) { idx ->
            val sys = systemOf(items[idx])
            if (!sys.isNullOrBlank()) {
                out.add(sys)
                if (out.size >= maxSystems) return out
            }
        }
        return out
    }

    data class MetaRow(
        val name: String,
        val meta: GameMetadata,
    )

    class PlatformTable(
        val hashToName: Map<String, String>,
        val metaByRawName: Map<String, GameMetadata>,
        val rowByExact: Map<String, MetaRow>,
        val rowByNorm: Map<String, MetaRow>,
        val rowByCompact: Map<String, MetaRow>,
    ) {
        /** 零拷贝只读视图，兼容直接按 `exactKey` 查询元数据。 */
        val metaByExactName: Map<String, GameMetadata> = RowMetaMapView(rowByExact)

        /** 零拷贝只读视图，兼容直接按 `normalizedTitle` 查询元数据。 */
        val metaByNormTitle: Map<String, GameMetadata> = RowMetaMapView(rowByNorm)

        @Volatile
        private var _rowByToken: Map<String, List<MetaRow>>? = null

        val isEmpty: Boolean
            get() = hashToName.isEmpty() && metaByRawName.isEmpty() && rowByExact.isEmpty()

        /** 仅在模糊降级查询时按需构建倒排分词索引，避免常规 CRC/精确/标题命中时占用内存。 */
        fun ensureTokenIndexBuilt(): Map<String, List<MetaRow>> {
            _rowByToken?.let { return it }
            synchronized(this) {
                _rowByToken?.let { return it }
                val map = HashMap<String, MutableList<MetaRow>>(rowByNorm.size + 16)
                for ((normKey, row) in rowByNorm) {
                    for (tok in TitleNormalizer.tokenize(normKey)) {
                        map.getOrPut(tok) { ArrayList(4) }.add(row)
                    }
                }
                _rowByToken = map
                return map
            }
        }

        companion object {
            val EMPTY = PlatformTable(
                hashToName = emptyMap(),
                metaByRawName = emptyMap(),
                rowByExact = emptyMap(),
                rowByNorm = emptyMap(),
                rowByCompact = emptyMap(),
            )
        }
    }

    private class RowMetaMapView(
        private val backing: Map<String, MetaRow>,
    ) : kotlin.collections.AbstractMap<String, GameMetadata>() {
        override val size: Int get() = backing.size
        override fun containsKey(key: String): Boolean = backing.containsKey(key)
        override fun get(key: String): GameMetadata? = backing[key]?.meta
        override val entries: Set<Map.Entry<String, GameMetadata>>
            get() = object : kotlin.collections.AbstractSet<Map.Entry<String, GameMetadata>>() {
                override val size: Int get() = backing.size
                override fun iterator(): Iterator<Map.Entry<String, GameMetadata>> {
                    val it = backing.entries.iterator()
                    return object : Iterator<Map.Entry<String, GameMetadata>> {
                        override fun hasNext(): Boolean = it.hasNext()
                        override fun next(): Map.Entry<String, GameMetadata> {
                            val e = it.next()
                            return object : Map.Entry<String, GameMetadata> {
                                override val key: String = e.key
                                override val value: GameMetadata = e.value.meta
                            }
                        }
                    }
                }
            }
    }

    private val cacheLock = Any()
    private val boxartLocks = ConcurrentHashMap<String, Any>()
    private val metaLocks = ConcurrentHashMap<String, Any>()
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

    /** 读取指定平台的内置封面文件名清单（按平台单飞锁防重复读盘解析，不存在时返回空列表）。 */
    fun loadBoxartNames(context: Context, system: String): List<String> {
        synchronized(cacheLock) {
            boxartCache[system]?.let { return it }
        }
        val sysLock = boxartLocks.getOrPut(system) { Any() }
        synchronized(sysLock) {
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
    }

    /** 读取指定平台的内置哈希与元数据表（按平台单飞锁防并发重复解析，不存在时返回 [PlatformTable.EMPTY]）。 */
    fun loadPlatformTable(context: Context, system: String): PlatformTable {
        synchronized(cacheLock) {
            metaCache[system]?.let { return it }
        }
        val sysLock = metaLocks.getOrPut(system) { Any() }
        synchronized(sysLock) {
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
    }

    /** 通过 ROM 的 CRC32（8 位十六进制）在内置库中查询官方标准名。 */
    fun lookupCanonicalNameByCrc(context: Context, system: String?, crc32: String?): String? {
        if (system.isNullOrBlank() || crc32.isNullOrBlank()) return null
        val key = crc32.trim().uppercase()
        return loadPlatformTable(context, system).hashToName[key]
    }

    /** 纯函数解析 `meta/<system>.tsv`，单遍构建全部零重复索引，便于 JVM 单元测试直接验证。 */
    fun parseMetaTsv(lines: Sequence<String>): PlatformTable {
        val hashToName = HashMap<String, String>(2048)
        val metaByRaw = HashMap<String, GameMetadata>(1024)
        val rowByExact = HashMap<String, MetaRow>(1024)
        val rowByNorm = HashMap<String, MetaRow>(1024)
        val rowByCompact = HashMap<String, MetaRow>(1024)

        for (raw in lines) {
            if (raw.length < 3 || raw[1] != '\t') continue
            when (raw[0]) {
                'H' -> {
                    val t2 = raw.indexOf('\t', 2)
                    if (t2 < 0) continue
                    val t3 = raw.indexOf('\t', t2 + 1)
                    val crc = raw.substring(2, t2).trim().uppercase()
                    val name = (if (t3 < 0) raw.substring(t2 + 1) else raw.substring(t2 + 1, t3)).trim()
                    if (crc.isNotEmpty() && name.isNotEmpty()) {
                        hashToName.putIfAbsent(crc, name)
                    }
                }
                'M' -> {
                    val t2 = raw.indexOf('\t', 2)
                    if (t2 < 0) continue
                    val t3 = raw.indexOf('\t', t2 + 1)
                    if (t3 < 0) continue
                    val t4 = raw.indexOf('\t', t3 + 1)
                    if (t4 < 0) continue
                    val t5 = raw.indexOf('\t', t4 + 1)
                    if (t5 < 0) continue
                    val t6 = raw.indexOf('\t', t5 + 1)
                    val name = raw.substring(2, t2).trim()
                    if (name.isEmpty()) continue
                    val meta = GameMetadata(
                        developer = raw.substring(t2 + 1, t3).trim().takeIf { it.isNotEmpty() },
                        publisher = raw.substring(t3 + 1, t4).trim().takeIf { it.isNotEmpty() },
                        genre = raw.substring(t4 + 1, t5).trim().takeIf { it.isNotEmpty() },
                        releaseDate = (if (t6 < 0) raw.substring(t5 + 1) else raw.substring(t5 + 1, t6))
                            .trim()
                            .takeIf { it.isNotEmpty() },
                    )
                    if (meta.isEmpty) continue
                    val mergedRaw = metaByRaw[name]?.let { mergeMeta(it, meta) } ?: meta
                    metaByRaw[name] = mergedRaw
                    val row = MetaRow(name, mergedRaw)

                    val exactKey = TitleNormalizer.exactKey(name)
                    if (exactKey.isNotEmpty()) {
                        val prev = rowByExact[exactKey]
                        rowByExact[exactKey] = if (prev == null) row else MetaRow(prev.name, mergeMeta(prev.meta, mergedRaw))
                    }
                    val normKey = TitleNormalizer.normalizedTitle(name)
                    if (normKey.isNotEmpty()) {
                        val prev = rowByNorm[normKey]
                        val nextNormRow = if (prev == null) row else MetaRow(prev.name, mergeMeta(prev.meta, mergedRaw))
                        rowByNorm[normKey] = nextNormRow
                        val compKey = TitleNormalizer.compactFromNorm(normKey)
                        if (compKey.isNotEmpty()) {
                            val prevC = rowByCompact[compKey]
                            rowByCompact[compKey] = if (prevC == null) nextNormRow else MetaRow(prevC.name, mergeMeta(prevC.meta, mergedRaw))
                        }
                    }
                }
            }
        }
        return PlatformTable(
            hashToName = hashToName,
            metaByRawName = metaByRaw,
            rowByExact = rowByExact,
            rowByNorm = rowByNorm,
            rowByCompact = rowByCompact,
        )
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

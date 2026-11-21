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
 * - 运行时优先秒读内置底座，不阻塞界面与封面加载；
 * - 远端 `api.github.com` / `libretro-database` 作为后台异步更新拉取后与内置底座合并写缓存，
 *   远端 403/断网时无任何影响；
 * - 用户点击「清理缓存」只清 `cacheDir` 和在线 SQLite 缓存，内置底座永久保留并在下次访问时直接生效。
 */
object OfflineGameDb {

    private val SAFE_FILE_REGEX = Regex("""[^a-zA-Z0-9._-]""")

    data class PlatformTable(
        val hashToName: Map<String, String>,
        val metaByExactName: Map<String, GameMetadata>,
        val metaByNormTitle: Map<String, GameMetadata>,
    ) {
        val isEmpty: Boolean
            get() = hashToName.isEmpty() && metaByExactName.isEmpty()

        companion object {
            val EMPTY = PlatformTable(emptyMap(), emptyMap(), emptyMap())
        }
    }

    private val boxartCache = ConcurrentHashMap<String, List<String>>()
    private val metaCache = ConcurrentHashMap<String, PlatformTable>()

    fun sanitizeSystemName(system: String): String = system.replace(SAFE_FILE_REGEX, "_")

    /** 读取指定平台的内置封面文件名清单（不存在时返回空列表）。 */
    fun loadBoxartNames(context: Context, system: String): List<String> {
        boxartCache[system]?.let { return it }
        val assetPath = "offline_db/boxart/${sanitizeSystemName(system)}.txt"
        val loaded = runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        }.getOrDefault(emptyList())
        boxartCache[system] = loaded
        return loaded
    }

    /** 读取指定平台的内置哈希与元数据表（不存在时返回 [PlatformTable.EMPTY]）。 */
    fun loadPlatformTable(context: Context, system: String): PlatformTable {
        metaCache[system]?.let { return it }
        val assetPath = "offline_db/meta/${sanitizeSystemName(system)}.tsv"
        val loaded = runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                parseMetaTsv(lines)
            }
        }.getOrDefault(PlatformTable.EMPTY)
        metaCache[system] = loaded
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
        return PlatformTable(hashToName, metaByExact, metaByNorm)
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

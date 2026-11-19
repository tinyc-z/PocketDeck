package com.pocketdeck.launcher.data

import androidx.annotation.StringRes
import com.pocketdeck.launcher.R

enum class AppTheme { System, Light, Dark }

data class SourceItemConfig(
    val id: String,
    val enabled: Boolean,
)

data class ScraperCredentials(
    val ssUser: String = "",
    val ssPass: String = "",
    val tgdbKey: String = "",
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
    val sgdbKey: String = "",
)

enum class CredentialKind { ScreenScraper, TheGamesDB, IGDB, SteamGridDB }

data class SourceDescriptor(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    val defaultEnabled: Boolean,
    val credentialKind: CredentialKind? = null,
)

/**
 * 内置封面抓取节点与游戏元数据服务商目录。
 *
 * 合并说明：
 * - 将原 3 个 Libretro 封面镜像（CDN / jsDelivr / GitHub）收拢为单一 `libretro_boxarts` 节点，
 *   内部仅在网络/5xx 故障时自动故障转移，彻底消除 3 倍并发 404 风暴；
 * - 将原 2 个 Libretro DB 镜像收拢为单一 `libretro_db` 节点，内存与磁盘缓存减半；
 * - 新增零密钥 `gametdb`（任天堂全系 + PS3 官方编号/多语言元数据与高清封面）与可选 `steamgriddb`（Switch/PC/独立/Android 封面）；
 * - `wikidata` 改为默认关闭，保留开关供有需要的用户启用。
 */
object ScraperCatalog {

    object CoverIds {
        const val LIBRETRO_BOXARTS = "libretro_boxarts"
        const val GAMETDB = "gametdb"
        const val HASHEOUS = "hasheous"
        const val ARCADEITALIA = "arcadeitalia"
        const val LIBRETRO_SNAPS = "libretro_snaps"
        const val STEAMGRIDDB = "steamgriddb"
        const val SCREENSCRAPER = "screenscraper"
        const val THEGAMESDB = "thegamesdb"
        const val IGDB = "igdb"

        // 兼容旧版配置迁移的遗留节点 ID
        const val LEGACY_LIBRETRO_CDN = "libretro_cdn"
        const val LEGACY_LIBRETRO_GITHUB = "libretro_github"
        const val LEGACY_LIBRETRO_JSDELIVR = "libretro_jsdelivr"
    }

    object MetaIds {
        const val LIBRETRO_DB = "libretro_db"
        const val GAMETDB = "gametdb"
        const val HASHEOUS = "hasheous"
        const val ARCADEITALIA = "arcadeitalia"
        const val SCREENSCRAPER = "screenscraper"
        const val THEGAMESDB = "thegamesdb"
        const val IGDB = "igdb"
        const val WIKIDATA = "wikidata"

        // 兼容旧版配置迁移的遗留节点 ID
        const val LEGACY_LIBRETRO_DB_GITHUB = "libretro_db_github"
        const val LEGACY_LIBRETRO_DB_JSDELIVR = "libretro_db_jsdelivr"
    }

    val coverNodes: List<SourceDescriptor> = listOf(
        SourceDescriptor(CoverIds.LIBRETRO_BOXARTS, R.string.node_libretro_boxarts, R.string.node_libretro_boxarts_desc, true),
        SourceDescriptor(CoverIds.GAMETDB, R.string.node_gametdb, R.string.node_gametdb_desc, true),
        SourceDescriptor(CoverIds.HASHEOUS, R.string.node_hasheous, R.string.node_hasheous_desc, true),
        SourceDescriptor(CoverIds.ARCADEITALIA, R.string.node_arcadeitalia, R.string.node_arcadeitalia_desc, true),
        SourceDescriptor(CoverIds.LIBRETRO_SNAPS, R.string.node_libretro_snaps, R.string.node_libretro_snaps_desc, false),
        SourceDescriptor(CoverIds.STEAMGRIDDB, R.string.node_steamgriddb, R.string.node_steamgriddb_desc, false, CredentialKind.SteamGridDB),
        SourceDescriptor(CoverIds.SCREENSCRAPER, R.string.node_screenscraper, R.string.node_screenscraper_desc, false, CredentialKind.ScreenScraper),
        SourceDescriptor(CoverIds.THEGAMESDB, R.string.node_thegamesdb, R.string.node_thegamesdb_desc, false, CredentialKind.TheGamesDB),
        SourceDescriptor(CoverIds.IGDB, R.string.node_igdb, R.string.node_igdb_desc, false, CredentialKind.IGDB),
    )

    val metadataProviders: List<SourceDescriptor> = listOf(
        SourceDescriptor(MetaIds.LIBRETRO_DB, R.string.meta_src_libretro_db, R.string.meta_src_libretro_db_desc, true),
        SourceDescriptor(MetaIds.GAMETDB, R.string.node_gametdb, R.string.meta_src_gametdb_desc, true),
        SourceDescriptor(MetaIds.HASHEOUS, R.string.node_hasheous, R.string.meta_src_hasheous_desc, true),
        SourceDescriptor(MetaIds.ARCADEITALIA, R.string.node_arcadeitalia, R.string.meta_src_arcadeitalia_desc, true),
        SourceDescriptor(MetaIds.SCREENSCRAPER, R.string.node_screenscraper, R.string.node_screenscraper_desc, false, CredentialKind.ScreenScraper),
        SourceDescriptor(MetaIds.THEGAMESDB, R.string.node_thegamesdb, R.string.node_thegamesdb_desc, false, CredentialKind.TheGamesDB),
        SourceDescriptor(MetaIds.IGDB, R.string.node_igdb, R.string.node_igdb_desc, false, CredentialKind.IGDB),
        SourceDescriptor(MetaIds.WIKIDATA, R.string.meta_src_wikidata, R.string.meta_src_wikidata_desc, false),
    )

    private val coverById = coverNodes.associateBy { it.id }
    private val metaById = metadataProviders.associateBy { it.id }

    private val LEGACY_COVER_MAP = mapOf(
        CoverIds.LEGACY_LIBRETRO_CDN to CoverIds.LIBRETRO_BOXARTS,
        CoverIds.LEGACY_LIBRETRO_JSDELIVR to CoverIds.LIBRETRO_BOXARTS,
        CoverIds.LEGACY_LIBRETRO_GITHUB to CoverIds.LIBRETRO_BOXARTS,
    )

    private val LEGACY_META_MAP = mapOf(
        MetaIds.LEGACY_LIBRETRO_DB_JSDELIVR to MetaIds.LIBRETRO_DB,
        MetaIds.LEGACY_LIBRETRO_DB_GITHUB to MetaIds.LIBRETRO_DB,
    )

    fun coverDescriptor(id: String): SourceDescriptor? = coverById[LEGACY_COVER_MAP[id] ?: id]
    fun metaDescriptor(id: String): SourceDescriptor? = metaById[LEGACY_META_MAP[id] ?: id]

    fun defaultCoverConfigs(): List<SourceItemConfig> =
        coverNodes.map { SourceItemConfig(it.id, it.defaultEnabled) }

    fun defaultMetadataConfigs(): List<SourceItemConfig> =
        metadataProviders.map { SourceItemConfig(it.id, it.defaultEnabled) }

    /** 合并已保存列表与内置目录（自动将旧版 Libretro 镜像 ID 迁移为统一节点，并追加新节点）。 */
    fun normalizeCoverConfigs(saved: List<SourceItemConfig>): List<SourceItemConfig> =
        normalize(saved, coverNodes, LEGACY_COVER_MAP)

    fun normalizeMetadataConfigs(saved: List<SourceItemConfig>): List<SourceItemConfig> =
        normalize(saved, metadataProviders, LEGACY_META_MAP)

    private fun normalize(
        saved: List<SourceItemConfig>,
        catalog: List<SourceDescriptor>,
        legacyMap: Map<String, String>,
    ): List<SourceItemConfig> {
        if (saved.isEmpty()) return catalog.map { SourceItemConfig(it.id, it.defaultEnabled) }
        val validIds = catalog.map { it.id }.toSet()
        val indexByCanonical = HashMap<String, Int>(catalog.size)
        val result = ArrayList<SourceItemConfig>(catalog.size)

        for (item in saved) {
            val canonical = legacyMap[item.id] ?: item.id
            if (canonical !in validIds) continue
            val existingIdx = indexByCanonical[canonical]
            if (existingIdx == null) {
                indexByCanonical[canonical] = result.size
                result += SourceItemConfig(canonical, item.enabled)
            } else if (item.enabled && !result[existingIdx].enabled) {
                // 多个旧镜像合为一个节点时，只要任一镜像原本启用就保持启用
                result[existingIdx] = result[existingIdx].copy(enabled = true)
            }
        }
        for (desc in catalog) {
            if (desc.id !in indexByCanonical) {
                result += SourceItemConfig(desc.id, desc.defaultEnabled)
            }
        }
        return result
    }

    fun hasCredentials(kind: CredentialKind?, cred: ScraperCredentials): Boolean = when (kind) {
        null -> true
        CredentialKind.ScreenScraper -> cred.ssUser.isNotBlank() && cred.ssPass.isNotBlank()
        CredentialKind.TheGamesDB -> cred.tgdbKey.isNotBlank()
        CredentialKind.IGDB -> cred.igdbClientId.isNotBlank() && cred.igdbClientSecret.isNotBlank()
        CredentialKind.SteamGridDB -> cred.sgdbKey.isNotBlank()
    }
}

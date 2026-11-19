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
)

enum class CredentialKind { ScreenScraper, TheGamesDB, IGDB }

data class SourceDescriptor(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    val defaultEnabled: Boolean,
    val credentialKind: CredentialKind? = null,
)

/**
 * 内置封面抓取节点与游戏元数据服务商目录。
 */
object ScraperCatalog {

    object CoverIds {
        const val LIBRETRO_CDN = "libretro_cdn"
        const val LIBRETRO_GITHUB = "libretro_github"
        const val LIBRETRO_JSDELIVR = "libretro_jsdelivr"
        const val HASHEOUS = "hasheous"
        const val ARCADEITALIA = "arcadeitalia"
        const val LIBRETRO_SNAPS = "libretro_snaps"
        const val SCREENSCRAPER = "screenscraper"
        const val THEGAMESDB = "thegamesdb"
        const val IGDB = "igdb"
    }

    object MetaIds {
        const val LIBRETRO_DB_GITHUB = "libretro_db_github"
        const val LIBRETRO_DB_JSDELIVR = "libretro_db_jsdelivr"
        const val HASHEOUS = "hasheous"
        const val ARCADEITALIA = "arcadeitalia"
        const val WIKIDATA = "wikidata"
        const val SCREENSCRAPER = "screenscraper"
        const val THEGAMESDB = "thegamesdb"
        const val IGDB = "igdb"
    }

    val coverNodes: List<SourceDescriptor> = listOf(
        SourceDescriptor(CoverIds.LIBRETRO_CDN, R.string.node_libretro_cdn, R.string.node_libretro_cdn_desc, true),
        SourceDescriptor(CoverIds.LIBRETRO_JSDELIVR, R.string.node_libretro_jsdelivr, R.string.node_libretro_jsdelivr_desc, true),
        SourceDescriptor(CoverIds.LIBRETRO_GITHUB, R.string.node_libretro_github, R.string.node_libretro_github_desc, true),
        SourceDescriptor(CoverIds.HASHEOUS, R.string.node_hasheous, R.string.node_hasheous_desc, true),
        SourceDescriptor(CoverIds.ARCADEITALIA, R.string.node_arcadeitalia, R.string.node_arcadeitalia_desc, true),
        SourceDescriptor(CoverIds.LIBRETRO_SNAPS, R.string.node_libretro_snaps, R.string.node_libretro_snaps_desc, false),
        SourceDescriptor(CoverIds.SCREENSCRAPER, R.string.node_screenscraper, R.string.node_screenscraper_desc, false, CredentialKind.ScreenScraper),
        SourceDescriptor(CoverIds.THEGAMESDB, R.string.node_thegamesdb, R.string.node_thegamesdb_desc, false, CredentialKind.TheGamesDB),
        SourceDescriptor(CoverIds.IGDB, R.string.node_igdb, R.string.node_igdb_desc, false, CredentialKind.IGDB),
    )

    val metadataProviders: List<SourceDescriptor> = listOf(
        SourceDescriptor(MetaIds.LIBRETRO_DB_JSDELIVR, R.string.meta_src_libretro_jsdelivr, R.string.meta_src_libretro_jsdelivr_desc, true),
        SourceDescriptor(MetaIds.LIBRETRO_DB_GITHUB, R.string.meta_src_libretro_github, R.string.meta_src_libretro_github_desc, true),
        SourceDescriptor(MetaIds.HASHEOUS, R.string.node_hasheous, R.string.meta_src_hasheous_desc, true),
        SourceDescriptor(MetaIds.ARCADEITALIA, R.string.node_arcadeitalia, R.string.meta_src_arcadeitalia_desc, true),
        SourceDescriptor(MetaIds.WIKIDATA, R.string.meta_src_wikidata, R.string.meta_src_wikidata_desc, true),
        SourceDescriptor(MetaIds.SCREENSCRAPER, R.string.node_screenscraper, R.string.node_screenscraper_desc, false, CredentialKind.ScreenScraper),
        SourceDescriptor(MetaIds.THEGAMESDB, R.string.node_thegamesdb, R.string.node_thegamesdb_desc, false, CredentialKind.TheGamesDB),
        SourceDescriptor(MetaIds.IGDB, R.string.node_igdb, R.string.node_igdb_desc, false, CredentialKind.IGDB),
    )

    private val coverById = coverNodes.associateBy { it.id }
    private val metaById = metadataProviders.associateBy { it.id }

    fun coverDescriptor(id: String): SourceDescriptor? = coverById[id]
    fun metaDescriptor(id: String): SourceDescriptor? = metaById[id]

    fun defaultCoverConfigs(): List<SourceItemConfig> =
        coverNodes.map { SourceItemConfig(it.id, it.defaultEnabled) }

    fun defaultMetadataConfigs(): List<SourceItemConfig> =
        metadataProviders.map { SourceItemConfig(it.id, it.defaultEnabled) }

    /** 合并已保存列表与内置目录（保留用户顺序，追加版本升级新增的节点）。 */
    fun normalizeCoverConfigs(saved: List<SourceItemConfig>): List<SourceItemConfig> =
        normalize(saved, coverNodes)

    fun normalizeMetadataConfigs(saved: List<SourceItemConfig>): List<SourceItemConfig> =
        normalize(saved, metadataProviders)

    private fun normalize(
        saved: List<SourceItemConfig>,
        catalog: List<SourceDescriptor>,
    ): List<SourceItemConfig> {
        if (saved.isEmpty()) return catalog.map { SourceItemConfig(it.id, it.defaultEnabled) }
        val validIds = catalog.map { it.id }.toSet()
        val seen = HashSet<String>()
        val result = ArrayList<SourceItemConfig>(catalog.size)
        for (item in saved) {
            if (item.id in validIds && seen.add(item.id)) {
                result += item
            }
        }
        for (desc in catalog) {
            if (seen.add(desc.id)) {
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
    }
}

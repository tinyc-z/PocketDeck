package com.pocketdeck.launcher.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pocketdeck.launcher.BuildConfig
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.AppLocale
import com.pocketdeck.launcher.data.AppTheme
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.CredentialKind
import com.pocketdeck.launcher.data.GalleryAlign
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.SourceDescriptor
import com.pocketdeck.launcher.data.SourceItemConfig
import com.pocketdeck.launcher.data.SystemTabConfig
import com.pocketdeck.launcher.data.ThemeAccent
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.components.ButtonBadge
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.PocketDeckAppIcon
import com.pocketdeck.launcher.ui.components.drawViewModeIcon
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.components.viewModeLabel
import com.pocketdeck.launcher.ui.home.MetaFieldKey
import com.pocketdeck.launcher.ui.home.RomDirEntry
import com.pocketdeck.launcher.ui.home.TabEntry
import com.pocketdeck.launcher.ui.home.TabType
import com.pocketdeck.launcher.ui.home.UncategorizedPlatformInfo
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.SystemFonts
import com.pocketdeck.launcher.ui.theme.cardSurface
import java.io.File
import java.util.Locale

// ════════════════════════════════════════════════════════════════
// 1. 外观 (Look)
// ════════════════════════════════════════════════════════════════

@Composable
internal fun LookSection(
    theme: AppTheme,
    themeAccent: ThemeAccent,
    accentCursor: Int?,
    onToggleAccentPicker: () -> Unit,
    onSelectThemeAccent: (ThemeAccent) -> Unit,
    backgroundStyle: BackgroundStyle,
    bgCursor: Int?,
    onToggleBgPicker: () -> Unit,
    onSelectBackgroundStyle: (BackgroundStyle) -> Unit,
    customBackground: String?,
    onPickCustomBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    reduceMotion: Boolean,
    onToggleReduceMotion: () -> Unit,
    fontId: String,
    onOpenFontPicker: () -> Unit,
    onCycleTheme: () -> Unit,
    highlightIndex: Int,
) {
    val themeLabel = when (theme) {
        AppTheme.System -> stringResource(R.string.theme_system)
        AppTheme.Light -> stringResource(R.string.theme_light)
        AppTheme.Dark -> stringResource(R.string.theme_dark)
    }

    GroupHeader(stringResource(R.string.settings_group_theme))
    GroupCard {
        SettingRow(
            label = stringResource(R.string.settings_theme),
            highlighted = highlightIndex == 0,
            onClick = onCycleTheme,
            trailing = { ValueChevron(themeLabel) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_theme_accent),
            highlighted = highlightIndex == 1 && accentCursor == null,
            onClick = onToggleAccentPicker,
            trailing = {
                AccentDots(
                    selected = themeAccent,
                    cursor = accentCursor,
                    onPick = onSelectThemeAccent,
                )
            },
        )
    }

    Spacer(Modifier.height(14.dp))
    GroupHeader(stringResource(R.string.settings_group_background))
    GroupCard {
        val bgRowHl = highlightIndex == 2 && bgCursor == null
        val p = LocalDeckColors.current
        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewWhen(bgRowHl || bgCursor != null)
                .clip(RoundedCornerShape(DeckBrand.RadiusS))
                .then(
                    if (bgRowHl) {
                        Modifier
                            .background(p.accentSoft, RoundedCornerShape(DeckBrand.RadiusS))
                            .border(1.5.dp, Brush.linearGradient(listOf(p.brandStart, p.brandMid, p.brandEnd)), RoundedCornerShape(DeckBrand.RadiusS))
                    } else Modifier,
                )
                .soundClickable(onClick = onToggleBgPicker),
        ) {
            BackgroundThumbs(
                selected = backgroundStyle,
                cursor = bgCursor,
                label = { bgStyleLabel(it) },
                onPick = onSelectBackgroundStyle,
            )
        }
        var nextIdx = 3
        if (backgroundStyle == BackgroundStyle.Image) {
            RowDivider()
            val fileName = remember(customBackground) {
                customBackground?.let { File(it).name }?.takeIf { it.isNotBlank() }
            }
            SettingRow(
                label = stringResource(R.string.settings_custom_bg),
                description = fileName ?: stringResource(R.string.settings_custom_bg_none),
                highlighted = highlightIndex == nextIdx,
                onClick = onPickCustomBackground,
                trailing = {
                    if (customBackground != null) {
                        ValuePill(stringResource(R.string.settings_custom_bg_clear), onClick = onClearCustomBackground)
                        Spacer(Modifier.width(6.dp))
                    }
                    ValuePill(stringResource(R.string.settings_custom_bg_pick), accent = true, onClick = onPickCustomBackground)
                },
            )
            nextIdx++
        }
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_reduce_motion),
            description = stringResource(R.string.settings_reduce_motion_desc),
            highlighted = highlightIndex == nextIdx,
            onClick = onToggleReduceMotion,
            trailing = { DeckSwitch(reduceMotion) },
        )
        nextIdx++

        Spacer(Modifier.height(0.dp))
        RowDivider()
        val fontLabel = fontDisplayName(fontId)
        SettingRow(
            label = stringResource(R.string.settings_font),
            highlighted = highlightIndex == nextIdx,
            onClick = onOpenFontPicker,
            trailing = { ValueChevron(fontLabel) },
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 2. 主页 (Home)
// ════════════════════════════════════════════════════════════════

@Composable
internal fun HomeSettingsSection(
    state: SettingsState,
    onSetViewMode: (ViewMode) -> Unit,
    onCycleGalleryAlign: () -> Unit,
    onToggleBottomBar: () -> Unit,
    onToggleShowMetadata: () -> Unit,
    onToggleMetaField: (MetaFieldKey) -> Unit,
    onTabRowClick: (Int) -> Unit,
    onMoveTabTo: (Int, Int) -> Unit,
    onQuickCreateCategory: (Platform) -> Unit,
    highlightIndex: Int,
) {
    val viewModes = ViewMode.entries
    GroupHeader(stringResource(R.string.settings_default_view))
    GroupCard {
        viewModes.forEachIndexed { i, mode ->
            if (i > 0) RowDivider()
            val active = mode == state.viewMode
            val tint = if (active) DeckColors.Ruby else DeckColors.TextSecondary
            SettingRow(
                label = viewModeLabel(mode),
                highlighted = highlightIndex == i,
                onClick = { onSetViewMode(mode) },
                leading = {
                    Box(
                        Modifier
                            .size(18.dp)
                            .drawBehind { drawViewModeIcon(mode, tint) },
                    )
                },
                trailing = { CheckMark(active) },
            )
        }
    }

    var idx = viewModes.size
    Spacer(Modifier.height(14.dp))
    GroupHeader(stringResource(R.string.settings_group_layout))
    GroupCard {
        var first = true
        if (state.viewMode == ViewMode.Gallery) {
            val alignLabel = when (state.galleryAlign) {
                GalleryAlign.Top -> stringResource(R.string.align_top)
                GalleryAlign.Center -> stringResource(R.string.align_center)
                GalleryAlign.Bottom -> stringResource(R.string.align_bottom)
            }
            SettingRow(
                label = stringResource(R.string.settings_gallery_align),
                highlighted = highlightIndex == idx,
                onClick = onCycleGalleryAlign,
                trailing = { ValueChevron(alignLabel) },
            )
            idx++
            first = false
        }
        if (!first) RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_show_bottom_bar),
            highlighted = highlightIndex == idx,
            onClick = onToggleBottomBar,
            trailing = { DeckSwitch(state.bottomBar) },
        )
        idx++
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_show_metadata),
            description = stringResource(R.string.settings_metadata_desc),
            highlighted = highlightIndex == idx,
            onClick = onToggleShowMetadata,
            trailing = { DeckSwitch(state.showMetadata) },
        )
        idx++
        if (state.showMetadata) {
            val fields = MetaFieldKey.entries
            RowDivider()
            Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.settings_metadata_fields),
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.TextLabel,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
                fields.chunked(2).forEachIndexed { rowIdx, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEachIndexed { colIdx, field ->
                            val fIdx = idx + rowIdx * 2 + colIdx
                            val checked = when (field) {
                                MetaFieldKey.ReleaseDate -> state.metaDisplay.releaseDate
                                MetaFieldKey.Developer -> state.metaDisplay.developer
                                MetaFieldKey.Publisher -> state.metaDisplay.publisher
                                MetaFieldKey.FileSize -> state.metaDisplay.fileSize
                            }
                            CompactCheckChip(
                                label = metaFieldLabel(field),
                                checked = checked,
                                highlighted = highlightIndex == fIdx,
                                onClick = { onToggleMetaField(field) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            idx += fields.size
        }
    }

    // 标签页管理
    Spacer(Modifier.height(14.dp))
    val tabsStart = idx
    val tabs = state.manageTabs
    val kindNow = stringResource(R.string.tab_kind_now)
    val kindFavs = stringResource(R.string.tab_kind_favs)
    val kindOther = stringResource(R.string.tab_kind_other)
    val kindCustom = stringResource(R.string.tab_kind_custom)
    val hiddenLabel = stringResource(R.string.tab_hidden)
    val countFmt = stringResource(R.string.tab_games_count)
    val nowDefaultName = stringResource(R.string.tab_name_now)
    val favsDefaultName = stringResource(R.string.tab_name_favs)
    val otherDefaultName = stringResource(R.string.tab_name_other)
    val catById = remember(state.categories) { state.categories.associateBy { it.id } }

    GroupHeader(stringResource(R.string.settings_tabs))
    GroupCard {
        DragReorderColumn(
            items = tabs,
            keyOf = { it.id },
            onMove = onMoveTabTo,
        ) { i, t, dragHandleModifier ->
            if (i > 0) RowDivider()
            val displayTitle = when (t.type) {
                TabType.Now -> if (t.followSystemLanguage || t.label.isBlank()) nowDefaultName else t.label
                TabType.Favs -> if (t.followSystemLanguage || t.label.isBlank()) favsDefaultName else t.label
                TabType.Other -> if (t.followSystemLanguage || t.label.isBlank()) otherDefaultName else t.label
                TabType.Custom -> t.label
            }
            val sub = when (t.type) {
                TabType.Now -> {
                    val sort = state.systemTabs["now"]?.sortOrder
                    if (sort == null) kindNow else "$kindNow · ${sortOrderLabel(sort)}"
                }
                TabType.Favs -> {
                    val sort = state.systemTabs["favs"]?.sortOrder ?: CategorySortOrder.TitleAsc
                    if (sort == CategorySortOrder.TitleAsc) kindFavs else "$kindFavs · ${sortOrderLabel(sort)}"
                }
                TabType.Other -> {
                    val sort = state.systemTabs["other"]?.sortOrder ?: CategorySortOrder.TitleAsc
                    if (sort == CategorySortOrder.TitleAsc) kindOther else "$kindOther · ${sortOrderLabel(sort)}"
                }
                TabType.Custom -> {
                    val base = if (t.includes.isEmpty()) kindCustom else t.includes.joinToString(", ")
                    val sort = catById[t.id]?.sortOrder ?: CategorySortOrder.TitleAsc
                    if (sort == CategorySortOrder.TitleAsc) base else "$base · ${sortOrderLabel(sort)}"
                }
            }
            SettingRow(
                label = displayTitle,
                description = sub,
                dimmed = t.hidden,
                highlighted = highlightIndex == tabsStart + i,
                onClick = { onTabRowClick(i) },
                leading = { DragHandle(dragHandleModifier) },
                trailing = {
                    ValuePill(if (t.hidden) "$hiddenLabel · ${t.gameCount}" else countFmt.format(t.gameCount))
                },
            )
        }
        RowDivider()
        SettingRow(
            label = stringResource(R.string.tab_new),
            accentLabel = true,
            highlighted = highlightIndex == tabsStart + tabs.size,
            onClick = { onTabRowClick(tabs.size) },
            leading = { DeckIcon(DeckIcons.Plus, size = 16.dp, tint = DeckColors.Ruby) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.tab_reset_order),
            highlighted = highlightIndex == tabsStart + tabs.size + 1,
            onClick = { onTabRowClick(tabs.size + 1) },
            leading = { DeckIcon(DeckIcons.Refresh, size = 16.dp, tint = DeckColors.TextSecondary) },
        )
    }
    GroupFooter(stringResource(R.string.settings_tabs_desc))

    if (state.uncategorizedPlatforms.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        GroupHeader(stringResource(R.string.tab_uncategorized_title, state.uncategorizedCount))
        GroupCard {
            val quickCreateLabel = stringResource(R.string.tab_new)
            state.uncategorizedPlatforms.forEachIndexed { uIdx, info ->
                if (uIdx > 0) RowDivider()
                val rel = tabs.size + 2 + uIdx
                SettingRow(
                    label = "${info.platform.shortName} · ${info.platform.fullName}",
                    description = countFmt.format(info.gameCount),
                    highlighted = highlightIndex == tabsStart + rel,
                    onClick = { onTabRowClick(rel) },
                    trailing = {
                        ValuePill(quickCreateLabel, accent = true, onClick = { onQuickCreateCategory(info.platform) })
                    },
                )
            }
        }
    } else {
        Spacer(Modifier.height(6.dp))
        GroupFooter(stringResource(R.string.tab_uncategorized_all_done))
    }
}

// ════════════════════════════════════════════════════════════════
// 3. 游戏库 (Library)
// ════════════════════════════════════════════════════════════════

@Composable
internal fun LibrarySettingsSection(
    dirs: List<RomDirEntry>,
    autoRoot: String?,
    showAndroidGames: Boolean,
    hiddenCount: Int,
    onRowClick: (Int) -> Unit,
    highlightIndex: Int,
) {
    val extRoot = remember { Environment.getExternalStorageDirectory().path }
    val countFmt = stringResource(R.string.tab_games_count)
    val autoLabel = stringResource(R.string.settings_dir_auto)
    val disabledLabel = stringResource(R.string.settings_dir_disabled)

    GroupHeader(stringResource(R.string.settings_group_folders))
    GroupCard {
        if (dirs.isEmpty()) {
            SettingRow(
                label = autoRoot?.replace(extRoot, "~") ?: stringResource(R.string.settings_custom_bg_none),
                description = autoLabel,
                highlighted = false,
                onClick = {},
                leading = { DeckIcon(DeckIcons.Folder, size = 16.dp, tint = DeckColors.TextSecondary) },
            )
            RowDivider()
        } else {
            dirs.forEachIndexed { idx, d ->
                if (idx > 0) RowDivider()
                val display = d.path.replace(extRoot, "~")
                val badge = if (!d.enabled) disabledLabel else d.count?.let { countFmt.format(it) } ?: autoLabel
                SettingRow(
                    label = display,
                    description = if (d.path == autoRoot) autoLabel else null,
                    dimmed = !d.enabled,
                    highlighted = highlightIndex == idx,
                    onClick = { onRowClick(idx) },
                    leading = { DeckIcon(DeckIcons.Folder, size = 16.dp, tint = if (d.enabled) DeckColors.Ruby else DeckColors.TextLabel) },
                    trailing = { ValuePill(badge) },
                )
            }
            RowDivider()
        }
        SettingRow(
            label = stringResource(R.string.settings_dir_add),
            accentLabel = true,
            highlighted = highlightIndex == dirs.size,
            onClick = { onRowClick(dirs.size) },
            leading = { DeckIcon(DeckIcons.Plus, size = 16.dp, tint = DeckColors.Ruby) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_rescan),
            highlighted = highlightIndex == dirs.size + 1,
            onClick = { onRowClick(dirs.size + 1) },
            leading = { DeckIcon(DeckIcons.Refresh, size = 16.dp, tint = DeckColors.TextSecondary) },
        )
    }
    GroupFooter(stringResource(R.string.settings_dirs_desc))

    Spacer(Modifier.height(14.dp))
    GroupHeader(stringResource(R.string.settings_group_actions))
    GroupCard {
        SettingRow(
            label = stringResource(R.string.library_android_games),
            description = stringResource(R.string.library_android_games_desc),
            highlighted = highlightIndex == dirs.size + 2,
            onClick = { onRowClick(dirs.size + 2) },
            trailing = { DeckSwitch(showAndroidGames) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.library_hidden_manage, hiddenCount),
            highlighted = highlightIndex == dirs.size + 3,
            onClick = { onRowClick(dirs.size + 3) },
            leading = { DeckIcon(DeckIcons.EyeOff, size = 16.dp, tint = DeckColors.TextSecondary) },
            trailing = { ValueChevron(null) },
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 4. 封面与信息 (Covers)
// ════════════════════════════════════════════════════════════════

@Composable
internal fun CoversSettingsSection(
    coverSources: List<SourceItemConfig>,
    metadataSources: List<SourceItemConfig>,
    credentials: ScraperCredentials,
    cacheSizeLabel: String,
    onToggleCover: (String) -> Unit,
    onMoveCover: (Int, Int) -> Unit,
    onToggleMeta: (String) -> Unit,
    onMoveMeta: (Int, Int) -> Unit,
    onOpenCred: (CredentialKind) -> Unit,
    onRowClick: (Int) -> Unit,
    onRefreshMetadata: () -> Unit,
    onClearCoverCache: () -> Unit,
    onViewLogs: () -> Unit,
    highlightIndex: Int,
) {
    GroupHeader(stringResource(R.string.scraping_cover_title))
    GroupCard {
        DragReorderColumn(
            items = coverSources,
            keyOf = { it.id },
            onMove = onMoveCover,
        ) { idx, cfg, dragHandleModifier ->
            if (idx > 0) RowDivider()
            val desc = ScraperCatalog.coverDescriptor(cfg.id)
            if (desc != null) {
                SourceConfigRow(
                    index = idx,
                    config = cfg,
                    descriptor = desc,
                    credentials = credentials,
                    highlighted = highlightIndex == idx,
                    dragHandleModifier = dragHandleModifier,
                    onRowClick = { onRowClick(idx) },
                    onToggle = { onToggleCover(cfg.id) },
                    onOpenCred = onOpenCred,
                )
            }
        }
    }
    GroupFooter(stringResource(R.string.scraping_cover_desc))

    Spacer(Modifier.height(14.dp))
    val metaStart = coverSources.size
    GroupHeader(stringResource(R.string.scraping_meta_title))
    GroupCard {
        DragReorderColumn(
            items = metadataSources,
            keyOf = { it.id },
            onMove = onMoveMeta,
        ) { idx, cfg, dragHandleModifier ->
            if (idx > 0) RowDivider()
            val desc = ScraperCatalog.metaDescriptor(cfg.id)
            if (desc != null) {
                SourceConfigRow(
                    index = idx,
                    config = cfg,
                    descriptor = desc,
                    credentials = credentials,
                    highlighted = highlightIndex == metaStart + idx,
                    dragHandleModifier = dragHandleModifier,
                    onRowClick = { onRowClick(metaStart + idx) },
                    onToggle = { onToggleMeta(cfg.id) },
                    onOpenCred = onOpenCred,
                )
            }
        }
    }
    GroupFooter(stringResource(R.string.scraping_meta_desc))

    Spacer(Modifier.height(14.dp))
    val actionsStart = metaStart + metadataSources.size
    GroupHeader(stringResource(R.string.settings_group_actions))
    GroupCard {
        SettingRow(
            label = stringResource(R.string.scraping_refresh_meta),
            highlighted = highlightIndex == actionsStart,
            onClick = onRefreshMetadata,
            leading = { DeckIcon(DeckIcons.Refresh, size = 16.dp, tint = DeckColors.Ruby) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.scraping_clear_cache),
            description = stringResource(R.string.scraping_clear_cache_desc),
            highlighted = highlightIndex == actionsStart + 1,
            onClick = onClearCoverCache,
            leading = { DeckIcon(DeckIcons.Trash, size = 16.dp, tint = DeckColors.TextSecondary) },
            trailing = { ValuePill(cacheSizeLabel) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.scraping_view_logs),
            highlighted = highlightIndex == actionsStart + 2,
            onClick = onViewLogs,
            leading = { DeckIcon(DeckIcons.FileText, size = 16.dp, tint = DeckColors.TextSecondary) },
            trailing = { ValueChevron(null) },
        )
    }
}

@Composable
private fun SourceConfigRow(
    index: Int,
    config: SourceItemConfig,
    descriptor: SourceDescriptor,
    credentials: ScraperCredentials,
    highlighted: Boolean,
    dragHandleModifier: Modifier,
    onRowClick: () -> Unit,
    onToggle: () -> Unit,
    onOpenCred: (CredentialKind) -> Unit,
) {
    val credConfigured = ScraperCatalog.hasCredentials(descriptor.credentialKind, credentials)
    val badge = descriptor.credentialKind?.let {
        if (credConfigured) stringResource(R.string.scraping_account_set)
        else stringResource(R.string.scraping_needs_account)
    }
    SettingRow(
        label = "${index + 1}. ${stringResource(descriptor.nameRes)}",
        description = stringResource(descriptor.descRes),
        dimmed = !config.enabled,
        highlighted = highlighted,
        onClick = onRowClick,
        leading = { DragHandle(dragHandleModifier) },
        trailing = {
            if (badge != null) {
                ValuePill(
                    text = badge,
                    accent = !credConfigured,
                    onClick = descriptor.credentialKind?.let { kind -> { onOpenCred(kind) } },
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.soundClickable(onClick = onToggle)) {
                DeckSwitch(config.enabled)
            }
        },
    )
}

// ════════════════════════════════════════════════════════════════
// 5. 模拟器 (Emulators)
// ════════════════════════════════════════════════════════════════

@Composable
internal fun EmulatorsSettingsSection(
    platforms: List<Platform>,
    showAllPlatforms: Boolean,
    onToggleShowAllPlatforms: () -> Unit,
    selected: Map<String, String>,
    aspectOverrides: Map<String, Float>,
    onPickLauncher: (Platform) -> Unit,
    highlightIndex: Int,
) {
    val context = LocalContext.current
    val autoLabel = stringResource(R.string.settings_launcher_auto)

    GroupHeader(stringResource(R.string.settings_sec_emulators))
    GroupCard {
        SettingRow(
            label = if (showAllPlatforms) stringResource(R.string.settings_launcher_show_lib)
            else stringResource(R.string.settings_launcher_show_all),
            highlighted = highlightIndex == 0,
            onClick = onToggleShowAllPlatforms,
            trailing = { DeckSwitch(showAllPlatforms) },
        )
        if (platforms.isEmpty()) {
            RowDivider()
            SettingRow(
                label = stringResource(R.string.settings_launcher_empty),
                dimmed = true,
                highlighted = false,
                onClick = {},
            )
        } else {
            val launcherDescMap by produceState(
                initialValue = emptyMap<String, String>(),
                key1 = platforms,
                key2 = selected,
                key3 = autoLabel,
            ) {
                value = withContext(Dispatchers.IO) {
                    val map = HashMap<String, String>(platforms.size)
                    for (plat in platforms) {
                        val currentId = selected[plat.id]
                        map[plat.id] = GameLauncher.describeLauncher(context, plat.id, currentId)?.let { desc ->
                            if (currentId == null) "$autoLabel · $desc" else desc
                        } ?: autoLabel
                    }
                    map
                }
            }
            platforms.forEachIndexed { idx, plat ->
                RowDivider()
                val currentId = selected[plat.id]
                val currentName = launcherDescMap[plat.id] ?: autoLabel
                val aspect = aspectOverrides[plat.id] ?: plat.coverAspect
                val aspectTag = String.format(Locale.US, "%.2f:1", aspect)
                val isCustom = currentId != null
                SettingRow(
                    label = "${plat.shortName} · ${plat.fullName}",
                    description = currentName,
                    highlighted = highlightIndex == idx + 1,
                    onClick = { onPickLauncher(plat) },
                    trailing = {
                        ValuePill(aspectTag, accent = isCustom)
                        Spacer(Modifier.width(4.dp))
                        DeckIcon(DeckIcons.ChevronRight, tint = DeckColors.TextLabel, size = 15.dp)
                    },
                )
            }
        }
    }
    GroupFooter(stringResource(R.string.settings_launcher_desc))
}

// ════════════════════════════════════════════════════════════════
// 6. 操控 (Controls)
// ════════════════════════════════════════════════════════════════

@Composable
internal fun ControlsSettingsSection(
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onToggleSwapAbxy: () -> Unit,
    onRebindAction: (KeyAction) -> Unit,
    onResetBindings: () -> Unit,
    highlightIndex: Int,
) {
    val actions = KeyAction.entries
    val unboundLabel = stringResource(R.string.keybind_unbound)

    GroupHeader(stringResource(R.string.settings_sec_controls))
    GroupCard {
        SettingRow(
            label = stringResource(R.string.settings_swap_abxy),
            description = stringResource(R.string.settings_swap_desc),
            highlighted = highlightIndex == 0,
            onClick = onToggleSwapAbxy,
            trailing = { DeckSwitch(swapAbxy) },
        )
    }

    Spacer(Modifier.height(14.dp))
    GroupHeader(stringResource(R.string.settings_keybindings))
    GroupCard {
        actions.forEachIndexed { i, action ->
            if (i > 0) RowDivider()
            val badge = GamepadKeys.badgeLabel(keyBindings, action, swapAbxy)
            SettingRow(
                label = keyActionLabel(action),
                highlighted = highlightIndex == 1 + i,
                onClick = { onRebindAction(action) },
                trailing = {
                    if (badge != null) {
                        ButtonBadge(badge)
                    } else {
                        ValuePill(unboundLabel)
                    }
                },
            )
        }
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_keybindings_reset),
            accentLabel = true,
            highlighted = highlightIndex == 1 + actions.size,
            onClick = onResetBindings,
            leading = { DeckIcon(DeckIcons.Refresh, size = 16.dp, tint = DeckColors.Ruby) },
        )
    }
    GroupFooter(stringResource(R.string.settings_keybindings_desc))
}

// ════════════════════════════════════════════════════════════════
// 7. 系统 (System)
// ════════════════════════════════════════════════════════════════

@Composable
internal fun SystemSettingsSection(
    state: SettingsState,
    langPicker: Int?,
    onToggleLangPicker: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onToggleClock12h: () -> Unit,
    onToggleKeySound: () -> Unit,
    onOpenLanService: () -> Unit,
    onToggleShowClock: () -> Unit,
    onToggleShowBattery: () -> Unit,
    onToggleShowWifi: () -> Unit,
    onToggleShowBluetooth: () -> Unit,
    onToggleShowMobileSignal: () -> Unit,
    onToggleShowHeadset: () -> Unit,
    onToggleShowAirplane: () -> Unit,
    onToggleShowDnd: () -> Unit,
    onOpenProjectUrl: () -> Unit,
    highlightIndex: Int,
) {
    val context = LocalContext.current
    val systemLabel = stringResource(R.string.lang_system)
    val langLabels = mapOf(
        AppLocale.SYSTEM to systemLabel,
        "en" to "English",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
    )

    GroupHeader(stringResource(R.string.settings_group_general))
    GroupCard {
        SettingRow(
            label = stringResource(R.string.settings_language),
            highlighted = highlightIndex == 0 && langPicker == null,
            onClick = onToggleLangPicker,
            trailing = { ValueChevron(langLabels[state.language] ?: systemLabel) },
        )
        if (langPicker != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(DeckBrand.RadiusS))
                    .background(DeckColors.SurfaceAlt)
                    .padding(4.dp),
            ) {
                AppLocale.CODES.forEachIndexed { idx, code ->
                    val hl = idx == langPicker
                    val cur = code == state.language
                    SettingRow(
                        label = langLabels[code] ?: code,
                        highlighted = hl,
                        onClick = { onLanguageChange(code) },
                        trailing = { CheckMark(cur) },
                    )
                }
            }
        }
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_clock_12h),
            highlighted = highlightIndex == 1,
            onClick = onToggleClock12h,
            trailing = { DeckSwitch(state.clock12h) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_key_sound),
            description = stringResource(R.string.settings_key_sound_desc),
            highlighted = highlightIndex == 2,
            onClick = onToggleKeySound,
            leading = { DeckIcon(DeckIcons.Volume, size = 16.dp, tint = DeckColors.TextSecondary) },
            trailing = { DeckSwitch(state.keySoundEnabled) },
        )
    }

    // 网络与局域网传输
    Spacer(Modifier.height(14.dp))
    val lanStatusText = when {
        state.lanShareRunning && state.lanWebRunning -> stringResource(R.string.lan_status_both)
        state.lanWebRunning -> stringResource(R.string.lan_status_web)
        state.lanShareRunning -> stringResource(R.string.lan_status_share)
        else -> ""
    }
    GroupHeader(stringResource(R.string.lan_nic_title))
    GroupCard {
        SettingRow(
            label = stringResource(R.string.lan_service_title),
            description = stringResource(R.string.settings_lan_share_desc),
            highlighted = highlightIndex == 3,
            onClick = onOpenLanService,
            leading = {
                DeckIcon(
                    DeckIcons.Radio,
                    size = 16.dp,
                    tint = if (lanStatusText.isNotEmpty()) DeckColors.Ruby else DeckColors.TextSecondary,
                )
            },
            trailing = {
                ValueChevron(
                    value = lanStatusText,
                    chevron = true,
                    valueColor = if (lanStatusText.isNotEmpty()) DeckColors.Ruby else DeckColors.TextSecondary,
                )
            },
        )
    }

    // 状态栏图标（2 列紧凑网格）
    Spacer(Modifier.height(14.dp))
    val barItems = listOf(
        Triple(stringResource(R.string.settings_show_clock), state.showClock, onToggleShowClock),
        Triple(stringResource(R.string.settings_show_battery), state.showBattery, onToggleShowBattery),
        Triple(stringResource(R.string.settings_show_wifi), state.showWifi, onToggleShowWifi),
        Triple(stringResource(R.string.settings_show_bluetooth), state.showBluetooth, onToggleShowBluetooth),
        Triple(stringResource(R.string.settings_show_mobile), state.showMobileSignal, onToggleShowMobileSignal),
        Triple(stringResource(R.string.settings_show_headset), state.showHeadset, onToggleShowHeadset),
        Triple(stringResource(R.string.settings_show_airplane), state.showAirplane, onToggleShowAirplane),
        Triple(stringResource(R.string.settings_show_dnd), state.showDnd, onToggleShowDnd),
    )
    GroupHeader(stringResource(R.string.settings_statusbar))
    GroupCard {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            barItems.chunked(2).forEachIndexed { rowIdx, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEachIndexed { colIdx, (label, checked, toggle) ->
                        val itemIdx = 4 + rowIdx * 2 + colIdx
                        CompactCheckChip(
                            label = label,
                            checked = checked,
                            highlighted = highlightIndex == itemIdx,
                            onClick = toggle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // 关于卡片（含可手柄聚焦的源代码链接）
    Spacer(Modifier.height(14.dp))
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { BuildConfig.VERSION_NAME }
    }
    val repoDisplay = remember {
        BuildConfig.PROJECT_URL
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
    }

    GroupHeader(stringResource(R.string.settings_about))
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface(RoundedCornerShape(DeckBrand.RadiusM), elevation = 2.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PocketDeckAppIcon(size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PocketDeck",
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    color = DeckColors.TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                ValuePill("v$versionName", accent = true)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.settings_about_desc),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    GroupCard {
        SettingRow(
            label = stringResource(R.string.settings_version),
            highlighted = highlightIndex == 12,
            onClick = {},
            trailing = { ValueChevron("v$versionName", chevron = false) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_build),
            highlighted = highlightIndex == 13,
            onClick = {},
            trailing = { ValueChevron("#${BuildConfig.BUILD_NUMBER} · ${BuildConfig.BUILD_TIME}", chevron = false) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_android_compat),
            highlighted = highlightIndex == 14,
            onClick = {},
            trailing = { ValueChevron("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", chevron = false) },
        )
        RowDivider()
        SettingRow(
            label = stringResource(R.string.settings_project),
            highlighted = highlightIndex == 15,
            onClick = onOpenProjectUrl,
            trailing = { ValueChevron(repoDisplay, chevron = true, valueColor = DeckColors.Ruby) },
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 公共小件与辅助函数
// ════════════════════════════════════════════════════════════════

@Composable
private fun CompactCheckChip(
    label: String,
    checked: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalDeckColors.current
    val shape = RoundedCornerShape(DeckBrand.RadiusS)
    Row(
        modifier
            .bringIntoViewWhen(highlighted)
            .clip(shape)
            .background(if (highlighted) p.accentSoft else p.surfaceAlt)
            .then(
                if (highlighted) Modifier.border(1.5.dp, Brush.linearGradient(listOf(p.brandStart, p.brandMid, p.brandEnd)), shape)
                else Modifier,
            )
            .soundClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
            color = if (checked || highlighted) DeckColors.TextPrimary else DeckColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        CheckMark(checked)
    }
}

@Composable
internal fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        DeckIcon(DeckIcons.GripHorizontal, tint = DeckColors.TextLabel, size = 15.dp)
    }
}

@Composable
internal fun <T> DragReorderColumn(
    items: List<T>,
    keyOf: (T) -> String,
    onMove: (Int, Int) -> Unit,
    rowContent: @Composable (index: Int, item: T, dragHandleModifier: Modifier) -> Unit,
) {
    var localItems by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartIdx by remember { mutableIntStateOf(-1) }
    val itemHeights = remember { HashMap<String, Float>() }

    val latestItems by rememberUpdatedState(items)
    val latestOnMove by rememberUpdatedState(onMove)

    LaunchedEffect(items, draggingKey) {
        if (draggingKey == null) localItems = items
    }

    Column(Modifier.fillMaxWidth()) {
        localItems.forEachIndexed { idx, item ->
            val itemKey = keyOf(item)
            key(itemKey) {
                val isDragging = draggingKey == itemKey
                Box(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 2f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            alpha = if (isDragging) 0.92f else 1f
                        }
                        .onSizeChanged { itemHeights[itemKey] = it.height.toFloat() },
                ) {
                    val handleModifier = Modifier.pointerInput(itemKey) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                draggingKey = itemKey
                                dragStartIdx = localItems.indexOfFirst { keyOf(it) == itemKey }
                                dragOffsetY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount
                                val cur = localItems.indexOfFirst { keyOf(it) == itemKey }
                                if (cur < 0) return@detectVerticalDragGestures
                                val h = itemHeights[itemKey] ?: 100f
                                if (dragOffsetY > h * 0.55f && cur < localItems.lastIndex) {
                                    val nextKey = keyOf(localItems[cur + 1])
                                    val swapH = itemHeights[nextKey] ?: h
                                    val list = localItems.toMutableList()
                                    list.add(cur + 1, list.removeAt(cur))
                                    localItems = list
                                    dragOffsetY -= swapH
                                } else if (dragOffsetY < -h * 0.55f && cur > 0) {
                                    val prevKey = keyOf(localItems[cur - 1])
                                    val swapH = itemHeights[prevKey] ?: h
                                    val list = localItems.toMutableList()
                                    list.add(cur - 1, list.removeAt(cur))
                                    localItems = list
                                    dragOffsetY += swapH
                                }
                            },
                            onDragEnd = {
                                val endIdx = localItems.indexOfFirst { keyOf(it) == itemKey }
                                val startIdx = dragStartIdx
                                draggingKey = null
                                dragOffsetY = 0f
                                dragStartIdx = -1
                                if (startIdx >= 0 && endIdx >= 0 && startIdx != endIdx) {
                                    latestOnMove(startIdx, endIdx)
                                }
                            },
                            onDragCancel = {
                                draggingKey = null
                                dragOffsetY = 0f
                                dragStartIdx = -1
                                localItems = latestItems
                            },
                        )
                    }
                    rowContent(idx, item, handleModifier)
                }
            }
        }
    }
}

@Composable
internal fun bgStyleLabel(style: BackgroundStyle): String = when (style) {
    BackgroundStyle.SunsetFlow -> stringResource(R.string.bg_style_sunset_flow)
    BackgroundStyle.PixelDrift -> stringResource(R.string.bg_style_pixel_drift)
    BackgroundStyle.RetroGrid -> stringResource(R.string.bg_style_retro_grid)
    BackgroundStyle.CoverGlow -> stringResource(R.string.bg_style_cover_glow)
    BackgroundStyle.Aurora -> stringResource(R.string.bg_style_aurora)
    BackgroundStyle.Image -> stringResource(R.string.bg_style_image)
    BackgroundStyle.None -> stringResource(R.string.bg_style_none)
}

@Composable
internal fun sortOrderLabel(order: CategorySortOrder): String = when (order) {
    CategorySortOrder.TitleAsc -> stringResource(R.string.sort_title_asc)
    CategorySortOrder.TitleDesc -> stringResource(R.string.sort_title_desc)
    CategorySortOrder.ReleaseNewest -> stringResource(R.string.sort_release_newest)
    CategorySortOrder.ReleaseOldest -> stringResource(R.string.sort_release_oldest)
    CategorySortOrder.ModifiedNewest -> stringResource(R.string.sort_modified_newest)
    CategorySortOrder.SizeDesc -> stringResource(R.string.sort_size_desc)
    CategorySortOrder.SizeAsc -> stringResource(R.string.sort_size_asc)
    CategorySortOrder.LastPlayedDesc -> stringResource(R.string.sort_last_played_desc)
    CategorySortOrder.PlayTimeDesc -> stringResource(R.string.sort_play_time_desc)
}

@Composable
internal fun keyActionLabel(action: KeyAction): String = when (action) {
    KeyAction.PrevTab -> stringResource(R.string.key_action_prev_tab)
    KeyAction.NextTab -> stringResource(R.string.key_action_next_tab)
    KeyAction.Apps -> stringResource(R.string.key_action_apps)
    KeyAction.Settings -> stringResource(R.string.key_action_settings)
    KeyAction.Play -> stringResource(R.string.key_action_play)
    KeyAction.Back -> stringResource(R.string.key_action_back)
    KeyAction.Menu -> stringResource(R.string.key_action_menu)
    KeyAction.ViewMode -> stringResource(R.string.key_action_view_mode)
    KeyAction.Search -> stringResource(R.string.key_action_search)
}

@Composable
private fun metaFieldLabel(field: MetaFieldKey): String = when (field) {
    MetaFieldKey.ReleaseDate -> stringResource(R.string.meta_release_date)
    MetaFieldKey.Developer -> stringResource(R.string.meta_developer)
    MetaFieldKey.Publisher -> stringResource(R.string.meta_publisher)
    MetaFieldKey.FileSize -> stringResource(R.string.meta_file_size)
}

@Composable
internal fun fontDisplayName(fontId: String): String = SystemFonts.displayName(
    fontId = fontId,
    defaultLabel = stringResource(R.string.font_default),
    sansLabel = stringResource(R.string.font_system_sans),
    serifLabel = stringResource(R.string.font_system_serif),
    monoLabel = stringResource(R.string.font_system_mono),
    missingFmt = stringResource(R.string.font_missing_fallback),
)

internal fun computeScrapeCacheBytes(cacheDir: File): Long {
    val dirs = listOf("covers", "web_covers", "boxart_index", "metadata")
    var total = 0L
    for (name in dirs) {
        val dir = File(cacheDir, name)
        if (!dir.exists()) continue
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
    }
    return total
}

internal fun formatCacheBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

internal fun openProjectUrl(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PROJECT_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

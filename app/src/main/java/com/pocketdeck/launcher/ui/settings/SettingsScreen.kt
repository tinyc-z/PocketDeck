package com.pocketdeck.launcher.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.data.AppLocale
import com.pocketdeck.launcher.data.AppTheme
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.CredentialKind
import com.pocketdeck.launcher.data.CustomLauncherPreset
import com.pocketdeck.launcher.data.GalleryAlign
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.SourceItemConfig
import com.pocketdeck.launcher.data.SystemTabConfig
import com.pocketdeck.launcher.data.ThemeAccent
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.home.MetaFieldKey
import com.pocketdeck.launcher.ui.home.RomDirEntry
import com.pocketdeck.launcher.ui.home.TabEntry
import com.pocketdeck.launcher.ui.home.TabType
import com.pocketdeck.launcher.ui.home.UncategorizedPlatformInfo
import com.pocketdeck.launcher.ui.theme.SystemFonts
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设置页面（Sunset Arcade）：7 个意图清晰的分区 + 左侧图标导航 + 右侧分组白卡。
 * 支持 D-Pad 上下左右、L1/R1 切换分区、A 确认、B 返回。
 */
data class SettingsState(
    val romRoot: String?,
    val clock12h: Boolean,
    val swapAbxy: Boolean,
    val nowTab: Boolean,
    val favsTab: Boolean,
    val bottomBar: Boolean,
    val showMetadata: Boolean,
    val backgroundStyle: BackgroundStyle,
    val customBackground: String? = null,
    val viewMode: ViewMode,
    val language: String = "system",
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
    val showMobileSignal: Boolean = true,
    val showHeadset: Boolean = true,
    val showAirplane: Boolean = true,
    val showDnd: Boolean = true,
    val platformLaunchers: Map<String, String> = emptyMap(),
    val launcherRows: List<LauncherRow> = emptyList(),
    val galleryAlign: GalleryAlign = GalleryAlign.Top,
    val romDirs: List<RomDirEntry> = emptyList(),
    val autoRoot: String? = null,
    val manageTabs: List<TabEntry> = emptyList(),
    val theme: AppTheme = AppTheme.System,
    val coverSources: List<SourceItemConfig> = ScraperCatalog.defaultCoverConfigs(),
    val metadataSources: List<SourceItemConfig> = ScraperCatalog.defaultMetadataConfigs(),
    val scraperCredentials: ScraperCredentials = ScraperCredentials(),
    val metaDisplay: MetadataDisplayConfig = MetadataDisplayConfig(),
    val categories: List<Category> = emptyList(),
    val libraryPlatforms: List<Platform> = emptyList(),
    val hiddenIds: Set<String> = emptySet(),
    val showAndroidGames: Boolean = true,
    val keyBindings: Map<KeyAction, Int> = emptyMap(),
    val keySoundEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val themeAccent: ThemeAccent = ThemeAccent.Sunset,
    val fontId: String = SystemFonts.DEFAULT_ID,
    val platformAspects: Map<String, Float> = emptyMap(),
    val customLaunchers: List<CustomLauncherPreset> = emptyList(),
    val systemTabs: Map<String, SystemTabConfig> = emptyMap(),
    val uncategorizedPlatforms: List<UncategorizedPlatformInfo> = emptyList(),
    val uncategorizedCount: Int = 0,
    val lanShareRunning: Boolean = false,
    val lanWebRunning: Boolean = false,
    val scrapeProgress: com.pocketdeck.launcher.data.ScrapeProgress = com.pocketdeck.launcher.data.ScrapeProgress(),
    val quickHudEnabled: Boolean = false,
)

class ManageCallbacks(
    val addDir: (String) -> Unit = {},
    val toggleDir: (String) -> Unit = {},
    val removeDir: (String) -> Unit = {},
    val moveTab: (String, Int) -> Unit = { _, _ -> },
    val moveTabTo: (Int, Int) -> Unit = { _, _ -> },
    val toggleTabHidden: (String) -> Unit = {},
    val updateSystemTab: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val createCategory: (String, List<String>) -> Unit = { _, _ -> },
    val quickCreateCategoryForPlatform: (Platform) -> Unit = {},
    val addPlatformToCategory: (String, String) -> Unit = { _, _ -> },
    val setCategorySources: (String, List<String>) -> Unit = { _, _ -> },
    val renameCategory: (String, String) -> Unit = { _, _ -> },
    val setCategorySortOrder: (String, CategorySortOrder?) -> Unit = { _, _ -> },
    val deleteCategory: (String) -> Unit = {},
    val mergeTab: (String, String) -> Unit = { _, _ -> },
    val resetTabOrder: () -> Unit = {},
    val toggleCoverSource: (String) -> Unit = {},
    val moveCoverSource: (Int, Int) -> Unit = { _, _ -> },
    val toggleMetadataSource: (String) -> Unit = {},
    val moveMetadataSource: (Int, Int) -> Unit = { _, _ -> },
    val saveCredentials: (ScraperCredentials) -> Unit = {},
    val clearCoverCache: suspend () -> Unit = {},
    val scrapeMissing: () -> Int = { 0 },
    val cancelScrape: () -> Unit = {},
    val dismissScrapeProgress: () -> Unit = {},
    val refreshAllMetadata: () -> Unit = {},
    val toggleMetaField: (MetaFieldKey) -> Unit = {},
    val unhideGame: (String) -> Unit = {},
    val toggleShowAndroidGames: () -> Unit = {},
    val setThemeAccent: (ThemeAccent) -> Unit = {},
    val setFontId: (String) -> Unit = {},
    val setPlatformAspect: (String, Float?) -> Unit = { _, _ -> },
    val saveCustomLauncherPreset: (String, String) -> Unit = { _, _ -> },
    val deleteCustomLauncherPreset: (String) -> Unit = {},
    val sampleGameFor: (String) -> com.pocketdeck.launcher.data.model.Game? = { null },
    val openLibraryHidden: () -> Unit = {},
    val openLanService: () -> Unit = {},
    val toggleQuickHudEnabled: () -> Unit = {},
)

data class LauncherRow(
    val platformId: String,
    val platformName: String,
    val options: List<Pair<String, String>>,
)

enum class SettingsSection {
    Look, Home, Library, Covers, Emulators, Controls, System
}

private class SettingsNavState {
    var selectedSection by mutableIntStateOf(0)
    var focusOnMenu by mutableStateOf(true)
    var contentItemIndex by mutableIntStateOf(0)
}

private data class BindingCategorySpec(
    val catId: String?,
    val name: String,
    val initialSources: List<String>,
)

private class ManageLabels(
    val enable: String,
    val disable: String,
    val remove: String,
    val moveUp: String,
    val moveDown: String,
    val show: String,
    val hide: String,
    val rename: String,
    val merge: String,
    val deleteCat: String,
    val mergeTitle: String,
    val catNameTitle: String,
    val catDefault: String,
)

private fun itemCountForSection(
    section: SettingsSection,
    state: SettingsState,
    showAllLauncherPlatforms: Boolean = false,
): Int = when (section) {
    SettingsSection.Look -> {
        val img = if (state.backgroundStyle == BackgroundStyle.Image) 1 else 0
        // Mode(0), Color(1), BgThumbs(2), [CustomBg(3)], ReduceMotion, Font
        5 + img
    }
    SettingsSection.Home -> {
        val align = if (state.viewMode == ViewMode.Gallery) 1 else 0
        val metaFields = if (state.showMetadata) MetaFieldKey.entries.size else 0
        val tabsTotal = state.manageTabs.size + 2 + state.uncategorizedPlatforms.size
        ViewMode.entries.size + align + 2 + metaFields + tabsTotal
    }
    SettingsSection.Library -> state.romDirs.size + 4
    SettingsSection.Covers -> state.coverSources.size + state.metadataSources.size + 3
    SettingsSection.Emulators -> 2 + launcherPlatformsFor(state, showAllLauncherPlatforms).size
    SettingsSection.Controls -> 1 + KeyAction.entries.size + 1
    // Language(0), 12h(1), UI sounds(2), LAN & Share(3), StatusBar x8(4..11), Experimental(12), About x5(13..17)
    SettingsSection.System -> 18
}

private fun launcherPlatformsFor(
    state: SettingsState,
    showAllKnown: Boolean = false,
): List<Platform> {
    val fromLib = state.libraryPlatforms.filter { it.id != PlatformCatalog.ANDROID_ID }
    if (showAllKnown) {
        return (fromLib + PlatformCatalog.known.filter { it.id != PlatformCatalog.ANDROID_ID })
            .distinctBy { it.id }
            .map { p ->
                val aspect = state.platformAspects[p.id]
                if (aspect != null) p.copy(coverAspect = aspect) else p
            }
    }
    if (fromLib.isNotEmpty()) return fromLib
    return state.launcherRows.map { PlatformCatalog.forFolder(it.platformId) }
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onDismiss: () -> Unit,
    onToggleClock12h: () -> Unit,
    onToggleSwapAbxy: () -> Unit,
    onToggleKeySound: () -> Unit = {},
    onToggleReduceMotion: () -> Unit = {},
    onToggleNowTab: () -> Unit,
    onToggleFavsTab: () -> Unit,
    onToggleBottomBar: () -> Unit,
    onToggleShowMetadata: () -> Unit,
    onCycleBackground: () -> Unit,
    onSetBackgroundStyle: (BackgroundStyle) -> Unit = {},
    onSetCustomBackground: (String?) -> Unit = {},
    onUpdateKeyBindings: (Map<KeyAction, Int>) -> Unit = {},
    onResetKeyBindings: () -> Unit = {},
    onCapturingKeyBindChange: (Boolean) -> Unit = {},
    onCycleViewMode: () -> Unit,
    onSetViewMode: (ViewMode) -> Unit = {},
    onCycleTheme: () -> Unit = {},
    onRescan: () -> Unit,
    onLanguageChange: (String) -> Unit = {},
    onToggleShowClock: () -> Unit = {},
    onToggleShowBattery: () -> Unit = {},
    onToggleShowWifi: () -> Unit = {},
    onToggleShowBluetooth: () -> Unit = {},
    onToggleShowMobileSignal: () -> Unit = {},
    onToggleShowHeadset: () -> Unit = {},
    onToggleShowAirplane: () -> Unit = {},
    onToggleShowDnd: () -> Unit = {},
    onSetPlatformLauncher: (platformId: String, emulatorId: String?) -> Unit = { _, _ -> },
    onCycleGalleryAlign: () -> Unit = {},
    manage: ManageCallbacks = ManageCallbacks(),
    isCovered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menu by remember { mutableStateOf<ActionMenuState?>(null) }
    val browser = remember(context) { DirBrowserState(context.applicationContext) }
    var browsing by remember { mutableStateOf(false) }
    var nameSpec by remember { mutableStateOf<NameDialogSpec?>(null) }
    val nameState = remember(nameSpec) { nameSpec?.let { NameDialogState(it) } }
    var credDialogKind by remember { mutableStateOf<CredentialKind?>(null) }
    var showScrapeLogs by remember { mutableStateOf(false) }
    var bindingCategorySpec by remember { mutableStateOf<BindingCategorySpec?>(null) }
    var launcherPickerPlatform by remember { mutableStateOf<Platform?>(null) }
    var showHiddenDialog by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }
    var editingSystemTab by remember { mutableStateOf<TabEntry?>(null) }
    var accentPicker by remember { mutableStateOf<Int?>(null) }
    var bgPicker by remember { mutableStateOf<Int?>(null) }
    var showAllLauncherPlatforms by rememberSaveable { mutableStateOf(false) }
    var capturingAction by remember { mutableStateOf<KeyAction?>(null) }
    var keyBindError by remember { mutableStateOf<String?>(null) }
    var pendingKeyConflict by remember { mutableStateOf<PendingKeyConflict?>(null) }
    var conflictChoiceIdx by remember { mutableIntStateOf(0) }
    var showResetBindingsConfirm by remember { mutableStateOf(false) }
    var resetConfirmChoiceIdx by remember { mutableIntStateOf(1) }

    val fontInvalidMsg = stringResource(R.string.font_invalid_file)
    val pickFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            SystemFonts.importFromUri(context, uri)
                .onSuccess { fontId -> manage.setFontId(fontId); showFontPicker = false }
                .onFailure { Toast.makeText(context, fontInvalidMsg, Toast.LENGTH_SHORT).show() }
        }
    }

    val pickBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "backgrounds")
                com.pocketdeck.launcher.data.ImageImportHelper
                    .saveOptimizedBackgroundFromUri(context, uri, dir)
                    ?.absolutePath
            }
            if (savedPath != null) onSetCustomBackground(savedPath)
            else Toast.makeText(context, context.getString(R.string.edit_image_failed), Toast.LENGTH_SHORT).show()
        }
    }

    var raCoreRefreshTick by remember { mutableIntStateOf(0) }
    var raCoreCount by remember { mutableIntStateOf(0) }
    var raSafAuthorized by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableRelease by remember { mutableStateOf<com.pocketdeck.launcher.data.ReleaseInfo?>(null) }
    var updateDownloadProgress by remember { mutableStateOf<Float?>(null) }
    fun triggerCheckUpdate() {
        if (checkingUpdate) return
        if (!NetworkStatus.isOnline(context)) {
            Toast.makeText(context, context.getString(R.string.offline_action_toast), Toast.LENGTH_SHORT).show()
            return
        }
        checkingUpdate = true
        scope.launch {
            val curVer = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { com.pocketdeck.launcher.BuildConfig.VERSION_NAME }
            val res = com.pocketdeck.launcher.data.UpdateChecker.checkLatestRelease(curVer)
            checkingUpdate = false
            res.onSuccess { info ->
                if (info != null) {
                    availableRelease = info
                } else {
                    Toast.makeText(context, context.getString(R.string.update_up_to_date), Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    context.getString(R.string.update_check_failed, err.message.orEmpty()),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    LaunchedEffect(raCoreRefreshTick) {
        val (cnt, authed) = withContext(Dispatchers.IO) {
            val scanned = com.pocketdeck.launcher.launch.RetroArchCoreCatalog.scanSafAndExternalCores(context)
            val uri = com.pocketdeck.launcher.launch.RetroArchCoreCatalog.getSafCoresUri(context)
            scanned.size to (uri != null)
        }
        raCoreCount = cnt
        raSafAuthorized = authed
    }
    val raSafPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        com.pocketdeck.launcher.launch.RetroArchCoreCatalog.saveSafCoresUri(context, uri)
        raCoreRefreshTick++
        scope.launch {
            val cnt = withContext(Dispatchers.IO) {
                com.pocketdeck.launcher.launch.RetroArchCoreCatalog.scanSafAndExternalCores(context).size
            }
            raCoreCount = cnt
            raSafAuthorized = true
            Toast.makeText(
                context,
                context.getString(R.string.settings_ra_cores_scanned_toast, cnt),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(capturingAction, pendingKeyConflict) {
        onCapturingKeyBindChange(capturingAction != null || pendingKeyConflict != null)
    }

    LaunchedEffect(
        nameSpec, browsing, credDialogKind, showScrapeLogs, bindingCategorySpec,
        launcherPickerPlatform, showHiddenDialog, showFontPicker, editingSystemTab,
        capturingAction, pendingKeyConflict, showResetBindingsConfirm, isCovered,
    ) {
        if (nameSpec == null && !browsing && credDialogKind == null && !showScrapeLogs &&
            bindingCategorySpec == null && launcherPickerPlatform == null && !showHiddenDialog &&
            !showFontPicker && editingSystemTab == null && capturingAction == null &&
            pendingKeyConflict == null && !showResetBindingsConfirm && !isCovered
        ) {
            focusRequester.requestFocus()
        }
    }

    val L = ManageLabels(
        enable = stringResource(R.string.action_enable),
        disable = stringResource(R.string.action_disable),
        remove = stringResource(R.string.action_remove),
        moveUp = stringResource(R.string.action_move_up),
        moveDown = stringResource(R.string.action_move_down),
        show = stringResource(R.string.action_show),
        hide = stringResource(R.string.action_hide),
        rename = stringResource(R.string.action_rename),
        merge = stringResource(R.string.action_merge),
        deleteCat = stringResource(R.string.action_delete_cat),
        mergeTitle = stringResource(R.string.merge_title),
        catNameTitle = stringResource(R.string.cat_name_title),
        catDefault = stringResource(R.string.cat_default_name),
    )
    val bindPlatformsLabel = stringResource(R.string.action_bind_platforms)
    val accountAddLabel = stringResource(R.string.scraping_account_add)
    val coverNames = ScraperCatalog.coverNodes.associate { it.id to stringResource(it.nameRes) }
    val metaNames = ScraperCatalog.metadataProviders.associate { it.id to stringResource(it.nameRes) }
    val systemNowLabel = stringResource(R.string.tab_name_now)
    val systemFavsLabel = stringResource(R.string.tab_name_favs)
    val systemOtherLabel = stringResource(R.string.tab_name_other)
    val quickCreateLabel = stringResource(R.string.tab_new)
    val addToExistingLabel = stringResource(R.string.tab_add_to_existing_cat)
    val pickCatTitleFmt = stringResource(R.string.tab_pick_category_title)
    val sortTitleLabel = stringResource(R.string.tab_sort_order)
    val sortMenuFmt = stringResource(R.string.tab_sort_order_menu)
    val sortRecentLabel = stringResource(R.string.sort_recent)
    val sortNames = mapOf(
        CategorySortOrder.TitleAsc to stringResource(R.string.sort_title_asc),
        CategorySortOrder.TitleDesc to stringResource(R.string.sort_title_desc),
        CategorySortOrder.ReleaseNewest to stringResource(R.string.sort_release_newest),
        CategorySortOrder.ReleaseOldest to stringResource(R.string.sort_release_oldest),
        CategorySortOrder.ModifiedNewest to stringResource(R.string.sort_modified_newest),
        CategorySortOrder.SizeDesc to stringResource(R.string.sort_size_desc),
        CategorySortOrder.SizeAsc to stringResource(R.string.sort_size_asc),
        CategorySortOrder.LastPlayedDesc to stringResource(R.string.sort_last_played_desc),
        CategorySortOrder.PlayTimeDesc to stringResource(R.string.sort_play_time_desc),
    )
    val reservedKeyError = stringResource(R.string.keybind_reserved)

    val sections = SettingsSection.entries
    // 独立导航状态容器：不在 SettingsScreen 顶层读取，确保切分区/上下移动焦点时零顶层重组，所有非活跃分区零重组
    val nav = remember { SettingsNavState() }
    var langPicker by remember { mutableStateOf<Int?>(null) }

    fun toggleLangPicker() {
        langPicker = if (langPicker == null) AppLocale.CODES.indexOf(state.language).coerceAtLeast(0) else null
    }
    fun toggleAccentPicker() {
        accentPicker = if (accentPicker == null) ThemeAccent.entries.indexOf(state.themeAccent).coerceAtLeast(0) else null
    }
    fun toggleBgPicker() {
        bgPicker = if (bgPicker == null) BackgroundStyle.entries.indexOf(state.backgroundStyle).coerceAtLeast(0) else null
    }
    fun selectSection(index: Int) {
        nav.selectedSection = index.coerceIn(0, sections.lastIndex)
        nav.contentItemIndex = 0
        langPicker = null
        accentPicker = null
        bgPicker = null
    }

    var scrapeCacheBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(nav) {
        snapshotFlow { sections[nav.selectedSection] }.collect { sec ->
            if (sec == SettingsSection.Covers) {
                scrapeCacheBytes = withContext(Dispatchers.IO) { computeScrapeCacheBytes(context.cacheDir) }
            }
        }
    }
    fun clearScrapeCacheWithFeedback() {
        scope.launch {
            val beforeBytes = withContext(Dispatchers.IO) { computeScrapeCacheBytes(context.cacheDir) }
            manage.clearCoverCache()
            val afterBytes = withContext(Dispatchers.IO) { computeScrapeCacheBytes(context.cacheDir) }
            scrapeCacheBytes = afterBytes
            val freed = (beforeBytes - afterBytes).coerceAtLeast(0L)
            Toast.makeText(
                context,
                context.getString(R.string.scraping_cache_cleared_toast, formatCacheBytes(freed)),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun resolvedTabLabel(t: TabEntry): String {
        if (t.type == TabType.Custom) return t.label
        if (!t.followSystemLanguage && t.label.isNotBlank()) return t.label
        return when (t.type) {
            TabType.Now -> systemNowLabel
            TabType.Favs -> systemFavsLabel
            TabType.Other -> systemOtherLabel
            TabType.Custom -> t.label
        }
    }

    fun applyKeyBinding(action: KeyAction, keyCode: Int) {
        onUpdateKeyBindings(GamepadKeys.withBinding(state.keyBindings, action, keyCode, state.swapAbxy))
    }

    fun openTabAction(relIdx: Int) {
        val tabs = state.manageTabs
        if (relIdx == tabs.size) {
            val n = tabs.count { it.type == TabType.Custom } + 1
            nameSpec = NameDialogSpec(L.catNameTitle, L.catDefault.format(n)) { newName ->
                bindingCategorySpec = BindingCategorySpec(null, newName, emptyList())
            }
            return
        }
        if (relIdx == tabs.size + 1) {
            manage.resetTabOrder()
            return
        }
        if (relIdx >= tabs.size + 2) {
            val info = state.uncategorizedPlatforms.getOrNull(relIdx - (tabs.size + 2)) ?: return
            val customCats = state.categories
            if (customCats.isEmpty()) {
                manage.quickCreateCategoryForPlatform(info.platform)
            } else {
                menu = ActionMenuState(
                    "${info.platform.shortName} · ${info.platform.fullName}",
                    listOf(
                        ActionItem("$quickCreateLabel (${info.platform.shortName})") {
                            manage.quickCreateCategoryForPlatform(info.platform)
                        },
                        ActionItem(addToExistingLabel) {
                            menu = ActionMenuState(
                                pickCatTitleFmt.format(info.platform.shortName),
                                customCats.map { cat ->
                                    ActionItem(cat.name) { manage.addPlatformToCategory(cat.id, info.platform.id) }
                                },
                            )
                        },
                    ),
                )
            }
            return
        }
        val t = tabs.getOrNull(relIdx) ?: return
        val displayName = resolvedTabLabel(t)
        val items = mutableListOf<ActionItem>()
        if (t.type == TabType.Custom) {
            val existingSources = state.categories.firstOrNull { it.id == t.id }?.sources.orEmpty()
            items += ActionItem(bindPlatformsLabel) {
                bindingCategorySpec = BindingCategorySpec(t.id, t.label, existingSources)
            }
        }
        val currentSort: CategorySortOrder? = when (t.type) {
            TabType.Now -> state.systemTabs["now"]?.sortOrder
            TabType.Favs -> state.systemTabs["favs"]?.sortOrder ?: CategorySortOrder.TitleAsc
            TabType.Other -> state.systemTabs["other"]?.sortOrder ?: CategorySortOrder.TitleAsc
            TabType.Custom -> state.categories.firstOrNull { it.id == t.id }?.sortOrder ?: CategorySortOrder.TitleAsc
        }
        val currentSortLabel = if (t.type == TabType.Now && currentSort == null) sortRecentLabel
        else sortNames[currentSort ?: CategorySortOrder.TitleAsc].orEmpty()
        items += ActionItem(sortMenuFmt.format(currentSortLabel)) {
            val subItems = buildList {
                if (t.type == TabType.Now) {
                    val mark = if (currentSort == null) "• " else ""
                    add(ActionItem("$mark$sortRecentLabel") { manage.setCategorySortOrder(t.id, null) })
                }
                CategorySortOrder.entries.forEach { order ->
                    val mark = if (order == currentSort) "• " else ""
                    add(ActionItem("$mark${sortNames[order].orEmpty()}") { manage.setCategorySortOrder(t.id, order) })
                }
            }
            menu = ActionMenuState("$displayName · $sortTitleLabel", subItems)
        }
        if (relIdx > 0) items += ActionItem(L.moveUp) { manage.moveTab(t.id, -1); nav.contentItemIndex = (nav.contentItemIndex - 1).coerceAtLeast(0) }
        if (relIdx < tabs.lastIndex) items += ActionItem(L.moveDown) { manage.moveTab(t.id, 1); nav.contentItemIndex++ }
        items += ActionItem(if (t.hidden) L.show else L.hide) { manage.toggleTabHidden(t.id) }
        if (t.type != TabType.Custom) {
            items += ActionItem(L.rename) { editingSystemTab = t }
        } else {
            items += ActionItem(L.rename) {
                nameSpec = NameDialogSpec(L.catNameTitle, t.label) { manage.renameCategory(t.id, it) }
            }
            val targets = tabs.filter { it.id != t.id && it.type == TabType.Custom }
            if (targets.isNotEmpty()) {
                items += ActionItem(L.merge) {
                    menu = ActionMenuState(
                        L.mergeTitle.format(displayName),
                        targets.map { d -> ActionItem(resolvedTabLabel(d)) { manage.mergeTab(t.id, d.id) } },
                    )
                }
            }
            items += ActionItem(L.deleteCat) { manage.deleteCategory(t.id) }
        }
        menu = ActionMenuState(displayName, items)
    }

    fun triggerItem(section: SettingsSection, idx: Int) {
        when (section) {
            SettingsSection.Look -> {
                val hasImg = state.backgroundStyle == BackgroundStyle.Image
                val motionIdx = if (hasImg) 4 else 3
                val fontIdx = motionIdx + 1
                when (idx) {
                    0 -> onCycleTheme()
                    1 -> toggleAccentPicker()
                    2 -> toggleBgPicker()
                    3 -> if (hasImg) pickBgLauncher.launch("image/*") else onToggleReduceMotion()
                    motionIdx -> onToggleReduceMotion()
                    fontIdx -> showFontPicker = true
                }
            }
            SettingsSection.Home -> {
                val viewModes = ViewMode.entries
                var cursor = viewModes.size
                if (idx < cursor) {
                    val mode = viewModes[idx]
                    onSetViewMode(mode)
                    return
                }
                if (state.viewMode == ViewMode.Gallery) {
                    if (idx == cursor) { onCycleGalleryAlign(); return }
                    cursor++
                }
                if (idx == cursor) { onToggleBottomBar(); return }
                cursor++
                if (idx == cursor) { onToggleShowMetadata(); return }
                cursor++
                if (state.showMetadata) {
                    val fields = MetaFieldKey.entries
                    if (idx < cursor + fields.size) {
                        manage.toggleMetaField(fields[idx - cursor])
                        return
                    }
                    cursor += fields.size
                }
                openTabAction(idx - cursor)
            }
            SettingsSection.Library -> {
                val dirs = state.romDirs
                when {
                    idx < dirs.size -> {
                        val d = dirs[idx]
                        menu = ActionMenuState(
                            d.path.replace(Environment.getExternalStorageDirectory().path, "~"),
                            listOf(
                                ActionItem(if (d.enabled) L.disable else L.enable) { manage.toggleDir(d.path) },
                                ActionItem(L.remove) {
                                    manage.removeDir(d.path)
                                    nav.contentItemIndex = (idx - 1).coerceAtLeast(0)
                                },
                            ),
                        )
                    }
                    idx == dirs.size -> { browser.reset(); browsing = true }
                    idx == dirs.size + 1 -> onRescan()
                    idx == dirs.size + 2 -> manage.toggleShowAndroidGames()
                    else -> manage.openLibraryHidden()
                }
            }
            SettingsSection.Covers -> {
                val coverCount = state.coverSources.size
                val metaCount = state.metadataSources.size
                val actionsStart = coverCount + metaCount
                when {
                    idx < coverCount -> {
                        val cfg = state.coverSources[idx]
                        val desc = ScraperCatalog.coverDescriptor(cfg.id) ?: return
                        val items = mutableListOf(
                            ActionItem(if (cfg.enabled) L.disable else L.enable) { manage.toggleCoverSource(cfg.id) },
                        )
                        if (idx > 0) items += ActionItem(L.moveUp) { manage.moveCoverSource(idx, idx - 1); nav.contentItemIndex = idx - 1 }
                        if (idx < coverCount - 1) items += ActionItem(L.moveDown) { manage.moveCoverSource(idx, idx + 1); nav.contentItemIndex = idx + 1 }
                        desc.credentialKind?.let { k -> items += ActionItem(accountAddLabel) { credDialogKind = k } }
                        menu = ActionMenuState(coverNames[cfg.id] ?: desc.id, items)
                    }
                    idx < actionsStart -> {
                        val mIdx = idx - coverCount
                        val cfg = state.metadataSources[mIdx]
                        val desc = ScraperCatalog.metaDescriptor(cfg.id) ?: return
                        val items = mutableListOf(
                            ActionItem(if (cfg.enabled) L.disable else L.enable) { manage.toggleMetadataSource(cfg.id) },
                        )
                        if (mIdx > 0) items += ActionItem(L.moveUp) { manage.moveMetadataSource(mIdx, mIdx - 1); nav.contentItemIndex = idx - 1 }
                        if (mIdx < metaCount - 1) items += ActionItem(L.moveDown) { manage.moveMetadataSource(mIdx, mIdx + 1); nav.contentItemIndex = idx + 1 }
                        desc.credentialKind?.let { k -> items += ActionItem(accountAddLabel) { credDialogKind = k } }
                        menu = ActionMenuState(metaNames[cfg.id] ?: desc.id, items)
                    }
                    idx == actionsStart -> {
                        if (state.scrapeProgress.running) {
                            manage.cancelScrape()
                            Toast.makeText(context, context.getString(R.string.scraping_missing_stopped_toast), Toast.LENGTH_SHORT).show()
                        } else {
                            val queued = manage.scrapeMissing()
                            val msg = if (queued > 0) {
                                context.getString(R.string.scraping_missing_started_toast, queued)
                            } else {
                                context.getString(R.string.scraping_missing_none_toast)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    idx == actionsStart + 1 -> clearScrapeCacheWithFeedback()
                    else -> showScrapeLogs = true
                }
            }
            SettingsSection.Emulators -> {
                if (idx == 0) {
                    showAllLauncherPlatforms = !showAllLauncherPlatforms
                    return
                }
                if (idx == 1) {
                    raSafPickerLauncher.launch(null)
                    return
                }
                launcherPlatformsFor(state, showAllLauncherPlatforms).getOrNull(idx - 2)?.let {
                    launcherPickerPlatform = it
                }
            }
            SettingsSection.Controls -> {
                val actions = KeyAction.entries
                when {
                    idx == 0 -> onToggleSwapAbxy()
                    idx in 1..actions.size -> {
                        keyBindError = null
                        capturingAction = actions[idx - 1]
                    }
                    else -> {
                        resetConfirmChoiceIdx = 1
                        showResetBindingsConfirm = true
                    }
                }
            }
            SettingsSection.System -> when (idx) {
                0 -> toggleLangPicker()
                1 -> onToggleClock12h()
                2 -> onToggleKeySound()
                3 -> manage.openLanService()
                4 -> onToggleShowClock()
                5 -> onToggleShowBattery()
                6 -> onToggleShowWifi()
                7 -> onToggleShowBluetooth()
                8 -> onToggleShowMobileSignal()
                9 -> onToggleShowHeadset()
                10 -> onToggleShowAirplane()
                11 -> onToggleShowDnd()
                12 -> manage.toggleQuickHudEnabled()
                13, 14, 15 -> Unit
                16 -> triggerCheckUpdate()
                17 -> openProjectUrl(context)
            }
        }
    }

    val railLook = stringResource(R.string.settings_sec_appearance)
    val railHome = stringResource(R.string.settings_sec_home)
    val railLib = stringResource(R.string.settings_sec_library)
    val railCovers = stringResource(R.string.settings_sec_covers)
    val railEmus = stringResource(R.string.settings_sec_emulators)
    val railCtrls = stringResource(R.string.settings_sec_controls)
    val railSys = stringResource(R.string.settings_sec_system)
    val railItems = remember(railLook, railHome, railLib, railCovers, railEmus, railCtrls, railSys) {
        listOf(
            RailItem(DeckIcons.Palette, railLook),
            RailItem(DeckIcons.Home, railHome),
            RailItem(DeckIcons.Library, railLib),
            RailItem(DeckIcons.Image, railCovers),
            RailItem(DeckIcons.Gamepad, railEmus),
            RailItem(DeckIcons.Joystick, railCtrls),
            RailItem(DeckIcons.Device, railSys),
        )
    }
    val cachedLauncherPlatforms = remember(state.libraryPlatforms, state.launcherRows, state.platformAspects, showAllLauncherPlatforms) {
        launcherPlatformsFor(state, showAllLauncherPlatforms)
    }
    val prevKeyBadge = GamepadKeys.badgeLabel(state.keyBindings, KeyAction.PrevTab, state.swapAbxy) ?: "L1"
    val nextKeyBadge = GamepadKeys.badgeLabel(state.keyBindings, KeyAction.NextTab, state.swapAbxy) ?: "R1"

    Box(
        modifier
            .fillMaxSize()
            .settingsBackdrop()
            .blockOverlayTouchThrough()
            .focusRequester(focusRequester)
            .focusProperties { canFocus = !isCovered }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (isCovered || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                nameSpec?.let { spec ->
                    return@onPreviewKeyEvent when {
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) -> {
                            nameSpec = null; true
                        }
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            spec.onConfirm(nameState?.value?.text.orEmpty()); nameSpec = null; true
                        }
                        else -> false
                    }
                }
                if (showScrapeLogs || showFontPicker || bindingCategorySpec != null || launcherPickerPlatform != null) {
                    return@onPreviewKeyEvent false
                }
                if (credDialogKind != null) {
                    return@onPreviewKeyEvent if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                        credDialogKind = null; true
                    } else false
                }
                if (editingSystemTab != null) {
                    return@onPreviewKeyEvent if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                        editingSystemTab = null; true
                    } else false
                }
                if (showHiddenDialog) {
                    if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                        showHiddenDialog = false; return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                capturingAction?.let { action ->
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        capturingAction = null; keyBindError = null; return@onPreviewKeyEvent true
                    }
                    if (GamepadKeys.isReservedKey(event.key)) {
                        keyBindError = reservedKeyError; return@onPreviewKeyEvent true
                    }
                    val code = event.key.nativeKeyCode
                    val conflicts = GamepadKeys.findConflicts(action, code, state.keyBindings, state.swapAbxy)
                    capturingAction = null; keyBindError = null
                    if (conflicts.isEmpty()) applyKeyBinding(action, code)
                    else { conflictChoiceIdx = 0; pendingKeyConflict = PendingKeyConflict(action, code, conflicts) }
                    return@onPreviewKeyEvent true
                }
                pendingKeyConflict?.let { conflict ->
                    val canSwap = GamepadKeys.canSwapConflicts(conflict.action, conflict.newKeyCode, conflict.conflicts, state.keyBindings, state.swapAbxy)
                    val optCount = if (canSwap) 3 else 2
                    when {
                        event.key == Key.DirectionUp -> conflictChoiceIdx = (conflictChoiceIdx - 1 + optCount) % optCount
                        event.key == Key.DirectionDown -> conflictChoiceIdx = (conflictChoiceIdx + 1) % optCount
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) -> pendingKeyConflict = null
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            val idx = conflictChoiceIdx; pendingKeyConflict = null
                            if (canSwap && idx == 0) {
                                val oldKey = GamepadKeys.resolvedKeyCode(conflict.action, state.keyBindings, state.swapAbxy)
                                var next = state.keyBindings
                                conflict.conflicts.forEach { c -> next = GamepadKeys.withBinding(next, c, oldKey, state.swapAbxy) }
                                onUpdateKeyBindings(GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy))
                            } else if ((canSwap && idx == 1) || (!canSwap && idx == 0)) {
                                var next = state.keyBindings
                                conflict.conflicts.forEach { c -> next = GamepadKeys.withBinding(next, c, GamepadKeys.UNBOUND, state.swapAbxy) }
                                onUpdateKeyBindings(GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy))
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (showResetBindingsConfirm) {
                    when {
                        event.key == Key.DirectionUp || event.key == Key.DirectionLeft -> resetConfirmChoiceIdx = 0
                        event.key == Key.DirectionDown || event.key == Key.DirectionRight -> resetConfirmChoiceIdx = 1
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) -> showResetBindingsConfirm = false
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            val confirm = resetConfirmChoiceIdx == 0
                            showResetBindingsConfirm = false
                            if (confirm) onResetKeyBindings()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (browsing) {
                    if (browser.showAutoCreate) {
                        if (GamepadKeys.isBack(event.key)) {
                            browser.showAutoCreate = false
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    when {
                        event.key == Key.DirectionUp -> browser.move(-1)
                        event.key == Key.DirectionDown -> browser.move(1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) || event.key == Key.DirectionRight ->
                            browser.confirm()?.let { manage.addDir(it); browsing = false }
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) || event.key == Key.DirectionLeft ->
                            if (!browser.up()) browsing = false
                    }
                    return@onPreviewKeyEvent true
                }
                menu?.let { m ->
                    when {
                        event.key == Key.DirectionUp -> m.move(-1)
                        event.key == Key.DirectionDown -> m.move(1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            val item = m.items.getOrNull(m.index); menu = null; item?.action?.invoke()
                        }
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) || event.key == Key.DirectionLeft -> menu = null
                    }
                    return@onPreviewKeyEvent true
                }
                // 主题色圆点水平选择
                accentPicker?.let { idx ->
                    val count = ThemeAccent.entries.size
                    when {
                        event.key == Key.DirectionLeft || event.key == Key.DirectionUp ->
                            accentPicker = (idx - 1 + count) % count
                        event.key == Key.DirectionRight || event.key == Key.DirectionDown ->
                            accentPicker = (idx + 1) % count
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            manage.setThemeAccent(ThemeAccent.entries[idx]); accentPicker = null
                        }
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) ->
                            accentPicker = null
                    }
                    return@onPreviewKeyEvent true
                }
                // 背景缩略图水平选择
                bgPicker?.let { idx ->
                    val count = BackgroundStyle.entries.size
                    when {
                        event.key == Key.DirectionLeft || event.key == Key.DirectionUp ->
                            bgPicker = (idx - 1 + count) % count
                        event.key == Key.DirectionRight || event.key == Key.DirectionDown ->
                            bgPicker = (idx + 1) % count
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            onSetBackgroundStyle(BackgroundStyle.entries[idx]); bgPicker = null
                        }
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) ->
                            bgPicker = null
                    }
                    return@onPreviewKeyEvent true
                }
                langPicker?.let { idx ->
                    when {
                        event.key == Key.DirectionUp -> langPicker = (idx - 1).coerceAtLeast(0)
                        event.key == Key.DirectionDown -> langPicker = (idx + 1).coerceAtMost(AppLocale.CODES.size - 1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            langPicker = null
                            val code = AppLocale.CODES[idx]
                            if (code != state.language) onLanguageChange(code)
                        }
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) || event.key == Key.DirectionLeft ->
                            langPicker = null
                    }
                    return@onPreviewKeyEvent true
                }

                // L1 / R1 切换设置分区
                if (GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings)) {
                    selectSection((nav.selectedSection - 1 + sections.size) % sections.size)
                    return@onPreviewKeyEvent true
                }
                if (GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings)) {
                    selectSection((nav.selectedSection + 1) % sections.size)
                    return@onPreviewKeyEvent true
                }

                if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                    if (nav.focusOnMenu) onDismiss() else nav.focusOnMenu = true
                    return@onPreviewKeyEvent true
                }
                if (GamepadKeys.isSettings(event.key, state.swapAbxy, state.keyBindings)) {
                    onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings)) {
                    if (nav.focusOnMenu) {
                        val maxItems = itemCountForSection(sections[nav.selectedSection], state, showAllLauncherPlatforms)
                        if (maxItems > 0) { nav.focusOnMenu = false; nav.contentItemIndex = 0 }
                    } else {
                        triggerItem(sections[nav.selectedSection], nav.contentItemIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.DirectionUp -> {
                        if (nav.focusOnMenu) {
                            if (nav.selectedSection > 0) selectSection(nav.selectedSection - 1)
                        } else if (nav.contentItemIndex > 0) {
                            nav.contentItemIndex--
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (nav.focusOnMenu) {
                            if (nav.selectedSection < sections.lastIndex) selectSection(nav.selectedSection + 1)
                        } else {
                            val max = itemCountForSection(sections[nav.selectedSection], state, showAllLauncherPlatforms)
                            if (nav.contentItemIndex < max - 1) nav.contentItemIndex++
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (nav.focusOnMenu) {
                            val maxItems = itemCountForSection(sections[nav.selectedSection], state, showAllLauncherPlatforms)
                            if (maxItems > 0) { nav.focusOnMenu = false; nav.contentItemIndex = 0 }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!nav.focusOnMenu) nav.focusOnMenu = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsHeader(
                title = stringResource(R.string.settings_title),
                switchHint = stringResource(R.string.settings_switch_section),
                prevKey = prevKeyBadge,
                nextKey = nextKeyBadge,
                onBack = onDismiss,
            )

            Row(Modifier.fillMaxSize()) {
                SettingsRailSlot(
                    nav = nav,
                    items = railItems,
                    onSelect = { selectSection(it) },
                    modifier = Modifier
                        .width(164.dp)
                        .fillMaxHeight(),
                )

                // 懒挂载 + 零重组切页保活：
                // - 顶层 SettingsScreen 与 SettingsContentHost 均不读取 nav.selectedSection
                // - 仅 Layout measurePolicy 切换可见分区；各分区通过 derivedStateOf 读取各自 hl
                // - 在左侧菜单栏切换分区时，7 个分区全部 0 重组、0 销毁重建，仅切换布局显隐
                val visitedSections = remember { mutableStateListOf(sections[nav.selectedSection]) }
                LaunchedEffect(nav) {
                    snapshotFlow { sections[nav.selectedSection] }.collect { sec ->
                        if (sec !in visitedSections) visitedSections.add(sec)
                    }
                }
                Layout(
                    content = {
                        visitedSections.forEach { section ->
                            key(section) {
                                val secIdx = section.ordinal
                                SectionPaneSlot(
                                    sectionIdx = secIdx,
                                    nav = nav,
                                    modifier = Modifier.layoutId(section),
                                ) { hl ->
                                    when (section) {
                                        SettingsSection.Look -> LookSection(
                                            theme = state.theme,
                                            themeAccent = state.themeAccent,
                                            accentCursor = accentPicker,
                                            onToggleAccentPicker = ::toggleAccentPicker,
                                            onSelectThemeAccent = { accentPicker = null; manage.setThemeAccent(it) },
                                            backgroundStyle = state.backgroundStyle,
                                            bgCursor = bgPicker,
                                            onToggleBgPicker = ::toggleBgPicker,
                                            onSelectBackgroundStyle = { bgPicker = null; onSetBackgroundStyle(it) },
                                            customBackground = state.customBackground,
                                            onPickCustomBackground = { pickBgLauncher.launch("image/*") },
                                            onClearCustomBackground = { onSetCustomBackground(null) },
                                            reduceMotion = state.reduceMotion,
                                            onToggleReduceMotion = onToggleReduceMotion,
                                            fontId = state.fontId,
                                            onOpenFontPicker = { showFontPicker = true },
                                            onCycleTheme = onCycleTheme,
                                            highlightIndex = hl,
                                        )
                                        SettingsSection.Home -> HomeSettingsSection(
                                            state = state,
                                            onSetViewMode = { mode ->
                                                onSetViewMode(mode)
                                                val max = itemCountForSection(SettingsSection.Home, state.copy(viewMode = mode))
                                                if (nav.contentItemIndex >= max) nav.contentItemIndex = max - 1
                                            },
                                            onCycleGalleryAlign = onCycleGalleryAlign,
                                            onToggleBottomBar = onToggleBottomBar,
                                            onToggleShowMetadata = onToggleShowMetadata,
                                            onToggleMetaField = manage.toggleMetaField,
                                            onTabRowClick = { relIdx -> nav.focusOnMenu = false; openTabAction(relIdx) },
                                            onMoveTabTo = manage.moveTabTo,
                                            onQuickCreateCategory = manage.quickCreateCategoryForPlatform,
                                            highlightIndex = hl,
                                        )
                                        SettingsSection.Library -> LibrarySettingsSection(
                                            dirs = state.romDirs,
                                            autoRoot = state.autoRoot,
                                            showAndroidGames = state.showAndroidGames,
                                            hiddenCount = state.hiddenIds.size,
                                            onRowClick = { idx -> nav.focusOnMenu = false; nav.contentItemIndex = idx; triggerItem(SettingsSection.Library, idx) },
                                            highlightIndex = hl,
                                        )
                                        SettingsSection.Covers -> CoversSettingsSection(
                                            coverSources = state.coverSources,
                                            metadataSources = state.metadataSources,
                                            credentials = state.scraperCredentials,
                                            cacheSizeLabel = formatCacheBytes(scrapeCacheBytes),
                                            onToggleCover = manage.toggleCoverSource,
                                            onMoveCover = manage.moveCoverSource,
                                            onToggleMeta = manage.toggleMetadataSource,
                                            onMoveMeta = manage.moveMetadataSource,
                                            onOpenCred = { credDialogKind = it },
                                            onRowClick = { idx -> nav.focusOnMenu = false; nav.contentItemIndex = idx; triggerItem(SettingsSection.Covers, idx) },
                                            onScrapeMissing = {
                                                if (state.scrapeProgress.running) {
                                                    manage.cancelScrape()
                                                    Toast.makeText(context, context.getString(R.string.scraping_missing_stopped_toast), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val queued = manage.scrapeMissing()
                                                    val msg = if (queued > 0) {
                                                        context.getString(R.string.scraping_missing_started_toast, queued)
                                                    } else {
                                                        context.getString(R.string.scraping_missing_none_toast)
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onCancelScrape = {
                                                manage.cancelScrape()
                                                Toast.makeText(context, context.getString(R.string.scraping_missing_stopped_toast), Toast.LENGTH_SHORT).show()
                                            },
                                            onDismissScrapeProgress = manage.dismissScrapeProgress,
                                            onRefreshMetadata = {
                                                if (!NetworkStatus.isOnline(context)) {
                                                    Toast.makeText(context, context.getString(R.string.offline_action_toast), Toast.LENGTH_SHORT).show()
                                                } else manage.refreshAllMetadata()
                                            },
                                            onClearCoverCache = ::clearScrapeCacheWithFeedback,
                                            onViewLogs = { showScrapeLogs = true },
                                            scrapeProgress = state.scrapeProgress,
                                            highlightIndex = hl,
                                        )
                                        SettingsSection.Emulators -> EmulatorsSettingsSection(
                                            platforms = cachedLauncherPlatforms,
                                            showAllPlatforms = showAllLauncherPlatforms,
                                            onToggleShowAllPlatforms = { showAllLauncherPlatforms = !showAllLauncherPlatforms; nav.contentItemIndex = 0 },
                                            onAuthorizeRetroArchCores = {
                                                val initial = com.pocketdeck.launcher.launch.RetroArchCoreCatalog.buildSuggestedInitialUri(context)
                                                raSafPickerLauncher.launch(initial)
                                            },
                                            retroArchCoreCount = raCoreCount,
                                            retroArchSafAuthorized = raSafAuthorized,
                                            selected = state.platformLaunchers,
                                            aspectOverrides = state.platformAspects,
                                            onPickLauncher = { launcherPickerPlatform = it },
                                            highlightIndex = hl,
                                        )
                                        SettingsSection.Controls -> ControlsSettingsSection(
                                            swapAbxy = state.swapAbxy,
                                            keyBindings = state.keyBindings,
                                            onToggleSwapAbxy = onToggleSwapAbxy,
                                            onRebindAction = { action -> keyBindError = null; capturingAction = action },
                                            onResetBindings = { resetConfirmChoiceIdx = 1; showResetBindingsConfirm = true },
                                            highlightIndex = hl,
                                        )
                                        SettingsSection.System -> SystemSettingsSection(
                                            state = state,
                                            langPicker = langPicker,
                                            onToggleLangPicker = ::toggleLangPicker,
                                            onLanguageChange = { code -> langPicker = null; if (code != state.language) onLanguageChange(code) },
                                            onToggleClock12h = onToggleClock12h,
                                            onToggleKeySound = onToggleKeySound,
                                            onOpenLanService = manage.openLanService,
                                            onToggleShowClock = onToggleShowClock,
                                            onToggleShowBattery = onToggleShowBattery,
                                            onToggleShowWifi = onToggleShowWifi,
                                            onToggleShowBluetooth = onToggleShowBluetooth,
                                            onToggleShowMobileSignal = onToggleShowMobileSignal,
                                            onToggleShowHeadset = onToggleShowHeadset,
                                            onToggleShowAirplane = onToggleShowAirplane,
                                            onToggleShowDnd = onToggleShowDnd,
                                            onToggleQuickHudEnabled = manage.toggleQuickHudEnabled,
                                            onCheckUpdate = ::triggerCheckUpdate,
                                            checkingUpdate = checkingUpdate,
                                            onOpenProjectUrl = { openProjectUrl(context) },
                                            highlightIndex = hl,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) { measurables, constraints ->
                    val activeSec = sections[nav.selectedSection]
                    val activeMeasurable = measurables.firstOrNull { it.layoutId == activeSec }
                    val placeable = activeMeasurable?.measure(constraints)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable?.placeRelative(0, 0)
                    }
                }
            }
        }

        // ── 弹层与选择器 ──
        menu?.let { m ->
            ActionMenuDialog(state = m, onPick = { item -> menu = null; item.action() }, onDismiss = { menu = null })
        }
        if (browsing) {
            DirBrowser(state = browser, onPick = { manage.addDir(it); browsing = false }, onDismiss = { browsing = false })
        }
        val spec = nameSpec
        if (spec != null && nameState != null) {
            NameDialog(
                spec = spec,
                state = nameState,
                onConfirm = { spec.onConfirm(nameState.value.text); nameSpec = null },
                onDismiss = { nameSpec = null },
            )
        }
        if (showScrapeLogs) {
            ScrapeLogsScreen(onBack = { showScrapeLogs = false }, swapAbxy = state.swapAbxy, keyBindings = state.keyBindings)
        }
        availableRelease?.let { rel ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DeckColors.CardShadow)
                    .clickable(enabled = updateDownloadProgress == null) { availableRelease = null },
                contentAlignment = Alignment.Center,
            ) {
                com.pocketdeck.launcher.ui.components.DialogPanelCard(
                    onDismiss = { if (updateDownloadProgress == null) availableRelease = null },
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 20.dp)
                        .widthIn(max = 460.dp)
                        .fillMaxWidth(),
                    swapAbxy = state.swapAbxy,
                    keyBindings = state.keyBindings,
                ) {
                    Text(
                        text = stringResource(R.string.update_available_title, rel.tagName),
                        style = DeckType.menuTitle,
                        color = DeckColors.TextPrimary,
                        modifier = Modifier.padding(end = 32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (rel.changelog.isNotBlank()) {
                        Text(
                            text = rel.changelog,
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    val dlProg = updateDownloadProgress
                    if (dlProg != null) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { dlProg.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(DeckShapes.tabPill),
                            color = DeckColors.Ruby,
                            trackColor = DeckColors.DialogBg,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.update_downloading_fmt, (dlProg * 100f).toInt()),
                            style = DeckType.metaLabel,
                            color = DeckColors.Ruby,
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        ) {
                            Text(
                                text = stringResource(android.R.string.cancel),
                                style = DeckType.menuButton,
                                color = DeckColors.TextSecondary,
                                modifier = Modifier
                                    .clip(DeckShapes.menuButton)
                                    .background(DeckColors.Surface)
                                    .clickable { availableRelease = null }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                            Text(
                                text = stringResource(R.string.update_download_install),
                                style = DeckType.menuButton,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(DeckShapes.menuButton)
                                    .background(com.pocketdeck.launcher.ui.theme.DeckBrand.brandBrush)
                                    .clickable {
                                        val url = rel.apkUrl
                                        if (url.isNullOrBlank()) {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.htmlUrl)))
                                            }
                                            availableRelease = null
                                        } else {
                                            updateDownloadProgress = 0f
                                            scope.launch {
                                                val res = com.pocketdeck.launcher.data.UpdateChecker.downloadApk(context, url) { p ->
                                                    updateDownloadProgress = p
                                                }
                                                updateDownloadProgress = null
                                                res.onSuccess { apk ->
                                                    availableRelease = null
                                                    com.pocketdeck.launcher.data.UpdateChecker.installApk(context, apk)
                                                }.onFailure { err ->
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.update_download_failed, err.message.orEmpty()),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        credDialogKind?.let { kind ->
            ScraperCredentialDialog(
                kind = kind,
                current = state.scraperCredentials,
                onSave = { updated -> manage.saveCredentials(updated); credDialogKind = null },
                onDismiss = { credDialogKind = null },
            )
        }
        bindingCategorySpec?.let { bSpec ->
            val allPlats = remember(state.libraryPlatforms) {
                (state.libraryPlatforms + PlatformCatalog.known)
                    .filter { it.id != PlatformCatalog.ANDROID_ID || it.id in state.libraryPlatforms.map { p -> p.id } }
                    .distinctBy { it.id }
            }
            val libIds = remember(state.libraryPlatforms) { state.libraryPlatforms.map { it.id }.toSet() }
            CategoryPlatformsDialog(
                categoryName = bSpec.name,
                availablePlatforms = allPlats,
                libraryPlatformIds = libIds,
                initialSelected = bSpec.initialSources,
                swapAbxy = state.swapAbxy,
                keyBindings = state.keyBindings,
                onConfirm = { picked ->
                    if (bSpec.catId == null) manage.createCategory(bSpec.name, picked)
                    else manage.setCategorySources(bSpec.catId, picked)
                    bindingCategorySpec = null
                },
                onDismiss = { bindingCategorySpec = null },
            )
        }
        launcherPickerPlatform?.let { plat ->
            val defaultPlat = remember(plat.id) { PlatformCatalog.forFolder(plat.id) }
            LauncherPickerDialog(
                title = stringResource(R.string.launcher_picker_title),
                platform = plat,
                currentLauncherId = state.platformLaunchers[plat.id],
                defaultLabel = stringResource(R.string.settings_launcher_auto),
                onSelect = { id -> onSetPlatformLauncher(plat.id, id); launcherPickerPlatform = null },
                onDismiss = { launcherPickerPlatform = null },
                showAspectRatio = true,
                defaultAspect = defaultPlat.coverAspect,
                currentAspect = state.platformAspects[plat.id],
                onSetAspect = { aspect -> manage.setPlatformAspect(plat.id, aspect) },
                customLaunchers = state.customLaunchers,
                onSavePreset = manage.saveCustomLauncherPreset,
                onDeletePreset = manage.deleteCustomLauncherPreset,
                testGame = remember(plat.id) { manage.sampleGameFor(plat.id) },
                swapAbxy = state.swapAbxy,
                keyBindings = state.keyBindings,
            )
        }
        if (showHiddenDialog) {
            HiddenGamesDialog(hiddenIds = state.hiddenIds, onRestore = { manage.unhideGame(it) }, onDismiss = { showHiddenDialog = false })
        }
        if (showFontPicker) {
            FontPickerDialog(
                currentFontId = state.fontId,
                swapAbxy = state.swapAbxy,
                keyBindings = state.keyBindings,
                onPickCustomFont = { runCatching { pickFontLauncher.launch(arrayOf("*/*")) } },
                onSelect = { id -> manage.setFontId(id); showFontPicker = false },
                onDismiss = { showFontPicker = false },
            )
        }
        editingSystemTab?.let { tab ->
            val cfg = state.systemTabs[tab.id] ?: SystemTabConfig()
            val defLabel = when (tab.type) {
                TabType.Now -> systemNowLabel
                TabType.Favs -> systemFavsLabel
                TabType.Other -> systemOtherLabel
                TabType.Custom -> tab.label
            }
            SystemTabEditDialog(
                defaultLabel = defLabel,
                initialCustomName = cfg.customName,
                initialFollowSystem = cfg.followSystemLanguage,
                onConfirm = { name, follow -> manage.updateSystemTab(tab.id, name, follow); editingSystemTab = null },
                onDismiss = { editingSystemTab = null },
            )
        }
        capturingAction?.let { action ->
            KeyBindCaptureDialog(
                actionLabel = keyActionLabel(action),
                currentBadge = GamepadKeys.badgeLabel(state.keyBindings, action, state.swapAbxy),
                defaultBadge = GamepadKeys.keyLabel(GamepadKeys.defaultKeyCode(action, state.swapAbxy)),
                errorMessage = keyBindError,
                onClear = { applyKeyBinding(action, GamepadKeys.UNBOUND); capturingAction = null; keyBindError = null },
                onDismiss = { capturingAction = null; keyBindError = null },
            )
        }
        pendingKeyConflict?.let { conflict ->
            val canSwap = GamepadKeys.canSwapConflicts(conflict.action, conflict.newKeyCode, conflict.conflicts, state.keyBindings, state.swapAbxy)
            KeyBindConflictDialog(
                targetLabel = keyActionLabel(conflict.action),
                newKeyCode = conflict.newKeyCode,
                conflictLabels = conflict.conflicts.map { keyActionLabel(it) },
                canSwap = canSwap,
                selectedOption = conflictChoiceIdx,
                onSwap = {
                    val oldKey = GamepadKeys.resolvedKeyCode(conflict.action, state.keyBindings, state.swapAbxy)
                    var next = state.keyBindings
                    conflict.conflicts.forEach { c -> next = GamepadKeys.withBinding(next, c, oldKey, state.swapAbxy) }
                    onUpdateKeyBindings(GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy))
                    pendingKeyConflict = null
                },
                onOverwrite = {
                    var next = state.keyBindings
                    conflict.conflicts.forEach { c -> next = GamepadKeys.withBinding(next, c, GamepadKeys.UNBOUND, state.swapAbxy) }
                    onUpdateKeyBindings(GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy))
                    pendingKeyConflict = null
                },
                onDismiss = { pendingKeyConflict = null },
            )
        }
        if (showResetBindingsConfirm) {
            ConfirmActionDialog(
                title = stringResource(R.string.keybind_reset_confirm_title),
                description = stringResource(R.string.keybind_reset_confirm_desc),
                confirmLabel = stringResource(R.string.keybind_reset_confirm_yes),
                cancelLabel = stringResource(R.string.menu_cancel),
                selectedOption = resetConfirmChoiceIdx,
                onConfirm = { showResetBindingsConfirm = false; onResetKeyBindings() },
                onDismiss = { showResetBindingsConfirm = false },
            )
        }
    }
}

@Composable
private fun SettingsRailSlot(
    nav: SettingsNavState,
    items: List<RailItem>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRail(
        items = items,
        selected = nav.selectedSection,
        focused = nav.focusOnMenu,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun SectionPaneSlot(
    sectionIdx: Int,
    nav: SettingsNavState,
    modifier: Modifier = Modifier,
    content: @Composable (highlightIndex: Int) -> Unit,
) {
    val contentScroll = rememberScrollState()
    LaunchedEffect(sectionIdx, nav) {
        snapshotFlow { nav.selectedSection == sectionIdx }
            .collect { active ->
                if (active) contentScroll.scrollTo(0)
            }
    }
    LaunchedEffect(sectionIdx, nav) {
        snapshotFlow { nav.selectedSection == sectionIdx && !nav.focusOnMenu && nav.contentItemIndex == 0 }
            .collect { atTop ->
                if (atTop) contentScroll.scrollTo(0)
            }
    }
    val hl by remember(sectionIdx, nav) {
        derivedStateOf {
            if (nav.selectedSection == sectionIdx && !nav.focusOnMenu) nav.contentItemIndex else -1
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(contentScroll)
            .padding(start = 10.dp, end = 18.dp, top = 4.dp, bottom = 22.dp),
    ) {
        content(hl)
    }
}

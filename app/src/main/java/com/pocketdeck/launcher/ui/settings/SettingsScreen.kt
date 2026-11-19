package com.pocketdeck.launcher.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pocketdeck.launcher.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pocketdeck.launcher.R
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
import com.pocketdeck.launcher.data.SourceDescriptor
import com.pocketdeck.launcher.data.SourceItemConfig
import com.pocketdeck.launcher.data.SystemTabConfig
import com.pocketdeck.launcher.data.ThemeAccent
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.components.ButtonBadge
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.drawViewModeIcon
import com.pocketdeck.launcher.ui.home.MetaFieldKey
import com.pocketdeck.launcher.ui.home.RomDirEntry
import com.pocketdeck.launcher.ui.home.TabEntry
import com.pocketdeck.launcher.ui.home.TabType
import com.pocketdeck.launcher.ui.home.UncategorizedPlatformInfo
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.SystemFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 设置页面：全屏，左侧分类菜单 + 右侧设置内容。
 * 完整的手柄导航支持（D-pad 上下左右 + A 确认 + B 返回）。
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
    /** 状态栏图标显示控制 */
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
    val showMobileSignal: Boolean = true,
    val showHeadset: Boolean = true,
    val showAirplane: Boolean = true,
    val showDnd: Boolean = true,
    /** 每平台首选启动器 */
    val platformLaunchers: Map<String, String> = emptyMap(),
    /** 启动器设置页中展示的平台行 */
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
    val themeAccent: ThemeAccent = ThemeAccent.Ruby,
    val fontId: String = SystemFonts.DEFAULT_ID,
    val platformAspects: Map<String, Float> = emptyMap(),
    val customLaunchers: List<CustomLauncherPreset> = emptyList(),
    val systemTabs: Map<String, SystemTabConfig> = emptyMap(),
    val uncategorizedPlatforms: List<UncategorizedPlatformInfo> = emptyList(),
    val uncategorizedCount: Int = 0,
)

/** 目录、标签与刮削管理的回调。 */
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
    val clearCoverCache: () -> Unit = {},
    val refreshAllMetadata: () -> Unit = {},
    val toggleMetaField: (MetaFieldKey) -> Unit = {},
    val unhideGame: (String) -> Unit = {},
    val toggleShowAndroidGames: () -> Unit = {},
    val setThemeAccent: (ThemeAccent) -> Unit = {},
    val setFontId: (String) -> Unit = {},
    val setPlatformAspect: (String, Float?) -> Unit = { _, _ -> },
    val saveCustomLauncherPreset: (String, String) -> Unit = { _, _ -> },
    val deleteCustomLauncherPreset: (String) -> Unit = {},
    val openLibraryHidden: () -> Unit = {},
)

/** 启动器设置中的一行：平台 + 该平台已安装可用的启动器 (id, 显示名)。 */
data class LauncherRow(
    val platformId: String,
    val platformName: String,
    val options: List<Pair<String, String>>,
)

/** 计算某平台下一个启动器选项（null 表示自动）。 */
private fun nextLauncher(row: LauncherRow, current: String?): String? {
    val cycle: List<String?> = listOf<String?>(null) + row.options.map { it.first }
    val idx = cycle.indexOf(current).coerceAtLeast(0)
    return cycle[(idx + 1) % cycle.size]
}

enum class SettingsSection {
    Appearance, HomeAndBar, LibraryAndScrape, Launcher, Controls, SystemAbout
}

private data class BindingCategorySpec(
    val catId: String?,
    val name: String,
    val initialSources: List<String>,
)

/** 设置页内容区域中可交互的条目总数（用于手柄上下导航） */
private fun itemCountForSection(
    section: SettingsSection,
    state: SettingsState,
    showAllLauncherPlatforms: Boolean = false,
): Int = when (section) {
    SettingsSection.Appearance -> {
        val bgImageRow = if (state.backgroundStyle == BackgroundStyle.Image) 1 else 0
        val galleryRow = if (state.viewMode == ViewMode.Gallery) 1 else 0
        4 + bgImageRow + ViewMode.entries.size + galleryRow
    }
    SettingsSection.HomeAndBar -> {
        val metaFields = if (state.showMetadata) MetaFieldKey.entries.size else 0
        state.manageTabs.size + 2 + state.uncategorizedPlatforms.size + 2 + metaFields + 8
    }
    SettingsSection.LibraryAndScrape ->
        state.romDirs.size + 4 + state.coverSources.size + 1 + state.metadataSources.size + 2
    SettingsSection.Launcher -> 1 + launcherPlatformsFor(state, showAllLauncherPlatforms).size
    SettingsSection.Controls -> 2 + KeyAction.entries.size + 1  // Swap ABXY + 按键音效 + 快捷键绑定 + 还原默认
    SettingsSection.SystemAbout -> 2  // 语言, 时钟（下方关于区域为展示与链接）
}

private fun launcherPlatformsFor(
    state: SettingsState,
    showAllKnown: Boolean = false,
): List<Platform> {
    val fromLib = state.libraryPlatforms.filter { it.id != PlatformCatalog.ANDROID_ID }
    if (showAllKnown) {
        val all = (fromLib + PlatformCatalog.known.filter { it.id != PlatformCatalog.ANDROID_ID })
            .distinctBy { it.id }
            .map { p ->
                val aspect = state.platformAspects[p.id]
                if (aspect != null) p.copy(coverAspect = aspect) else p
            }
        return all
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

    // 弹层状态
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
    val pickFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = SystemFonts.importFromUri(context, uri)
            result.onSuccess { fontId ->
                manage.setFontId(fontId)
                showFontPicker = false
            }.onFailure {
                Toast.makeText(context, fontInvalidMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickBgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
                    val target = File(dir, "bg_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (target.isFile && target.length() > 0) target.absolutePath else null
                }.getOrNull()
            }
            if (savedPath != null) {
                onSetCustomBackground(savedPath)
            } else {
                Toast.makeText(context, context.getString(R.string.edit_image_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(capturingAction, pendingKeyConflict) {
        onCapturingKeyBindChange(capturingAction != null || pendingKeyConflict != null)
    }

    // 弹层关闭后把焦点还给设置页
    LaunchedEffect(
        nameSpec,
        browsing,
        credDialogKind,
        showScrapeLogs,
        bindingCategorySpec,
        launcherPickerPlatform,
        showHiddenDialog,
        showFontPicker,
        editingSystemTab,
        capturingAction,
        pendingKeyConflict,
        showResetBindingsConfirm,
        isCovered,
    ) {
        if (nameSpec == null && !browsing && credDialogKind == null && !showScrapeLogs && bindingCategorySpec == null &&
            launcherPickerPlatform == null && !showHiddenDialog && !showFontPicker &&
            editingSystemTab == null && capturingAction == null && pendingKeyConflict == null &&
            !showResetBindingsConfirm && !isCovered
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

    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val sections = SettingsSection.entries

    // 当前是在左侧菜单还是右侧内容
    var focusOnMenu by rememberSaveable { mutableStateOf(true) }
    // 右侧内容区当前选中的条目索引
    var contentItemIndex by rememberSaveable { mutableIntStateOf(0) }
    // 语言选择列表展开时的高亮索引（null = 收起）
    var langPicker by remember { mutableStateOf<Int?>(null) }
    fun toggleLangPicker() {
        langPicker = if (langPicker == null) AppLocale.CODES.indexOf(state.language).coerceAtLeast(0) else null
    }

    // 切换 section 时重置 content index
    fun selectSection(index: Int) {
        selectedSection = index
        contentItemIndex = 0
        langPicker = null
        accentPicker = null
        bgPicker = null
    }

    var scrapeCacheBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sections[selectedSection]) {
        if (sections[selectedSection] == SettingsSection.LibraryAndScrape) {
            scrapeCacheBytes = withContext(Dispatchers.IO) { computeScrapeCacheBytes(context.cacheDir) }
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

    val accountAddLabel = stringResource(R.string.scraping_account_add)
    val coverNames = ScraperCatalog.coverNodes.associate { it.id to stringResource(it.nameRes) }
    val metaNames = ScraperCatalog.metadataProviders.associate { it.id to stringResource(it.nameRes) }
    val systemNowLabel = stringResource(R.string.tab_name_now)
    val systemFavsLabel = stringResource(R.string.tab_name_favs)
    val systemOtherLabel = stringResource(R.string.tab_name_other)
    val quickCreateLabel = stringResource(R.string.tab_quick_create_cat)
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
    )
    val reservedKeyError = stringResource(R.string.keybind_reserved)

    fun toggleAccentPicker() {
        accentPicker = if (accentPicker == null) ThemeAccent.entries.indexOf(state.themeAccent).coerceAtLeast(0) else null
    }

    fun toggleBgPicker() {
        bgPicker = if (bgPicker == null) BackgroundStyle.entries.indexOf(state.backgroundStyle).coerceAtLeast(0) else null
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
        val next = GamepadKeys.withBinding(state.keyBindings, action, keyCode, state.swapAbxy)
        onUpdateKeyBindings(next)
    }

    /** 外观选择器 / 主页标签管理 / 游戏库与刮削 / 操控 / 启动器条目的 A 键操作。返回 true 表示已处理。 */
    fun openManageAction(section: SettingsSection, idx: Int): Boolean {
        when (section) {
            SettingsSection.Appearance -> {
                when {
                    idx == 1 -> {
                        toggleAccentPicker()
                        return true
                    }
                    idx == 2 -> {
                        showFontPicker = true
                        return true
                    }
                    idx == 3 -> {
                        toggleBgPicker()
                        return true
                    }
                    state.backgroundStyle == BackgroundStyle.Image && idx == 4 -> {
                        pickBgLauncher.launch("image/*")
                        return true
                    }
                }
                return false
            }
            SettingsSection.HomeAndBar -> {
                val tabs = state.manageTabs
                val tabsTotal = tabs.size + 2 + state.uncategorizedPlatforms.size
                if (idx >= tabsTotal) return false // 下方主页与状态栏开关交由 triggerContentAction 处理
                if (idx == tabs.size) {
                    val n = tabs.count { it.type == TabType.Custom } + 1
                    nameSpec = NameDialogSpec(L.catNameTitle, L.catDefault.format(n)) { newName ->
                        bindingCategorySpec = BindingCategorySpec(null, newName, emptyList())
                    }
                    return true
                }
                if (idx == tabs.size + 1) {
                    manage.resetTabOrder()
                    return true
                }
                if (idx >= tabs.size + 2) {
                    val uIdx = idx - (tabs.size + 2)
                    val info = state.uncategorizedPlatforms.getOrNull(uIdx) ?: return true
                    val customCats = state.categories
                    if (customCats.isEmpty()) {
                        manage.quickCreateCategoryForPlatform(info.platform)
                    } else {
                        val actions = buildList {
                            add(ActionItem("$quickCreateLabel (${info.platform.shortName})") {
                                manage.quickCreateCategoryForPlatform(info.platform)
                            })
                            add(ActionItem(addToExistingLabel) {
                                menu = ActionMenuState(
                                    pickCatTitleFmt.format(info.platform.shortName),
                                    customCats.map { cat ->
                                        ActionItem(cat.name) {
                                            manage.addPlatformToCategory(cat.id, info.platform.id)
                                        }
                                    },
                                )
                            })
                        }
                        menu = ActionMenuState("${info.platform.shortName} · ${info.platform.fullName}", actions)
                    }
                    return true
                }
                val t = tabs.getOrNull(idx) ?: return true
                val displayName = resolvedTabLabel(t)
                val items = mutableListOf<ActionItem>()
                if (t.type == TabType.Custom) {
                    val cat = state.categories.firstOrNull { it.id == t.id }
                    val existingSources = cat?.sources.orEmpty()
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
                val currentSortLabel = if (t.type == TabType.Now && currentSort == null) {
                    sortRecentLabel
                } else {
                    sortNames[currentSort ?: CategorySortOrder.TitleAsc].orEmpty()
                }
                items += ActionItem(sortMenuFmt.format(currentSortLabel)) {
                    val subItems = buildList {
                        if (t.type == TabType.Now) {
                            val mark = if (currentSort == null) "✓ " else ""
                            add(ActionItem("$mark$sortRecentLabel") { manage.setCategorySortOrder(t.id, null) })
                        }
                        CategorySortOrder.entries.forEach { order ->
                            val mark = if (order == currentSort) "✓ " else ""
                            add(ActionItem("$mark${sortNames[order].orEmpty()}") { manage.setCategorySortOrder(t.id, order) })
                        }
                    }
                    menu = ActionMenuState("$displayName · $sortTitleLabel", subItems)
                }
                if (idx > 0) items += ActionItem(L.moveUp) { manage.moveTab(t.id, -1); contentItemIndex = idx - 1 }
                if (idx < tabs.lastIndex) items += ActionItem(L.moveDown) { manage.moveTab(t.id, 1); contentItemIndex = idx + 1 }
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
                return true
            }
            SettingsSection.LibraryAndScrape -> {
                val dirs = state.romDirs
                val scrapeOffset = dirs.size + 4
                if (idx < scrapeOffset) {
                    when {
                        idx < dirs.size -> {
                            val d = dirs[idx]
                            menu = ActionMenuState(
                                d.path.replace(android.os.Environment.getExternalStorageDirectory().path, "~"),
                                listOf(
                                    ActionItem(if (d.enabled) L.disable else L.enable) { manage.toggleDir(d.path) },
                                    ActionItem(L.remove) {
                                        manage.removeDir(d.path)
                                        contentItemIndex = (idx - 1).coerceAtLeast(0)
                                    },
                                ),
                            )
                        }
                        idx == dirs.size -> {
                            browser.reset()
                            browsing = true
                        }
                        idx == dirs.size + 1 -> onRescan()
                        idx == dirs.size + 2 -> manage.toggleShowAndroidGames()
                        else -> manage.openLibraryHidden()
                    }
                    return true
                }
                val sIdx = idx - scrapeOffset
                val coverCount = state.coverSources.size
                val metaStart = coverCount + 1
                val metaCount = state.metadataSources.size
                when {
                    sIdx < coverCount -> {
                        val cfg = state.coverSources[sIdx]
                        val desc = ScraperCatalog.coverDescriptor(cfg.id) ?: return true
                        val items = mutableListOf<ActionItem>()
                        items += ActionItem(if (cfg.enabled) L.disable else L.enable) {
                            manage.toggleCoverSource(cfg.id)
                        }
                        if (sIdx > 0) items += ActionItem(L.moveUp) {
                            manage.moveCoverSource(sIdx, sIdx - 1); contentItemIndex = idx - 1
                        }
                        if (sIdx < coverCount - 1) items += ActionItem(L.moveDown) {
                            manage.moveCoverSource(sIdx, sIdx + 1); contentItemIndex = idx + 1
                        }
                        desc.credentialKind?.let { kind ->
                            items += ActionItem(accountAddLabel) { credDialogKind = kind }
                        }
                        menu = ActionMenuState(coverNames[cfg.id] ?: desc.id, items)
                    }
                    sIdx == coverCount -> clearScrapeCacheWithFeedback()
                    sIdx in metaStart until (metaStart + metaCount) -> {
                        val mIdx = sIdx - metaStart
                        val cfg = state.metadataSources[mIdx]
                        val desc = ScraperCatalog.metaDescriptor(cfg.id) ?: return true
                        val items = mutableListOf<ActionItem>()
                        items += ActionItem(if (cfg.enabled) L.disable else L.enable) {
                            manage.toggleMetadataSource(cfg.id)
                        }
                        if (mIdx > 0) items += ActionItem(L.moveUp) {
                            manage.moveMetadataSource(mIdx, mIdx - 1); contentItemIndex = idx - 1
                        }
                        if (mIdx < metaCount - 1) items += ActionItem(L.moveDown) {
                            manage.moveMetadataSource(mIdx, mIdx + 1); contentItemIndex = idx + 1
                        }
                        desc.credentialKind?.let { kind ->
                            items += ActionItem(accountAddLabel) { credDialogKind = kind }
                        }
                        menu = ActionMenuState(metaNames[cfg.id] ?: desc.id, items)
                    }
                    sIdx == metaStart + metaCount -> {
                        if (!NetworkStatus.isOnline(context)) {
                            Toast.makeText(context, context.getString(R.string.offline_action_toast), Toast.LENGTH_SHORT).show()
                        } else {
                            manage.refreshAllMetadata()
                        }
                    }
                    else -> showScrapeLogs = true
                }
                return true
            }
            SettingsSection.Controls -> {
                val actions = KeyAction.entries
                return when {
                    idx == 0 -> { onToggleSwapAbxy(); true }
                    idx == 1 -> {
                        onToggleKeySound()
                        true
                    }
                    idx in 2 until (2 + actions.size) -> {
                        keyBindError = null
                        capturingAction = actions[idx - 2]
                        true
                    }
                    idx == 2 + actions.size -> {
                        resetConfirmChoiceIdx = 1
                        showResetBindingsConfirm = true
                        true
                    }
                    else -> false
                }
            }
            SettingsSection.Launcher -> {
                if (idx == 0) {
                    showAllLauncherPlatforms = !showAllLauncherPlatforms
                    return true
                }
                val plats = launcherPlatformsFor(state, showAllLauncherPlatforms)
                plats.getOrNull(idx - 1)?.let { plat ->
                    launcherPickerPlatform = plat
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    fun clickManageRow(section: SettingsSection, idx: Int) {
        focusOnMenu = false
        contentItemIndex = idx
        openManageAction(section, idx)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(DeckColors.Background)
            .focusRequester(focusRequester)
            .focusProperties { canFocus = !isCovered }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (isCovered) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // 名称输入框：只拦截手柄确认/取消，其余按键交给输入框
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

                // 日志独立界面：交由 ScrapeLogsScreen 自行处理焦点与按键
                if (showScrapeLogs) return@onPreviewKeyEvent false

                // 刮削凭据输入框：允许 Back/取消 关闭
                if (credDialogKind != null) {
                    return@onPreviewKeyEvent if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                        credDialogKind = null; true
                    } else false
                }

                // 系统标签名编辑框 / 凭据框：允许 Back/取消 关闭
                if (editingSystemTab != null) {
                    return@onPreviewKeyEvent if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                        editingSystemTab = null; true
                    } else false
                }

                // 字体选择 / 分类关联平台 / 启动器选择弹窗：交由弹窗自行处理方向与确认/取消按键
                if (showFontPicker || bindingCategorySpec != null || launcherPickerPlatform != null) {
                    return@onPreviewKeyEvent false
                }

                // 已隐藏游戏弹窗：允许 Back/B 关闭
                if (showHiddenDialog) {
                    if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                        showHiddenDialog = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // 快捷键捕获弹窗
                capturingAction?.let { action ->
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        capturingAction = null
                        keyBindError = null
                        return@onPreviewKeyEvent true
                    }
                    if (GamepadKeys.isReservedKey(event.key)) {
                        keyBindError = reservedKeyError
                        return@onPreviewKeyEvent true
                    }
                    val code = event.key.nativeKeyCode
                    val conflicts = GamepadKeys.findConflicts(action, code, state.keyBindings, state.swapAbxy)
                    capturingAction = null
                    keyBindError = null
                    if (conflicts.isEmpty()) {
                        applyKeyBinding(action, code)
                    } else {
                        conflictChoiceIdx = 0
                        pendingKeyConflict = PendingKeyConflict(action, code, conflicts)
                    }
                    return@onPreviewKeyEvent true
                }

                // 快捷键冲突处理弹窗
                pendingKeyConflict?.let { conflict ->
                    val canSwap = GamepadKeys.canSwapConflicts(
                        conflict.action,
                        conflict.newKeyCode,
                        conflict.conflicts,
                        state.keyBindings,
                        state.swapAbxy,
                    )
                    val optCount = if (canSwap) 3 else 2
                    when {
                        event.key == Key.DirectionUp -> conflictChoiceIdx = (conflictChoiceIdx - 1 + optCount) % optCount
                        event.key == Key.DirectionDown -> conflictChoiceIdx = (conflictChoiceIdx + 1) % optCount
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) ->
                            pendingKeyConflict = null
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            val idx = conflictChoiceIdx
                            pendingKeyConflict = null
                            if (canSwap && idx == 0) {
                                val oldKey = GamepadKeys.resolvedKeyCode(conflict.action, state.keyBindings, state.swapAbxy)
                                var next = state.keyBindings
                                conflict.conflicts.forEach { c ->
                                    next = GamepadKeys.withBinding(next, c, oldKey, state.swapAbxy)
                                }
                                next = GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy)
                                onUpdateKeyBindings(next)
                            } else if ((canSwap && idx == 1) || (!canSwap && idx == 0)) {
                                var next = state.keyBindings
                                conflict.conflicts.forEach { c ->
                                    next = GamepadKeys.withBinding(next, c, GamepadKeys.UNBOUND, state.swapAbxy)
                                }
                                next = GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy)
                                onUpdateKeyBindings(next)
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                // 恢复默认按键二次确认弹窗
                if (showResetBindingsConfirm) {
                    when {
                        event.key == Key.DirectionUp || event.key == Key.DirectionLeft ->
                            resetConfirmChoiceIdx = 0
                        event.key == Key.DirectionDown || event.key == Key.DirectionRight ->
                            resetConfirmChoiceIdx = 1
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) ->
                            showResetBindingsConfirm = false
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            val confirm = resetConfirmChoiceIdx == 0
                            showResetBindingsConfirm = false
                            if (confirm) onResetKeyBindings()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                // 目录浏览器
                if (browsing) {
                    when {
                        event.key == Key.DirectionUp -> browser.move(-1)
                        event.key == Key.DirectionDown -> browser.move(1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy) || event.key == Key.DirectionRight ->
                            browser.confirm()?.let { manage.addDir(it); browsing = false }
                        GamepadKeys.isBack(event.key) ||
                            GamepadKeys.isCancelOrApps(event.key, state.swapAbxy) ||
                            event.key == Key.DirectionLeft -> if (!browser.up()) browsing = false
                    }
                    return@onPreviewKeyEvent true
                }

                // 操作菜单
                menu?.let { m ->
                    when {
                        event.key == Key.DirectionUp -> m.move(-1)
                        event.key == Key.DirectionDown -> m.move(1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy) -> {
                            val item = m.items.getOrNull(m.index)
                            menu = null
                            item?.action?.invoke()
                        }
                        GamepadKeys.isBack(event.key) ||
                            GamepadKeys.isCancelOrApps(event.key, state.swapAbxy) ||
                            event.key == Key.DirectionLeft -> menu = null
                    }
                    return@onPreviewKeyEvent true
                }

                // 主题色列表展开时：上下选择、A 确认、B 收起
                accentPicker?.let { idx ->
                    val count = ThemeAccent.entries.size
                    when {
                        event.key == Key.DirectionUp -> accentPicker = (idx - 1).coerceAtLeast(0)
                        event.key == Key.DirectionDown -> accentPicker = (idx + 1).coerceAtMost(count - 1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            accentPicker = null
                            manage.setThemeAccent(ThemeAccent.entries[idx])
                        }
                        GamepadKeys.isBack(event.key) ||
                            GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) ||
                            event.key == Key.DirectionLeft -> accentPicker = null
                    }
                    return@onPreviewKeyEvent true
                }

                // 背景样式列表展开时：上下选择、A 确认、B 收起
                bgPicker?.let { idx ->
                    val count = BackgroundStyle.entries.size
                    when {
                        event.key == Key.DirectionUp -> bgPicker = (idx - 1).coerceAtLeast(0)
                        event.key == Key.DirectionDown -> bgPicker = (idx + 1).coerceAtMost(count - 1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            bgPicker = null
                            onSetBackgroundStyle(BackgroundStyle.entries[idx])
                        }
                        GamepadKeys.isBack(event.key) ||
                            GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) ||
                            event.key == Key.DirectionLeft -> bgPicker = null
                    }
                    return@onPreviewKeyEvent true
                }

                // 语言列表展开时：上下选择、A 确认、B 收起
                langPicker?.let { idx ->
                    when {
                        event.key == Key.DirectionUp -> langPicker = (idx - 1).coerceAtLeast(0)
                        event.key == Key.DirectionDown -> langPicker = (idx + 1).coerceAtMost(AppLocale.CODES.size - 1)
                        GamepadKeys.isConfirm(event.key, state.swapAbxy) -> {
                            langPicker = null
                            val code = AppLocale.CODES[idx]
                            if (code != state.language) onLanguageChange(code)
                        }
                        GamepadKeys.isBack(event.key) ||
                            GamepadKeys.isCancelOrApps(event.key, state.swapAbxy) ||
                            event.key == Key.DirectionLeft -> langPicker = null
                    }
                    return@onPreviewKeyEvent true
                }

                // B / Back → 返回
                if (GamepadKeys.isBack(event.key) ||
                    GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)
                ) {
                    if (focusOnMenu) {
                        onDismiss()
                    } else {
                        focusOnMenu = true
                    }
                    return@onPreviewKeyEvent true
                }

                // Y (settings) → 返回
                if (GamepadKeys.isSettings(event.key, state.swapAbxy, state.keyBindings)) {
                    onDismiss()
                    return@onPreviewKeyEvent true
                }

                // A / Enter → 确认
                if (GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings)) {
                    if (focusOnMenu) {
                        // 从左侧菜单进入右侧内容
                        val maxItems = itemCountForSection(sections[selectedSection], state, showAllLauncherPlatforms)
                        if (maxItems > 0) {
                            focusOnMenu = false
                            contentItemIndex = 0
                        }
                    } else if (sections[selectedSection] == SettingsSection.SystemAbout && contentItemIndex == 0) {
                        toggleLangPicker()
                    } else if (openManageAction(sections[selectedSection], contentItemIndex)) {
                        // 已由标签 / 目录管理等处理
                    } else {
                        // 在右侧内容中触发当前条目的操作
                        triggerContentAction(
                            section = sections[selectedSection],
                            itemIndex = contentItemIndex,
                            state = state,
                            onToggleClock12h = onToggleClock12h,
                            onToggleSwapAbxy = onToggleSwapAbxy,
                            onToggleNowTab = onToggleNowTab,
                            onToggleFavsTab = onToggleFavsTab,
                            onToggleBottomBar = onToggleBottomBar,
                            onToggleShowMetadata = onToggleShowMetadata,
                            onCycleBackground = onCycleBackground,
                            onCycleViewMode = onCycleViewMode,
                            onSetViewMode = onSetViewMode,
                            onCycleTheme = onCycleTheme,
                            onRescan = onRescan,
                            onToggleShowClock = onToggleShowClock,
                            onToggleShowBattery = onToggleShowBattery,
                            onToggleShowWifi = onToggleShowWifi,
                            onToggleShowBluetooth = onToggleShowBluetooth,
                            onToggleShowMobileSignal = onToggleShowMobileSignal,
                            onToggleShowHeadset = onToggleShowHeadset,
                            onToggleShowAirplane = onToggleShowAirplane,
                            onToggleShowDnd = onToggleShowDnd,
                            onSetPlatformLauncher = onSetPlatformLauncher,
                            onCycleGalleryAlign = onCycleGalleryAlign,
                            onToggleMetaField = manage.toggleMetaField,
                        )
                    }
                    return@onPreviewKeyEvent true
                }

                // 方向键上下
                when (event.key) {
                    Key.DirectionUp -> {
                        if (focusOnMenu) {
                            if (selectedSection > 0) selectSection(selectedSection - 1)
                        } else {
                            if (contentItemIndex > 0) contentItemIndex--
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (focusOnMenu) {
                            if (selectedSection < sections.size - 1) selectSection(selectedSection + 1)
                        } else {
                            val max = itemCountForSection(sections[selectedSection], state, showAllLauncherPlatforms)
                            if (contentItemIndex < max - 1) contentItemIndex++
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (focusOnMenu) {
                            val maxItems = itemCountForSection(sections[selectedSection], state, showAllLauncherPlatforms)
                            if (maxItems > 0) {
                                focusOnMenu = false
                                contentItemIndex = 0
                            }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!focusOnMenu) {
                            focusOnMenu = true
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(onDismiss = onDismiss)

            Row(Modifier.fillMaxSize()) {
                // 左侧菜单
                SettingsMenu(
                    sections = sections,
                    selected = selectedSection,
                    isFocused = focusOnMenu,
                    onSelect = { selectSection(it) },
                    modifier = Modifier
                        .width(190.dp)
                        .fillMaxHeight(),
                )

                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(DeckColors.DialogBorder)
                )

                // 右侧内容
                AnimatedContent(
                    targetState = sections[selectedSection],
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "settings-content",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) { section ->
                    val contentScroll = rememberScrollState()
                    // 回到第一项时滚回顶部，让分区标题也可见
                    LaunchedEffect(contentItemIndex, focusOnMenu) {
                        if (contentItemIndex == 0) contentScroll.animateScrollTo(0)
                    }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(contentScroll)
                            .padding(horizontal = 24.dp, vertical = 22.dp),
                    ) {
                        val highlight = if (focusOnMenu) -1 else contentItemIndex
                        when (section) {
                            SettingsSection.Appearance -> DisplaySection(
                                theme = state.theme,
                                themeAccent = state.themeAccent,
                                accentPicker = accentPicker,
                                onToggleAccentPicker = ::toggleAccentPicker,
                                onSelectThemeAccent = {
                                    accentPicker = null
                                    manage.setThemeAccent(it)
                                },
                                fontId = state.fontId,
                                onOpenFontPicker = { showFontPicker = true },
                                backgroundStyle = state.backgroundStyle,
                                bgPicker = bgPicker,
                                onToggleBgPicker = ::toggleBgPicker,
                                onSelectBackgroundStyle = {
                                    bgPicker = null
                                    onSetBackgroundStyle(it)
                                },
                                customBackground = state.customBackground,
                                onPickCustomBackground = { pickBgLauncher.launch("image/*") },
                                onClearCustomBackground = { onSetCustomBackground(null) },
                                viewMode = state.viewMode,
                                onCycleTheme = onCycleTheme,
                                onSetViewMode = { mode ->
                                    onSetViewMode(mode)
                                    if (mode != ViewMode.Gallery) {
                                        val max = itemCountForSection(SettingsSection.Appearance, state.copy(viewMode = mode))
                                        if (contentItemIndex >= max) contentItemIndex = max - 1
                                    }
                                },
                                galleryAlign = state.galleryAlign,
                                onCycleGalleryAlign = onCycleGalleryAlign,
                                highlightIndex = highlight,
                            )
                            SettingsSection.HomeAndBar -> {
                                val tabsTotal = state.manageTabs.size + 2 + state.uncategorizedPlatforms.size
                                TabsSection(
                                    tabs = state.manageTabs,
                                    categories = state.categories,
                                    systemTabs = state.systemTabs,
                                    uncategorizedCount = state.uncategorizedCount,
                                    uncategorizedPlatforms = state.uncategorizedPlatforms,
                                    onRowClick = { clickManageRow(SettingsSection.HomeAndBar, it) },
                                    onMoveTabTo = manage.moveTabTo,
                                    onQuickCreateCategory = manage.quickCreateCategoryForPlatform,
                                    highlightIndex = highlight,
                                )
                                Spacer(Modifier.height(18.dp))
                                HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
                                Spacer(Modifier.height(16.dp))
                                HomeElementsAndStatusBarBlock(
                                    state = state,
                                    startIndex = tabsTotal,
                                    highlightIndex = highlight,
                                    onToggleBottomBar = onToggleBottomBar,
                                    onToggleShowMetadata = onToggleShowMetadata,
                                    onToggleMetaField = manage.toggleMetaField,
                                    onToggleShowClock = onToggleShowClock,
                                    onToggleShowBattery = onToggleShowBattery,
                                    onToggleShowWifi = onToggleShowWifi,
                                    onToggleShowBluetooth = onToggleShowBluetooth,
                                    onToggleShowMobileSignal = onToggleShowMobileSignal,
                                    onToggleShowHeadset = onToggleShowHeadset,
                                    onToggleShowAirplane = onToggleShowAirplane,
                                    onToggleShowDnd = onToggleShowDnd,
                                )
                            }
                            SettingsSection.LibraryAndScrape -> {
                                val scrapeOffset = state.romDirs.size + 4
                                LibrarySection(
                                    dirs = state.romDirs,
                                    autoRoot = state.autoRoot,
                                    showAndroidGames = state.showAndroidGames,
                                    hiddenCount = state.hiddenIds.size,
                                    onRowClick = { clickManageRow(SettingsSection.LibraryAndScrape, it) },
                                    highlightIndex = highlight,
                                )
                                Spacer(Modifier.height(20.dp))
                                HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
                                Spacer(Modifier.height(18.dp))
                                ScrapingSection(
                                    coverSources = state.coverSources,
                                    metadataSources = state.metadataSources,
                                    credentials = state.scraperCredentials,
                                    cacheSizeLabel = formatCacheBytes(scrapeCacheBytes),
                                    onToggleCover = manage.toggleCoverSource,
                                    onMoveCover = manage.moveCoverSource,
                                    onToggleMeta = manage.toggleMetadataSource,
                                    onMoveMeta = manage.moveMetadataSource,
                                    onOpenCred = { credDialogKind = it },
                                    onRowClick = { clickManageRow(SettingsSection.LibraryAndScrape, scrapeOffset + it) },
                                    onClearCoverCache = ::clearScrapeCacheWithFeedback,
                                    onRefreshMetadata = {
                                        if (!NetworkStatus.isOnline(context)) {
                                            Toast.makeText(context, context.getString(R.string.offline_action_toast), Toast.LENGTH_SHORT).show()
                                        } else {
                                            manage.refreshAllMetadata()
                                        }
                                    },
                                    onViewLogs = { showScrapeLogs = true },
                                    highlightIndex = if (highlight >= scrapeOffset) highlight - scrapeOffset else -1,
                                )
                            }
                            SettingsSection.Launcher -> LauncherSection(
                                platforms = launcherPlatformsFor(state, showAllLauncherPlatforms),
                                showAllPlatforms = showAllLauncherPlatforms,
                                onToggleShowAllPlatforms = {
                                    showAllLauncherPlatforms = !showAllLauncherPlatforms
                                    contentItemIndex = 0
                                },
                                selected = state.platformLaunchers,
                                aspectOverrides = state.platformAspects,
                                onPickLauncher = { launcherPickerPlatform = it },
                                highlightIndex = highlight,
                            )
                            SettingsSection.Controls -> InputSection(
                                swapAbxy = state.swapAbxy,
                                keySoundEnabled = state.keySoundEnabled,
                                keyBindings = state.keyBindings,
                                onToggleSwapAbxy = onToggleSwapAbxy,
                                onToggleKeySound = onToggleKeySound,
                                onRebindAction = { action ->
                                    keyBindError = null
                                    capturingAction = action
                                },
                                onResetBindings = {
                                    resetConfirmChoiceIdx = 1
                                    showResetBindingsConfirm = true
                                },
                                highlightIndex = highlight,
                            )
                            SettingsSection.SystemAbout -> {
                                GeneralSection(
                                    clock12h = state.clock12h,
                                    language = state.language,
                                    onToggleClock12h = onToggleClock12h,
                                    onLanguageChange = { code ->
                                        langPicker = null
                                        if (code != state.language) onLanguageChange(code)
                                    },
                                    langPicker = langPicker,
                                    onToggleLangPicker = ::toggleLangPicker,
                                    highlightIndex = highlight,
                                )
                                Spacer(Modifier.height(20.dp))
                                HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
                                Spacer(Modifier.height(18.dp))
                                AboutSection()
                            }
                        }
                    }
                }
            }
        }

        menu?.let { m ->
            ActionMenuDialog(
                state = m,
                onPick = { item -> menu = null; item.action() },
                onDismiss = { menu = null },
            )
        }
        if (browsing) {
            DirBrowser(
                state = browser,
                onPick = { manage.addDir(it); browsing = false },
                onDismiss = { browsing = false },
            )
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
            ScrapeLogsScreen(
                onBack = { showScrapeLogs = false },
                swapAbxy = state.swapAbxy,
                keyBindings = state.keyBindings,
            )
        }
        credDialogKind?.let { kind ->
            ScraperCredentialDialog(
                kind = kind,
                current = state.scraperCredentials,
                onSave = { updated ->
                    manage.saveCredentials(updated)
                    credDialogKind = null
                },
                onDismiss = { credDialogKind = null },
            )
        }
        bindingCategorySpec?.let { spec ->
            val allPlats = remember(state.libraryPlatforms) {
                (state.libraryPlatforms + PlatformCatalog.known)
                    .filter { it.id != PlatformCatalog.ANDROID_ID || it.id in state.libraryPlatforms.map { p -> p.id } }
                    .distinctBy { it.id }
            }
            val libIds = remember(state.libraryPlatforms) { state.libraryPlatforms.map { it.id }.toSet() }
            CategoryPlatformsDialog(
                categoryName = spec.name,
                availablePlatforms = allPlats,
                libraryPlatformIds = libIds,
                initialSelected = spec.initialSources,
                swapAbxy = state.swapAbxy,
                keyBindings = state.keyBindings,
                onConfirm = { picked ->
                    if (spec.catId == null) {
                        manage.createCategory(spec.name, picked)
                    } else {
                        manage.setCategorySources(spec.catId, picked)
                    }
                    bindingCategorySpec = null
                },
                onDismiss = {
                    bindingCategorySpec = null
                },
            )
        }
        launcherPickerPlatform?.let { plat ->
            val defaultPlat = remember(plat.id) { PlatformCatalog.forFolder(plat.id) }
            LauncherPickerDialog(
                title = stringResource(R.string.launcher_picker_title),
                platform = plat,
                currentLauncherId = state.platformLaunchers[plat.id],
                defaultLabel = stringResource(R.string.settings_launcher_auto),
                onSelect = { id ->
                    onSetPlatformLauncher(plat.id, id)
                    launcherPickerPlatform = null
                },
                onDismiss = { launcherPickerPlatform = null },
                showAspectRatio = true,
                defaultAspect = defaultPlat.coverAspect,
                currentAspect = state.platformAspects[plat.id],
                onSetAspect = { aspect -> manage.setPlatformAspect(plat.id, aspect) },
                customLaunchers = state.customLaunchers,
                onSavePreset = manage.saveCustomLauncherPreset,
                onDeletePreset = manage.deleteCustomLauncherPreset,
                swapAbxy = state.swapAbxy,
                keyBindings = state.keyBindings,
            )
        }
        if (showHiddenDialog) {
            HiddenGamesDialog(
                hiddenIds = state.hiddenIds,
                onRestore = { manage.unhideGame(it) },
                onDismiss = { showHiddenDialog = false },
            )
        }
        if (showFontPicker) {
            FontPickerDialog(
                currentFontId = state.fontId,
                swapAbxy = state.swapAbxy,
                keyBindings = state.keyBindings,
                onPickCustomFont = {
                    runCatching { pickFontLauncher.launch(arrayOf("*/*")) }
                },
                onSelect = { id ->
                    manage.setFontId(id)
                    showFontPicker = false
                },
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
                onConfirm = { name, follow ->
                    manage.updateSystemTab(tab.id, name, follow)
                    editingSystemTab = null
                },
                onDismiss = { editingSystemTab = null },
            )
        }
        capturingAction?.let { action ->
            val actionLabel = keyActionLabel(action)
            val currentBadge = GamepadKeys.badgeLabel(state.keyBindings, action, state.swapAbxy)
            val defaultBadge = GamepadKeys.keyLabel(GamepadKeys.defaultKeyCode(action, state.swapAbxy))
            KeyBindCaptureDialog(
                actionLabel = actionLabel,
                currentBadge = currentBadge,
                defaultBadge = defaultBadge,
                errorMessage = keyBindError,
                onClear = {
                    applyKeyBinding(action, GamepadKeys.UNBOUND)
                    capturingAction = null
                    keyBindError = null
                },
                onDismiss = {
                    capturingAction = null
                    keyBindError = null
                },
            )
        }
        pendingKeyConflict?.let { conflict ->
            val targetLabel = keyActionLabel(conflict.action)
            val conflictLabels = conflict.conflicts.map { keyActionLabel(it) }
            val canSwap = GamepadKeys.canSwapConflicts(
                conflict.action,
                conflict.newKeyCode,
                conflict.conflicts,
                state.keyBindings,
                state.swapAbxy,
            )
            KeyBindConflictDialog(
                targetLabel = targetLabel,
                newKeyCode = conflict.newKeyCode,
                conflictLabels = conflictLabels,
                canSwap = canSwap,
                selectedOption = conflictChoiceIdx,
                onSwap = {
                    val oldKey = GamepadKeys.resolvedKeyCode(conflict.action, state.keyBindings, state.swapAbxy)
                    var next = state.keyBindings
                    conflict.conflicts.forEach { c ->
                        next = GamepadKeys.withBinding(next, c, oldKey, state.swapAbxy)
                    }
                    next = GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy)
                    onUpdateKeyBindings(next)
                    pendingKeyConflict = null
                },
                onOverwrite = {
                    var next = state.keyBindings
                    conflict.conflicts.forEach { c ->
                        next = GamepadKeys.withBinding(next, c, GamepadKeys.UNBOUND, state.swapAbxy)
                    }
                    next = GamepadKeys.withBinding(next, conflict.action, conflict.newKeyCode, state.swapAbxy)
                    onUpdateKeyBindings(next)
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
                onConfirm = {
                    showResetBindingsConfirm = false
                    onResetKeyBindings()
                },
                onDismiss = { showResetBindingsConfirm = false },
            )
        }
    }
}

/** 菜单里用到的文字（在组合阶段取好，供按键回调使用）。 */
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

/** 根据 section + itemIndex 触发对应操作 */
private fun triggerContentAction(
    section: SettingsSection,
    itemIndex: Int,
    state: SettingsState,
    onToggleClock12h: () -> Unit,
    onToggleSwapAbxy: () -> Unit,
    onToggleNowTab: () -> Unit,
    onToggleFavsTab: () -> Unit,
    onToggleBottomBar: () -> Unit,
    onToggleShowMetadata: () -> Unit,
    onCycleBackground: () -> Unit,
    onCycleViewMode: () -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onCycleTheme: () -> Unit,
    onRescan: () -> Unit,
    onToggleShowClock: () -> Unit,
    onToggleShowBattery: () -> Unit,
    onToggleShowWifi: () -> Unit,
    onToggleShowBluetooth: () -> Unit,
    onToggleShowMobileSignal: () -> Unit,
    onToggleShowHeadset: () -> Unit,
    onToggleShowAirplane: () -> Unit,
    onToggleShowDnd: () -> Unit,
    onSetPlatformLauncher: (String, String?) -> Unit,
    onCycleGalleryAlign: () -> Unit,
    onToggleMetaField: (MetaFieldKey) -> Unit = {},
) {
    when (section) {
        SettingsSection.SystemAbout -> when (itemIndex) {
            0 -> {} // 语言：A 键展开下拉框由 composable 内部处理
            1 -> onToggleClock12h()
        }
        SettingsSection.Appearance -> {
            val viewModes = ViewMode.entries
            val bgImageRow = if (state.backgroundStyle == BackgroundStyle.Image) 1 else 0
            val viewStart = 4 + bgImageRow
            val alignIdx = viewStart + viewModes.size
            when {
                itemIndex == 0 -> onCycleTheme()
                itemIndex in 1..3 || (bgImageRow == 1 && itemIndex == 4) -> Unit // 由 openManageAction 处理
                itemIndex in viewStart until alignIdx -> onSetViewMode(viewModes[itemIndex - viewStart])
                itemIndex == alignIdx && state.viewMode == ViewMode.Gallery -> onCycleGalleryAlign()
            }
        }
        SettingsSection.HomeAndBar -> {
            val tabsTotal = state.manageTabs.size + 2 + state.uncategorizedPlatforms.size
            val rel = itemIndex - tabsTotal
            if (rel < 0) return
            val fields = MetaFieldKey.entries
            val fieldCount = if (state.showMetadata) fields.size else 0
            val barStart = 2 + fieldCount
            when {
                rel == 0 -> onToggleBottomBar()
                rel == 1 -> onToggleShowMetadata()
                state.showMetadata && rel in 2 until (2 + fieldCount) -> onToggleMetaField(fields[rel - 2])
                rel == barStart + 0 -> onToggleShowClock()
                rel == barStart + 1 -> onToggleShowBattery()
                rel == barStart + 2 -> onToggleShowWifi()
                rel == barStart + 3 -> onToggleShowBluetooth()
                rel == barStart + 4 -> onToggleShowMobileSignal()
                rel == barStart + 5 -> onToggleShowHeadset()
                rel == barStart + 6 -> onToggleShowAirplane()
                rel == barStart + 7 -> onToggleShowDnd()
            }
        }
        SettingsSection.LibraryAndScrape -> {} // 由 openManageAction 处理
        SettingsSection.Controls -> when (itemIndex) {
            0 -> onToggleSwapAbxy()
        }
        SettingsSection.Launcher -> state.launcherRows.getOrNull(itemIndex)?.let { row ->
            onSetPlatformLauncher(row.platformId, nextLauncher(row, state.platformLaunchers[row.platformId]))
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 顶部标题栏
// ════════════════════════════════════════════════════════════════

@Composable
private fun SettingsTopBar(onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(DeckShapes.tabPill)
                .background(DeckColors.Surface)
                .soundClickable(onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", style = DeckType.menuButton, color = DeckColors.TextPrimary)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.settings_title),
            style = DeckType.settingsSection.copy(fontWeight = FontWeight.Bold),
            color = DeckColors.Ruby,
        )
    }
    HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
}

// ════════════════════════════════════════════════════════════════
// 左侧菜单
// ════════════════════════════════════════════════════════════════

@Composable
private fun SettingsMenu(
    sections: List<SettingsSection>,
    selected: Int,
    isFocused: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(DeckColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        sections.forEachIndexed { index, section ->
            val isSelected = index == selected
            val bgColor = when {
                isSelected && isFocused -> DeckColors.Ruby.copy(alpha = 0.12f)
                isSelected -> DeckColors.Surface
                else -> Color.Transparent
            }
            val textColor = when {
                isSelected && isFocused -> DeckColors.Ruby
                isSelected -> DeckColors.Ruby
                else -> DeckColors.TextSecondary
            }
            val borderColor = if (isSelected && isFocused) DeckColors.Ruby else Color.Transparent

            Row(
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewWhen(isSelected)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(DeckShapes.viewMenuItem)
                    .background(bgColor)
                    .soundClickable { onSelect(index) }
                    .padding(start = if (isSelected && isFocused) 10.dp else 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 选中指示条
                if (isSelected && isFocused) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .clip(DeckShapes.settingsDropdown)
                            .background(DeckColors.Ruby)
                    )
                    Spacer(Modifier.width(7.dp))
                }
                DeckIcon(
                    icon = sectionIcon(section),
                    tint = textColor,
                    size = 18.dp,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    sectionLabel(section),
                    style = if (isSelected) DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold)
                    else DeckType.settingsValue,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun sectionLabel(section: SettingsSection): String = when (section) {
    SettingsSection.Appearance -> stringResource(R.string.settings_sec_appearance)
    SettingsSection.HomeAndBar -> stringResource(R.string.settings_sec_home)
    SettingsSection.LibraryAndScrape -> stringResource(R.string.settings_sec_library_scrape)
    SettingsSection.Launcher -> stringResource(R.string.settings_launcher)
    SettingsSection.Controls -> stringResource(R.string.settings_sec_controls)
    SettingsSection.SystemAbout -> stringResource(R.string.settings_sec_system)
}

private fun sectionIcon(section: SettingsSection) = when (section) {
    SettingsSection.Appearance -> DeckIcons.Palette
    SettingsSection.HomeAndBar -> DeckIcons.LayoutGrid
    SettingsSection.LibraryAndScrape -> DeckIcons.Folder
    SettingsSection.Launcher -> DeckIcons.Cpu
    SettingsSection.Controls -> DeckIcons.Gamepad
    SettingsSection.SystemAbout -> DeckIcons.Sliders
}

// ════════════════════════════════════════════════════════════════
// 通用设置
// ════════════════════════════════════════════════════════════════

@Composable
private fun GeneralSection(
    clock12h: Boolean,
    language: String,
    onToggleClock12h: () -> Unit,
    onLanguageChange: (String) -> Unit,
    langPicker: Int?,
    onToggleLangPicker: () -> Unit,
    highlightIndex: Int = -1,
) {
    SectionTitle(stringResource(R.string.settings_general))

    val systemLabel = stringResource(R.string.lang_system)
    val labels = mapOf(
        AppLocale.SYSTEM to systemLabel,
        "en" to "English",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "ja" to "日本語",
        "ru" to "Русский",
    )

    SettingsChoice(
        label = stringResource(R.string.settings_language),
        current = labels[language] ?: systemLabel,
        onClick = onToggleLangPicker,
        highlighted = highlightIndex == 0 && langPicker == null,
    )
    // 语言列表：内联展开，支持手柄上下选择
    if (langPicker != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                .clip(DeckShapes.settingsDropdown)
                .background(DeckColors.Surface)
                .padding(vertical = 4.dp),
        ) {
            AppLocale.CODES.forEachIndexed { idx, code ->
                val hl = idx == langPicker
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewWhen(hl)
                        .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else Color.Transparent)
                        .soundClickable { onLanguageChange(code) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        labels[code].orEmpty(),
                        style = DeckType.settingsValue,
                        color = if (hl || code == language) DeckColors.Ruby else DeckColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (code == language) {
                        Text("✓", style = DeckType.settingsValue, color = DeckColors.Ruby)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsToggle(
        label = stringResource(R.string.settings_clock_12h),
        description = stringResource(R.string.settings_clock_desc),
        checked = clock12h,
        onToggle = onToggleClock12h,
        highlighted = highlightIndex == 1,
    )
}

// ════════════════════════════════════════════════════════════════
// 显示设置
// ════════════════════════════════════════════════════════════════

@Composable
private fun DisplaySection(
    theme: AppTheme,
    themeAccent: ThemeAccent,
    accentPicker: Int?,
    onToggleAccentPicker: () -> Unit,
    onSelectThemeAccent: (ThemeAccent) -> Unit,
    fontId: String,
    onOpenFontPicker: () -> Unit,
    backgroundStyle: BackgroundStyle,
    bgPicker: Int?,
    onToggleBgPicker: () -> Unit,
    onSelectBackgroundStyle: (BackgroundStyle) -> Unit,
    customBackground: String?,
    onPickCustomBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    viewMode: ViewMode,
    onCycleTheme: () -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    galleryAlign: GalleryAlign,
    onCycleGalleryAlign: () -> Unit,
    highlightIndex: Int = -1,
) {
    SectionTitle(stringResource(R.string.settings_sec_appearance))

    SettingsChoice(
        label = stringResource(R.string.settings_theme),
        current = when (theme) {
            AppTheme.System -> stringResource(R.string.theme_system)
            AppTheme.Light -> stringResource(R.string.theme_light)
            AppTheme.Dark -> stringResource(R.string.theme_dark)
        },
        onClick = onCycleTheme,
        highlighted = highlightIndex == 0,
    )

    // 主题色（8 种预设颜色 + 内联展开色板）
    val accentHl = highlightIndex == 1 && accentPicker == null
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(accentHl)
            .clip(DeckShapes.settingsDropdown)
            .background(if (accentHl) DeckColors.Ruby.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onToggleAccentPicker)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (accentHl) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(DeckShapes.settingsDropdown)
                        .background(DeckColors.Ruby),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(R.string.settings_theme_accent),
                style = DeckType.settingsValue,
                color = if (accentHl) DeckColors.Ruby else DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier
                .clip(DeckShapes.settingsDropdown)
                .background(DeckColors.Surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(DeckColors.Ruby),
            )
            Text(
                themeAccentLabel(themeAccent),
                style = DeckType.settingsNav,
                color = DeckColors.Ruby,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (accentPicker != null) {
        val isDarkNow = DeckColors.isDark
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
                .clip(DeckShapes.settingsDropdown)
                .background(DeckColors.Surface)
                .padding(vertical = 4.dp),
        ) {
            ThemeAccent.entries.forEachIndexed { idx, acc ->
                val hl = idx == accentPicker
                val sel = acc == themeAccent
                val swatch = Color(if (isDarkNow) acc.darkPrimary else acc.lightPrimary)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewWhen(hl)
                        .background(if (hl) swatch.copy(alpha = 0.14f) else Color.Transparent)
                        .soundClickable { onSelectThemeAccent(acc) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(swatch)
                            .border(
                                width = if (sel) 1.dp else 0.5.dp,
                                color = if (sel) DeckColors.TextPrimary else DeckColors.DialogBorder,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        themeAccentLabel(acc),
                        style = if (hl || sel) DeckType.listItemSelected else DeckType.settingsValue,
                        color = if (hl || sel) swatch else DeckColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (sel) {
                        Text("✓", style = DeckType.settingsValue, color = swatch)
                    }
                }
            }
        }
    }

    // 字体选择
    val fontDefault = stringResource(R.string.font_default)
    val fontSans = stringResource(R.string.font_system_sans)
    val fontSerif = stringResource(R.string.font_system_serif)
    val fontMono = stringResource(R.string.font_system_mono)
    val fontMissingFmt = stringResource(R.string.font_missing_fallback)
    val currentFontName = remember(fontId, fontDefault, fontSans, fontSerif, fontMono, fontMissingFmt) {
        SystemFonts.displayName(fontId, fontDefault, fontSans, fontSerif, fontMono, fontMissingFmt)
    }
    SettingsChoice(
        label = stringResource(R.string.settings_font),
        current = currentFontName,
        onClick = onOpenFontPicker,
        highlighted = highlightIndex == 2,
    )

    SettingsChoice(
        label = stringResource(R.string.settings_background),
        current = bgStyleLabel(backgroundStyle),
        onClick = onToggleBgPicker,
        highlighted = highlightIndex == 3 && bgPicker == null,
    )

    if (bgPicker != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
                .clip(DeckShapes.settingsDropdown)
                .background(DeckColors.Surface)
                .padding(vertical = 4.dp),
        ) {
            BackgroundStyle.entries.forEachIndexed { idx, style ->
                val hl = idx == bgPicker
                val sel = style == backgroundStyle
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewWhen(hl)
                        .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else Color.Transparent)
                        .soundClickable { onSelectBackgroundStyle(style) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            bgStyleLabel(style),
                            style = if (hl || sel) DeckType.listItemSelected else DeckType.settingsValue,
                            color = if (hl || sel) DeckColors.Ruby else DeckColors.TextPrimary,
                        )
                        Text(
                            bgStyleDesc(style),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                    }
                    if (sel) {
                        Spacer(Modifier.width(10.dp))
                        Text("✓", style = DeckType.settingsValue, color = DeckColors.Ruby)
                    }
                }
            }
        }
    }

    val bgImageRow = if (backgroundStyle == BackgroundStyle.Image) 1 else 0
    if (backgroundStyle == BackgroundStyle.Image) {
        val bgHl = highlightIndex == 4
        val fileName = customBackground?.let { File(it).name } ?: stringResource(R.string.settings_custom_bg_none)
        ManageRow(
            title = stringResource(R.string.settings_custom_bg),
            subtitle = fileName,
            badge = stringResource(R.string.settings_custom_bg_pick),
            onBadgeClick = onPickCustomBackground,
            badgeAccent = true,
            dimmed = customBackground == null,
            highlighted = bgHl,
            onClick = onPickCustomBackground,
            trailing = if (customBackground != null) {
                {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.settings_custom_bg_clear),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                        modifier = Modifier
                            .clip(DeckShapes.settingsDropdown)
                            .background(DeckColors.Surface)
                            .clickable(onClick = onClearCustomBackground)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            } else null,
        )
    }

    Spacer(Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.settings_default_view),
        style = DeckType.settingsLabel,
        color = DeckColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )

    val viewStart = 4 + bgImageRow
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DeckShapes.settingsDropdown)
            .background(DeckColors.Surface)
            .padding(vertical = 4.dp),
    ) {
        ViewMode.entries.forEachIndexed { idx, mode ->
            val hl = highlightIndex == viewStart + idx
            val active = mode == viewMode
            val tint = if (hl || active) DeckColors.Ruby else DeckColors.TextPrimary
            Row(
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewWhen(hl)
                    .background(
                        when {
                            hl -> DeckColors.Ruby.copy(alpha = 0.12f)
                            active -> DeckColors.TextPrimary.copy(alpha = 0.05f)
                            else -> Color.Transparent
                        },
                    )
                    .soundClickable { onSetViewMode(mode) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .drawBehind { drawViewModeIcon(mode, tint) },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = mode.name,
                    style = if (active || hl) DeckType.listItemSelected else DeckType.settingsValue,
                    color = tint,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    Text("✓", style = DeckType.settingsValue, color = DeckColors.Ruby)
                }
            }
        }
    }

    if (viewMode == ViewMode.Gallery) {
        Spacer(Modifier.height(12.dp))
        SettingsChoice(
            label = stringResource(R.string.settings_gallery_align),
            current = when (galleryAlign) {
                GalleryAlign.Top -> stringResource(R.string.align_top)
                GalleryAlign.Center -> stringResource(R.string.align_center)
                GalleryAlign.Bottom -> stringResource(R.string.align_bottom)
            },
            onClick = onCycleGalleryAlign,
            highlighted = highlightIndex == viewStart + ViewMode.entries.size,
        )
    }
}

@Composable
private fun themeAccentLabel(accent: ThemeAccent): String = when (accent) {
    ThemeAccent.Ruby -> stringResource(R.string.accent_ruby)
    ThemeAccent.Ocean -> stringResource(R.string.accent_ocean)
    ThemeAccent.Emerald -> stringResource(R.string.accent_emerald)
    ThemeAccent.Violet -> stringResource(R.string.accent_violet)
    ThemeAccent.Sunset -> stringResource(R.string.accent_sunset)
    ThemeAccent.Sakura -> stringResource(R.string.accent_sakura)
    ThemeAccent.Cyan -> stringResource(R.string.accent_cyan)
    ThemeAccent.Gold -> stringResource(R.string.accent_gold)
    ThemeAccent.Sage -> stringResource(R.string.accent_sage)
    ThemeAccent.Slate -> stringResource(R.string.accent_slate)
    ThemeAccent.Terracotta -> stringResource(R.string.accent_terracotta)
    ThemeAccent.Graphite -> stringResource(R.string.accent_graphite)
}

@Composable
private fun bgStyleLabel(style: BackgroundStyle): String = when (style) {
    BackgroundStyle.Waves -> stringResource(R.string.bg_style_waves)
    BackgroundStyle.Gradient -> stringResource(R.string.bg_style_gradient)
    BackgroundStyle.Particles -> stringResource(R.string.bg_style_particles)
    BackgroundStyle.Aurora -> stringResource(R.string.bg_style_aurora)
    BackgroundStyle.Topo -> stringResource(R.string.bg_style_topo)
    BackgroundStyle.DotMatrix -> stringResource(R.string.bg_style_dot_matrix)
    BackgroundStyle.CoverBlur -> stringResource(R.string.bg_style_cover_blur)
    BackgroundStyle.Static -> stringResource(R.string.bg_style_static)
    BackgroundStyle.Image -> stringResource(R.string.bg_style_image)
    BackgroundStyle.None -> stringResource(R.string.bg_style_none)
}

@Composable
private fun bgStyleDesc(style: BackgroundStyle): String = when (style) {
    BackgroundStyle.Waves -> stringResource(R.string.bg_style_waves_desc)
    BackgroundStyle.Gradient -> stringResource(R.string.bg_style_gradient_desc)
    BackgroundStyle.Particles -> stringResource(R.string.bg_style_particles_desc)
    BackgroundStyle.Aurora -> stringResource(R.string.bg_style_aurora_desc)
    BackgroundStyle.Topo -> stringResource(R.string.bg_style_topo_desc)
    BackgroundStyle.DotMatrix -> stringResource(R.string.bg_style_dot_matrix_desc)
    BackgroundStyle.CoverBlur -> stringResource(R.string.bg_style_cover_blur_desc)
    BackgroundStyle.Static -> stringResource(R.string.bg_style_static_desc)
    BackgroundStyle.Image -> stringResource(R.string.bg_style_image_desc)
    BackgroundStyle.None -> stringResource(R.string.bg_style_none_desc)
}

// ════════════════════════════════════════════════════════════════
// 标签设置
// ════════════════════════════════════════════════════════════════

@Composable
private fun TabsSection(
    tabs: List<TabEntry>,
    categories: List<Category>,
    systemTabs: Map<String, SystemTabConfig> = emptyMap(),
    uncategorizedCount: Int,
    uncategorizedPlatforms: List<UncategorizedPlatformInfo>,
    onRowClick: (Int) -> Unit,
    onMoveTabTo: (Int, Int) -> Unit,
    onQuickCreateCategory: (Platform) -> Unit,
    highlightIndex: Int = -1,
) {
    SectionTitle(stringResource(R.string.settings_tabs))
    Text(stringResource(R.string.settings_tabs_desc), style = DeckType.settingsLabel, color = DeckColors.TextSecondary)
    Spacer(Modifier.height(12.dp))

    val kindNow = stringResource(R.string.tab_kind_now)
    val kindFavs = stringResource(R.string.tab_kind_favs)
    val kindOther = stringResource(R.string.tab_kind_other)
    val kindCustom = stringResource(R.string.tab_kind_custom)
    val includes = stringResource(R.string.tab_includes)
    val hiddenLabel = stringResource(R.string.tab_hidden)
    val countFmt = stringResource(R.string.tab_games_count)
    val nowDefaultName = stringResource(R.string.tab_name_now)
    val favsDefaultName = stringResource(R.string.tab_name_favs)
    val otherDefaultName = stringResource(R.string.tab_name_other)
    val catById = remember(categories) { categories.associateBy { it.id } }

    DragReorderColumn(
        items = tabs,
        keyOf = { it.id },
        onMove = onMoveTabTo,
    ) { idx, t, dragHandleModifier ->
        val displayTitle = when (t.type) {
            TabType.Now -> if (t.followSystemLanguage || t.label.isBlank()) nowDefaultName else t.label
            TabType.Favs -> if (t.followSystemLanguage || t.label.isBlank()) favsDefaultName else t.label
            TabType.Other -> if (t.followSystemLanguage || t.label.isBlank()) otherDefaultName else t.label
            TabType.Custom -> t.label
        }
        val kindBase = when (t.type) {
            TabType.Now -> {
                val sort = systemTabs["now"]?.sortOrder
                if (sort == null) kindNow else "$kindNow · ${sortOrderLabel(sort)}"
            }
            TabType.Favs -> {
                val sort = systemTabs["favs"]?.sortOrder ?: CategorySortOrder.TitleAsc
                if (sort == CategorySortOrder.TitleAsc) kindFavs else "$kindFavs · ${sortOrderLabel(sort)}"
            }
            TabType.Other -> {
                val sort = systemTabs["other"]?.sortOrder ?: CategorySortOrder.TitleAsc
                if (sort == CategorySortOrder.TitleAsc) kindOther else "$kindOther · ${sortOrderLabel(sort)}"
            }
            TabType.Custom -> {
                val base = if (t.includes.isEmpty()) kindCustom
                else kindCustom + " · " + includes.format(t.includes.joinToString(", "))
                val sort = catById[t.id]?.sortOrder ?: CategorySortOrder.TitleAsc
                if (sort == CategorySortOrder.TitleAsc) base else "$base · ${sortOrderLabel(sort)}"
            }
        }
        val countText = countFmt.format(t.gameCount)
        ManageRow(
            title = displayTitle,
            subtitle = kindBase,
            badge = if (t.hidden) "$hiddenLabel · ${t.gameCount}" else countText,
            dimmed = t.hidden,
            highlighted = highlightIndex == idx,
            onClick = { onRowClick(idx) },
            leading = { DragHandle(dragHandleModifier) },
        )
    }
    Spacer(Modifier.height(8.dp))
    ActionRow(
        label = stringResource(R.string.tab_new),
        highlighted = highlightIndex == tabs.size,
        onClick = { onRowClick(tabs.size) },
    )
    Spacer(Modifier.height(8.dp))
    ActionRow(
        label = stringResource(R.string.tab_reset_order),
        highlighted = highlightIndex == tabs.size + 1,
        onClick = { onRowClick(tabs.size + 1) },
    )

    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
    Spacer(Modifier.height(16.dp))

    if (uncategorizedPlatforms.isEmpty()) {
        Text(
            stringResource(R.string.tab_uncategorized_all_done),
            style = DeckType.settingsLabel,
            color = DeckColors.TextSecondary,
        )
    } else {
        Text(
            stringResource(R.string.tab_uncategorized_title, uncategorizedCount),
            style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
            color = DeckColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        val quickCreateLabel = stringResource(R.string.tab_quick_create_cat)
        uncategorizedPlatforms.forEachIndexed { uIdx, info ->
            val rowIdx = tabs.size + 2 + uIdx
            ManageRow(
                title = "${info.platform.shortName} · ${info.platform.fullName}",
                subtitle = countFmt.format(info.gameCount),
                badge = quickCreateLabel,
                onBadgeClick = { onQuickCreateCategory(info.platform) },
                badgeAccent = true,
                dimmed = false,
                highlighted = highlightIndex == rowIdx,
                onClick = { onRowClick(rowIdx) },
            )
        }
    }
}

@Composable
private fun sortOrderLabel(order: CategorySortOrder): String = when (order) {
    CategorySortOrder.TitleAsc -> stringResource(R.string.sort_title_asc)
    CategorySortOrder.TitleDesc -> stringResource(R.string.sort_title_desc)
    CategorySortOrder.ReleaseNewest -> stringResource(R.string.sort_release_newest)
    CategorySortOrder.ReleaseOldest -> stringResource(R.string.sort_release_oldest)
    CategorySortOrder.ModifiedNewest -> stringResource(R.string.sort_modified_newest)
    CategorySortOrder.SizeDesc -> stringResource(R.string.sort_size_desc)
    CategorySortOrder.SizeAsc -> stringResource(R.string.sort_size_asc)
}

// ════════════════════════════════════════════════════════════════
// 刮削与元数据服务商设置
// ════════════════════════════════════════════════════════════════

@Composable
private fun ScrapingSection(
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
    onClearCoverCache: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onViewLogs: () -> Unit,
    highlightIndex: Int = -1,
) {
    SectionTitle(stringResource(R.string.settings_scraping))

    Text(
        stringResource(R.string.scraping_cover_title),
        style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
        color = DeckColors.TextPrimary,
    )
    Text(
        stringResource(R.string.scraping_cover_desc),
        style = DeckType.settingsLabel,
        color = DeckColors.TextSecondary,
    )
    Spacer(Modifier.height(10.dp))

    DragReorderColumn(
        items = coverSources,
        keyOf = { it.id },
        onMove = onMoveCover,
    ) { idx, cfg, dragHandleModifier ->
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

    Spacer(Modifier.height(10.dp))
    val clearIdx = coverSources.size
    ManageRow(
        title = stringResource(R.string.scraping_clear_cache),
        subtitle = stringResource(R.string.scraping_clear_cache_desc),
        badge = cacheSizeLabel,
        dimmed = false,
        highlighted = highlightIndex == clearIdx,
        onClick = onClearCoverCache,
        leading = {
            DeckIcon(
                DeckIcons.Trash,
                size = 16.dp,
                tint = if (highlightIndex == clearIdx) DeckColors.Ruby else DeckColors.TextSecondary,
            )
        },
    )

    Spacer(Modifier.height(22.dp))
    HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
    Spacer(Modifier.height(18.dp))

    Text(
        stringResource(R.string.scraping_meta_title),
        style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
        color = DeckColors.TextPrimary,
    )
    Text(
        stringResource(R.string.scraping_meta_desc),
        style = DeckType.settingsLabel,
        color = DeckColors.TextSecondary,
    )
    Spacer(Modifier.height(10.dp))

    val metaOffset = coverSources.size + 1
    DragReorderColumn(
        items = metadataSources,
        keyOf = { it.id },
        onMove = onMoveMeta,
    ) { idx, cfg, dragHandleModifier ->
        val desc = ScraperCatalog.metaDescriptor(cfg.id)
        if (desc != null) {
            SourceConfigRow(
                index = idx,
                config = cfg,
                descriptor = desc,
                credentials = credentials,
                highlighted = highlightIndex == metaOffset + idx,
                dragHandleModifier = dragHandleModifier,
                onRowClick = { onRowClick(metaOffset + idx) },
                onToggle = { onToggleMeta(cfg.id) },
                onOpenCred = onOpenCred,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    ActionRow(
        label = stringResource(R.string.scraping_refresh_meta),
        highlighted = highlightIndex == metaOffset + metadataSources.size,
        onClick = onRefreshMetadata,
    )
    Spacer(Modifier.height(6.dp))
    ActionRow(
        label = stringResource(R.string.scraping_view_logs),
        highlighted = highlightIndex == metaOffset + metadataSources.size + 1,
        onClick = onViewLogs,
    )
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

    ManageRow(
        title = "${index + 1}. ${stringResource(descriptor.nameRes)}",
        titleMaxLines = 2,
        subtitle = stringResource(descriptor.descRes),
        badge = badge,
        onBadgeClick = descriptor.credentialKind?.let { kind -> { onOpenCred(kind) } },
        badgeAccent = descriptor.credentialKind != null && !credConfigured,
        dimmed = !config.enabled,
        highlighted = highlighted,
        onClick = onRowClick,
        leading = { DragHandle(dragHandleModifier) },
        trailing = {
            Switch(
                checked = config.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DeckColors.Background,
                    checkedTrackColor = DeckColors.Ruby,
                    uncheckedThumbColor = DeckColors.TextSecondary,
                    uncheckedTrackColor = DeckColors.Surface,
                ),
            )
        },
    )
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(DeckShapes.settingsDropdown)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        DeckIcon(
            icon = DeckIcons.GripHorizontal,
            tint = DeckColors.TextSecondary,
            size = 16.dp,
        )
    }
}

@Composable
private fun <T> DragReorderColumn(
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
        if (draggingKey == null) {
            localItems = items
        }
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
                                val h = itemHeights[itemKey] ?: 110f
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

// ════════════════════════════════════════════════════════════════
// 输入设置
// ════════════════════════════════════════════════════════════════

@Composable
private fun InputSection(
    swapAbxy: Boolean,
    keySoundEnabled: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onToggleSwapAbxy: () -> Unit,
    onToggleKeySound: () -> Unit,
    onRebindAction: (KeyAction) -> Unit,
    onResetBindings: () -> Unit,
    highlightIndex: Int = -1,
) {
    SectionTitle(stringResource(R.string.settings_input))

    SettingsToggle(
        label = stringResource(R.string.settings_swap_abxy),
        description = stringResource(R.string.settings_swap_desc),
        checked = swapAbxy,
        onToggle = onToggleSwapAbxy,
        highlighted = highlightIndex == 0,
    )

    Spacer(Modifier.height(4.dp))

    SettingsToggle(
        label = stringResource(R.string.settings_key_sound),
        description = stringResource(R.string.settings_key_sound_desc),
        checked = keySoundEnabled,
        onToggle = onToggleKeySound,
        highlighted = highlightIndex == 1,
    )

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
    Spacer(Modifier.height(14.dp))

    Text(
        stringResource(R.string.settings_keybindings),
        style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
        color = DeckColors.TextPrimary,
    )
    Text(
        stringResource(R.string.settings_keybindings_desc),
        style = DeckType.settingsLabel,
        color = DeckColors.TextSecondary,
    )
    Spacer(Modifier.height(10.dp))

    val unboundLabel = stringResource(R.string.keybind_unbound)
    val actions = KeyAction.entries
    actions.forEachIndexed { idx, action ->
        val rowIdx = 2 + idx
        val hl = highlightIndex == rowIdx
        val badge = GamepadKeys.badgeLabel(keyBindings, action, swapAbxy)
        Row(
            Modifier
                .fillMaxWidth()
                .bringIntoViewWhen(hl)
                .clip(DeckShapes.settingsDropdown)
                .background(if (hl) DeckColors.Ruby.copy(alpha = 0.08f) else Color.Transparent)
                .soundClickable { onRebindAction(action) }
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (hl) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .clip(DeckShapes.settingsDropdown)
                            .background(DeckColors.Ruby),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    keyActionLabel(action),
                    style = DeckType.settingsValue,
                    color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                )
            }
            if (badge != null) {
                ButtonBadge(badge)
            } else {
                Text(
                    unboundLabel,
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    ActionRow(
        label = stringResource(R.string.settings_keybindings_reset),
        highlighted = highlightIndex == 2 + actions.size,
        onClick = onResetBindings,
    )
}

@Composable
private fun keyActionLabel(action: KeyAction): String = when (action) {
    KeyAction.PrevTab -> stringResource(R.string.key_action_prev_tab)
    KeyAction.NextTab -> stringResource(R.string.key_action_next_tab)
    KeyAction.Apps -> stringResource(R.string.key_action_apps)
    KeyAction.Settings -> stringResource(R.string.key_action_settings)
    KeyAction.Play -> stringResource(R.string.key_action_play)
    KeyAction.Back -> stringResource(R.string.key_action_back)
    KeyAction.Menu -> stringResource(R.string.key_action_menu)
}

// ════════════════════════════════════════════════════════════════
// 状态栏设置
// ════════════════════════════════════════════════════════════════

@Composable
private fun StatusBarSection(
    state: SettingsState,
    onToggleShowClock: () -> Unit,
    onToggleShowBattery: () -> Unit,
    onToggleShowWifi: () -> Unit,
    onToggleShowBluetooth: () -> Unit,
    onToggleShowMobileSignal: () -> Unit,
    onToggleShowHeadset: () -> Unit,
    onToggleShowAirplane: () -> Unit,
    onToggleShowDnd: () -> Unit,
    highlightIndex: Int = -1,
) {
    SectionTitle(stringResource(R.string.settings_statusbar))

    Text(
        stringResource(R.string.settings_statusbar_desc),
        style = DeckType.settingsLabel,
        color = DeckColors.TextSecondary,
        modifier = Modifier.padding(bottom = 16.dp),
    )

    SettingsToggle(
        label = stringResource(R.string.settings_show_clock),
        description = "",
        checked = state.showClock,
        onToggle = onToggleShowClock,
        highlighted = highlightIndex == 0,
    )
    SettingsToggle(
        label = stringResource(R.string.settings_show_battery),
        description = "",
        checked = state.showBattery,
        onToggle = onToggleShowBattery,
        highlighted = highlightIndex == 1,
    )
    SettingsToggle(
        label = stringResource(R.string.settings_show_wifi),
        description = "",
        checked = state.showWifi,
        onToggle = onToggleShowWifi,
        highlighted = highlightIndex == 2,
    )
    SettingsToggle(
        label = stringResource(R.string.settings_show_bluetooth),
        description = "",
        checked = state.showBluetooth,
        onToggle = onToggleShowBluetooth,
        highlighted = highlightIndex == 3,
    )
    SettingsToggle(
        label = stringResource(R.string.settings_show_mobile),
        description = stringResource(R.string.settings_show_mobile_desc),
        checked = state.showMobileSignal,
        onToggle = onToggleShowMobileSignal,
        highlighted = highlightIndex == 4,
    )
    SettingsToggle(
        label = stringResource(R.string.settings_show_headset),
        description = "",
        checked = state.showHeadset,
        onToggle = onToggleShowHeadset,
        highlighted = highlightIndex == 5,
    )
    SettingsToggle(
        label = stringResource(R.string.settings_show_airplane),
        description = "",
        checked = state.showAirplane,
        onToggle = onToggleShowAirplane,
        highlighted = highlightIndex == 6,
    )
    SettingsToggle(
        label = stringResource(R.string.settings_show_dnd),
        description = "",
        checked = state.showDnd,
        onToggle = onToggleShowDnd,
        highlighted = highlightIndex == 7,
    )
}

/** 主页底栏、封面元数据字段与紧凑双列状态栏开关块（合入「主页与状态栏」分类）。 */
@Composable
private fun HomeElementsAndStatusBarBlock(
    state: SettingsState,
    startIndex: Int,
    highlightIndex: Int,
    onToggleBottomBar: () -> Unit,
    onToggleShowMetadata: () -> Unit,
    onToggleMetaField: (MetaFieldKey) -> Unit,
    onToggleShowClock: () -> Unit,
    onToggleShowBattery: () -> Unit,
    onToggleShowWifi: () -> Unit,
    onToggleShowBluetooth: () -> Unit,
    onToggleShowMobileSignal: () -> Unit,
    onToggleShowHeadset: () -> Unit,
    onToggleShowAirplane: () -> Unit,
    onToggleShowDnd: () -> Unit,
) {
    SettingsToggle(
        label = stringResource(R.string.settings_show_bottom_bar),
        description = "",
        checked = state.bottomBar,
        onToggle = onToggleBottomBar,
        highlighted = highlightIndex == startIndex,
    )
    Spacer(Modifier.height(4.dp))
    SettingsToggle(
        label = stringResource(R.string.settings_show_metadata),
        description = stringResource(R.string.settings_metadata_desc),
        checked = state.showMetadata,
        onToggle = onToggleShowMetadata,
        highlighted = highlightIndex == startIndex + 1,
    )

    val metaFields = if (state.showMetadata) MetaFieldKey.entries.size else 0
    if (state.showMetadata) {
        Text(
            text = stringResource(R.string.settings_metadata_fields),
            style = DeckType.settingsLabel,
            color = DeckColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(DeckShapes.settingsDropdown)
                .background(DeckColors.Surface)
                .padding(vertical = 4.dp),
        ) {
            val items = listOf(
                Triple(MetaFieldKey.ReleaseDate, stringResource(R.string.meta_release_date), state.metaDisplay.releaseDate),
                Triple(MetaFieldKey.Genre, stringResource(R.string.meta_genre), state.metaDisplay.genre),
                Triple(MetaFieldKey.Developer, stringResource(R.string.meta_developer), state.metaDisplay.developer),
                Triple(MetaFieldKey.Publisher, stringResource(R.string.meta_publisher), state.metaDisplay.publisher),
                Triple(MetaFieldKey.FileSize, stringResource(R.string.meta_file_size), state.metaDisplay.fileSize),
            )
            items.forEachIndexed { idx, (key, label, checked) ->
                val hl = highlightIndex == startIndex + 2 + idx
                val tint = if (hl || checked) DeckColors.Ruby else DeckColors.TextPrimary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewWhen(hl)
                        .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else Color.Transparent)
                        .soundClickable { onToggleMetaField(key) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = if (hl) DeckType.listItemSelected else DeckType.settingsValue,
                        color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (checked) {
                        DeckIcon(DeckIcons.Check, tint = tint, size = 16.dp)
                    } else {
                        Text("—", style = DeckType.settingsValue, color = DeckColors.TextSecondary)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
    Spacer(Modifier.height(16.dp))

    Text(
        stringResource(R.string.settings_statusbar),
        style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
        color = DeckColors.TextPrimary,
    )
    Text(
        stringResource(R.string.settings_statusbar_desc),
        style = DeckType.settingsLabel,
        color = DeckColors.TextSecondary,
    )
    Spacer(Modifier.height(10.dp))

    val barStart = startIndex + 2 + metaFields
    val statusToggles = listOf(
        Triple(stringResource(R.string.settings_show_clock), state.showClock, onToggleShowClock),
        Triple(stringResource(R.string.settings_show_battery), state.showBattery, onToggleShowBattery),
        Triple(stringResource(R.string.settings_show_wifi), state.showWifi, onToggleShowWifi),
        Triple(stringResource(R.string.settings_show_bluetooth), state.showBluetooth, onToggleShowBluetooth),
        Triple(stringResource(R.string.settings_show_mobile), state.showMobileSignal, onToggleShowMobileSignal),
        Triple(stringResource(R.string.settings_show_headset), state.showHeadset, onToggleShowHeadset),
        Triple(stringResource(R.string.settings_show_airplane), state.showAirplane, onToggleShowAirplane),
        Triple(stringResource(R.string.settings_show_dnd), state.showDnd, onToggleShowDnd),
    )
    // 紧凑 2 列卡片网格，一屏即可总览全部状态栏图标开关
    statusToggles.chunked(2).forEachIndexed { rowIdx, pair ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEachIndexed { colIdx, (label, checked, onToggle) ->
                val itemIdx = barStart + rowIdx * 2 + colIdx
                val hl = highlightIndex == itemIdx
                Row(
                    Modifier
                        .weight(1f)
                        .bringIntoViewWhen(hl)
                        .clip(DeckShapes.settingsDropdown)
                        .background(
                            when {
                                hl -> DeckColors.Ruby.copy(alpha = 0.12f)
                                checked -> DeckColors.Surface
                                else -> DeckColors.Surface.copy(alpha = 0.55f)
                            },
                        )
                        .soundClickable(onClick = onToggle)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = if (hl) DeckType.listItemSelected else DeckType.settingsValue,
                        color = when {
                            hl -> DeckColors.Ruby
                            checked -> DeckColors.TextPrimary
                            else -> DeckColors.TextSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (checked) DeckColors.Ruby else DeckColors.DialogBorder),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (checked) {
                            DeckIcon(DeckIcons.Check, tint = DeckColors.Background, size = 12.dp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ════════════════════════════════════════════════════════════════
// 游戏库设置
// ════════════════════════════════════════════════════════════════

@Composable
private fun LibrarySection(
    dirs: List<RomDirEntry>,
    autoRoot: String?,
    showAndroidGames: Boolean,
    hiddenCount: Int,
    onRowClick: (Int) -> Unit,
    highlightIndex: Int = -1,
) {
    SectionTitle(stringResource(R.string.settings_library))
    Text(stringResource(R.string.settings_dirs_desc), style = DeckType.settingsLabel, color = DeckColors.TextSecondary)
    Spacer(Modifier.height(12.dp))

    val internalLabel = stringResource(R.string.picker_internal)
    fun pretty(path: String): String {
        val sd = android.os.Environment.getExternalStorageDirectory().path
        return if (path.startsWith(sd)) internalLabel + path.removePrefix(sd).ifEmpty { "/" } else path
    }
    if (dirs.isEmpty()) {
        ManageRow(
            title = pretty(autoRoot.orEmpty()),
            titleMaxLines = 2,
            subtitle = stringResource(R.string.settings_dir_auto),
            badge = null,
            dimmed = false,
            highlighted = false,
            onClick = {},
        )
    }
    val gamesFmt = stringResource(R.string.settings_dir_games)
    val disabled = stringResource(R.string.settings_dir_disabled)
    dirs.forEachIndexed { idx, d ->
        ManageRow(
            title = pretty(d.path),
            titleMaxLines = 2,
            subtitle = if (!d.enabled) disabled else gamesFmt.format(d.count ?: 0),
            badge = null,
            dimmed = !d.enabled,
            highlighted = highlightIndex == idx,
            onClick = { onRowClick(idx) },
            trailing = {
                Switch(
                    checked = d.enabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeckColors.Background,
                        checkedTrackColor = DeckColors.Ruby,
                        uncheckedThumbColor = DeckColors.TextSecondary,
                        uncheckedTrackColor = DeckColors.Surface,
                    ),
                )
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    ActionRow(
        label = stringResource(R.string.settings_dir_add),
        highlighted = highlightIndex == dirs.size,
        onClick = { onRowClick(dirs.size) },
    )
    Spacer(Modifier.height(8.dp))
    ActionRow(
        label = stringResource(R.string.settings_rescan),
        highlighted = highlightIndex == dirs.size + 1,
        onClick = { onRowClick(dirs.size + 1) },
    )
    Spacer(Modifier.height(8.dp))
    SettingsToggle(
        label = stringResource(R.string.library_android_games),
        description = stringResource(R.string.library_android_games_desc),
        checked = showAndroidGames,
        onToggle = { onRowClick(dirs.size + 2) },
        highlighted = highlightIndex == dirs.size + 2,
    )
    Spacer(Modifier.height(8.dp))
    ActionRow(
        label = stringResource(R.string.library_hidden_manage, hiddenCount),
        highlighted = highlightIndex == dirs.size + 3,
        onClick = { onRowClick(dirs.size + 3) },
    )
}

@Composable
private fun ManageRow(
    title: String,
    subtitle: String,
    badge: String?,
    dimmed: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onBadgeClick: (() -> Unit)? = null,
    badgeAccent: Boolean = false,
    titleMaxLines: Int = 1,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(highlighted)
            .clip(DeckShapes.settingsDropdown)
            .background(if (highlighted) DeckColors.Ruby.copy(alpha = 0.08f) else Color.Transparent)
            .soundClickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (highlighted) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(DeckShapes.settingsDropdown)
                    .background(DeckColors.Ruby)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = DeckType.settingsValue,
                color = when {
                    highlighted -> DeckColors.Ruby
                    dimmed -> DeckColors.TextSecondary
                    else -> DeckColors.TextPrimary
                },
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                badge,
                style = DeckType.settingsLabel,
                color = if (badgeAccent) DeckColors.Ruby else DeckColors.TextSecondary,
                modifier = Modifier
                    .clip(DeckShapes.settingsDropdown)
                    .background(if (badgeAccent) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                    .then(if (onBadgeClick != null) Modifier.clickable(onClick = onBadgeClick) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun ActionRow(label: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(highlighted)
            .clip(DeckShapes.menuButton)
            .background(if (highlighted) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DeckType.menuButton, color = DeckColors.Ruby)
    }
}

// ════════════════════════════════════════════════════════════════
// 启动器
// ════════════════════════════════════════════════════════════════

@Composable
private fun LauncherSection(
    platforms: List<Platform>,
    showAllPlatforms: Boolean,
    onToggleShowAllPlatforms: () -> Unit,
    selected: Map<String, String>,
    aspectOverrides: Map<String, Float>,
    onPickLauncher: (Platform) -> Unit,
    highlightIndex: Int = -1,
) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.settings_launcher))
    Text(
        stringResource(R.string.settings_launcher_desc),
        style = DeckType.settingsLabel,
        color = DeckColors.TextSecondary,
    )
    Spacer(Modifier.height(10.dp))
    ActionRow(
        label = if (showAllPlatforms) stringResource(R.string.settings_launcher_show_lib) else stringResource(R.string.settings_launcher_show_all),
        highlighted = highlightIndex == 0,
        onClick = onToggleShowAllPlatforms,
    )
    Spacer(Modifier.height(10.dp))
    if (platforms.isEmpty()) {
        Text(
            stringResource(R.string.settings_launcher_empty),
            style = DeckType.settingsLabel,
            color = DeckColors.TextSecondary,
        )
        return
    }
    val autoLabel = stringResource(R.string.settings_launcher_auto)
    platforms.forEachIndexed { idx, plat ->
        val cur = selected[plat.id]
        val curName = if (cur == null) {
            val detected = GameLauncher.describeLauncher(context, plat.id, null)
            if (detected != null) "$autoLabel · $detected" else autoLabel
        } else {
            GameLauncher.describeLauncher(context, plat.id, cur) ?: cur
        }
        val aspect = aspectOverrides[plat.id] ?: plat.coverAspect
        val ratioTag = String.format(Locale.US, "%.2f:1", aspect)
        LauncherPlatformRow(
            shortName = plat.shortName,
            fullName = plat.fullName,
            launcherLabel = curName,
            ratioTag = ratioTag,
            isCustom = cur != null,
            highlighted = highlightIndex == idx + 1,
            onClick = { onPickLauncher(plat) },
        )
    }
}

/**
 * 双层启动器配置行：
 * - 上行：平台简称 + 完整名称（永不被挤压截断）+ 右侧紧凑封面比例与箭头
 * - 下行：独立一行展示当前生效的模拟器与核心，彻底杜绝长模拟器名挤掉 SNES 等平台简称的 bug。
 */
@Composable
private fun LauncherPlatformRow(
    shortName: String,
    fullName: String,
    launcherLabel: String,
    ratioTag: String,
    isCustom: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(highlighted)
            .padding(vertical = 3.dp)
            .clip(DeckShapes.settingsDropdown)
            .background(if (highlighted) DeckColors.Ruby.copy(alpha = 0.09f) else DeckColors.Surface.copy(alpha = 0.65f))
            .soundClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (highlighted) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(DeckShapes.settingsDropdown)
                    .background(DeckColors.Ruby),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortName,
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                    color = if (highlighted) DeckColors.Ruby else DeckColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                if (fullName.isNotBlank() && !fullName.equals(shortName, ignoreCase = true)) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = fullName,
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ratioTag,
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(4.dp))
                DeckIcon(
                    icon = DeckIcons.ChevronRight,
                    tint = if (highlighted) DeckColors.Ruby else DeckColors.TextSecondary,
                    size = 15.dp,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = launcherLabel,
                style = DeckType.settingsLabel,
                color = if (isCustom) DeckColors.Ruby else DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(DeckShapes.settingsDropdown)
                    .background(if (isCustom) DeckColors.Ruby.copy(alpha = 0.11f) else DeckColors.Background.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 关于
// ════════════════════════════════════════════════════════════════

@Composable
private fun AboutSection() {
    val context = LocalContext.current
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

    SectionTitle(stringResource(R.string.settings_about))

    // 品牌头卡：新掌机标志 + 应用名 + 版本胶囊 + 一句话简介
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeckColors.Surface)
            .border(0.6.dp, DeckColors.DialogBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PocketDeckBrandBadge(size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PocketDeck",
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "v$versionName",
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                    color = DeckColors.Ruby,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_about_desc),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // 结构化信息卡片：左右严格单行对齐，绝不折行错位
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeckColors.Surface)
            .border(0.6.dp, DeckColors.DialogBorder, RoundedCornerShape(12.dp)),
    ) {
        AboutInfoRow(
            label = stringResource(R.string.settings_version),
            value = "v$versionName",
        )
        HorizontalDivider(
            color = DeckColors.DialogBorder.copy(alpha = 0.65f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AboutInfoRow(
            label = stringResource(R.string.settings_build),
            value = "#${BuildConfig.BUILD_NUMBER} · ${BuildConfig.BUILD_TIME}",
        )
        HorizontalDivider(
            color = DeckColors.DialogBorder.copy(alpha = 0.65f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AboutInfoRow(
            label = stringResource(R.string.settings_android_compat),
            value = "Android 8.0+ (API ${android.os.Build.VERSION.SDK_INT})",
        )
        HorizontalDivider(
            color = DeckColors.DialogBorder.copy(alpha = 0.65f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AboutInfoRow(
            label = stringResource(R.string.settings_project),
            value = repoDisplay,
            valueColor = DeckColors.Ruby,
            trailingChevron = true,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PROJECT_URL)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    valueColor: Color = DeckColors.TextSecondary,
    trailingChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.soundClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = DeckType.settingsValue,
            color = DeckColors.TextPrimary,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = DeckType.settingsLabel,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (trailingChevron) {
            Spacer(Modifier.width(6.dp))
            DeckIcon(DeckIcons.ChevronRight, size = 14.dp, tint = DeckColors.Ruby)
        }
    }
}

/** 与 PocketDeck 应用主图标一致的亮色珊瑚绯红掌机品牌徽章。 */
@Composable
private fun PocketDeckBrandBadge(
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    val corner = size * 0.24f
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    ) {
        val w = this.size.width
        val h = this.size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFF6B72), Color(0xFFE5394B), Color(0xFFC4243A)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(w * 0.26f, h * 0.20f),
                radius = w * 0.58f,
            ),
            radius = w * 0.58f,
            center = Offset(w * 0.26f, h * 0.20f),
        )
        // 纯白掌机机身
        val bodyL = w * 0.14f
        val bodyT = h * 0.27f
        val bodyW = w * 0.72f
        val bodyH = h * 0.46f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(bodyL, bodyT),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
        )
        // 中央凹陷屏幕
        drawRoundRect(
            color = Color(0xFFE5394B),
            topLeft = Offset(w * 0.36f, h * 0.36f),
            size = Size(w * 0.28f, h * 0.23f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        )
        // 左侧十字方向键
        val cx = w * 0.255f
        val cy = h * 0.495f
        val arm = w * 0.055f
        val sw = (w * 0.036f).coerceAtLeast(1.4f)
        drawLine(Color(0xFFE5394B), Offset(cx - arm, cy), Offset(cx + arm, cy), sw, cap = StrokeCap.Round)
        drawLine(Color(0xFFE5394B), Offset(cx, cy - arm), Offset(cx, cy + arm), sw, cap = StrokeCap.Round)
        // 右侧斜排 A/B 键
        val r = (w * 0.028f).coerceAtLeast(1.3f)
        drawCircle(Color(0xFFE5394B), r, Offset(w * 0.775f, h * 0.465f))
        drawCircle(Color(0xFFE5394B), r, Offset(w * 0.715f, h * 0.535f))
    }
}

private fun computeScrapeCacheBytes(cacheDir: File): Long {
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

private fun formatCacheBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

// ════════════════════════════════════════════════════════════════
// 通用组件
// ════════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = DeckType.settingsSection,
        color = DeckColors.Ruby,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
    highlighted: Boolean = false,
) {
        val toggleContext = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(highlighted)
            .clip(DeckShapes.settingsDropdown)
            .background(if (highlighted) DeckColors.Ruby.copy(alpha = 0.08f) else Color.Transparent)
            .soundClickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 焦点指示条
        if (highlighted) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(DeckShapes.settingsDropdown)
                    .background(DeckColors.Ruby)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = DeckType.settingsValue,
                color = if (highlighted) DeckColors.Ruby else DeckColors.TextPrimary,
            )
            if (description.isNotEmpty()) {
                Text(description, style = DeckType.settingsLabel, color = DeckColors.TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                KeySound.playTouch(toggleContext)
                onToggle()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeckColors.Background,
                checkedTrackColor = DeckColors.Ruby,
                uncheckedThumbColor = DeckColors.TextSecondary,
                uncheckedTrackColor = DeckColors.Surface,
            ),
        )
    }
}

@Composable
private fun SettingsChoice(
    label: String,
    current: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(highlighted)
            .clip(DeckShapes.settingsDropdown)
            .background(if (highlighted) DeckColors.Ruby.copy(alpha = 0.08f) else Color.Transparent)
            .soundClickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (highlighted) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(DeckShapes.settingsDropdown)
                        .background(DeckColors.Ruby)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = DeckType.settingsValue,
                color = if (highlighted) DeckColors.Ruby else DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            current,
            style = DeckType.settingsNav,
            color = DeckColors.Ruby,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .clip(DeckShapes.settingsDropdown)
                .background(DeckColors.Surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SettingsInfo(
    label: String,
    value: String,
    highlighted: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(highlighted)
            .clip(DeckShapes.settingsDropdown)
            .background(if (highlighted) DeckColors.Ruby.copy(alpha = 0.08f) else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = DeckType.settingsValue, color = DeckColors.TextPrimary)
        Text(
            value,
            style = DeckType.settingsLabel,
            color = DeckColors.TextSecondary,
            maxLines = 1,
        )
    }
}

/** 高亮时自动滚动到可见区域（手柄上下切换时内容不跑出屏幕）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewWhen(active: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) requester.bringIntoView() }
    return this.bringIntoViewRequester(requester)
}

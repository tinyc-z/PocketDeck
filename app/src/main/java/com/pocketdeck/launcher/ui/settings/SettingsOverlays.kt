package com.pocketdeck.launcher.ui.settings

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.data.CredentialKind
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.ScrapeLogStore
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.ButtonBadge
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.gamepadClickable
import com.pocketdeck.launcher.ui.components.requestFocusSafely
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandFocusRow
import com.pocketdeck.launcher.ui.theme.FontOption
import com.pocketdeck.launcher.ui.theme.InterFamily
import com.pocketdeck.launcher.ui.theme.SystemFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import java.io.File

// ════════════════════════════════════════════════════════════════
// 通用操作菜单（居中弹出，手柄上下选择 + A 确认 + B 关闭）
// ════════════════════════════════════════════════════════════════

class ActionItem(val label: String, val selected: Boolean = false, val action: () -> Unit)

class ActionMenuState(val title: String, val items: List<ActionItem>) {
    var index by mutableIntStateOf(0)
    fun move(delta: Int) {
        if (items.isNotEmpty()) index = (index + delta + items.size) % items.size
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionMenuDialog(
    state: ActionMenuState,
    onPick: (ActionItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(340.dp)
                .heightIn(max = maxPanelHeight)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                state.title,
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.items.forEachIndexed { idx, item ->
                    val hl = idx == state.index
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(hl) {
                        if (hl) bringIntoView.bringIntoView()
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoView)
                            .padding(vertical = 3.dp)
                            .then(if (hl) Modifier.brandFocusRow(true, DeckShapes.menuButton) else Modifier)
                            .clip(DeckShapes.menuButton)
                            .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                            .clickable { onPick(item) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.label,
                            style = DeckType.menuButton,
                            color = if (hl || item.selected) DeckColors.Ruby else DeckColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.selected) {
                            Spacer(Modifier.width(8.dp))
                            DeckIcon(DeckIcons.Check, tint = DeckColors.Ruby, size = 16.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.menu_cancel),
                style = DeckType.menuButton,
                color = DeckColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 4.dp, horizontal = 16.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 目录浏览器（不用 SAF，直接走文件系统，手柄可操作）
// ════════════════════════════════════════════════════════════════

class DirBrowserState(private val appContext: Context? = null) {
    enum class Kind { Use, Up, Dir }
    data class Row(val kind: Kind, val file: File?, val label: String)

    var current by mutableStateOf<File?>(null)
        private set
    var rows by mutableStateOf<List<Row>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
    var showAutoCreate by mutableStateOf(false)

    private fun storageRoots(): List<File> {
        val seen = LinkedHashSet<String>()
        val roots = ArrayList<File>()
        fun add(dir: File?) {
            if (dir == null || !dir.isDirectory || !dir.canRead()) return
            val canon = runCatching { dir.canonicalPath }.getOrDefault(dir.path)
            if (seen.add(canon)) roots += dir
        }
        add(Environment.getExternalStorageDirectory())
        val ctx = appContext
        if (ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                sm?.storageVolumes?.forEach { vol ->
                    if (!vol.isPrimary && vol.state == Environment.MEDIA_MOUNTED) {
                        add(vol.directory)
                    }
                }
            }
        }
        runCatching {
            File("/storage").listFiles { f ->
                f.isDirectory && f.name != "emulated" && f.name != "self" && f.name != "enc_emulated" && f.canRead()
            }.orEmpty().sortedBy { it.name }.forEach(::add)
        }
        return roots
    }

    private val rootPaths get() = storageRoots().map { it.path }.toSet()

    fun reset() {
        current = null
        index = 0
        showAutoCreate = false
        load()
    }

    private fun load() {
        val cur = current
        rows = if (cur == null) {
            storageRoots().map { Row(Kind.Dir, it, it.path) }
        } else {
            cur.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                .orEmpty()
                .sortedBy { it.name.lowercase() }
                .map { Row(Kind.Dir, it, it.name) }
        }
    }

    fun move(delta: Int) {
        val minIdx = if (current != null) -1 else 0
        val maxIdx = (rows.size - 1).coerceAtLeast(minIdx)
        index = (index + delta).coerceIn(minIdx, maxIdx)
    }

    private fun enter(dir: File) {
        current = dir
        load()
        // 有子文件夹时默认停在第一个子文件夹上；空目录时直接聚焦顶部的「使用此目录」按钮
        index = if (rows.isNotEmpty()) 0 else -1
    }

    /** 返回上一级；已在存储列表时返回 false。 */
    fun up(): Boolean {
        val cur = current ?: return false
        current = if (cur.path in rootPaths) null else cur.parentFile
        load()
        index = rows.indexOfFirst { it.file?.path == cur.path }.coerceAtLeast(0)
        return true
    }

    /** A 键：进入目录 / 选中当前目录（返回路径）。 */
    fun confirm(): String? {
        if (index == -1) return current?.path
        val row = rows.getOrNull(index) ?: return null
        when (row.kind) {
            Kind.Use -> return row.file?.path
            Kind.Up -> up()
            Kind.Dir -> row.file?.let { enter(it) }
        }
        return null
    }
}

@Composable
fun DirBrowser(
    state: DirBrowserState,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.index, state.current) {
        if (state.index >= 0) {
            listState.animateScrollToItem((state.index - 3).coerceAtLeast(0))
        }
    }
    val internalPath = remember { Environment.getExternalStorageDirectory().path }
    val useLabel = stringResource(R.string.picker_use)
    val upLabel = stringResource(R.string.picker_up)
    val internalLabel = stringResource(R.string.picker_internal)

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.Background)
            .blockOverlayTouchThrough(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Surface)
                        .soundClickable { if (!state.up()) onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DeckIcon(DeckIcons.ChevronLeft, tint = DeckColors.TextPrimary, size = 18.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.picker_title),
                        style = DeckType.settingsSection.copy(fontWeight = FontWeight.Bold),
                        color = DeckColors.Ruby,
                    )
                    Text(
                        state.current?.path ?: stringResource(R.string.picker_storage),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .brandBackground()
                        .soundClickable { state.showAutoCreate = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DeckIcon(DeckIcons.Folder, tint = Color.White, size = 14.dp)
                    Text(
                        stringResource(R.string.picker_auto_create_btn),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.picker_hint),
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                )
            }
            HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
            if (state.current != null) {
                val useFocused = state.index == -1
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .background(DeckColors.Ruby.copy(alpha = 0.12f))
                            .soundClickable { state.up() }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        DeckIcon(PanelIcons.CornerLeftUp, tint = DeckColors.Ruby, size = 14.dp)
                        Text(
                            upLabel,
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = DeckColors.Ruby,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .then(
                                if (useFocused) Modifier.brandBackground(DeckShapes.tabPill)
                                else Modifier.background(DeckColors.Ruby.copy(alpha = 0.14f)),
                            )
                            .brandBorder(
                                active = useFocused,
                                shape = DeckShapes.tabPill,
                                activeWidth = 1.5.dp,
                                inactiveWidth = 0.8.dp,
                                inactiveColor = DeckColors.Ruby.copy(alpha = 0.35f),
                            )
                            .soundClickable {
                                state.current?.path?.let(onPick)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DeckIcon(DeckIcons.Check, tint = if (useFocused) Color.White else DeckColors.Ruby, size = 14.dp)
                        Text(
                            useLabel,
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold),
                            color = if (useFocused) Color.White else DeckColors.Ruby,
                            maxLines = 1,
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                itemsIndexed(state.rows) { idx, row ->
                    val hl = idx == state.index
                    val label = if (state.current == null && row.file?.path == internalPath) internalLabel else row.label
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(DeckShapes.settingsDropdown)
                            .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else Color.Transparent)
                            .soundClickable {
                                state.index = idx
                                state.confirm()?.let(onPick)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckIcon(
                            icon = if (state.current == null) DeckIcons.HardDrive else DeckIcons.Folder,
                            tint = DeckColors.Ruby,
                            size = 16.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            style = DeckType.settingsValue,
                            color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (state.current != null && state.rows.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.picker_empty),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
        if (state.showAutoCreate) {
            val baseDir = state.current ?: Environment.getExternalStorageDirectory()
            AutoCreateRomDirsOverlay(
                baseDir = baseDir,
                onCreated = { targetPath ->
                    state.showAutoCreate = false
                    onPick(targetPath)
                },
                onDismiss = { state.showAutoCreate = false },
            )
        }
    }
}

private val COMMON_AUTO_CREATE_PLATFORM_IDS = listOf(
    "nes", "snes", "gb", "gbc", "gba", "nds", "3ds",
    "n64", "psx", "psp", "ps2", "megadrive", "arcade", "dreamcast",
)

private val EXCLUDED_AUTO_CREATE_IDS = setOf(
    PlatformCatalog.ANDROID_ID,
    "sfc",      // 与 snes 同义，避免重复建目录
    "genesis",  // 与 megadrive 同义，避免重复建目录
)

@Composable
private fun AutoCreateRomDirsOverlay(
    baseDir: File,
    onCreated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val commonSet = remember { COMMON_AUTO_CREATE_PLATFORM_IDS.toSet() }
    val allCandidates = remember {
        PlatformCatalog.known.filter { it.id !in EXCLUDED_AUTO_CREATE_IDS }
    }
    val commonPlatforms = remember(allCandidates) {
        val byId = allCandidates.associateBy { it.id }
        COMMON_AUTO_CREATE_PLATFORM_IDS.mapNotNull { byId[it] }
    }
    val morePlatforms = remember(allCandidates) {
        allCandidates.filter { it.id !in commonSet }
    }

    var parentName by remember(baseDir) { mutableStateOf("roms") }
    var selected by remember { mutableStateOf(commonSet) }
    var creating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val trimmedName = parentName.trim()
    val isNameValid = trimmedName.isEmpty() ||
        (!trimmedName.contains('/') && !trimmedName.contains('\\') && trimmedName != "." && trimmedName != ".." && !trimmedName.contains(".."))
    val targetRoot = remember(baseDir, trimmedName, isNameValid) {
        if (!isNameValid || trimmedName.isEmpty()) baseDir else File(baseDir, trimmedName)
    }
    val existingInfo by produceState(initialValue = false to emptySet<String>(), targetRoot) {
        value = withContext(Dispatchers.IO) {
            if (!targetRoot.isDirectory) false to emptySet()
            else true to targetRoot.listFiles { f -> f.isDirectory }.orEmpty().map { it.name.lowercase() }.toSet()
        }
    }
    val parentAlreadyExists = existingInfo.first
    val existingIds = existingInfo.second

    val invalidNameStr = stringResource(R.string.picker_auto_create_invalid_name)
    val failedStr = stringResource(R.string.picker_auto_create_failed)

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss { if (!creating) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 720.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .blockOverlayTouchThrough(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            closeInset = 10.dp,
            onDismiss = { if (!creating) onDismiss() },
        ) {
            // 标题（右侧避让 DialogPanelCard 自带关闭按钮）
            Text(
                stringResource(R.string.picker_auto_create_title),
                style = DeckType.settingsSection.copy(fontWeight = FontWeight.Bold),
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )

            Spacer(Modifier.height(6.dp))

            // 父文件夹名输入 + 目标路径预览（同一行紧凑排布）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(32.dp)
                        .clip(DeckShapes.settingsDropdown)
                        .background(DeckColors.Surface)
                        .border(
                            1.dp,
                            if (isNameValid) DeckColors.DialogBorder else Color(0xFFE53935),
                            DeckShapes.settingsDropdown,
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (parentName.isEmpty()) {
                        Text(
                            stringResource(R.string.picker_auto_create_parent_ph),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = parentName,
                        onValueChange = { parentName = it; errorMsg = null },
                        singleLine = true,
                        textStyle = DeckType.settingsLabel.copy(
                            color = DeckColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        cursorBrush = SolidColor(DeckColors.Ruby),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.picker_auto_create_target, targetRoot.path),
                    style = DeckType.settingsLabel,
                    color = if (isNameValid) DeckColors.Ruby else Color(0xFFE53935),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (parentAlreadyExists && isNameValid) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.picker_auto_create_dir_exists_warn),
                    style = DeckType.settingsLabel,
                    color = Color(0xFFD97706),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))

            // 快捷选择栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                @Composable
                fun QuickChip(label: String, onClick: () -> Unit) {
                    Box(
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .background(DeckColors.Surface)
                            .border(1.dp, DeckColors.DialogBorder, DeckShapes.tabPill)
                            .soundClickable { onClick() }
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(label, style = DeckType.settingsLabel, color = DeckColors.TextPrimary)
                    }
                }
                QuickChip(stringResource(R.string.picker_auto_create_sel_common)) {
                    selected = commonSet
                }
                QuickChip(stringResource(R.string.picker_auto_create_sel_all)) {
                    selected = allCandidates.map { it.id }.toSet()
                }
                QuickChip(stringResource(R.string.picker_auto_create_sel_none)) {
                    selected = emptySet()
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${selected.size} / ${allCandidates.size}",
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold),
                    color = DeckColors.Ruby,
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)

            // 4 列紧凑平台勾选网格（常用 + 更多）
            val commonRows = remember(commonPlatforms) { commonPlatforms.chunked(4) }
            val moreRows = remember(morePlatforms) { morePlatforms.chunked(4) }
            val existsBadge = stringResource(R.string.picker_auto_create_exists)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.picker_auto_create_common),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold),
                        color = DeckColors.Ruby,
                    )
                }
                items(commonRows) { chunk ->
                    PlatformCheckRow(
                        platforms = chunk,
                        selected = selected,
                        existingIds = existingIds,
                        existsBadge = existsBadge,
                        onToggle = { id ->
                            selected = if (id in selected) selected - id else selected + id
                        },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.picker_auto_create_more),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold),
                        color = DeckColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(moreRows) { chunk ->
                    PlatformCheckRow(
                        platforms = chunk,
                        selected = selected,
                        existingIds = existingIds,
                        existsBadge = existsBadge,
                        onToggle = { id ->
                            selected = if (id in selected) selected - id else selected + id
                        },
                    )
                }
            }

            HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))

            // 底部按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                errorMsg?.let { err ->
                    Text(
                        err,
                        style = DeckType.settingsLabel,
                        color = Color(0xFFE53935),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Surface)
                        .border(1.dp, DeckColors.DialogBorder, DeckShapes.tabPill)
                        .soundClickable(enabled = !creating) { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.menu_cancel),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = DeckColors.TextPrimary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                val canSubmit = !creating && isNameValid && selected.isNotEmpty()
                Box(
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .then(
                            if (canSubmit) Modifier.brandBackground()
                            else Modifier.background(DeckColors.Surface),
                        )
                        .soundClickable(enabled = canSubmit) {
                            if (!isNameValid) {
                                errorMsg = invalidNameStr
                                return@soundClickable
                            }
                            creating = true
                            errorMsg = null
                            val toCreate = selected.toList()
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (!targetRoot.exists() && !targetRoot.mkdirs()) {
                                            error("root_mkdir_failed")
                                        }
                                        var createdCount = 0
                                        var existedCount = 0
                                        for (id in toCreate) {
                                            val sub = File(targetRoot, id)
                                            if (sub.isDirectory) {
                                                existedCount++
                                            } else if (sub.mkdirs() || sub.isDirectory) {
                                                createdCount++
                                            }
                                        }
                                        createdCount to existedCount
                                    }
                                }
                                creating = false
                                result.onSuccess { (createdCount, existedCount) ->
                                    val msg = if (existedCount > 0) {
                                        context.getString(R.string.picker_auto_create_done, createdCount, existedCount)
                                    } else {
                                        context.getString(R.string.picker_auto_create_done_all_new, createdCount)
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onCreated(targetRoot.path)
                                }.onFailure {
                                    errorMsg = failedStr
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.picker_auto_create_submit, selected.size),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold),
                        color = if (canSubmit) Color.White else DeckColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformCheckRow(
    platforms: List<Platform>,
    selected: Set<String>,
    existingIds: Set<String>,
    existsBadge: String,
    onToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (plat in platforms) {
            val checked = plat.id in selected
            val exists = plat.id in existingIds
            val displayLabel = if (plat.shortName.equals(plat.id, ignoreCase = true)) {
                plat.id
            } else {
                "${plat.id} (${plat.shortName})"
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(DeckShapes.settingsDropdown)
                    .background(if (checked) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                    .border(
                        width = 1.dp,
                        color = if (checked) DeckColors.Ruby else DeckColors.DialogBorder,
                        shape = DeckShapes.settingsDropdown,
                    )
                    .soundClickable { onToggle(plat.id) }
                    .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(14.dp)
                        .clip(DeckShapes.settingsDropdown)
                        .then(
                            if (checked) Modifier.brandBackground()
                            else Modifier.border(1.dp, DeckColors.TextSecondary.copy(alpha = 0.5f), DeckShapes.settingsDropdown),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        DeckIcon(DeckIcons.Check, tint = Color.White, size = 10.dp)
                    }
                }
                Text(
                    displayLabel,
                    style = DeckType.settingsLabel.copy(fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium),
                    color = if (checked) DeckColors.Ruby else DeckColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (exists) {
                    Text(
                        existsBadge,
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        repeat(4 - platforms.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 名称输入框
// ════════════════════════════════════════════════════════════════

class NameDialogSpec(val title: String, val initial: String, val onConfirm: (String) -> Unit)

class NameDialogState(spec: NameDialogSpec) {
    var value by mutableStateOf(TextFieldValue(spec.initial, TextRange(0, spec.initial.length)))
}

@Composable
fun NameDialog(
    spec: NameDialogSpec,
    state: NameDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(spec) { fr.requestFocusSafely() }
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 48.dp)
                .width(420.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(spec.title, style = DeckType.menuTitle, color = DeckColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            TextField(
                value = state.value,
                onValueChange = { state.value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DeckColors.Surface,
                    unfocusedContainerColor = DeckColors.Surface,
                    focusedTextColor = DeckColors.TextPrimary,
                    unfocusedTextColor = DeckColors.TextPrimary,
                    focusedIndicatorColor = DeckColors.Ruby,
                    cursorColor = DeckColors.Ruby,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fr),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_ok),
                    style = DeckType.menuButton,
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandBackground(DeckShapes.menuButton)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 刮削服务商账号 / API 凭据对话框
// ════════════════════════════════════════════════════════════════

@Composable
fun ScraperCredentialDialog(
    kind: CredentialKind,
    current: ScraperCredentials,
    onSave: (ScraperCredentials) -> Unit,
    onDismiss: () -> Unit,
) {
    var field1 by remember(kind, current) {
        mutableStateOf(
            when (kind) {
                CredentialKind.ScreenScraper -> current.ssUser
                CredentialKind.TheGamesDB -> current.tgdbKey
                CredentialKind.IGDB -> current.igdbClientId
                CredentialKind.SteamGridDB -> current.sgdbKey
            },
        )
    }
    var field2 by remember(kind, current) {
        mutableStateOf(
            when (kind) {
                CredentialKind.ScreenScraper -> current.ssPass
                CredentialKind.TheGamesDB -> ""
                CredentialKind.IGDB -> current.igdbClientSecret
                CredentialKind.SteamGridDB -> ""
            },
        )
    }

    val title = when (kind) {
        CredentialKind.ScreenScraper -> stringResource(R.string.scraping_ss_title)
        CredentialKind.TheGamesDB -> stringResource(R.string.scraping_tgdb_title)
        CredentialKind.IGDB -> stringResource(R.string.scraping_igdb_title)
        CredentialKind.SteamGridDB -> stringResource(R.string.scraping_sgdb_title)
    }
    val label1 = when (kind) {
        CredentialKind.ScreenScraper -> stringResource(R.string.scraping_ss_user)
        CredentialKind.TheGamesDB -> stringResource(R.string.scraping_tgdb_key)
        CredentialKind.IGDB -> stringResource(R.string.scraping_igdb_id)
        CredentialKind.SteamGridDB -> stringResource(R.string.scraping_sgdb_key)
    }
    val label2 = when (kind) {
        CredentialKind.ScreenScraper -> stringResource(R.string.scraping_ss_pass)
        CredentialKind.TheGamesDB -> null
        CredentialKind.IGDB -> stringResource(R.string.scraping_igdb_secret)
        CredentialKind.SteamGridDB -> null
    }

    fun applySave(clear: Boolean) {
        val v1 = if (clear) "" else field1.trim()
        val v2 = if (clear) "" else field2.trim()
        val updated = when (kind) {
            CredentialKind.ScreenScraper -> current.copy(ssUser = v1, ssPass = v2)
            CredentialKind.TheGamesDB -> current.copy(tgdbKey = v1)
            CredentialKind.IGDB -> current.copy(igdbClientId = v1, igdbClientSecret = v2)
            CredentialKind.SteamGridDB -> current.copy(sgdbKey = v1)
        }
        onSave(updated)
    }

    val firstFr = remember { FocusRequester() }
    LaunchedEffect(kind) { firstFr.requestFocusSafely() }

    val tfColors = TextFieldDefaults.colors(
        focusedContainerColor = DeckColors.Surface,
        unfocusedContainerColor = DeckColors.Surface,
        focusedTextColor = DeckColors.TextPrimary,
        unfocusedTextColor = DeckColors.TextPrimary,
        focusedLabelColor = DeckColors.Ruby,
        unfocusedLabelColor = DeckColors.TextSecondary,
        focusedIndicatorColor = DeckColors.Ruby,
        cursorColor = DeckColors.Ruby,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(title, style = DeckType.menuTitle, color = DeckColors.TextPrimary)
            Spacer(Modifier.height(14.dp))
            TextField(
                value = field1,
                onValueChange = { field1 = it },
                label = { Text(label1, style = DeckType.settingsLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (label2 != null) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applySave(clear = false) }),
                colors = tfColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFr),
            )
            if (label2 != null) {
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = field2,
                    onValueChange = { field2 = it },
                    label = { Text(label2, style = DeckType.settingsLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applySave(clear = false) }),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.scraping_account_clear),
                    style = DeckType.settingsLabel,
                    color = DeckColors.Ruby,
                    modifier = Modifier
                        .clip(DeckShapes.settingsDropdown)
                        .gamepadClickable(shape = DeckShapes.settingsDropdown) { applySave(clear = true) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .gamepadClickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_ok),
                    style = DeckType.menuButton,
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandBackground(DeckShapes.menuButton)
                        .gamepadClickable { applySave(clear = false) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 快捷键绑定捕获 & 冲突解决对话框
// ════════════════════════════════════════════════════════════════

data class PendingKeyConflict(
    val action: KeyAction,
    val newKeyCode: Int,
    val conflicts: List<KeyAction>,
)

@Composable
fun KeyBindCaptureDialog(
    actionLabel: String,
    currentBadge: String?,
    defaultBadge: String,
    errorMessage: String?,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier.width(420.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.keybind_capture_title, actionLabel),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.keybind_capture_prompt),
                style = DeckType.settingsValue,
                color = DeckColors.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.keybind_current),
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                )
                if (currentBadge != null) {
                    ButtonBadge(currentBadge)
                } else {
                    Text(
                        stringResource(R.string.keybind_unbound),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.keybind_default_hint, defaultBadge),
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                )
            }
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    errorMessage,
                    style = DeckType.settingsLabel,
                    color = DeckColors.Ruby,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.keybind_clear),
                    style = DeckType.menuButton,
                    color = DeckColors.Ruby,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby.copy(alpha = 0.10f))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun KeyBindConflictDialog(
    targetLabel: String,
    newKeyCode: Int,
    conflictLabels: List<String>,
    canSwap: Boolean,
    selectedOption: Int,
    onSwap: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val keyName = GamepadKeys.keyLabel(newKeyCode)
    val options = buildList {
        if (canSwap) add(stringResource(R.string.keybind_conflict_swap) to onSwap)
        add(stringResource(R.string.keybind_conflict_overwrite) to onOverwrite)
        add(stringResource(R.string.menu_cancel) to onDismiss)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(420.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.keybind_conflict_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.keybind_conflict_desc,
                    keyName,
                    conflictLabels.joinToString("、"),
                    targetLabel,
                ),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            options.forEachIndexed { idx, (label, action) ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                val hl = idx == selectedOption
                Text(
                    label,
                    style = DeckType.menuButton,
                    color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeckShapes.menuButton)
                        .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                        .brandBorder(active = hl, shape = DeckShapes.menuButton, activeWidth = 1.4.dp, inactiveWidth = 0.6.dp, inactiveColor = DeckColors.DialogBorder)
                        .clickable(onClick = action)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    description: String,
    confirmLabel: String,
    cancelLabel: String,
    selectedOption: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(confirmLabel to onConfirm, cancelLabel to onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(400.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(24.dp),
        ) {
            Text(
                title,
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            options.forEachIndexed { idx, (label, action) ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                val hl = idx == selectedOption
                Text(
                    label,
                    style = DeckType.menuButton,
                    color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeckShapes.menuButton)
                        .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                        .brandBorder(active = hl, shape = DeckShapes.menuButton, activeWidth = 1.4.dp, inactiveWidth = 0.6.dp, inactiveColor = DeckColors.DialogBorder)
                        .clickable(onClick = action)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 系统特殊标签显示设置（NOW / FAVS / OTHER：自定义显示名 + 跟随系统语言开关）
// ════════════════════════════════════════════════════════════════

@Composable
fun SystemTabEditDialog(
    defaultLabel: String,
    initialCustomName: String,
    initialFollowSystem: Boolean,
    onConfirm: (customName: String, followSystemLanguage: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(defaultLabel, initialCustomName) { mutableStateOf(initialCustomName) }
    var followSystem by remember(defaultLabel, initialFollowSystem) { mutableStateOf(initialFollowSystem) }
    val fieldFr = remember { FocusRequester() }
    LaunchedEffect(defaultLabel) { fieldFr.requestFocusSafely() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 44.dp)
                .width(440.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(
                "${stringResource(R.string.tab_system_edit_title)} · $defaultLabel",
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = name,
                onValueChange = { next ->
                    name = next
                    if (next.isNotBlank()) followSystem = false
                    else followSystem = true
                },
                placeholder = {
                    Text(
                        stringResource(R.string.tab_system_name_hint),
                        style = DeckType.settingsLabel,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = name.trim()
                        onConfirm(trimmed, if (trimmed.isEmpty()) true else followSystem)
                    },
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DeckColors.Surface,
                    unfocusedContainerColor = DeckColors.Surface,
                    focusedTextColor = DeckColors.TextPrimary,
                    unfocusedTextColor = DeckColors.TextPrimary,
                    focusedIndicatorColor = DeckColors.Ruby,
                    cursorColor = DeckColors.Ruby,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFr),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(DeckShapes.settingsDropdown)
                    .background(DeckColors.Surface)
                    .gamepadClickable(shape = DeckShapes.settingsDropdown) { followSystem = !followSystem }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tab_follow_system_lang),
                        style = DeckType.settingsValue,
                        color = DeckColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.tab_follow_system_lang_desc),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                DeckSwitch(checked = followSystem)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .gamepadClickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_ok),
                    style = DeckType.menuButton,
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandBackground(DeckShapes.menuButton)
                        .gamepadClickable {
                            val trimmed = name.trim()
                            onConfirm(trimmed, if (trimmed.isEmpty()) true else followSystem)
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 系统字体选择器（读取系统已安装字体 + 本地字体文件导入，带异步预览与手柄导航）
// ════════════════════════════════════════════════════════════════

@Composable
fun FontPickerDialog(
    currentFontId: String,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onPickCustomFont: () -> Unit = {},
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultLabel = stringResource(R.string.font_default)
    val sansLabel = stringResource(R.string.font_system_sans)
    val serifLabel = stringResource(R.string.font_system_serif)
    val monoLabel = stringResource(R.string.font_system_mono)
    val customTag = stringResource(R.string.font_custom_tag)
    val sampleText = stringResource(R.string.font_preview_sample)

    val fonts by produceState<List<FontOption>>(
        initialValue = listOf(FontOption(SystemFonts.DEFAULT_ID, defaultLabel, isBuiltIn = true)),
        currentFontId,
        defaultLabel,
        sansLabel,
        serifLabel,
        monoLabel,
    ) {
        value = withContext(Dispatchers.IO) {
            val base = SystemFonts.availableFonts(defaultLabel, sansLabel, serifLabel, monoLabel)
            if (SystemFonts.isCustomFont(currentFontId) && SystemFonts.isFontAvailable(currentFontId)) {
                val customLabel = SystemFonts.displayName(currentFontId, defaultLabel, sansLabel, serifLabel, monoLabel)
                val customOpt = FontOption(currentFontId, customLabel, isBuiltIn = false, isCustom = true)
                listOf(base.first(), customOpt) + base.drop(1)
            } else {
                base
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // focusIdx: 0 = 选择本地字体文件按钮；1..fonts.size = 字体列表项
    var focusIdx by remember { mutableIntStateOf(0) }
    var initializedSelection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }
    LaunchedEffect(fonts) {
        if (!initializedSelection && fonts.size > 1) {
            val idx = fonts.indexOfFirst { it.id == currentFontId }
            if (idx >= 0) focusIdx = idx + 1
            initializedSelection = true
        }
    }
    LaunchedEffect(focusIdx) {
        if (focusIdx >= 1) {
            listState.animateScrollToItem((focusIdx - 3).coerceAtLeast(0))
        }
    }

    val totalItems = 1 + fonts.size

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        focusIdx = (focusIdx - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        focusIdx = (focusIdx + 1).coerceAtMost(totalItems - 1)
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (focusIdx == 0) {
                            onPickCustomFont()
                        } else {
                            fonts.getOrNull(focusIdx - 1)?.let { onSelect(it.id) }
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 580.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Text(
                stringResource(R.string.font_picker_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )
            Spacer(Modifier.height(10.dp))

            val pickFileHl = focusIdx == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(DeckShapes.menuButton)
                    .background(if (pickFileHl) DeckColors.AccentSoft else DeckColors.Ruby.copy(alpha = 0.06f))
                    .brandBorder(active = pickFileHl, shape = DeckShapes.menuButton, activeWidth = 1.4.dp, inactiveWidth = 0.5.dp, inactiveColor = DeckColors.Ruby.copy(alpha = 0.35f))
                    .clickable {
                        focusIdx = 0
                        onPickCustomFont()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckIcon(DeckIcons.Folder, tint = DeckColors.Ruby, size = 17.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.font_pick_file),
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.Ruby,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DeckColors.DialogBorder)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(fonts, key = { _, opt -> opt.id }) { idx, opt ->
                    val selected = opt.id == currentFontId
                    val hl = focusIdx == idx + 1
                    val cachedPreview = SystemFonts.cachedOrNull(opt.id)
                    val previewFamily by produceState(
                        initialValue = cachedPreview ?: InterFamily,
                        opt.id,
                    ) {
                        value = SystemFonts.cachedOrNull(opt.id)
                            ?: withContext(Dispatchers.IO) { SystemFonts.resolve(opt.id) }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.menuButton)
                            .background(
                                when {
                                    hl -> DeckColors.AccentSoft
                                    selected -> DeckColors.Ruby.copy(alpha = 0.08f)
                                    else -> DeckColors.Surface
                                },
                            )
                            .brandBorder(
                                active = hl || selected,
                                shape = DeckShapes.menuButton,
                                activeWidth = if (hl) 1.4.dp else 1.0.dp,
                                inactiveWidth = 0.5.dp,
                                inactiveColor = DeckColors.DialogBorder,
                            )
                            .clickable {
                                focusIdx = idx + 1
                                onSelect(opt.id)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    opt.label,
                                    style = DeckType.settingsValue.copy(
                                        fontFamily = previewFamily,
                                        fontWeight = if (selected || hl) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (selected || hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (opt.isCustom) {
                                    Text(
                                        customTag,
                                        style = DeckType.settingsLabel,
                                        color = DeckColors.Ruby,
                                        modifier = Modifier
                                            .clip(DeckShapes.settingsDropdown)
                                            .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                sampleText,
                                style = DeckType.settingsLabel.copy(fontFamily = previewFamily),
                                color = DeckColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            Spacer(Modifier.width(10.dp))
                            DeckIcon(DeckIcons.Check, tint = DeckColors.Ruby, size = 16.dp)
                        }
                    }
                }
            }
        }
    }
}

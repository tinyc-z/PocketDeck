package com.pocketdeck.launcher.ui.edit

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.ScrapedCoverCandidate
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.apps.drawBackArrow
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.GameCategoryPickerDialog
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.settings.LauncherPickerDialog
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckFontFamily
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Edit Game 页面：改名、查看 ROM 文件名、所属分类、自定义封面与 Delete / Save。
 */
@Composable
fun EditGameScreen(
    game: Game,
    platform: Platform?,
    covers: CoverSource?,
    officialName: String?,
    customLauncherId: String? = null,
    onSetGameLauncher: ((String?) -> Unit)? = null,
    categories: List<Category> = emptyList(),
    onSetGameCategories: ((Set<String>) -> Unit)? = null,
    onCreateCategory: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (name: String?, coverPath: String?) -> Unit,
    onHide: () -> Unit,
    onDeleteFile: (() -> Unit)? = null,
    onLocateFile: (() -> Unit)? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember(game.id) { mutableStateOf(game.title) }
    var coverPath by remember(game.id) { mutableStateOf(game.customCover) }
    var scrapingDialog by remember { mutableStateOf(false) }
    var launcherDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isRomFile = !game.id.startsWith(GameLauncher.APK_PREFIX) && onDeleteFile != null

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "covers").apply { mkdirs() }
                    val target = File(dir, "${game.id.hashCode().toUInt()}_${System.currentTimeMillis()}.png")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (target.isFile && target.length() > 0) target.absolutePath else null
                }.getOrNull()
            }
            if (savedPath != null) {
                coverPath = savedPath
            } else {
                Toast.makeText(context, context.getString(R.string.edit_image_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val changed = title.trim() != game.title || coverPath != game.customCover
    val canSave = changed && title.isNotBlank()
    val overlayOpen = scrapingDialog || launcherDialog || categoryDialog || showDeleteConfirm
    BackHandler(enabled = overlayOpen) {
        when {
            showDeleteConfirm -> showDeleteConfirm = false
            categoryDialog -> categoryDialog = false
            launcherDialog -> launcherDialog = false
            scrapingDialog -> scrapingDialog = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (overlayOpen) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (GamepadKeys.isBack(event.key) ||
                    (event.key != Key.Back && GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings))
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(DeckColors.Background),
        ) {
        val backTint = DeckColors.TextPrimary
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .drawBehind { drawBackArrow(backTint) },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.edit_game),
                fontFamily = DeckFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = DeckColors.TextPrimary,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.width(340.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_name), style = DeckType.settingsLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DeckColors.Surface,
                        unfocusedContainerColor = DeckColors.Surface,
                        focusedTextColor = DeckColors.TextPrimary,
                        unfocusedTextColor = DeckColors.TextPrimary,
                        focusedLabelColor = DeckColors.Ruby,
                        unfocusedLabelColor = DeckColors.TextSecondary,
                        focusedIndicatorColor = DeckColors.Ruby,
                        cursorColor = DeckColors.Ruby,
                    ),
                )

                Spacer(Modifier.height(14.dp))

                val canLocateFile = !game.id.startsWith(GameLauncher.APK_PREFIX) && onLocateFile != null
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "📄",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        text = game.fileName,
                        style = DeckType.metaValue,
                        color = DeckColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canLocateFile) {
                        Spacer(Modifier.width(8.dp))
                        var locateFocused by remember { mutableStateOf(false) }
                        Text(
                            text = stringResource(R.string.edit_locate_file),
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = if (locateFocused) Color.White else DeckColors.TextPrimary,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(if (locateFocused) DeckColors.Ruby else DeckColors.Surface)
                                .onFocusChanged { locateFocused = it.isFocused }
                                .focusable()
                                .border(
                                    width = if (locateFocused) 1.5.dp else 1.dp,
                                    color = if (locateFocused) DeckColors.Ruby else DeckColors.DialogBorder,
                                    shape = DeckShapes.tabPill,
                                )
                                .clickable { onLocateFile() }
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown &&
                                        GamepadKeys.isConfirm(ev.key, swapAbxy, keyBindings)
                                    ) {
                                        onLocateFile()
                                        true
                                    } else false
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                val previewGame = remember(game, coverPath, title) {
                    game.copy(title = title.ifBlank { game.title }, customCover = coverPath)
                }
                val aspect = platform?.coverAspect ?: 1f
                val previewH = 112.dp
                val previewW = (previewH * aspect).coerceIn(82.dp, 152.dp)

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GameCoverCard(
                        game = previewGame,
                        platform = platform,
                        covers = covers,
                        officialName = officialName,
                        width = previewW,
                        height = previewH,
                        isFocused = false,
                    )

                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { pickImageLauncher.launch("image/*") },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, DeckColors.DialogBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.edit_pick_image),
                                style = DeckType.bottomBar,
                                color = DeckColors.TextPrimary,
                            )
                        }

                        if (covers != null) {
                            OutlinedButton(
                                onClick = { scrapingDialog = true },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, DeckColors.Ruby.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.edit_scrape_boxart),
                                    style = DeckType.bottomBar,
                                    color = DeckColors.Ruby,
                                )
                            }
                        }

                        if (coverPath != null) {
                            var resetFocused by remember { mutableStateOf(false) }
                            Text(
                                text = stringResource(R.string.edit_reset_cover),
                                style = DeckType.settingsLabel,
                                color = DeckColors.Ruby,
                                modifier = Modifier
                                    .clip(DeckShapes.settingsDropdown)
                                    .onFocusChanged { resetFocused = it.isFocused }
                                    .focusable()
                                    .border(
                                        width = if (resetFocused) 1.5.dp else 0.dp,
                                        color = if (resetFocused) DeckColors.Ruby else Color.Transparent,
                                        shape = DeckShapes.settingsDropdown,
                                    )
                                    .clickable { coverPath = null }
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown &&
                                            GamepadKeys.isConfirm(ev.key, swapAbxy, keyBindings)
                                        ) {
                                            coverPath = null
                                            true
                                        } else false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                if (onSetGameLauncher != null && platform != null && !game.id.startsWith(GameLauncher.APK_PREFIX)) {
                    Spacer(Modifier.height(16.dp))
                    val followPlatformLabel = stringResource(R.string.edit_launcher_follow_platform)
                    val launcherValue = if (customLauncherId == null) followPlatformLabel else {
                        GameLauncher.describeLauncher(context, platform.id, customLauncherId) ?: customLauncherId
                    }
                    var launcherFocused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.settingsDropdown)
                            .background(if (launcherFocused) DeckColors.Ruby.copy(alpha = 0.08f) else DeckColors.Surface)
                            .onFocusChanged { launcherFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = if (launcherFocused) 1.5.dp else 1.dp,
                                color = if (launcherFocused) DeckColors.Ruby else DeckColors.DialogBorder,
                                shape = DeckShapes.settingsDropdown,
                            )
                            .clickable { launcherDialog = true }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown &&
                                    GamepadKeys.isConfirm(ev.key, swapAbxy, keyBindings)
                                ) {
                                    launcherDialog = true
                                    true
                                } else false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.prop_launcher),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            launcherValue,
                            style = DeckType.settingsValue,
                            color = if (customLauncherId != null) DeckColors.Ruby else DeckColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (onSetGameCategories != null) {
                    Spacer(Modifier.height(12.dp))
                    val matchedCats = categories.filter { game.platformId in it.sources || game.id in it.games }
                    val catSummary = if (matchedCats.isEmpty()) {
                        stringResource(R.string.edit_categories_none)
                    } else {
                        matchedCats.joinToString(" · ") { it.name }
                    }
                    var catFocused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.settingsDropdown)
                            .background(if (catFocused) DeckColors.Ruby.copy(alpha = 0.08f) else DeckColors.Surface)
                            .onFocusChanged { catFocused = it.isFocused }
                            .focusable()
                            .border(
                                width = if (catFocused) 1.5.dp else 1.dp,
                                color = if (catFocused) DeckColors.Ruby else DeckColors.DialogBorder,
                                shape = DeckShapes.settingsDropdown,
                            )
                            .clickable { categoryDialog = true }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown &&
                                    GamepadKeys.isConfirm(ev.key, swapAbxy, keyBindings)
                                ) {
                                    categoryDialog = true
                                    true
                                } else false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.edit_categories),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            catSummary,
                            style = DeckType.settingsValue,
                            color = if (matchedCats.isNotEmpty()) DeckColors.Ruby else DeckColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(26.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onHide,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, DeckColors.DialogBorder),
                        ) {
                            Text(
                                stringResource(R.string.menu_dismiss),
                                style = DeckType.bottomBar,
                                color = DeckColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                        }

                        if (isRomFile) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, DeckColors.Ruby.copy(alpha = 0.45f)),
                            ) {
                                Text(
                                    stringResource(R.string.edit_delete_file),
                                    style = DeckType.bottomBar,
                                    color = DeckColors.Ruby,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onSave(title.trim(), coverPath) },
                        enabled = canSave,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeckColors.Ruby,
                            contentColor = Color.White,
                            disabledContainerColor = DeckColors.PillSelected,
                            disabledContentColor = DeckColors.TextSecondary,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.edit_save),
                            style = DeckType.bottomBar,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
        }

        if (scrapingDialog && covers != null) {
            ScrapeCoverDialog(
                game = game.copy(title = title.ifBlank { game.title }),
                platform = platform,
                covers = covers,
                officialName = officialName,
                onPickUrl = { url ->
                    if (!NetworkStatus.isOnline(context)) {
                        Toast.makeText(context, context.getString(R.string.offline_action_toast), Toast.LENGTH_SHORT).show()
                        return@ScrapeCoverDialog
                    }
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                val dir = File(context.filesDir, "covers").apply { mkdirs() }
                                val target = File(dir, "${game.id.hashCode().toUInt()}_${System.currentTimeMillis()}.png")
                                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 10_000
                                    readTimeout = 15_000
                                    requestMethod = "GET"
                                }
                                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                                    conn.inputStream.use { input ->
                                        target.outputStream().use { out -> input.copyTo(out) }
                                    }
                                    conn.disconnect()
                                    if (target.isFile && target.length() > 0) target.absolutePath else null
                                } else {
                                    conn.disconnect()
                                    null
                                }
                            }.getOrNull()
                        }
                        if (saved != null) {
                            coverPath = saved
                            scrapingDialog = false
                        } else {
                            Toast.makeText(context, context.getString(R.string.edit_image_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onDismiss = { scrapingDialog = false },
            )
        }

        if (launcherDialog && platform != null && onSetGameLauncher != null) {
            LauncherPickerDialog(
                title = game.title,
                platform = platform,
                currentLauncherId = customLauncherId,
                defaultLabel = stringResource(R.string.edit_launcher_follow_platform),
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onSelect = { id ->
                    onSetGameLauncher(id)
                    launcherDialog = false
                },
                onDismiss = { launcherDialog = false },
            )
        }

        if (categoryDialog && onSetGameCategories != null) {
            GameCategoryPickerDialog(
                game = game,
                categories = categories,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onCreateCategory = onCreateCategory,
                onConfirm = { pickedIds ->
                    onSetGameCategories(pickedIds)
                    categoryDialog = false
                },
                onDismiss = { categoryDialog = false },
            )
        }

        if (showDeleteConfirm && onDeleteFile != null) {
            DeleteFileConfirmDialog(
                fileName = game.fileName,
                filePath = game.id,
                onUseHide = {
                    showDeleteConfirm = false
                    onHide()
                },
                onConfirmDelete = {
                    showDeleteConfirm = false
                    onDeleteFile()
                },
                onDismiss = { showDeleteConfirm = false },
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
            )
        }
    }
}

@Composable
fun DeleteFileConfirmDialog(
    fileName: String,
    filePath: String,
    onUseHide: (() -> Unit)?,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
) {
    val focusRequester = remember { FocusRequester() }
    val options = buildList {
        if (onUseHide != null) add(Triple(stringResource(R.string.delete_file_use_hide), false, onUseHide))
        add(Triple(stringResource(R.string.delete_file_confirm_yes), true, onConfirmDelete))
        add(Triple(stringResource(R.string.menu_cancel), false, onDismiss))
    }
    val cancelIdx = options.lastIndex
    var selectedIdx by remember { mutableIntStateOf(cancelIdx) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
                    event.key == Key.DirectionUp -> {
                        selectedIdx = (selectedIdx - 1 + options.size) % options.size
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        selectedIdx = (selectedIdx + 1) % options.size
                        true
                    }
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        options.getOrNull(selectedIdx)?.third?.invoke()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.delete_file_confirm_title),
                style = DeckType.menuTitle,
                color = DeckColors.Ruby,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.delete_file_confirm_desc, fileName, filePath),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            options.forEachIndexed { idx, (label, destructive, action) ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                val hl = idx == selectedIdx
                val bg = when {
                    destructive && hl -> DeckColors.Ruby.copy(alpha = 0.16f)
                    hl -> DeckColors.Ruby.copy(alpha = 0.10f)
                    else -> DeckColors.Surface
                }
                val fg = when {
                    destructive -> DeckColors.Ruby
                    hl -> DeckColors.Ruby
                    else -> DeckColors.TextPrimary
                }
                Text(
                    label,
                    style = DeckType.menuButton,
                    color = fg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeckShapes.menuButton)
                        .background(bg)
                        .border(
                            width = if (hl) 1.5.dp else 1.dp,
                            color = if (hl) DeckColors.Ruby else DeckColors.DialogBorder,
                            shape = DeckShapes.menuButton,
                        )
                        .clickable(onClick = action)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun ScrapeCoverDialog(
    game: Game,
    platform: Platform?,
    covers: CoverSource,
    officialName: String?,
    onPickUrl: (String) -> Unit,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val rowState = rememberLazyListState()
    var loading by remember { mutableStateOf(true) }
    var isOffline by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<ScrapedCoverCandidate>>(emptyList()) }
    var failedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var focusIdx by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(game.id, game.title) {
        if (!NetworkStatus.isOnline(context)) {
            isOffline = true
            loading = false
            candidates = emptyList()
            return@LaunchedEffect
        }
        isOffline = false
        loading = true
        failedUrls = emptySet()
        candidates = covers.scrapeAllForPicker(game, platform, officialName)
        loading = false
    }

    val visibleCandidates = remember(candidates, failedUrls) {
        candidates.filter { it.url !in failedUrls }
    }
    LaunchedEffect(focusIdx, visibleCandidates.size) {
        if (visibleCandidates.isNotEmpty()) {
            focusIdx = focusIdx.coerceIn(0, visibleCandidates.lastIndex)
            rowState.animateScrollToItem(focusIdx)
        }
    }

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
                    event.key == Key.DirectionLeft && visibleCandidates.isNotEmpty() -> {
                        focusIdx = (focusIdx - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionRight && visibleCandidates.isNotEmpty() -> {
                        focusIdx = (focusIdx + 1).coerceAtMost(visibleCandidates.lastIndex)
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        visibleCandidates.getOrNull(focusIdx)?.let { onPickUrl(it.url) }
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
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Text(
                stringResource(R.string.scrape_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )
            Spacer(Modifier.height(14.dp))
            when {
                loading -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(148.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = DeckColors.Ruby,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.scrape_loading),
                            style = DeckType.settingsValue,
                            color = DeckColors.TextSecondary,
                        )
                    }
                }
                isOffline -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.scrape_offline),
                            style = DeckType.settingsValue,
                            color = DeckColors.TextSecondary,
                        )
                    }
                }
                candidates.isEmpty() || visibleCandidates.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.scrape_empty),
                            style = DeckType.settingsValue,
                            color = DeckColors.TextSecondary,
                        )
                    }
                }
                else -> {
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(visibleCandidates, key = { _, item -> item.url }) { idx, item ->
                            val hl = idx == focusIdx
                            val nodeName = ScraperCatalog.coverDescriptor(item.nodeId)
                                ?.let { stringResource(it.nameRes) }
                                ?: item.nodeId
                            Column(
                                Modifier
                                    .width(126.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (hl) DeckColors.Ruby.copy(alpha = 0.10f) else DeckColors.Surface)
                                    .border(
                                        width = if (hl) 2.dp else 1.dp,
                                        color = if (hl) DeckColors.Ruby else DeckColors.DialogBorder,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { onPickUrl(item.url) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(
                                    model = item.url,
                                    contentDescription = nodeName,
                                    contentScale = ContentScale.Fit,
                                    onError = { failedUrls = failedUrls + item.url },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(124.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = nodeName,
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

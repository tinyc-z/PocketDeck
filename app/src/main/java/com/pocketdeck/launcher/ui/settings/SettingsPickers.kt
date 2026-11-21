package com.pocketdeck.launcher.ui.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.pocketdeck.launcher.ui.components.HorizontalDivider
import com.pocketdeck.launcher.ui.components.Switch
import com.pocketdeck.launcher.ui.components.SwitchDefaults
import com.pocketdeck.launcher.ui.components.Text
import com.pocketdeck.launcher.ui.components.TextField
import com.pocketdeck.launcher.ui.components.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CustomLauncherPreset
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.launch.CustomLaunchMode
import com.pocketdeck.launcher.launch.CustomLauncherCodec
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.gamepadClickable
import com.pocketdeck.launcher.ui.components.requestFocusSafely
import java.util.Locale
import kotlin.math.abs
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.ui.components.DeckSwitch
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.brandPillBackground
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class PickableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/**
 * 自定义分类绑定平台多选弹窗（如将「任天堂红白机」同时关联 NES / FC / SNES / SFC 等）。
 */
@Composable
fun CategoryPlatformsDialog(
    categoryName: String,
    availablePlatforms: List<Platform>,
    libraryPlatformIds: Set<String>,
    initialSelected: List<String>,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(initialSelected) { mutableStateOf(initialSelected.toSet()) }
    val sortedPlatforms = remember(availablePlatforms, libraryPlatformIds) {
        availablePlatforms.sortedWith(
            compareByDescending<Platform> { it.id in libraryPlatformIds }
                .thenBy { PlatformCatalog.sortKey(it.id) }
                .thenBy { it.shortName },
        )
    }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // 0..sortedPlatforms.lastIndex = 平台行; sortedPlatforms.size = 取消; sortedPlatforms.size + 1 = 确定
    var focusIdx by remember { mutableIntStateOf(0) }
    val totalFocus = sortedPlatforms.size + 2

    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }
    LaunchedEffect(focusIdx) {
        if (focusIdx in sortedPlatforms.indices) {
            listState.animateScrollToItem((focusIdx - 3).coerceAtLeast(0))
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
                    event.key == Key.DirectionUp -> {
                        focusIdx = (focusIdx - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        focusIdx = (focusIdx + 1).coerceAtMost(totalFocus - 1)
                        true
                    }
                    event.key == Key.DirectionLeft && focusIdx == sortedPlatforms.size + 1 -> {
                        focusIdx = sortedPlatforms.size
                        true
                    }
                    event.key == Key.DirectionRight && focusIdx == sortedPlatforms.size -> {
                        focusIdx = sortedPlatforms.size + 1
                        true
                    }
                    GamepadKeys.isMenu(event.key, swapAbxy, keyBindings) -> {
                        onConfirm(selected.toList())
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when {
                            focusIdx in sortedPlatforms.indices -> {
                                val pid = sortedPlatforms[focusIdx].id
                                selected = if (pid in selected) selected - pid else selected + pid
                            }
                            focusIdx == sortedPlatforms.size -> onDismiss()
                            else -> onConfirm(selected.toList())
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(480.dp)
                .heightIn(max = 580.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.cat_bind_title, categoryName),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.cat_bind_desc),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DeckColors.Line)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(sortedPlatforms, key = { _, p -> p.id }) { idx, plat ->
                    val checked = plat.id in selected
                    val inLib = plat.id in libraryPlatformIds
                    val hl = focusIdx == idx
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.menuButton)
                            .background(
                                when {
                                    hl -> DeckColors.AccentSoft
                                    checked -> DeckColors.Ruby.copy(alpha = 0.08f)
                                    else -> DeckColors.Surface
                                },
                            )
                            .brandBorder(active = hl, shape = DeckShapes.menuButton, activeWidth = 1.4.dp, inactiveWidth = 0.dp, inactiveColor = Color.Transparent)
                            .clickable {
                                focusIdx = idx
                                selected = if (checked) selected - plat.id else selected + plat.id
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckIcon(
                            if (checked) PanelIcons.CheckSquare else PanelIcons.Square,
                            tint = if (checked || hl) DeckColors.Ruby else DeckColors.TextSecondary,
                            size = 17.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${plat.shortName} · ${plat.fullName}",
                                style = DeckType.settingsValue,
                                color = if (checked || hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (inLib) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.cat_in_library),
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val cancelHl = focusIdx == sortedPlatforms.size
                val okHl = focusIdx == sortedPlatforms.size + 1
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = if (cancelHl) DeckColors.Ruby else DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandBorder(active = cancelHl, shape = DeckShapes.menuButton, activeWidth = 1.3.dp, inactiveWidth = 0.dp, inactiveColor = Color.Transparent)
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
                        .border(
                            width = if (okHl) 1.dp else 0.dp,
                            color = if (okHl) DeckColors.TextPrimary else Color.Transparent,
                            shape = DeckShapes.menuButton,
                        )
                        .clickable { onConfirm(selected.toList()) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun InstalledAppPickerDialog(
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onPick: (packageName: String, label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<PickableApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0)
            val seen = HashSet<String>()
            resolved.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
                val label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
                val bmp = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                PickableApp(pkg, label, bmp)
            }.sortedBy { it.label.lowercase() }
        }
    }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { q in it.label.lowercase() || q in it.packageName.lowercase() }
    }
    val listState = rememberLazyListState()
    var focusIdx by remember(filtered.size) { mutableIntStateOf(0) }
    val listFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { listFr.requestFocusSafely() }
    LaunchedEffect(focusIdx) {
        if (focusIdx in filtered.indices) {
            listState.animateScrollToItem((focusIdx - 2).coerceAtLeast(0))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(ev.key) || (ev.key != Key.Back && GamepadKeys.isCancelOrApps(ev.key, swapAbxy, keyBindings)) -> {
                        onDismiss()
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
                stringResource(R.string.launcher_pick_app_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = query,
                onValueChange = { query = it; focusIdx = 0 },
                placeholder = { Text(stringResource(R.string.launcher_search_app_hint), style = DeckType.settingsLabel) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DeckColors.Surface,
                    unfocusedContainerColor = DeckColors.Surface,
                    focusedTextColor = DeckColors.TextPrimary,
                    unfocusedTextColor = DeckColors.TextPrimary,
                    focusedIndicatorColor = DeckColors.Ruby,
                    cursorColor = DeckColors.Ruby,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .focusRequester(listFr)
                    .focusable()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            ev.key == Key.DirectionUp && filtered.isNotEmpty() -> {
                                if (focusIdx > 0) { focusIdx--; true } else false
                            }
                            ev.key == Key.DirectionDown && filtered.isNotEmpty() -> {
                                focusIdx = (focusIdx + 1).coerceAtMost(filtered.lastIndex)
                                true
                            }
                            GamepadKeys.isConfirm(ev.key, swapAbxy, keyBindings) -> {
                                filtered.getOrNull(focusIdx)?.let { onPick(it.packageName, it.label) }
                                true
                            }
                            else -> false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(filtered, key = { _, a -> a.packageName }) { idx, app ->
                    val hl = idx == focusIdx
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.menuButton)
                            .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                            .brandBorder(active = hl, shape = DeckShapes.menuButton, activeWidth = 1.4.dp, inactiveWidth = 0.dp, inactiveColor = Color.Transparent)
                            .clickable {
                                focusIdx = idx
                                onPick(app.packageName, app.label)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = DeckType.settingsValue,
                                color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                app.packageName,
                                style = DeckType.metaLabel,
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

/**
 * 已隐藏游戏恢复弹窗。
 */
@Composable
fun HiddenGamesDialog(
    hiddenIds: Set<String>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(hiddenIds) { hiddenIds.sortedBy { it.lowercase() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 560.dp),
        ) {
            Text(
                stringResource(R.string.library_hidden_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DeckColors.Line)
            if (sorted.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.library_hidden_empty),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(sorted, key = { _, id -> id }) { _, id ->
                        val name = if (id.startsWith("apk://")) id.removePrefix("apk://") else File(id).name
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.menuButton)
                                .background(DeckColors.Surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    style = DeckType.settingsValue,
                                    color = DeckColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    id,
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.library_restore),
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.Ruby,
                                modifier = Modifier
                                    .clip(DeckShapes.settingsDropdown)
                                    .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                    .clickable { onRestore(id) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

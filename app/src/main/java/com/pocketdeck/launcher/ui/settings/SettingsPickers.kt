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
import java.util.Locale
import kotlin.math.abs
import com.pocketdeck.launcher.ui.theme.DeckColors
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

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
            HorizontalDivider(color = DeckColors.DialogBorder)

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
                                    hl -> DeckColors.Ruby.copy(alpha = 0.16f)
                                    checked -> DeckColors.Ruby.copy(alpha = 0.08f)
                                    else -> DeckColors.Surface
                                },
                            )
                            .border(
                                width = if (hl) 1.5.dp else 0.dp,
                                color = if (hl) DeckColors.Ruby else Color.Transparent,
                                shape = DeckShapes.menuButton,
                            )
                            .clickable {
                                focusIdx = idx
                                selected = if (checked) selected - plat.id else selected + plat.id
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (checked) "☑" else "☐",
                            style = DeckType.menuButton,
                            color = if (checked || hl) DeckColors.Ruby else DeckColors.TextSecondary,
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
                        .border(
                            width = if (cancelHl) 1.5.dp else 0.dp,
                            color = if (cancelHl) DeckColors.Ruby else Color.Transparent,
                            shape = DeckShapes.menuButton,
                        )
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
                        .background(DeckColors.Ruby)
                        .border(
                            width = if (okHl) 2.dp else 0.dp,
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

/**
 * 平台 / 单游戏启动器选择弹窗：支持 自动 / 内置模拟器 / RetroArch 核心（含手动输入） / 预设 / 任意已安装 Android App + 启动协议 / 平台封面比例。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherPickerDialog(
    title: String,
    platform: Platform,
    currentLauncherId: String?,
    defaultLabel: String,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    showAspectRatio: Boolean = false,
    defaultAspect: Float = platform.coverAspect,
    currentAspect: Float? = null,
    onSetAspect: ((Float?) -> Unit)? = null,
    customLaunchers: List<CustomLauncherPreset> = emptyList(),
    onSavePreset: ((name: String, launcherId: String) -> Unit)? = null,
    onDeletePreset: ((presetId: String) -> Unit)? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
) {
    val context = LocalContext.current
    var pickingApp by remember { mutableStateOf(false) }
    var pickedPkgForMode by remember { mutableStateOf<Pair<String, String>?>(null) }
    var customCoreDialog by remember { mutableStateOf(false) }
    var showNotInstalled by remember { mutableStateOf(false) }
    val firstFocusFr = remember { FocusRequester() }

    val builtInOptions = remember(platform.id) {
        GameLauncher.availableLaunchers(context, platform.id)
    }
    val notInstalledEmus = remember(platform.id) {
        GameLauncher.supportedStandaloneEmulators(context, platform.id)
            .filter { !it.second }
            .map { it.first }
    }
    val raInstalled = remember { GameLauncher.isRetroArchInstalled(context) }
    val raCores = remember(platform.id) {
        GameLauncher.installedRetroArchCores(context, platform)
    }
    val customCoreSelected = remember(currentLauncherId, raCores) {
        val core = CustomLauncherCodec.decodeRaCore(currentLauncherId)
        if (core != null && core !in raCores) core else null
    }
    val customSpec = remember(currentLauncherId) {
        CustomLauncherCodec.decodeCustomApp(currentLauncherId)
    }

    if (pickingApp) {
        InstalledAppPickerDialog(
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onPick = { pkg, label ->
                pickingApp = false
                pickedPkgForMode = pkg to label
            },
            onDismiss = { pickingApp = false },
        )
        return
    }

    pickedPkgForMode?.let { (pkg, appLabel) ->
        CustomLaunchModeDialog(
            appLabel = appLabel,
            initialMode = customSpec?.takeIf { it.packageName == pkg }?.mode ?: CustomLaunchMode.ViewContentUri,
            initialExtraKey = customSpec?.takeIf { it.packageName == pkg }?.extraKey.orEmpty(),
            initialActivityClass = customSpec?.takeIf { it.packageName == pkg }?.activityClass.orEmpty(),
            canSavePreset = onSavePreset != null,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onConfirm = { mode, extraKey, activityClass, presetName ->
                pickedPkgForMode = null
                val encoded = CustomLauncherCodec.encodeCustomApp(pkg, mode, extraKey, activityClass)
                if (!presetName.isNullOrBlank()) {
                    onSavePreset?.invoke(presetName, encoded)
                }
                onSelect(encoded)
            },
            onDismiss = { pickedPkgForMode = null },
        )
        return
    }

    if (customCoreDialog) {
        CustomRetroArchCoreDialog(
            initialCore = customCoreSelected ?: platform.core.orEmpty(),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onConfirm = { core ->
                customCoreDialog = false
                if (core.isNotBlank()) {
                    onSelect(CustomLauncherCodec.encodeRaCore(core))
                }
            },
            onDismiss = { customCoreDialog = false },
        )
        return
    }

    LaunchedEffect(pickingApp, pickedPkgForMode, customCoreDialog) {
        if (!pickingApp && pickedPkgForMode == null && !customCoreDialog) {
            runCatching { firstFocusFr.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings))
                ) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Column(Modifier.padding(end = 36.dp)) {
                Text(
                    title,
                    style = DeckType.menuTitle,
                    color = DeckColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${platform.fullName} (${platform.shortName})",
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DeckColors.DialogBorder)

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showAspectRatio && onSetAspect != null) {
                    val effectiveAspect = currentAspect ?: defaultAspect
                    val aspectPresets = listOf(
                        null to stringResource(R.string.launcher_aspect_default, defaultAspect),
                        0.72f to stringResource(R.string.launcher_aspect_portrait),
                        0.70f to stringResource(R.string.launcher_aspect_dvd),
                        1.00f to stringResource(R.string.launcher_aspect_square),
                        1.37f to stringResource(R.string.launcher_aspect_landscape),
                        1.78f to stringResource(R.string.launcher_aspect_wide),
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.menuButton)
                            .background(DeckColors.Surface)
                            .padding(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.launcher_aspect_title),
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "−",
                                    style = DeckType.menuButton,
                                    color = DeckColors.Ruby,
                                    modifier = Modifier
                                        .clip(DeckShapes.settingsDropdown)
                                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                            val next = (effectiveAspect - 0.05f).coerceIn(0.40f, 2.20f)
                                            onSetAspect(next)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                                Text(
                                    String.format(Locale.US, "%.2f:1", effectiveAspect),
                                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                    color = DeckColors.Ruby,
                                )
                                Text(
                                    "+",
                                    style = DeckType.menuButton,
                                    color = DeckColors.Ruby,
                                    modifier = Modifier
                                        .clip(DeckShapes.settingsDropdown)
                                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                            val next = (effectiveAspect + 0.05f).coerceIn(0.40f, 2.20f)
                                            onSetAspect(next)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            aspectPresets.forEach { (presetVal, label) ->
                                val isSel = if (presetVal == null) {
                                    currentAspect == null
                                } else {
                                    currentAspect != null && abs(currentAspect - presetVal) < 0.015f
                                }
                                Text(
                                    label,
                                    style = DeckType.metaLabel.copy(
                                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (isSel) DeckColors.Ruby else DeckColors.TextPrimary,
                                    modifier = Modifier
                                        .clip(DeckShapes.settingsDropdown)
                                        .background(if (isSel) DeckColors.Ruby.copy(alpha = 0.16f) else DeckColors.Background)
                                        .border(
                                            width = if (isSel) 1.dp else 0.5.dp,
                                            color = if (isSel) DeckColors.Ruby else DeckColors.DialogBorder,
                                            shape = DeckShapes.settingsDropdown,
                                        )
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                            onSetAspect(presetVal)
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                LauncherOptionRow(
                    label = defaultLabel,
                    subtitle = null,
                    selected = currentLauncherId == null,
                    focusRequester = firstFocusFr,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onClick = { onSelect(null) },
                )

                builtInOptions.forEach { (id, name) ->
                    LauncherOptionRow(
                        label = name,
                        subtitle = if (id == GameLauncher.RETROARCH_ID) stringResource(R.string.launcher_ra_auto_core) else null,
                        selected = currentLauncherId == id,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onClick = { onSelect(id) },
                    )
                }

                if (raCores.isNotEmpty() || raInstalled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.launcher_ra_cores),
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    raCores.forEach { core ->
                        val encoded = CustomLauncherCodec.encodeRaCore(core)
                        LauncherOptionRow(
                            label = "RetroArch · $core",
                            subtitle = "${core}_libretro_android.so",
                            selected = currentLauncherId == encoded,
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = { onSelect(encoded) },
                        )
                    }
                    if (customCoreSelected != null) {
                        val encoded = CustomLauncherCodec.encodeRaCore(customCoreSelected)
                        LauncherOptionRow(
                            label = "RetroArch · $customCoreSelected",
                            subtitle = "${customCoreSelected}_libretro_android.so",
                            selected = true,
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = { customCoreDialog = true },
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.menuButton)
                            .background(DeckColors.Surface)
                            .border(0.5.dp, DeckColors.DialogBorder, DeckShapes.menuButton)
                            .gamepadClickable(swapAbxy, keyBindings) { customCoreDialog = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(
                            stringResource(R.string.launcher_ra_custom_core),
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                            color = DeckColors.Ruby,
                        )
                    }
                }

                if (customLaunchers.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.launcher_presets_title),
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    customLaunchers.forEach { preset ->
                        val selected = currentLauncherId == preset.launcherId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.menuButton)
                                .background(if (selected) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                                .border(
                                    width = if (selected) 1.dp else 0.5.dp,
                                    color = if (selected) DeckColors.Ruby else DeckColors.DialogBorder,
                                    shape = DeckShapes.menuButton,
                                )
                                .gamepadClickable(swapAbxy, keyBindings) { onSelect(preset.launcherId) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    preset.name,
                                    style = if (selected) DeckType.listItemSelected else DeckType.settingsValue,
                                    color = if (selected) DeckColors.Ruby else DeckColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    preset.launcherId.removePrefix(CustomLauncherCodec.PREFIX_CUSTOM_APP),
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (onDeletePreset != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "✕",
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextSecondary,
                                    modifier = Modifier
                                        .clip(DeckShapes.settingsDropdown)
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                            onDeletePreset(preset.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                if (customSpec != null && customLaunchers.none { it.launcherId == currentLauncherId }) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.launcher_custom_current),
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    val appName = GameLauncher.describeLauncher(context, platform.id, currentLauncherId)
                        ?: customSpec.packageName
                    LauncherOptionRow(
                        label = appName,
                        subtitle = "${customSpec.packageName} · ${customSpec.mode.name}",
                        selected = true,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onClick = { pickedPkgForMode = customSpec.packageName to appName },
                    )
                }

                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                        .gamepadClickable(swapAbxy, keyBindings) { pickingApp = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.launcher_pick_custom_app),
                        style = DeckType.menuButton,
                        color = DeckColors.Ruby,
                    )
                }

                if (notInstalledEmus.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.settingsDropdown)
                            .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                showNotInstalled = !showNotInstalled
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${stringResource(R.string.launcher_supported_not_installed)} (${notInstalledEmus.size})",
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (showNotInstalled) "▾" else "▸",
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                    }
                    if (showNotInstalled) {
                        notInstalledEmus.forEach { emu ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(DeckShapes.menuButton)
                                    .background(DeckColors.Surface.copy(alpha = 0.55f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        emu.displayName,
                                        style = DeckType.settingsLabel,
                                        color = DeckColors.TextSecondary,
                                    )
                                    Text(
                                        emu.packages.firstOrNull().orEmpty(),
                                        style = DeckType.metaLabel,
                                        color = DeckColors.TextLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    stringResource(R.string.launcher_not_installed),
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomRetroArchCoreDialog(
    initialCore: String,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var core by remember(initialCore) { mutableStateOf(initialCore) }
    val fieldFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFr.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (GamepadKeys.isBack(ev.key) || GamepadKeys.isCancelOrApps(ev.key, swapAbxy, keyBindings))
                ) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(420.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.launcher_ra_custom_core_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = core,
                onValueChange = { core = it },
                placeholder = { Text(stringResource(R.string.launcher_ra_custom_core_hint), style = DeckType.settingsLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(core.trim().removeSuffix("_libretro_android.so")) }),
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .gamepadClickable(swapAbxy, keyBindings, onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_ok),
                    style = DeckType.menuButton,
                    color = DeckColors.Background,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby)
                        .gamepadClickable(swapAbxy, keyBindings) {
                            onConfirm(core.trim().removeSuffix("_libretro_android.so"))
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LauncherOptionRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DeckShapes.menuButton)
            .background(if (selected) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) DeckColors.Ruby else DeckColors.DialogBorder,
                shape = DeckShapes.menuButton,
            )
            .gamepadClickable(swapAbxy, keyBindings, focusRequester = focusRequester, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (selected) DeckType.listItemSelected else DeckType.settingsValue,
                color = if (selected) DeckColors.Ruby else DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text("✓", style = DeckType.settingsValue, color = DeckColors.Ruby)
        }
    }
}

@Composable
private fun InstalledAppPickerDialog(
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
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
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
    LaunchedEffect(Unit) { runCatching { listFr.requestFocus() } }
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
                            .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                            .border(
                                width = if (hl) 1.5.dp else 0.dp,
                                color = if (hl) DeckColors.Ruby else Color.Transparent,
                                shape = DeckShapes.menuButton,
                            )
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

@Composable
private fun CustomLaunchModeDialog(
    appLabel: String,
    initialMode: CustomLaunchMode,
    initialExtraKey: String,
    initialActivityClass: String,
    canSavePreset: Boolean,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onConfirm: (CustomLaunchMode, String, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var extraKey by remember { mutableStateOf(initialExtraKey.ifBlank { "ROM" }) }
    var activityClass by remember { mutableStateOf(initialActivityClass) }
    var saveAsPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf(appLabel) }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFr.requestFocus() } }

    val modes = listOf(
        Triple(CustomLaunchMode.ViewContentUri, R.string.launch_mode_content_uri, R.string.launch_mode_content_uri_desc),
        Triple(CustomLaunchMode.ViewFileUri, R.string.launch_mode_file_uri, R.string.launch_mode_file_uri_desc),
        Triple(CustomLaunchMode.MainWithExtra, R.string.launch_mode_extra, R.string.launch_mode_extra_desc),
        Triple(CustomLaunchMode.AppOnly, R.string.launch_mode_app_only, R.string.launch_mode_app_only_desc),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (GamepadKeys.isBack(ev.key) || GamepadKeys.isCancelOrApps(ev.key, swapAbxy, keyBindings))
                ) {
                    onDismiss()
                    true
                } else false
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.launch_mode_title, appLabel),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))

            modes.forEachIndexed { idx, (m, titleRes, descRes) ->
                val sel = m == mode
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(DeckShapes.menuButton)
                        .background(if (sel) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                        .gamepadClickable(swapAbxy, keyBindings, focusRequester = if (idx == 0) firstFr else null) {
                            mode = m
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(titleRes),
                            style = if (sel) DeckType.listItemSelected else DeckType.settingsValue,
                            color = if (sel) DeckColors.Ruby else DeckColors.TextPrimary,
                        )
                        Text(
                            stringResource(descRes),
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                        )
                    }
                    if (sel) {
                        Spacer(Modifier.width(8.dp))
                        Text("✓", style = DeckType.settingsValue, color = DeckColors.Ruby)
                    }
                }
            }

            if (mode == CustomLaunchMode.MainWithExtra) {
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = extraKey,
                    onValueChange = { extraKey = it },
                    label = { Text(stringResource(R.string.launch_mode_extra_key), style = DeckType.settingsLabel) },
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
            }

            Spacer(Modifier.height(8.dp))
            TextField(
                value = activityClass,
                onValueChange = { activityClass = it },
                label = { Text(stringResource(R.string.launcher_activity_class), style = DeckType.settingsLabel) },
                placeholder = { Text(stringResource(R.string.launcher_activity_hint), style = DeckType.settingsLabel) },
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

            if (canSavePreset) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(DeckShapes.settingsDropdown)
                        .background(DeckColors.Surface)
                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                            saveAsPreset = !saveAsPreset
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.launcher_save_preset),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = saveAsPreset,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeckColors.Background,
                            checkedTrackColor = DeckColors.Ruby,
                        ),
                    )
                }
                if (saveAsPreset) {
                    Spacer(Modifier.height(6.dp))
                    TextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text(stringResource(R.string.launcher_preset_name), style = DeckType.settingsLabel) },
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
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .gamepadClickable(swapAbxy, keyBindings, onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_ok),
                    style = DeckType.menuButton,
                    color = DeckColors.Background,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby)
                        .gamepadClickable(swapAbxy, keyBindings) {
                            onConfirm(
                                mode,
                                extraKey.trim(),
                                activityClass.trim(),
                                if (saveAsPreset) presetName.trim().ifBlank { appLabel } else null,
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
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
            HorizontalDivider(color = DeckColors.DialogBorder)
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

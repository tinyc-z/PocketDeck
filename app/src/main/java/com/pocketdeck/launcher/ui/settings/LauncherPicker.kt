package com.pocketdeck.launcher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.launch.CoreNote
import com.pocketdeck.launcher.launch.CustomLaunchMode
import com.pocketdeck.launcher.launch.CustomLaunchProbe
import com.pocketdeck.launcher.launch.CustomLauncherCodec
import com.pocketdeck.launcher.launch.EmulatorDef
import com.pocketdeck.launcher.launch.EmulatorRegistry
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.launch.RetroArchCoreCatalog
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.ui.components.DeckSwitch
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.gamepadClickable
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.brandPillBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private sealed interface PickerPage {
    data object Main : PickerPage
    data object Cores : PickerPage
    data object ManualCore : PickerPage
    data object AppPicker : PickerPage
    data class Variants(val emu: EmulatorDef) : PickerPage
    data class CustomApp(
        val pkg: String,
        val label: String,
        val initial: CustomLauncherCodec.CustomAppSpec?,
    ) : PickerPage
}

private sealed interface InstalledEntry {
    data object RetroArch : InstalledEntry
    data class Emu(val emu: EmulatorDef, val packages: List<String>) : InstalledEntry
}

/**
 * 平台 / 单游戏启动器选择弹窗（两步式）：
 * 第 1 步选模拟器（自动 / 已安装 / 预设 / 未安装 / 其他应用兜底），
 * 第 2 步按需选 RetroArch 核心或模拟器版本；手动填写仅作为最后兜底。
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
    testGame: Game? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<PickerPage>(PickerPage.Main) }

    // 选择应用：复用全屏应用列表，挑完后先尝试自动识别
    if (page == PickerPage.AppPicker) {
        BackHandler(enabled = true) { page = PickerPage.Main }
        InstalledAppPickerDialog(
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onPick = { pkg, label ->
                when {
                    GameLauncher.isRetroArchPackage(pkg) -> {
                        Toast.makeText(context, context.getString(R.string.launcher_recognized, "RetroArch"), Toast.LENGTH_SHORT).show()
                        page = PickerPage.Cores
                    }
                    EmulatorRegistry.findByPackage(pkg) != null -> {
                        val emu = EmulatorRegistry.findByPackage(pkg)!!
                        Toast.makeText(context, context.getString(R.string.launcher_recognized, emu.displayName), Toast.LENGTH_SHORT).show()
                        val installed = EmulatorRegistry.installedPackages(context, emu)
                        onSelect(if (installed.size > 1) CustomLauncherCodec.encodeEmuVariant(emu.id, pkg) else emu.id)
                    }
                    else -> page = PickerPage.CustomApp(pkg, label, null)
                }
            },
            onDismiss = { page = PickerPage.Main },
        )
        return
    }

    fun goBack() {
        page = when (page) {
            PickerPage.ManualCore -> PickerPage.Cores
            PickerPage.Main -> {
                onDismiss()
                PickerPage.Main
            }
            else -> PickerPage.Main
        }
    }

    // 系统返回键只回退一级（子页面 → 第 1 步 → 关闭），不穿透到设置页
    BackHandler(enabled = true) { goBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key != Key.Back &&
                    (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings))
                ) {
                    goBack()
                    true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            val firstFr = remember(page) { FocusRequester() }
            LaunchedEffect(page) { runCatching { firstFr.requestFocus() } }

            when (val p = page) {
                PickerPage.Main -> MainPage(
                    title = title,
                    platform = platform,
                    currentLauncherId = currentLauncherId,
                    defaultLabel = defaultLabel,
                    showAspectRatio = showAspectRatio,
                    defaultAspect = defaultAspect,
                    currentAspect = currentAspect,
                    onSetAspect = onSetAspect,
                    customLaunchers = customLaunchers,
                    onDeletePreset = onDeletePreset,
                    firstFr = firstFr,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onSelect = onSelect,
                    onNavigate = { page = it },
                )
                PickerPage.Cores -> CoresPage(
                    platform = platform,
                    currentLauncherId = currentLauncherId,
                    firstFr = firstFr,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onBack = { page = PickerPage.Main },
                    onSelect = onSelect,
                    onManual = { page = PickerPage.ManualCore },
                )
                PickerPage.ManualCore -> ManualCorePage(
                    initialCore = CustomLauncherCodec.decodeRaCore(currentLauncherId) ?: platform.core.orEmpty(),
                    firstFr = firstFr,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onBack = { page = PickerPage.Cores },
                    onConfirm = { core ->
                        if (core.isNotBlank()) onSelect(CustomLauncherCodec.encodeRaCore(core))
                    },
                )
                is PickerPage.Variants -> VariantsPage(
                    emu = p.emu,
                    currentLauncherId = currentLauncherId,
                    firstFr = firstFr,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onBack = { page = PickerPage.Main },
                    onSelect = onSelect,
                )
                is PickerPage.CustomApp -> CustomAppPage(
                    pkg = p.pkg,
                    appLabel = p.label,
                    initial = p.initial,
                    platform = platform,
                    testGame = testGame,
                    canSavePreset = onSavePreset != null,
                    firstFr = firstFr,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onBack = { page = PickerPage.Main },
                    onConfirm = { encoded, presetName ->
                        if (!presetName.isNullOrBlank()) onSavePreset?.invoke(presetName, encoded)
                        onSelect(encoded)
                    },
                )
                PickerPage.AppPicker -> Unit
            }
        }
    }
}

// ───────────────────────── 第 1 步：选模拟器 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.MainPage(
    title: String,
    platform: Platform,
    currentLauncherId: String?,
    defaultLabel: String,
    showAspectRatio: Boolean,
    defaultAspect: Float,
    currentAspect: Float?,
    onSetAspect: ((Float?) -> Unit)?,
    customLaunchers: List<CustomLauncherPreset>,
    onDeletePreset: ((String) -> Unit)?,
    firstFr: FocusRequester,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onSelect: (String?) -> Unit,
    onNavigate: (PickerPage) -> Unit,
) {
    val context = LocalContext.current
    val isAndroid = platform.id == PlatformCatalog.ANDROID_ID

    val available = remember(platform.id) { GameLauncher.availableLaunchers(context, platform.id) }
    val raInstalled = remember { GameLauncher.isRetroArchInstalled(context) }
    val entries = remember(platform.id, available, raInstalled) {
        val emus = EmulatorRegistry.allForPlatform(platform.id)
            .map { InstalledEntry.Emu(it, EmulatorRegistry.installedPackages(context, it)) }
            .filter { it.packages.isNotEmpty() }
        val byId = emus.associateBy { it.emu.id }
        buildList<InstalledEntry> {
            available.forEach { (id, _) ->
                if (id == GameLauncher.RETROARCH_ID) add(InstalledEntry.RetroArch) else byId[id]?.let(::add)
            }
            emus.forEach { if (it !in this) add(it) }
            if (raInstalled && !isAndroid && InstalledEntry.RetroArch !in this) add(InstalledEntry.RetroArch)
        }
    }
    val recommendedId = available.firstOrNull()?.first
    val autoSubtitle = remember(platform.id) {
        GameLauncher.describeLauncher(context, platform.id, null)
    }
    val coreCount = remember(platform.id) { GameLauncher.platformCoreCandidates(platform).size }
    val notInstalled = remember(platform.id) {
        GameLauncher.supportedStandaloneEmulators(context, platform.id).filter { !it.second }.map { it.first }
    }
    val icons by produceState<Map<String, ImageBitmap>>(emptyMap(), entries) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val pkgs = entries.mapNotNull { e ->
                when (e) {
                    InstalledEntry.RetroArch -> listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")
                        .firstOrNull { EmulatorRegistry.isPackageInstalled(context, it) }
                    is InstalledEntry.Emu -> e.packages.first()
                }
            }
            pkgs.mapNotNull { pkg ->
                runCatching { pkg to pm.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap() }.getOrNull()
            }.toMap()
        }
    }
    val customSpec = remember(currentLauncherId) { CustomLauncherCodec.decodeCustomApp(currentLauncherId) }
    val currentRaCore = CustomLauncherCodec.decodeRaCore(currentLauncherId)
    val currentEmuId = CustomLauncherCodec.emulatorIdOf(currentLauncherId)

    PickerHeader(title = title, subtitle = "${platform.fullName} (${platform.shortName})")
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = DeckColors.Line)

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showAspectRatio && onSetAspect != null) {
            AspectRatioCard(defaultAspect, currentAspect, onSetAspect, swapAbxy, keyBindings)
            Spacer(Modifier.height(4.dp))
        }

        PickerRow(
            label = defaultLabel,
            subtitle = if (isAndroid) null else autoSubtitle?.let { stringResource(R.string.launcher_auto_will_use, it) }
                ?: stringResource(R.string.launcher_auto_none),
            selected = currentLauncherId == null,
            focusRequester = firstFr,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onClick = { onSelect(null) },
        )

        if (entries.isNotEmpty()) {
            SectionLabel(stringResource(R.string.launcher_section_installed))
            entries.forEach { entry ->
                when (entry) {
                    InstalledEntry.RetroArch -> {
                        val selected = currentLauncherId == GameLauncher.RETROARCH_ID || currentRaCore != null
                        val sub = when {
                            currentRaCore != null -> RetroArchCoreCatalog.displayName(currentRaCore)
                            currentLauncherId == GameLauncher.RETROARCH_ID -> stringResource(R.string.launcher_ra_auto_core_row)
                            coreCount > 0 -> stringResource(R.string.launcher_ra_cores_count, coreCount)
                            else -> null
                        }
                        val raIcon = icons.entries.firstOrNull { GameLauncher.isRetroArchPackage(it.key) }?.value
                        PickerRow(
                            label = "RetroArch",
                            subtitle = sub,
                            selected = selected,
                            icon = raIcon,
                            tags = buildList {
                                if (recommendedId == GameLauncher.RETROARCH_ID) add(stringResource(R.string.launcher_tag_recommended))
                                add(stringResource(R.string.launcher_tag_multicore))
                            },
                            chevron = true,
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = { onNavigate(PickerPage.Cores) },
                        )
                    }
                    is InstalledEntry.Emu -> {
                        val multi = entry.packages.size > 1
                        val selected = currentEmuId == entry.emu.id
                        val variantPkg = CustomLauncherCodec.decodeEmuVariant(currentLauncherId)?.second
                        val sub = when {
                            selected && variantPkg != null -> GameLauncher.appLabel(context, variantPkg)
                            multi -> stringResource(R.string.launcher_variants_count, entry.packages.size)
                            else -> null
                        }
                        PickerRow(
                            label = entry.emu.displayName,
                            subtitle = sub,
                            selected = selected,
                            icon = icons[entry.packages.first()],
                            tags = if (recommendedId == entry.emu.id) listOf(stringResource(R.string.launcher_tag_recommended)) else emptyList(),
                            chevron = multi,
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = {
                                if (multi) onNavigate(PickerPage.Variants(entry.emu)) else onSelect(entry.emu.id)
                            },
                        )
                    }
                }
            }
        }

        if (customLaunchers.isNotEmpty()) {
            SectionLabel(stringResource(R.string.launcher_presets_title))
            customLaunchers.forEach { preset ->
                val selected = currentLauncherId == preset.launcherId
                PickerRow(
                    label = preset.name,
                    subtitle = GameLauncher.describeLauncher(context, platform.id, preset.launcherId),
                    selected = selected,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onClick = { onSelect(preset.launcherId) },
                    trailing = if (onDeletePreset != null) {
                        {
                            Box(
                                modifier = Modifier
                                    .clip(DeckShapes.settingsDropdown)
                                    .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                        onDeletePreset(preset.id)
                                    }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                DeckIcon(DeckIcons.Close, tint = DeckColors.TextSecondary, size = 14.dp)
                            }
                        }
                    } else null,
                )
            }
        }

        if (customSpec != null && customLaunchers.none { it.launcherId == currentLauncherId }) {
            SectionLabel(stringResource(R.string.launcher_custom_current))
            val appName = GameLauncher.appLabel(context, customSpec.packageName) ?: customSpec.packageName
            PickerRow(
                label = appName,
                subtitle = customSpec.packageName,
                selected = true,
                chevron = true,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onClick = { onNavigate(PickerPage.CustomApp(customSpec.packageName, appName, customSpec)) },
            )
        }

        if (notInstalled.isNotEmpty()) {
            Text(
                stringResource(
                    R.string.launcher_also_supported,
                    notInstalled.joinToString(
                        if (context.resources.configuration.locales[0].language == "zh") "、" else ", ",
                    ) { it.displayName },
                ),
                style = DeckType.metaLabel,
                color = DeckColors.TextLabel,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        if (!isAndroid) {
            Spacer(Modifier.height(4.dp))
            PickerRow(
                label = stringResource(R.string.launcher_pick_custom_app),
                subtitle = stringResource(R.string.launcher_other_app_desc),
                selected = false,
                subdued = true,
                chevron = true,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onClick = { onNavigate(PickerPage.AppPicker) },
            )
        }
    }
}

// ───────────────────────── 第 2 步：RetroArch 核心 ─────────────────────────

@Composable
private fun ColumnScope.CoresPage(
    platform: Platform,
    currentLauncherId: String?,
    firstFr: FocusRequester,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
    onManual: () -> Unit,
) {
    val context = LocalContext.current
    val candidates = remember(platform.id) { GameLauncher.platformCoreCandidates(platform) }
    val detected = remember { GameLauncher.detectedRetroArchCores(context) }
    val autoCore = remember(platform.id) { GameLauncher.autoRetroArchCore(context, platform) }
    val otherDetected = remember(candidates, detected) {
        // 已知平台只保留不属于其他平台的未知核心（避免在 SNES 下出现 FCEUmm）；
        // 自定义/未知平台（candidates 为空，如 test）则直接展示全部已检测到的核心供一键点选。
        val lower = candidates.map { it.lowercase() }.toSet()
        if (candidates.isEmpty()) {
            detected.sorted()
        } else {
            val known = GameLauncher.allKnownCores()
            detected.filter { it !in lower && it !in known }.sorted()
        }
    }
    val currentCore = CustomLauncherCodec.decodeRaCore(currentLauncherId)
    val listedCores = candidates.map { it.lowercase() }.toSet() + otherDetected

    SubPageHeader(stringResource(R.string.launcher_ra_core_title), platform.shortName, swapAbxy, keyBindings, onBack)
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = DeckColors.Line)

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PickerRow(
            label = stringResource(R.string.launcher_ra_auto_core_row),
            subtitle = autoCore?.let { RetroArchCoreCatalog.displayName(it) },
            selected = currentLauncherId == GameLauncher.RETROARCH_ID,
            focusRequester = firstFr,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onClick = { onSelect(GameLauncher.RETROARCH_ID) },
        )

        val recommendedLabel = stringResource(R.string.launcher_tag_recommended)
        val detectedLabel = stringResource(R.string.launcher_tag_detected)
        candidates.forEachIndexed { idx, core ->
            CoreRow(
                core = core,
                selected = currentCore == core,
                tags = buildList {
                    if (idx == 0) add(recommendedLabel)
                    if (core.lowercase() in detected) add(detectedLabel)
                },
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onClick = { onSelect(CustomLauncherCodec.encodeRaCore(core)) },
            )
        }
        if (currentCore != null && currentCore.lowercase() !in listedCores) {
            CoreRow(
                core = currentCore,
                selected = true,
                tags = emptyList(),
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onClick = onManual,
            )
        }
        if (otherDetected.isNotEmpty()) {
            SectionLabel(stringResource(R.string.launcher_ra_other_detected))
            otherDetected.forEach { core ->
                CoreRow(
                    core = core,
                    selected = currentCore?.lowercase() == core,
                    tags = listOf(detectedLabel),
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onClick = { onSelect(CustomLauncherCodec.encodeRaCore(core)) },
                )
            }
        }
        Text(
            stringResource(R.string.launcher_ra_detect_hint),
            style = DeckType.metaLabel,
            color = DeckColors.TextLabel,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        PickerRow(
            label = stringResource(R.string.launcher_ra_manual),
            subtitle = null,
            selected = false,
            subdued = true,
            chevron = true,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onClick = onManual,
        )
    }
}

@Composable
private fun CoreRow(
    core: String,
    selected: Boolean,
    tags: List<String>,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onClick: () -> Unit,
) {
    val info = RetroArchCoreCatalog.info(core)
    val note = info.note?.let { stringResource(coreNoteRes(it)) }
    PickerRow(
        label = info.displayName,
        subtitle = note ?: core,
        selected = selected,
        tags = tags,
        swapAbxy = swapAbxy,
        keyBindings = keyBindings,
        onClick = onClick,
    )
}

private fun coreNoteRes(note: CoreNote): Int = when (note) {
    CoreNote.Balanced -> R.string.launcher_note_balanced
    CoreNote.Accurate -> R.string.launcher_note_accurate
    CoreNote.Fast -> R.string.launcher_note_fast
    CoreNote.Legacy -> R.string.launcher_note_legacy
    CoreNote.Hardware -> R.string.launcher_note_hw
    CoreNote.ArcadeModern -> R.string.launcher_note_arcade_new
    CoreNote.ArcadeLegacy -> R.string.launcher_note_arcade_old
}

@Composable
private fun ColumnScope.ManualCorePage(
    initialCore: String,
    firstFr: FocusRequester,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var core by remember(initialCore) { mutableStateOf(initialCore) }
    SubPageHeader(stringResource(R.string.launcher_ra_custom_core_title), null, swapAbxy, keyBindings, onBack)
    Spacer(Modifier.height(12.dp))
    TextField(
        value = core,
        onValueChange = { core = it },
        placeholder = { Text(stringResource(R.string.launcher_ra_custom_core_hint), style = DeckType.settingsLabel) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onConfirm(core.trim().removeSuffix("_libretro_android.so")) }),
        colors = pickerFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(firstFr),
    )
    Spacer(Modifier.height(14.dp))
    ActionButtons(
        swapAbxy = swapAbxy,
        keyBindings = keyBindings,
        confirmLabel = stringResource(R.string.action_ok),
        onCancel = onBack,
        onConfirm = { onConfirm(core.trim().removeSuffix("_libretro_android.so")) },
    )
}

// ───────────────────────── 第 2 步：模拟器版本 ─────────────────────────

@Composable
private fun ColumnScope.VariantsPage(
    emu: EmulatorDef,
    currentLauncherId: String?,
    firstFr: FocusRequester,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val context = LocalContext.current
    val packages = remember(emu.id) { EmulatorRegistry.installedPackages(context, emu) }
    val currentPkg = CustomLauncherCodec.decodeEmuVariant(currentLauncherId)?.second

    SubPageHeader(stringResource(R.string.launcher_variant_title), emu.displayName, swapAbxy, keyBindings, onBack)
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = DeckColors.Line)
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PickerRow(
            label = stringResource(R.string.launcher_variant_auto),
            subtitle = packages.firstOrNull()?.let { GameLauncher.appLabel(context, it) },
            selected = currentLauncherId == emu.id,
            focusRequester = firstFr,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onClick = { onSelect(emu.id) },
        )
        packages.forEach { pkg ->
            PickerRow(
                label = GameLauncher.appLabel(context, pkg) ?: pkg,
                subtitle = pkg,
                selected = currentPkg == pkg,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onClick = { onSelect(CustomLauncherCodec.encodeEmuVariant(emu.id, pkg)) },
            )
        }
    }
}

// ───────────────────────── 兜底：其他应用 ─────────────────────────

@Composable
private fun ColumnScope.CustomAppPage(
    pkg: String,
    appLabel: String,
    initial: CustomLauncherCodec.CustomAppSpec?,
    platform: Platform,
    testGame: Game?,
    canSavePreset: Boolean,
    firstFr: FocusRequester,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onBack: () -> Unit,
    onConfirm: (encoded: String, presetName: String?) -> Unit,
) {
    val context = LocalContext.current
    val detection = remember(pkg) { CustomLaunchProbe.detect(context, pkg) }
    var mode by remember(pkg) { mutableStateOf(initial?.mode ?: detection.mode) }
    var extraKey by remember(pkg) { mutableStateOf(initial?.extraKey?.ifBlank { null } ?: "ROM") }
    var activityClass by remember(pkg) { mutableStateOf(initial?.activityClass ?: detection.activityClass) }
    var advancedOpen by remember(pkg) { mutableStateOf(false) }
    var saveAsPreset by remember(pkg) { mutableStateOf(canSavePreset && initial == null) }
    var presetName by remember(pkg) { mutableStateOf("$appLabel · ${platform.shortName}") }

    fun encoded() = CustomLauncherCodec.encodeCustomApp(pkg, mode, extraKey.trim(), activityClass.trim())

    val modes = listOf(
        Triple(CustomLaunchMode.ViewContentUri, R.string.launch_mode_content_uri, R.string.launch_mode_content_uri_desc),
        Triple(CustomLaunchMode.ViewFileUri, R.string.launch_mode_file_uri, R.string.launch_mode_file_uri_desc),
        Triple(CustomLaunchMode.MainWithExtra, R.string.launch_mode_extra, R.string.launch_mode_extra_desc),
        Triple(CustomLaunchMode.AppOnly, R.string.launch_mode_app_only, R.string.launch_mode_app_only_desc),
    )
    val modeName = stringResource(modes.first { it.first == mode }.second)

    SubPageHeader(appLabel, pkg, swapAbxy, keyBindings, onBack)
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = DeckColors.Line)
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(DeckShapes.menuButton)
                .background(DeckColors.AccentSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckIcon(DeckIcons.Check, size = 16.dp, tint = DeckColors.Ruby)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.launcher_custom_detected, modeName),
                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                color = DeckColors.TextPrimary,
            )
        }

        if (testGame != null) {
            PickerRow(
                label = stringResource(R.string.launcher_custom_test),
                subtitle = stringResource(R.string.launcher_custom_test_desc, testGame.title),
                selected = false,
                focusRequester = firstFr,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onClick = {
                    val res = GameLauncher.launch(context, testGame, platform, encoded())
                    if (res is GameLauncher.Result.Error) {
                        Toast.makeText(context, context.getString(R.string.launcher_custom_test_failed), Toast.LENGTH_LONG).show()
                        advancedOpen = true
                    }
                },
            )
        }

        if (canSavePreset) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(DeckShapes.menuButton)
                    .background(DeckColors.Surface)
                    .gamepadClickable(swapAbxy, keyBindings) { saveAsPreset = !saveAsPreset }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.launcher_save_preset),
                    style = DeckType.settingsValue,
                    color = DeckColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                DeckSwitch(checked = saveAsPreset)
            }
            if (saveAsPreset) {
                TextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(stringResource(R.string.launcher_preset_name), style = DeckType.settingsLabel) },
                    singleLine = true,
                    colors = pickerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(DeckShapes.settingsDropdown)
                .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) { advancedOpen = !advancedOpen }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.launcher_advanced),
                style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                color = DeckColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            DeckIcon(
                if (advancedOpen) PanelIcons.ChevronDown else DeckIcons.ChevronRight,
                tint = DeckColors.TextSecondary,
                size = 15.dp,
            )
        }
        if (advancedOpen) {
            modes.forEach { (m, titleRes, descRes) ->
                PickerRow(
                    label = stringResource(titleRes),
                    subtitle = stringResource(descRes),
                    selected = m == mode,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onClick = { mode = m },
                )
            }
            if (mode == CustomLaunchMode.MainWithExtra) {
                TextField(
                    value = extraKey,
                    onValueChange = { extraKey = it },
                    label = { Text(stringResource(R.string.launch_mode_extra_key), style = DeckType.settingsLabel) },
                    singleLine = true,
                    colors = pickerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextField(
                value = activityClass,
                onValueChange = { activityClass = it },
                label = { Text(stringResource(R.string.launcher_activity_class), style = DeckType.settingsLabel) },
                placeholder = { Text(stringResource(R.string.launcher_activity_hint), style = DeckType.settingsLabel) },
                singleLine = true,
                colors = pickerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    ActionButtons(
        swapAbxy = swapAbxy,
        keyBindings = keyBindings,
        confirmLabel = stringResource(R.string.launcher_save),
        confirmFocus = if (testGame == null) firstFr else null,
        onCancel = onBack,
        onConfirm = {
            onConfirm(encoded(), if (saveAsPreset) presetName.trim().ifBlank { appLabel } else null)
        },
    )
}

// ───────────────────────── 通用组件 ─────────────────────────

@Composable
private fun PickerHeader(title: String, subtitle: String?) {
    Column(Modifier.padding(end = 36.dp)) {
        Text(
            title,
            style = DeckType.menuTitle,
            color = DeckColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = DeckType.settingsLabel, color = DeckColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SubPageHeader(
    title: String,
    subtitle: String?,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onBack: () -> Unit,
) {
    Row(
        Modifier.padding(end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(DeckColors.Surface)
                .border(0.6.dp, DeckColors.Line, RoundedCornerShape(50))
                .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(50), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            DeckIcon(DeckIcons.ChevronLeft, size = 16.dp, tint = DeckColors.TextPrimary)
        }
        Spacer(Modifier.width(10.dp))
        PickerHeader(title, subtitle)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(
        text,
        style = DeckType.metaLabel,
        color = DeckColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun PickerRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    icon: ImageBitmap? = null,
    tags: List<String> = emptyList(),
    chevron: Boolean = false,
    subdued: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DeckShapes.menuButton)
            .background(
                when {
                    selected -> DeckColors.AccentSoft
                    subdued -> DeckColors.Surface.copy(alpha = 0.55f)
                    else -> DeckColors.Surface
                },
            )
            .brandBorder(active = selected, shape = DeckShapes.menuButton, activeWidth = 1.2.dp, inactiveWidth = 0.5.dp)
            .gamepadClickable(swapAbxy, keyBindings, focusRequester = focusRequester, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = label,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = if (selected) DeckType.listItemSelected else DeckType.settingsValue,
                    color = when {
                        selected -> DeckColors.Ruby
                        subdued -> DeckColors.TextSecondary
                        else -> DeckColors.TextPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                tags.forEach { tag ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tag,
                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = DeckColors.Ruby,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(DeckColors.Ruby.copy(alpha = 0.10f))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
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
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            DeckIcon(DeckIcons.Check, size = 16.dp, tint = DeckColors.Ruby)
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            DeckIcon(DeckIcons.ChevronRight, size = 16.dp, tint = DeckColors.TextSecondary)
        }
    }
}

@Composable
private fun ActionButtons(
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmFocus: FocusRequester? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            stringResource(R.string.menu_cancel),
            style = DeckType.menuButton,
            color = DeckColors.TextSecondary,
            modifier = Modifier
                .clip(DeckShapes.menuButton)
                .gamepadClickable(swapAbxy, keyBindings, onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            confirmLabel,
            style = DeckType.menuButton,
            color = DeckColors.Background,
            modifier = Modifier
                .clip(DeckShapes.menuButton)
                .brandBackground(DeckShapes.menuButton)
                .gamepadClickable(swapAbxy, keyBindings, focusRequester = confirmFocus, onClick = onConfirm)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun pickerFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = DeckColors.Surface,
    unfocusedContainerColor = DeckColors.Surface,
    focusedTextColor = DeckColors.TextPrimary,
    unfocusedTextColor = DeckColors.TextPrimary,
    focusedIndicatorColor = DeckColors.Ruby,
    cursorColor = DeckColors.Ruby,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AspectRatioCard(
    defaultAspect: Float,
    currentAspect: Float?,
    onSetAspect: (Float?) -> Unit,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "−",
                    style = DeckType.menuButton,
                    color = DeckColors.Ruby,
                    modifier = Modifier
                        .clip(DeckShapes.settingsDropdown)
                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                            onSetAspect((effectiveAspect - 0.05f).coerceIn(0.40f, 2.20f))
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
                            onSetAspect((effectiveAspect + 0.05f).coerceIn(0.40f, 2.20f))
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
                val isSel = if (presetVal == null) currentAspect == null
                else currentAspect != null && abs(currentAspect - presetVal) < 0.015f
                Text(
                    label,
                    style = DeckType.metaLabel.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (isSel) Color.White else DeckColors.TextPrimary,
                    modifier = Modifier
                        .clip(DeckShapes.settingsDropdown)
                        .brandPillBackground(selected = isSel, shape = DeckShapes.settingsDropdown, unselectedColor = DeckColors.Background)
                        .then(if (isSel) Modifier else Modifier.border(0.5.dp, DeckColors.Line, DeckShapes.settingsDropdown))
                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) { onSetAspect(presetVal) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
    }
}

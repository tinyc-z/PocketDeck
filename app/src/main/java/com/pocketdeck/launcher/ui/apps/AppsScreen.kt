package com.pocketdeck.launcher.ui.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.pocketdeck.launcher.ui.components.BrandTileIcon
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.input.triggerLongPressHaptic
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.components.DeckButton
import com.pocketdeck.launcher.ui.components.DeckButtonStyle
import com.pocketdeck.launcher.ui.components.DeckCard
import com.pocketdeck.launcher.ui.components.DeckDivider
import com.pocketdeck.launcher.ui.components.DeckIconButton
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.DeckRow
import com.pocketdeck.launcher.ui.components.DeckScreenHeader
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.brandFocusRing
import com.pocketdeck.launcher.ui.theme.cardSurface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import com.pocketdeck.launcher.ui.library.GameLibraryAppIcon
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import com.pocketdeck.launcher.ui.theme.DeckFontFamily
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APPS_COLUMNS = 5

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isSystemApp: Boolean = false,
)

/**
 * Apps 抽屉：5 列已安装应用网格 + 长按振动与菜单（打开 / 加入游戏库 / 应用信息 / 卸载）+ 左上返回 + 右上系统设置。
 */
private object InstalledAppsCache {
    @Volatile
    var apps: List<InstalledApp> = emptyList()
}

@Composable
fun AppsScreen(
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    androidGamePackages: Set<String> = emptySet(),
    excludedAndroidPackages: Set<String> = emptySet(),
    autoGamePackages: Set<String> = emptySet(),
    onToggleInGameLibrary: ((String, Boolean) -> Unit)? = null,
    onOpenGameLibrary: () -> Unit = {},
    onOpenQuickShare: () -> Unit = {},
    hasTopOverlay: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    var focusIndex by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    var apps by remember { mutableStateOf(InstalledAppsCache.apps) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var menuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var menuSelection by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        val loaded = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0)
            val prevByPkg = InstalledAppsCache.apps.associateBy { it.packageName }
            val seen = HashSet<String>()
            resolved.mapNotNull { info ->
                val ai = info.activityInfo?.applicationInfo
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val cached = prevByPkg[pkg]
                val bmp = if (cached != null && cached.label == label) {
                    cached.icon
                } else {
                    runCatching {
                        info.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
                    }.getOrNull() ?: return@mapNotNull null
                }
                val isSys = ai != null &&
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                InstalledApp(pkg, label, bmp, isSys)
            }.sortedBy { it.label.lowercase() }
        }
        InstalledAppsCache.apps = loaded
        apps = loaded
        val totalItems = 2 + apps.size
        if (focusIndex >= totalItems) {
            focusIndex = (totalItems - 1).coerceAtLeast(0)
        }
    }

    // 卸载或从系统设置返回后自动刷新列表
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun launchApp(app: InstalledApp) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
        }
    }

    fun openAppInfo(app: InstalledApp) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    fun uninstallApp(app: InstalledApp) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    fun openMenuFor(app: InstalledApp, vibrate: Boolean) {
        if (vibrate) triggerLongPressHaptic(context, view)
        menuSelection = 0
        menuApp = app
    }

    fun isInGames(pkg: String): Boolean =
        (pkg in autoGamePackages || pkg in androidGamePackages) && pkg !in excludedAndroidPackages

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(hasTopOverlay) {
        if (!hasTopOverlay) focusRequester.requestFocus()
    }

    val totalItems = 2 + apps.size
    LaunchedEffect(focusIndex, totalItems) {
        if (focusIndex in 0 until totalItems) {
            val visible = gridState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == focusIndex }) {
                gridState.animateScrollToItem(focusIndex)
            }
        }
    }

    val openLabel = stringResource(R.string.app_menu_open)
    val addGameLabel = stringResource(R.string.app_menu_add_to_games)
    val removeGameLabel = stringResource(R.string.app_menu_remove_from_games)
    val infoLabel = stringResource(R.string.app_menu_info)
    val uninstallLabel = stringResource(R.string.app_menu_uninstall)

    fun buildMenuActions(app: InstalledApp): List<Triple<ImageVector, String, () -> Unit>> = buildList {
        add(Triple(PanelIcons.Play, openLabel) { menuApp = null; launchApp(app) })
        if (onToggleInGameLibrary != null) {
            val inGames = isInGames(app.packageName)
            add(
                Triple(
                    if (inGames) PanelIcons.Close else PanelIcons.Plus,
                    if (inGames) removeGameLabel else addGameLabel,
                ) {
                    onToggleInGameLibrary(app.packageName, !inGames)
                    menuApp = null
                },
            )
        }
        add(Triple(PanelIcons.Info, infoLabel) { menuApp = null; openAppInfo(app) })
        if (!app.isSystemApp) {
            add(Triple(DeckIcons.Trash, uninstallLabel) { menuApp = null; uninstallApp(app) })
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(DeckColors.Background)
            .blockOverlayTouchThrough()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val activeMenuApp = menuApp
                if (activeMenuApp != null) {
                    val actions = buildMenuActions(activeMenuApp)
                    return@onPreviewKeyEvent when {
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                            menuApp = null
                            true
                        }
                        event.key == Key.DirectionUp -> {
                            menuSelection = (menuSelection - 1 + actions.size) % actions.size
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            menuSelection = (menuSelection + 1) % actions.size
                            true
                        }
                        GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                            actions.getOrNull(menuSelection)?.third?.invoke()
                            true
                        }
                        else -> true
                    }
                }

                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    GamepadKeys.isSettings(event.key, swapAbxy, keyBindings) -> {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        focusIndex = (focusIndex - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        focusIndex = (focusIndex + 1).coerceAtMost(totalItems - 1)
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        focusIndex = (focusIndex - APPS_COLUMNS).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        focusIndex = (focusIndex + APPS_COLUMNS).coerceAtMost(totalItems - 1)
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when (focusIndex) {
                            0 -> onOpenGameLibrary()
                            1 -> onOpenQuickShare()
                            else -> apps.getOrNull(focusIndex - 2)?.let(::launchApp)
                        }
                        true
                    }
                    GamepadKeys.isMenu(event.key, swapAbxy, keyBindings) -> {
                        when (focusIndex) {
                            0 -> onOpenGameLibrary()
                            1 -> onOpenQuickShare()
                            else -> apps.getOrNull(focusIndex - 2)?.let { openMenuFor(it, vibrate = true) }
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            DeckScreenHeader(
                title = stringResource(R.string.apps_title),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                backModifier = Modifier.soundClickable(onClick = onDismiss),
                trailing = {
                    DeckIconButton(
                        PanelIcons.Settings,
                        modifier = Modifier.soundClickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        size = 38.dp,
                        iconSize = 20.dp,
                        elevated = true,
                    )
                },
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(APPS_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "__game_library__") {
                    AppTile(
                        label = stringResource(R.string.game_library_title),
                        focused = focusIndex == 0,
                        tapKey = "game_library",
                        onTap = {
                            KeySound.playTouch(context)
                            focusIndex = 0
                            onOpenGameLibrary()
                        },
                        onLongPress = {
                            focusIndex = 0
                            onOpenGameLibrary()
                        },
                    ) { GameLibraryAppIcon(size = 54.dp) }
                }
                item(key = "__quick_share__") {
                    AppTile(
                        label = stringResource(R.string.quick_share_title),
                        focused = focusIndex == 1,
                        tapKey = "quick_share",
                        onTap = {
                            KeySound.playTouch(context)
                            focusIndex = 1
                            onOpenQuickShare()
                        },
                        onLongPress = {
                            focusIndex = 1
                            onOpenQuickShare()
                        },
                    ) { QuickShareAppIcon(size = 54.dp) }
                }
                itemsIndexed(apps, key = { _, a -> a.packageName }) { idx, app ->
                    val gridIdx = idx + 2
                    AppTile(
                        label = app.label,
                        focused = gridIdx == focusIndex,
                        tapKey = app.packageName,
                        onTap = {
                            KeySound.playTouch(context)
                            focusIndex = gridIdx
                            launchApp(app)
                        },
                        onLongPress = {
                            focusIndex = gridIdx
                            openMenuFor(app, vibrate = true)
                        },
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(13.dp)),
                        )
                    }
                }
            }
        }

        // App 长按操作弹窗（打开 / 加入游戏库 / 应用信息 / 卸载）
        BackHandler(enabled = menuApp != null) { menuApp = null }
        menuApp?.let { app ->
            val actions = buildMenuActions(app)
            val sheetShape = RoundedCornerShape(DeckBrand.RadiusXL)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DeckColors.CardShadow)
                    .scrimTapDismiss { menuApp = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.86f).dp)
                        .cardSurface(sheetShape, elevation = 6.dp)
                        .clip(sheetShape)
                        .background(DeckColors.DialogBg)
                        .clickable(enabled = false, onClick = {})
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(11.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                app.packageName,
                                style = DeckType.metaLabel,
                                color = DeckColors.TextLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    DeckCard(elevation = 1.dp) {
                        actions.forEachIndexed { idx, (icon, label, action) ->
                            if (idx > 0) DeckDivider(startIndent = 58.dp)
                            val isDestructive = label == uninstallLabel
                            DeckRow(
                                title = label,
                                icon = icon,
                                focused = idx == menuSelection,
                                titleColor = if (isDestructive) DeckColors.Ruby else DeckColors.TextPrimary,
                                modifier = Modifier.soundClickable(onClick = action),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    DeckButton(
                        text = stringResource(R.string.menu_cancel),
                        style = DeckButtonStyle.Ghost,
                        compact = true,
                        modifier = Modifier.soundClickable { menuApp = null },
                    )
                }
            }
        }
    }
}

/** 应用网格中的单个图标磁贴：手柄焦点时显示白卡底 + 品牌渐变焦点环。 */
@Composable
private fun AppTile(
    label: String,
    focused: Boolean,
    tapKey: Any,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(DeckBrand.RadiusM)
    Column(
        Modifier
            .brandFocusRing(focused, cornerRadius = DeckBrand.RadiusM, gap = 2.dp)
            .then(if (focused) Modifier.cardSurface(shape, elevation = 3.dp) else Modifier)
            .clip(shape)
            .pointerInput(tapKey) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontFamily = DeckFontFamily,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = if (focused) DeckColors.TextPrimary else DeckColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「游戏快传」品牌图标：带闪电标签与金手指的游戏卡带 + 速度线，与 App 主图标、游戏库同一风格。 */
@Composable
fun QuickShareAppIcon(
    size: Dp = 54.dp,
    modifier: Modifier = Modifier,
) = BrandTileIcon(R.drawable.ic_tile_quick_share, size, modifier)

internal fun DrawScope.drawBackArrow(color: Color) {
    val sw = 2.dp.toPx()
    val cy = size.height / 2f
    val left = size.width * 0.25f
    val right = size.width * 0.75f
    val tip = size.width * 0.22f
    drawLine(color, Offset(right, cy), Offset(left, cy), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(left, cy), Offset(left + tip, cy - tip), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(left, cy), Offset(left + tip, cy + tip), sw, cap = StrokeCap.Round)
}

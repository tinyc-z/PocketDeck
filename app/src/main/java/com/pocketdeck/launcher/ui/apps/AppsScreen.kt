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
import com.pocketdeck.launcher.ui.library.GameLibraryAppIcon
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckFontFamily
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var menuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var menuSelection by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0)
            val seen = HashSet<String>()
            resolved.mapNotNull { info ->
                val ai = info.activityInfo?.applicationInfo
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val bmp = runCatching {
                    info.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
                }.getOrNull() ?: return@mapNotNull null
                val isSys = ai != null &&
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                InstalledApp(pkg, label, bmp, isSys)
            }.sortedBy { it.label.lowercase() }
        }
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

    fun buildMenuActions(app: InstalledApp): List<Triple<String, String, () -> Unit>> = buildList {
        add(Triple("▶", openLabel) { menuApp = null; launchApp(app) })
        if (onToggleInGameLibrary != null) {
            val inGames = isInGames(app.packageName)
            add(
                Triple(
                    if (inGames) "★" else "☆",
                    if (inGames) removeGameLabel else addGameLabel,
                ) {
                    onToggleInGameLibrary(app.packageName, !inGames)
                    menuApp = null
                },
            )
        }
        add(Triple("ⓘ", infoLabel) { menuApp = null; openAppInfo(app) })
        if (!app.isSystemApp) {
            add(Triple("🗑", uninstallLabel) { menuApp = null; uninstallApp(app) })
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(DeckColors.Background)
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
            val iconTint = DeckColors.TextPrimary
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
                        .soundClickable(onClick = onDismiss)
                        .drawBehind { drawBackArrow(iconTint) },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.apps_title),
                    fontFamily = DeckFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = iconTint,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .soundClickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        .drawBehind { drawGearIcon(iconTint) },
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(APPS_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "__game_library__") {
                    val focused = focusIndex == 0
                    val libLabel = stringResource(R.string.game_library_title)
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (focused) {
                                    Modifier
                                        .background(DeckColors.Surface)
                                        .border(1.5.dp, DeckColors.CardBorder, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .pointerInput("game_library") {
                                detectTapGestures(
                                    onTap = {
                                        KeySound.playTouch(context)
                                        focusIndex = 0
                                        onOpenGameLibrary()
                                    },
                                    onLongPress = {
                                        focusIndex = 0
                                        onOpenGameLibrary()
                                    },
                                )
                            }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GameLibraryAppIcon(size = 54.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = libLabel,
                            fontFamily = DeckFontFamily,
                            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = DeckColors.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item(key = "__quick_share__") {
                    val focused = focusIndex == 1
                    val qsLabel = stringResource(R.string.quick_share_title)
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (focused) {
                                    Modifier
                                        .background(DeckColors.Surface)
                                        .border(1.5.dp, DeckColors.CardBorder, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .pointerInput("quick_share") {
                                detectTapGestures(
                                    onTap = {
                                        KeySound.playTouch(context)
                                        focusIndex = 1
                                        onOpenQuickShare()
                                    },
                                    onLongPress = {
                                        focusIndex = 1
                                        onOpenQuickShare()
                                    },
                                )
                            }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        QuickShareAppIcon(size = 54.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = qsLabel,
                            fontFamily = DeckFontFamily,
                            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = DeckColors.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                itemsIndexed(apps, key = { _, a -> a.packageName }) { idx, app ->
                    val gridIdx = idx + 2
                    val focused = gridIdx == focusIndex
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (focused) {
                                    Modifier
                                        .background(DeckColors.Surface)
                                        .border(1.5.dp, DeckColors.CardBorder, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .pointerInput(app.packageName) {
                                detectTapGestures(
                                    onTap = {
                                        KeySound.playTouch(context)
                                        focusIndex = gridIdx
                                        launchApp(app)
                                    },
                                    onLongPress = {
                                        focusIndex = gridIdx
                                        openMenuFor(app, vibrate = true)
                                    },
                                )
                            }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = app.label,
                            fontFamily = DeckFontFamily,
                            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = DeckColors.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // App 长按操作弹窗（打开 / 加入游戏库 / 应用信息 / 卸载）
        BackHandler(enabled = menuApp != null) { menuApp = null }
        menuApp?.let { app ->
            val actions = buildMenuActions(app)
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
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.86f).dp)
                        .clip(DeckShapes.menuPanel)
                        .background(DeckColors.DialogBg)
                        .clickable(enabled = false, onClick = {})
                        .padding(24.dp),
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
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = DeckType.menuTitle,
                                color = DeckColors.TextPrimary,
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

                    Spacer(Modifier.height(18.dp))

                    actions.forEachIndexed { idx, (icon, label, action) ->
                        if (idx > 0) Spacer(Modifier.height(8.dp))
                        val isDestructive = label == uninstallLabel
                        val focused = idx == menuSelection
                        val bg = when {
                            isDestructive && focused -> DeckColors.Ruby.copy(alpha = 0.14f)
                            focused -> DeckColors.Surface
                            else -> DeckColors.Background
                        }
                        val fg = if (isDestructive) DeckColors.Ruby else DeckColors.TextPrimary
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.menuButton)
                                .background(bg)
                                .border(
                                    width = if (focused) 1.5.dp else 1.dp,
                                    color = if (focused) DeckColors.CardBorder else DeckColors.DialogBorder,
                                    shape = DeckShapes.menuButton,
                                )
                                .soundClickable(onClick = action)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(icon, style = DeckType.menuButton, color = fg)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = DeckType.menuButton, color = fg)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        stringResource(R.string.menu_cancel),
                        style = DeckType.menuButton,
                        color = DeckColors.TextSecondary,
                        modifier = Modifier
                            .soundClickable { menuApp = null }
                            .padding(vertical = 6.dp, horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** 「游戏快传」内置应用图标：翠绿渐变底 + 双向高速传输箭头。 */
@Composable
fun QuickShareAppIcon(
    size: Dp = 54.dp,
    modifier: Modifier = Modifier,
) {
    val corner = size * 0.22f
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    ) {
        val w = this.size.width
        val h = this.size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF0F766E)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.16f),
            radius = w * 0.45f,
            center = Offset(w * 0.78f, h * 0.18f),
        )
        val sw = (w * 0.08f).coerceAtLeast(2.5f)
        // 右上发送箭头
        val arrowUp = Path().apply {
            moveTo(w * 0.30f, h * 0.40f)
            lineTo(w * 0.72f, h * 0.40f)
            moveTo(w * 0.56f, h * 0.25f)
            lineTo(w * 0.72f, h * 0.40f)
            lineTo(w * 0.56f, h * 0.54f)
        }
        drawPath(arrowUp, color = Color.White, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // 左下接收箭头
        val arrowDown = Path().apply {
            moveTo(w * 0.70f, h * 0.62f)
            lineTo(w * 0.28f, h * 0.62f)
            moveTo(w * 0.44f, h * 0.48f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.44f, h * 0.76f)
        }
        drawPath(arrowDown, color = Color.White.copy(alpha = 0.88f), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

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

private fun DrawScope.drawGearIcon(color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val innerR = size.minDimension * 0.14f
    val outerR = size.minDimension * 0.27f
    val toothR = size.minDimension * 0.34f
    val sw = 2.2.dp.toPx()
    drawCircle(color, outerR, Offset(cx, cy), style = Stroke(sw))
    drawCircle(color, innerR, Offset(cx, cy), style = Stroke(sw))
    for (i in 0 until 8) {
        val a = (i * 45.0) * PI / 180.0
        val sx = cx + cos(a).toFloat() * outerR
        val sy = cy + sin(a).toFloat() * outerR
        val ex = cx + cos(a).toFloat() * toothR
        val ey = cy + sin(a).toFloat() * toothR
        drawLine(color, Offset(sx, sy), Offset(ex, ey), sw * 1.15f, cap = StrokeCap.Round)
    }
}

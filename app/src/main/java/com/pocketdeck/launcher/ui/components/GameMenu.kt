package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandFocusRow
import com.pocketdeck.launcher.ui.theme.cardSurface

private data class MenuActionItem(
    val icon: ImageVector?,
    val label: String,
    val isFavoriteToggle: Boolean = false,
    val isPrimary: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 游戏长按 / X 键菜单（Sunset Arcade）：白卡居中弹出面板，线性图标 + 品牌渐变焦点行。
 */
@Composable
fun GameMenu(
    game: Game,
    isFavorite: Boolean,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onProperties: () -> Unit,
    onLaunch: () -> Unit,
    onLaunchDisc: ((String) -> Unit)? = null,
    onGenerateM3u: (() -> Unit)? = null,
    onManualScrape: (() -> Unit)? = null,
    onDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var pickingDisc by remember(game.id) { mutableStateOf(false) }
    var selectedIdx by remember(game.id, pickingDisc) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    val chooseDiscLabel = stringResource(R.string.menu_choose_disc, game.discFiles.size)
    val generateM3uLabel = stringResource(R.string.menu_generate_m3u)
    val discLabelFmt = stringResource(R.string.disc_label_fmt)
    val backLabel = stringResource(android.R.string.cancel)

    val actions = if (pickingDisc && game.discFiles.size > 1) {
        game.discFiles.mapIndexed { idx, path ->
            MenuActionItem(
                icon = HomeIcons.Play,
                label = "$discLabelFmt ${idx + 1} · ${File(path).name}",
                isPrimary = idx == 0,
                onClick = { onLaunchDisc?.invoke(path) },
            )
        } + MenuActionItem(
            icon = null,
            label = backLabel,
            onClick = { pickingDisc = false },
        )
    } else {
        buildList {
            add(MenuActionItem(HomeIcons.Play, stringResource(R.string.menu_play), isPrimary = true, onClick = onLaunch))
            if (onDetail != null) {
                add(MenuActionItem(HomeIcons.Info, stringResource(R.string.menu_detail), onClick = onDetail))
            }
            if (game.discFiles.size > 1 && onLaunchDisc != null) {
                add(MenuActionItem(HomeIcons.Play, chooseDiscLabel, onClick = { pickingDisc = true }))
            }
            if (game.discFiles.size > 1 && !game.fileName.endsWith(".m3u", ignoreCase = true) && onGenerateM3u != null) {
                add(MenuActionItem(HomeIcons.Pencil, generateM3uLabel, onClick = onGenerateM3u))
            }
            add(
                MenuActionItem(
                    icon = null,
                    label = if (isFavorite) stringResource(R.string.menu_unfavourite) else stringResource(R.string.menu_favourite),
                    isFavoriteToggle = true,
                    onClick = onFavorite,
                ),
            )
            add(MenuActionItem(HomeIcons.Pencil, stringResource(R.string.menu_edit), onClick = onEdit))
            if (onManualScrape != null) {
                add(MenuActionItem(HomeIcons.Search, stringResource(R.string.menu_manual_scrape), onClick = onManualScrape))
            }
            add(MenuActionItem(HomeIcons.Info, stringResource(R.string.menu_properties), onClick = onProperties))
        }
    }

    LaunchedEffect(game.id) {
        focusRequester.requestFocus()
    }

    val panelShape = RoundedCornerShape(DeckBrand.RadiusXL)

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF17111F).copy(alpha = if (DeckColors.isDark) 0.62f else 0.30f))
            .scrimTapDismiss(onDismiss)
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
                        selectedIdx = (selectedIdx - 1 + actions.size) % actions.size
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        selectedIdx = (selectedIdx + 1) % actions.size
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        actions[selectedIdx].onClick()
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
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .heightIn(max = maxPanelHeight)
                .cardSurface(shape = panelShape, elevation = 16.dp)
                .border(0.8.dp, DeckColors.Line, panelShape)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                game.title,
                style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                color = DeckColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEachIndexed { idx, item ->
                    MenuButton(
                        icon = item.icon,
                        isFavoriteHeart = item.isFavoriteToggle,
                        isFavorite = isFavorite,
                        isPrimary = item.isPrimary,
                        label = item.label,
                        focused = idx == selectedIdx,
                        highlight = idx == 1 && isFavorite,
                        onClick = item.onClick,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.menu_cancel),
                style = DeckType.menuButton,
                color = DeckColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(DeckBrand.RadiusS))
                    .soundClickable(onClick = onDismiss)
                    .padding(vertical = 5.dp, horizontal = 18.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuButton(
    icon: ImageVector?,
    isFavoriteHeart: Boolean,
    isFavorite: Boolean,
    isPrimary: Boolean,
    label: String,
    focused: Boolean,
    highlight: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) bringIntoView.bringIntoView()
    }

    val shape = RoundedCornerShape(DeckBrand.RadiusM)
    val useBrandFill = isPrimary && focused
    val fg = when {
        useBrandFill -> Color.White
        highlight -> DeckColors.Ruby
        else -> DeckColors.TextPrimary
    }

    val bgModifier = when {
        useBrandFill -> Modifier.brandBackground(shape)
        focused -> Modifier.brandFocusRow(focused = true, shape = shape)
        highlight -> Modifier.background(DeckColors.AccentSoft, shape)
        else -> Modifier.background(DeckColors.SurfaceAlt, shape)
    }

    Row(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(shape)
            .then(bgModifier)
            .soundClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (isFavoriteHeart) {
            FavoriteHeartIcon(
                filled = isFavorite,
                color = fg,
                size = 18.dp,
            )
        } else if (icon != null) {
            DeckIcon(icon, size = 18.dp, tint = fg)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = DeckType.menuButton.copy(fontWeight = if (focused || isPrimary) FontWeight.SemiBold else FontWeight.Medium),
            color = fg,
        )
    }
}

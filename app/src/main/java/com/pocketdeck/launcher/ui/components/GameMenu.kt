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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType

private data class MenuActionItem(
    val iconText: String?,
    val label: String,
    val isFavoriteToggle: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 游戏长按 / X 键菜单：居中弹出面板，包含 Play / Favourite / Edit / Properties，支持滚动。
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
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var selectedIdx by remember(game.id) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    val actions = listOf(
        MenuActionItem("▶", stringResource(R.string.menu_play), onClick = onLaunch),
        MenuActionItem(
            iconText = null,
            label = if (isFavorite) stringResource(R.string.menu_unfavourite) else stringResource(R.string.menu_favourite),
            isFavoriteToggle = true,
            onClick = onFavorite,
        ),
        MenuActionItem("✎", stringResource(R.string.menu_edit), onClick = onEdit),
        MenuActionItem("ⓘ", stringResource(R.string.menu_properties), onClick = onProperties),
    )

    LaunchedEffect(game.id) {
        focusRequester.requestFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
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
                .width(320.dp)
                .heightIn(max = maxPanelHeight)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                game.title,
                style = DeckType.menuTitle,
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
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                actions.forEachIndexed { idx, item ->
                    MenuButton(
                        icon = item.iconText,
                        isFavoriteHeart = item.isFavoriteToggle,
                        isFavorite = isFavorite,
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
                    .soundClickable(onClick = onDismiss)
                    .padding(vertical = 4.dp, horizontal = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuButton(
    icon: String?,
    isFavoriteHeart: Boolean,
    isFavorite: Boolean,
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

    val bg = when {
        highlight -> DeckColors.Ruby.copy(alpha = 0.12f)
        focused -> DeckColors.Surface
        else -> DeckColors.Background
    }
    val fg = if (highlight) DeckColors.Ruby else DeckColors.TextPrimary

    Row(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(DeckShapes.menuButton)
            .background(bg)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) DeckColors.CardBorder else DeckColors.DialogBorder,
                shape = DeckShapes.menuButton,
            )
            .soundClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (isFavoriteHeart) {
            FavoriteHeartIcon(
                filled = isFavorite,
                color = fg,
                size = 17.dp,
            )
        } else {
            Text(icon.orEmpty(), style = DeckType.menuButton, color = fg)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = DeckType.menuButton, color = fg)
    }
}

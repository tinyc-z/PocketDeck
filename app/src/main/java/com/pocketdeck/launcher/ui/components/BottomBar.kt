package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType

/**
 * 底栏（Sunset Arcade）：
 * (B) Apps  (Y) Settings  (X) Options  [视图图标] View  [搜索]  ...  (A 渐变) Play
 */
@Composable
fun BottomBar(
    viewMode: ViewMode = ViewMode.Gallery,
    searching: Boolean = false,
    onApps: () -> Unit = {},
    onSettings: () -> Unit = {},
    onOptions: (() -> Unit)? = null,
    onViewSwitch: () -> Unit = {},
    onSearch: () -> Unit = {},
    onPlay: () -> Unit = {},
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val playBtn = GamepadKeys.badgeLabel(keyBindings, KeyAction.Play, swapAbxy)
    val appsBtn = GamepadKeys.badgeLabel(keyBindings, KeyAction.Apps, swapAbxy)
    val settingsBtn = GamepadKeys.badgeLabel(keyBindings, KeyAction.Settings, swapAbxy)
    val menuBtn = GamepadKeys.badgeLabel(keyBindings, KeyAction.Menu, swapAbxy)

    val ink = DeckColors.TextPrimary
    val accent = DeckColors.Ruby

    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // (B) Apps
        BarButton(badge = appsBtn, label = stringResource(R.string.apps), onClick = onApps)

        Spacer(Modifier.width(2.dp))

        // (Y) Settings
        BarButton(badge = settingsBtn, label = stringResource(R.string.settings), onClick = onSettings)

        if (onOptions != null) {
            Spacer(Modifier.width(2.dp))
            // (X) Options
            BarButton(badge = menuBtn, label = stringResource(R.string.hint_options), onClick = onOptions)
        }

        Spacer(Modifier.width(2.dp))

        // 视图切换（图标 + View 文案）
        BarChipButton(onClick = onViewSwitch) {
            Box(
                Modifier
                    .size(18.dp)
                    .drawBehind { drawViewModeIcon(viewMode, ink) },
            )
            Text(
                stringResource(R.string.hint_view),
                style = DeckType.bottomBar.copy(fontWeight = FontWeight.Medium, fontSize = 13.5.sp),
                color = ink,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(2.dp))

        // 搜索图标（搜索时切换为强调色关闭图标）
        BarIconButton(onClick = onSearch) {
            DeckIcon(
                icon = if (searching) HomeIcons.Close else HomeIcons.Search,
                size = 19.dp,
                tint = if (searching) accent else ink,
            )
        }

        Spacer(Modifier.weight(1f))

        // (A) Play —— 主操作使用品牌渐变徽标
        BarButton(
            badge = playBtn,
            label = stringResource(R.string.play),
            primary = true,
            onClick = onPlay,
        )
    }
}

@Composable
private fun BarButton(
    badge: String?,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .clip(CircleShape)
            .background(if (pressed) DeckColors.TextPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (badge != null) {
            ButtonBadge(badge, primary = primary, size = 22.dp)
        }
        Text(
            label,
            style = DeckType.bottomBar.copy(
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.5.sp,
            ),
            color = DeckColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun BarChipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .clip(CircleShape)
            .background(if (pressed) DeckColors.TextPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        content()
    }
}

@Composable
private fun BarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .clip(CircleShape)
            .background(if (pressed) DeckColors.TextPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

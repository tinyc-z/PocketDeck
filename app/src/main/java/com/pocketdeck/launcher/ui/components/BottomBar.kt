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
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType

/**
 * 底栏（Sunset Arcade）：
 * (B) Apps  (Y) Settings  (视图图标) View  (🔍) Search  ...  (A 渐变) Play
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

    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // (B) Apps
        BarButton(badge = appsBtn, label = stringResource(R.string.apps), onClick = onApps)

        Spacer(Modifier.width(6.dp))

        // (Y) Settings
        BarButton(badge = settingsBtn, label = stringResource(R.string.settings), onClick = onSettings)

        Spacer(Modifier.width(6.dp))

        // 视图切换 —— 使用当前桌面模式的形象图标（不绑快捷键字母）
        BarCustomIconLabeledButton(
            label = stringResource(R.string.hint_view),
            onClick = onViewSwitch,
        ) { iconTint ->
            Box(
                Modifier
                    .size(15.dp)
                    .drawBehind { drawViewModeIcon(viewMode, iconTint) },
            )
        }

        Spacer(Modifier.width(6.dp))

        // (🔍) Search —— 深色圆形徽标，搜索激活时高亮为品牌渐变关闭徽标
        BarIconLabeledButton(
            icon = if (searching) HomeIcons.Close else HomeIcons.Search,
            label = stringResource(R.string.lib_search_hint),
            primary = searching,
            onClick = onSearch,
        )

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
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (badge != null) {
            ButtonBadge(badge, primary = primary, size = 24.dp)
        }
        Text(
            label,
            style = DeckType.bottomBar.copy(
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 14.sp,
            ),
            color = DeckColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun BarIconLabeledButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BarCustomIconLabeledButton(
        label = label,
        onClick = onClick,
        primary = primary,
        modifier = modifier,
    ) { iconTint ->
        DeckIcon(icon = icon, size = 14.dp, tint = iconTint)
    }
}

@Composable
private fun BarCustomIconLabeledButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    iconContent: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val badgeBg = if (primary) Modifier.background(DeckBrand.brandBrush) else Modifier.background(DeckColors.ButtonBadge)
    val iconTint = if (primary) Color.White else DeckColors.ButtonBadgeText
    Row(
        modifier
            .clip(CircleShape)
            .background(if (pressed) DeckColors.TextPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            iconContent(iconTint)
        }
        Text(
            label,
            style = DeckType.bottomBar.copy(
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 14.sp,
            ),
            color = DeckColors.TextPrimary,
            maxLines = 1,
        )
    }
}

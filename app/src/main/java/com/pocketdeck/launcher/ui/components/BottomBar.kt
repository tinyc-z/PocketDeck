package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import androidx.compose.ui.res.stringResource

/**
 * 底栏：(B) Apps  (Y) Settings · 视图图标 搜索图标 ... (A) Play
 */
@Composable
fun BottomBar(
    viewMode: ViewMode = ViewMode.Gallery,
    searching: Boolean = false,
    onApps: () -> Unit = {},
    onSettings: () -> Unit = {},
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
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // (B) Apps
        BarButton(badge = appsBtn, label = stringResource(R.string.apps), onClick = onApps)

        Spacer(Modifier.width(4.dp))

        // (Y) Settings
        BarButton(badge = settingsBtn, label = stringResource(R.string.settings), onClick = onSettings)

        Spacer(Modifier.width(4.dp))
        Text("·", style = DeckType.bottomBar, color = DeckColors.TextSecondary)
        Spacer(Modifier.width(4.dp))

        val primaryTint = DeckColors.TextPrimary
        val rubyTint = DeckColors.Ruby

        // 当前视图图标（点击弹出视图切换菜单）
        BarIconButton(onClick = onViewSwitch) {
            Box(
                Modifier
                    .size(22.dp)
                    .drawBehind { drawViewModeIcon(viewMode, primaryTint) },
            )
        }

        Spacer(Modifier.width(4.dp))

        // 搜索图标（搜索时高亮为 Ruby 色关闭图标）
        BarIconButton(onClick = onSearch) {
            Box(
                Modifier
                    .size(22.dp)
                    .drawBehind {
                        if (searching) {
                            drawCloseIcon(rubyTint)
                        } else {
                            drawSearchIcon(primaryTint)
                        }
                    },
            )
        }

        Spacer(Modifier.weight(1f))

        // (A) Play
        BarButton(badge = playBtn, label = stringResource(R.string.play), onClick = onPlay)
    }
}

/** 搜索图标：圆圈 + 右下斜线把手 */
private fun DrawScope.drawSearchIcon(color: Color) {
    val sw = 2f.dp.toPx()
    val cx = size.width * 0.42f
    val cy = size.height * 0.42f
    val r = size.width * 0.26f

    drawCircle(color, r, Offset(cx, cy), style = Stroke(sw))
    val handleStart = Offset(cx + r * 0.7f, cy + r * 0.7f)
    val handleEnd = Offset(size.width - 4.dp.toPx(), size.height - 4.dp.toPx())
    drawLine(color, handleStart, handleEnd, sw, cap = StrokeCap.Round)
}

/** 关闭图标 */
private fun DrawScope.drawCloseIcon(color: Color) {
    val sw = 2f.dp.toPx()
    val pad = 5.dp.toPx()
    drawLine(color, Offset(pad, pad), Offset(size.width - pad, size.height - pad), sw, cap = StrokeCap.Round)
    drawLine(color, Offset(size.width - pad, pad), Offset(pad, size.height - pad), sw, cap = StrokeCap.Round)
}

@Composable
private fun BarButton(
    badge: String?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .clip(DeckShapes.tabPill)
            .background(if (pressed) DeckColors.TextPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (badge != null) {
            ButtonBadge(badge)
        }
        Text(label, style = DeckType.bottomBar, color = DeckColors.TextPrimary)
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
            .clip(DeckShapes.tabPill)
            .background(if (pressed) DeckColors.TextPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

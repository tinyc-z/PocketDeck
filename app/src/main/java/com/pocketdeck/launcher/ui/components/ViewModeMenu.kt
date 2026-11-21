package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandFocusRow
import com.pocketdeck.launcher.ui.theme.cardSurface

/** 返回视图模式的本地化名称。 */
@Composable
fun viewModeLabel(mode: ViewMode): String = when (mode) {
    ViewMode.Gallery -> stringResource(R.string.view_gallery)
    ViewMode.List -> stringResource(R.string.view_list)
    ViewMode.Grid -> stringResource(R.string.view_grid)
    ViewMode.Bubbles -> stringResource(R.string.view_bubbles)
}

/**
 * 视图切换弹出菜单（Sunset Arcade）：白卡 + 渐变焦点行 + 本地化名称 + 勾选图标。
 */
@Composable
fun ViewModeMenu(
    currentMode: ViewMode,
    highlightedIndex: Int,
    onSelect: (ViewMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(DeckBrand.RadiusL)
    val rowShape = RoundedCornerShape(DeckBrand.RadiusS)

    Box(
        modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 156.dp, bottom = 52.dp)
                .width(172.dp)
                .cardSurface(shape = cardShape, elevation = 12.dp)
                .border(0.8.dp, DeckColors.Line, cardShape)
                .clickable(enabled = false, onClick = {})
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ViewMode.entries.forEachIndexed { idx, mode ->
                val focused = idx == highlightedIndex
                val active = mode == currentMode
                val tint = if (focused || active) DeckColors.Ruby else DeckColors.TextPrimary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .then(
                            when {
                                focused -> Modifier.brandFocusRow(focused = true, shape = rowShape)
                                active -> Modifier.background(DeckColors.AccentSoft, rowShape)
                                else -> Modifier
                            },
                        )
                        .soundClickable { onSelect(mode) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Box(
                        Modifier
                            .size(19.dp)
                            .drawBehind { drawViewModeIcon(mode, tint) },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = viewModeLabel(mode),
                        style = if (focused || active) {
                            DeckType.listItemSelected.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            DeckType.listItem
                        },
                        color = tint,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) {
                        DeckIcon(HomeIcons.Check, size = 16.dp, tint = DeckColors.Ruby)
                    }
                }
            }
        }
    }
}

/** 统一绘制 4 种视图模式图标（BottomBar 和 ViewModeMenu 共用）。 */
fun DrawScope.drawViewModeIcon(mode: ViewMode, color: Color) {
    when (mode) {
        ViewMode.Gallery -> drawGalleryIcon(color)
        ViewMode.List -> drawListIcon(color)
        ViewMode.Grid -> drawGridIcon(color)
        ViewMode.Bubbles -> drawBubblesIcon(color)
    }
}

private fun DrawScope.drawGalleryIcon(color: Color) {
    val pad = size.width * 0.14f
    val h = size.height - pad * 2
    val bigW = size.width * 0.44f
    val r = CornerRadius(2.dp.toPx())
    drawRoundRect(color, Offset(pad, pad), Size(bigW, h), r)
    val barW = size.width * 0.10f
    val barH = h * 0.78f
    val barY = pad + (h - barH) / 2f
    val x1 = pad + bigW + size.width * 0.07f
    val x2 = x1 + barW + size.width * 0.06f
    drawRoundRect(color, Offset(x1, barY), Size(barW, barH), CornerRadius(1.dp.toPx()))
    drawRoundRect(color, Offset(x2, barY), Size(barW, barH), CornerRadius(1.dp.toPx()))
}

private fun DrawScope.drawListIcon(color: Color) {
    val pad = size.width * 0.16f
    val dotR = size.width * 0.06f
    val sw = 2.dp.toPx()
    val lineStart = pad + dotR * 2 + size.width * 0.10f
    val lineEnd = size.width - pad
    for (i in 0..2) {
        val y = size.height * (0.28f + i * 0.22f)
        drawCircle(color, dotR, Offset(pad + dotR, y))
        drawLine(color, Offset(lineStart, y), Offset(lineEnd, y), sw, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawGridIcon(color: Color) {
    val pad = size.width * 0.17f
    val gap = size.width * 0.12f
    val w = (size.width - pad * 2 - gap) / 2f
    val h = (size.height - pad * 2 - gap) / 2f
    val r = CornerRadius(1.5.dp.toPx())
    drawRoundRect(color, Offset(pad, pad), Size(w, h), r)
    drawRoundRect(color, Offset(pad + w + gap, pad), Size(w, h), r)
    drawRoundRect(color, Offset(pad, pad + h + gap), Size(w, h), r)
    drawRoundRect(color, Offset(pad + w + gap, pad + h + gap), Size(w, h), r)
}

private fun DrawScope.drawBubblesIcon(color: Color) {
    val r = size.width * 0.15f
    drawCircle(color, r, Offset(size.width * 0.50f, size.height * 0.28f))
    drawCircle(color, r, Offset(size.width * 0.29f, size.height * 0.67f))
    drawCircle(color, r, Offset(size.width * 0.71f, size.height * 0.67f))
}

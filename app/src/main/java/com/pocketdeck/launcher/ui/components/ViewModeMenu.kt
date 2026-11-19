package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType

/**
 * 视图切换弹出菜单：Gallery / List / Grid / Bubbles，1:1 复刻 Beacon。
 */
@Composable
fun ViewModeMenu(
    currentMode: ViewMode,
    highlightedIndex: Int,
    onSelect: (ViewMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 152.dp, bottom = 52.dp)
                .width(156.dp)
                .shadow(10.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(vertical = 6.dp),
        ) {
            ViewMode.entries.forEachIndexed { idx, mode ->
                val selected = idx == highlightedIndex
                val active = mode == currentMode
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                selected -> DeckColors.Ruby.copy(alpha = 0.14f)
                                active -> DeckColors.TextPrimary.copy(alpha = 0.06f)
                                else -> Color.Transparent
                            },
                        )
                        .soundClickable { onSelect(mode) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    val tint = if (selected || active) DeckColors.Ruby else DeckColors.TextPrimary
                    Box(
                        Modifier
                            .size(20.dp)
                            .drawBehind { drawViewModeIcon(mode, tint) },
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = mode.name,
                        style = if (selected || active) DeckType.listItemSelected else DeckType.listItem,
                        color = tint,
                    )
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
    // 主大卡
    drawRoundRect(color, Offset(pad, pad), Size(bigW, h), r)
    // 右侧两张叠卡窄条
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

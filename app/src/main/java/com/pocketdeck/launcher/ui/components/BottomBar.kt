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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.NavBarStyle
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion

/**
 * 底部操作栏：
 * - 统一采用清爽线性图标（四宫格应用、八齿齿轮设置、画廊视图、搜索）+ 品牌渐变「▶ 开始」胶囊；
 * - [NavBarStyle.Classic]（默认）：透明无底全宽分布；
 * - [NavBarStyle.GlassDock]（悬浮磨砂岛）：居中悬浮半透明毛玻璃长胶囊，右端嵌套「▶ 开始」渐变胶囊，自动适配浅/深色与主题色。
 */
@Composable
fun BottomBar(
    navBarStyle: NavBarStyle = NavBarStyle.Classic,
    searching: Boolean = false,
    onApps: () -> Unit = {},
    onSettings: () -> Unit = {},
    onViewSwitch: () -> Unit = {},
    onSearch: () -> Unit = {},
    onPlay: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isGlassDock = navBarStyle == NavBarStyle.GlassDock
    val palette = LocalDeckColors.current
    val reduceMotion = LocalReduceMotion.current

    val appsLabel = stringResource(R.string.apps)
    val settingsLabel = stringResource(R.string.settings)
    val viewLabel = stringResource(R.string.hint_view)
    val searchLabel = stringResource(R.string.lib_search_hint)
    val playLabel = stringResource(R.string.play)

    val density = LocalDensity.current
    val fallbackWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var measuredWidthDp by remember(fallbackWidthDp) { mutableStateOf(fallbackWidthDp) }

    Box(
        modifier
            .fillMaxWidth()
            .height(if (isGlassDock) 56.dp else 48.dp)
            .padding(
                horizontal = if (isGlassDock) 12.dp else 14.dp,
                vertical = if (isGlassDock) 4.dp else 0.dp,
            )
            .onSizeChanged { sz ->
                if (sz.width > 0) {
                    val w = with(density) { sz.width.toDp() }
                    if (w != measuredWidthDp) measuredWidthDp = w
                }
            },
        contentAlignment = if (isGlassDock) Alignment.Center else Alignment.CenterStart,
    ) {
        // 窄屏（如 1:1 方屏、8:7、4:3 640x480、竖屏）下 View 与 Search 仅保留图标，确保右侧 Play 完整显示
        val showSecondaryLabels = measuredWidthDp >= 460.dp

        if (isGlassDock) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .glassDockCapsule(
                        isDark = palette.isDark,
                        surfaceColor = palette.surface,
                        backgroundColor = palette.background,
                        reduceMotion = reduceMotion,
                    )
                    .padding(start = 10.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (showSecondaryLabels) 2.dp else 0.dp),
            ) {
                BarOutlineIconButton(
                    icon = HomeIcons.Apps,
                    label = appsLabel,
                    onClick = onApps,
                )
                BarOutlineIconButton(
                    icon = HomeIcons.Settings,
                    label = settingsLabel,
                    onClick = onSettings,
                )
                BarOutlineIconButton(
                    icon = HomeIcons.ViewCarousel,
                    label = viewLabel,
                    showLabel = showSecondaryLabels,
                    onClick = onViewSwitch,
                )
                BarOutlineIconButton(
                    icon = if (searching) HomeIcons.Close else HomeIcons.Search,
                    label = searchLabel,
                    showLabel = showSecondaryLabels,
                    active = searching,
                    onClick = onSearch,
                )
                Spacer(Modifier.width(6.dp))
                BarPlayPillButton(
                    label = playLabel,
                    onClick = onPlay,
                    reduceMotion = reduceMotion,
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarOutlineIconButton(
                    icon = HomeIcons.Apps,
                    label = appsLabel,
                    onClick = onApps,
                )
                Spacer(Modifier.width(4.dp))
                BarOutlineIconButton(
                    icon = HomeIcons.Settings,
                    label = settingsLabel,
                    onClick = onSettings,
                )
                Spacer(Modifier.width(4.dp))
                BarOutlineIconButton(
                    icon = HomeIcons.ViewCarousel,
                    label = viewLabel,
                    showLabel = showSecondaryLabels,
                    onClick = onViewSwitch,
                )
                Spacer(Modifier.width(4.dp))
                BarOutlineIconButton(
                    icon = if (searching) HomeIcons.Close else HomeIcons.Search,
                    label = searchLabel,
                    showLabel = showSecondaryLabels,
                    active = searching,
                    onClick = onSearch,
                )
                Spacer(Modifier.weight(1f))
                BarPlayPillButton(
                    label = playLabel,
                    onClick = onPlay,
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}

@Composable
private fun BarOutlineIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    showLabel: Boolean = true,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (active) DeckColors.Ruby else DeckColors.TextPrimary
    val bgColor = when {
        pressed -> DeckColors.TextPrimary.copy(alpha = 0.10f)
        active -> DeckColors.AccentSoft
        else -> Color.Transparent
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(bgColor)
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = if (showLabel) 10.dp else 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (showLabel) 6.dp else 0.dp),
    ) {
        DeckIcon(icon = icon, size = 18.dp, tint = tint)
        if (showLabel) {
            Text(
                label,
                style = DeckType.bottomBar.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 14.sp,
                ),
                color = tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BarPlayPillButton(
    label: String,
    onClick: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDeckColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val brandStart = palette.brandStart
    val brandMid = palette.brandMid
    val brandEnd = palette.brandEnd
    val glowColor = palette.brandEnd

    Box(
        modifier
            .drawBehind {
                if (!reduceMotion && size.width > 0f && size.height > 0f) {
                    val r = size.height / 2f
                    val yOff = 2.dp.toPx()
                    for (step in 3 downTo 1) {
                        val spread = step * 2.2.dp.toPx()
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.11f * (4 - step) / 3f),
                            topLeft = Offset(-spread * 0.4f, yOff - spread * 0.25f),
                            size = Size(size.width + spread * 0.8f, size.height + spread * 0.7f),
                            cornerRadius = CornerRadius(r + spread * 0.35f, r + spread * 0.35f),
                        )
                    }
                }
            }
            .height(34.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(brandStart, brandMid, brandEnd),
                ),
            )
            .then(
                if (pressed) Modifier.background(Color.Black.copy(alpha = 0.14f)) else Modifier,
            )
            .soundClickableNoIndication(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            DeckIcon(icon = HomeIcons.Play, size = 14.dp, tint = Color.White)
            Text(
                label,
                style = DeckType.bottomBar.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

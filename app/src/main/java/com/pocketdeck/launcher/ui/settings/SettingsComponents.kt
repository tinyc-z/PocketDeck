package com.pocketdeck.launcher.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pocketdeck.launcher.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.data.ThemeAccent
import com.pocketdeck.launcher.ui.components.BackgroundPreview
import com.pocketdeck.launcher.ui.components.ButtonBadge
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.brandFocusRing
import com.pocketdeck.launcher.ui.theme.cardSurface

/*
 * Sunset Arcade 设置页视觉组件：圆形返回键 + 大标题、左侧图标导航、分组白卡、
 * 渐变开关、主题色圆点与背景缩略图。
 */

internal val SettingsRowMinHeight = 44.dp
private val GroupShape = RoundedCornerShape(DeckBrand.RadiusM)
private val RowShape = RoundedCornerShape(DeckBrand.RadiusS)

// ════════════════════════════════════════════════════════════════
// 顶栏
// ════════════════════════════════════════════════════════════════

@Composable
internal fun SettingsHeader(
    title: String,
    switchHint: String,
    prevKey: String,
    nextKey: String,
    scrapeRunning: Boolean = false,
    scrapePercent: Int = 0,
    onScrapeBadgeClick: () -> Unit = {},
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .cardSurface(CircleShape, elevation = 3.dp)
                .clip(CircleShape)
                .soundClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            DeckIcon(DeckIcons.ChevronLeft, tint = DeckColors.TextPrimary, size = 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = DeckType.settingsSection.copy(fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, lineHeight = 28.sp),
            color = DeckColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (scrapeRunning) {
            com.pocketdeck.launcher.ui.components.ScrapeStatusPill(
                percent = scrapePercent,
                onClick = onScrapeBadgeClick,
            )
            Spacer(Modifier.width(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ButtonBadge(prevKey)
            Spacer(Modifier.width(5.dp))
            ButtonBadge(nextKey)
            Spacer(Modifier.width(7.dp))
            Text(
                switchHint,
                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = DeckColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 左侧导航
// ════════════════════════════════════════════════════════════════

internal class RailItem(val icon: ImageVector, val label: String)

@Composable
internal fun SettingsRail(
    items: List<RailItem>,
    selected: Int,
    focused: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            val shape = RoundedCornerShape(DeckBrand.RadiusM)
            Row(
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewWhen(isSelected)
                    .height(42.dp)
                    .then(
                        if (isSelected) {
                            Modifier
                                .brandFocusRing(focused, cornerRadius = DeckBrand.RadiusM, gap = 0.dp, glow = focused)
                                .cardSurface(shape, elevation = if (focused) 0.dp else 2.dp)
                        } else Modifier,
                    )
                    .clip(shape)
                    .soundClickable { onSelect(index) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) Modifier.background(DeckBrand.brandBrush)
                            else Modifier.background(DeckColors.SurfaceAlt),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DeckIcon(
                        item.icon,
                        tint = if (isSelected) Color.White else DeckColors.TextSecondary,
                        size = 16.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    style = DeckType.settingsNav.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = if (isSelected) DeckColors.TextPrimary else DeckColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 分组卡片
// ════════════════════════════════════════════════════════════════

@Composable
internal fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp),
        color = DeckColors.TextLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 6.dp),
    )
}

@Composable
internal fun GroupFooter(text: String) {
    Text(
        text,
        style = DeckType.settingsLabel,
        color = DeckColors.TextLabel,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
    )
}

@Composable
internal fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .cardSurface(GroupShape, elevation = 2.dp)
            .padding(4.dp),
        content = content,
    )
}

@Composable
internal fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(DeckColors.Line),
    )
}

/**
 * 通用设置行：约 44dp 高，左侧可选图标/拖拽手柄，右侧为值、开关或其他附件。
 * 手柄焦点：柔和强调底色 + 品牌渐变细描边。
 */
@Composable
internal fun SettingRow(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    description: String? = null,
    dimmed: Boolean = false,
    accentLabel: Boolean = false,
    labelMaxLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val p = LocalDeckColors.current
    val highlightBrush = remember(p.brandStart, p.brandMid, p.brandEnd) {
        Brush.linearGradient(listOf(p.brandStart, p.brandMid, p.brandEnd))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewWhen(highlighted)
            .heightIn(min = SettingsRowMinHeight)
            .clip(RowShape)
            .then(
                if (highlighted) {
                    Modifier
                        .background(p.accentSoft, RowShape)
                        .border(1.5.dp, highlightBrush, RowShape)
                } else Modifier,
            )
            .soundClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = DeckType.settingsValue.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                color = when {
                    accentLabel -> DeckColors.Ruby
                    dimmed -> DeckColors.TextSecondary
                    else -> DeckColors.TextPrimary
                },
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrEmpty()) {
                Text(
                    description,
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** 值文本 + 右箭头。 */
@Composable
internal fun ValueChevron(value: String?, chevron: Boolean = true, valueColor: Color = DeckColors.TextSecondary) {
    if (!value.isNullOrEmpty()) {
        Text(
            value,
            style = DeckType.settingsLabel.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
        )
    }
    if (chevron) {
        Spacer(Modifier.width(4.dp))
        DeckIcon(DeckIcons.ChevronRight, tint = DeckColors.TextLabel, size = 15.dp)
    }
}

/** 小号胶囊标签（数量、状态等）。 */
@Composable
internal fun ValuePill(text: String, accent: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
        color = if (accent) DeckColors.Ruby else DeckColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) DeckColors.AccentSoft else DeckColors.SurfaceAlt)
            .then(if (onClick != null) Modifier.soundClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** 勾选标记：选中为渐变圆底白勾，未选中为浅色空心圆。 */
@Composable
internal fun CheckMark(checked: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(DeckBrand.brandBrush)
                else Modifier.border(1.5.dp, DeckColors.Line, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) DeckIcon(DeckIcons.Check, tint = Color.White, size = 13.dp)
    }
}

/** 渐变开关：开启为品牌渐变轨道，关闭为 Line 色轨道，白色圆形滑块（去硬件 shadow，减少动态效果时瞬切）。 */
@Composable
internal fun DeckSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val targetX = if (checked) 18.dp else 2.dp
    val thumbX = if (reduceMotion) targetX else animateDpAsState(targetX, tween(140), label = "switch").value
    val trackShape = RoundedCornerShape(50)
    Box(
        modifier
            .width(42.dp)
            .height(24.dp)
            .clip(trackShape)
            .then(
                if (checked) Modifier.background(DeckBrand.brandBrush)
                else Modifier.background(DeckColors.Line),
            ),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 2.dp)
                .size(20.dp)
                .then(
                    if (reduceMotion) Modifier
                    else Modifier.drawBehind {
                        drawCircle(
                            color = Color(0x24000000),
                            radius = size.minDimension / 2f,
                            center = Offset(size.width / 2f, size.height / 2f + 1.dp.toPx()),
                        )
                    },
                )
                .background(Color.White, CircleShape),
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 主题色圆点
// ════════════════════════════════════════════════════════════════

/**
 * 8 个渐变主题色圆点。[cursor] 非空时表示正在用手柄挑选（左右移动），
 * 光标圆点显示品牌焦点环；当前选中的主题显示墨色外环。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentDots(
    selected: ThemeAccent,
    cursor: Int?,
    onPick: (ThemeAccent) -> Unit,
) {
    val ink = DeckColors.TextPrimary
    val gap = DeckColors.Surface
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 460
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = if (compact) 4 else ThemeAccent.entries.size,
    ) {
        ThemeAccent.entries.forEachIndexed { idx, acc ->
            val isSel = acc == selected
            val isCursor = cursor == idx
            val brush = remember(acc) {
                Brush.linearGradient(listOf(Color(acc.gradStart), Color(acc.gradEnd)))
            }
            Box(
                Modifier
                    .size(19.dp)
                    .brandFocusRing(isCursor, cornerRadius = 10.dp, width = 2.dp, gap = 4.dp, glow = false)
                    .drawBehind {
                        if (isSel) {
                            val r = size.minDimension / 2f
                            drawCircle(gap, radius = r + 1.7.dp.toPx())
                            drawCircle(
                                ink,
                                radius = r + 2.6.dp.toPx(),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.7.dp.toPx()),
                            )
                        }
                    }
                    .clip(CircleShape)
                    .background(brush)
                    .soundClickable { onPick(acc) },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 背景缩略图
// ════════════════════════════════════════════════════════════════

@Composable
internal fun BackgroundThumbs(
    selected: BackgroundStyle,
    cursor: Int?,
    label: @Composable (BackgroundStyle) -> String,
    onPick: (BackgroundStyle) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackgroundStyle.entries.forEachIndexed { idx, style ->
            val isSel = style == selected
            val ringOn = if (cursor != null) cursor == idx else isSel
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier
                    .bringIntoViewWhen(ringOn)
                    .padding(4.dp)
                    .width(78.dp)
                    .height(54.dp)
                    .brandFocusRing(ringOn, cornerRadius = 9.dp, width = 2.dp, gap = 2.5.dp, glow = cursor != null)
                    .clip(shape)
                    .soundClickable { onPick(style) },
            ) {
                SettingsBackgroundPreview(style, Modifier.fillMaxSize())
                val light = !DeckColors.isDark
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    if (light) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.60f),
                                ),
                            ),
                        ),
                )
                Text(
                    label(style),
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp),
                    color = if (light) Color(0xFF24172F) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                if (isSel) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(14.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        DeckIcon(DeckIcons.Check, tint = DeckColors.BrandEnd, size = 10.dp)
                    }
                }
            }
        }
    }
}

/** 设置页背景缩略图：直接调用 WaveBackground.kt 的真实 BackgroundPreview。 */
@Composable
internal fun SettingsBackgroundPreview(style: BackgroundStyle, modifier: Modifier = Modifier) {
    BackgroundPreview(style, modifier)
}

// ════════════════════════════════════════════════════════════════
// 设置页静态柔光底（不随帧重绘）
// ════════════════════════════════════════════════════════════════

@Composable
internal fun Modifier.settingsBackdrop(): Modifier {
    val p = LocalDeckColors.current
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val a = Brush.radialGradient(listOf(p.flowA, Color.Transparent), center = Offset(w * 0.05f, h * 0.1f), radius = w * 0.55f)
        val b = Brush.radialGradient(listOf(p.flowB, Color.Transparent), center = Offset(w * 0.95f, h * 0.25f), radius = w * 0.5f)
        val c = Brush.radialGradient(listOf(p.flowC, Color.Transparent), center = Offset(w * 0.4f, h * 1.05f), radius = w * 0.5f)
        onDrawBehind {
            drawRect(p.background)
            drawRect(a)
            drawRect(b)
            drawRect(c)
        }
    }
}

/** 高亮时自动滚动到可见区域（手柄上下切换时内容不跑出屏幕）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.bringIntoViewWhen(active: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) requester.bringIntoView() }
    return this.bringIntoViewRequester(requester)
}

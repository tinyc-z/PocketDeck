package com.pocketdeck.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandFocusRing
import com.pocketdeck.launcher.ui.theme.brandFocusRow
import com.pocketdeck.launcher.ui.theme.cardSurface

/**
 * Sunset Arcade 共享控件：开关、按钮、卡片、分组标题、进度条、胶囊标签等。
 *
 * 这些控件只负责“外观”；点击 / 手柄焦点逻辑仍由调用方通过 [Modifier]
 * （`dpadNavItem` / `gamepadClickable` / `soundClickable`）传入，保持原有焦点链不变。
 */

val DeckPillShape = RoundedCornerShape(50)

// ───────────────────────── 开关 ─────────────────────────

/**
 * 品牌开关：开启时渐变轨道 + 白色圆钮，关闭时 SurfaceAlt 轨道。
 * [onCheckedChange] 为 null 时仅作展示（由整行负责点击）。
 */
@Composable
fun DeckSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    focused: Boolean = false,
) {
    val p = LocalDeckColors.current
    val context = LocalContext.current
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "deckSwitch")
    val trackW = 42.dp
    val trackH = 24.dp
    val thumb = 18.dp
    val clickMod = if (onCheckedChange != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            role = Role.Switch,
        ) {
            KeySound.playTouch(context)
            onCheckedChange(!checked)
        }
    } else Modifier
    Box(
        modifier
            .brandFocusRing(focused, cornerRadius = trackH / 2, gap = 2.dp, width = 2.dp, glow = false)
            .size(trackW, trackH)
            .then(clickMod)
            .drawWithCache {
                val r = CornerRadius(size.height / 2f)
                val brush = Brush.linearGradient(
                    listOf(p.brandStart, p.brandMid, p.brandEnd),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val offColor = if (p.isDark) Color(0xFF3A2D48) else Color(0xFFEBDDD8)
                val alpha = if (enabled) 1f else 0.45f
                onDrawBehind {
                    drawRoundRect(color = offColor, cornerRadius = r, alpha = alpha)
                    if (t > 0f) drawRoundRect(brush = brush, cornerRadius = r, alpha = t * alpha)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = trackW - thumb - 6.dp
        Box(
            Modifier
                .offset(x = 3.dp + travel * t)
                .size(thumb)
                .shadow(2.dp, CircleShape, clip = false)
                .background(Color.White, CircleShape),
        )
    }
}

// ───────────────────────── 复选 / 单选 ─────────────────────────

/** 圆角方形复选框：选中为渐变底 + 白勾。 */
@Composable
fun DeckCheckbox(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .then(
                if (checked) Modifier.brandBackground(shape)
                else Modifier
                    .background(DeckColors.Surface, shape)
                    .border(1.5.dp, DeckColors.TextLabel.copy(alpha = 0.6f), shape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) DeckIcon(PanelIcons.Check, tint = Color.White, size = size * 0.7f)
    }
}

/** 圆形单选：选中为渐变外圈 + 白点。 */
@Composable
fun DeckRadio(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.brandBackground(CircleShape)
                else Modifier
                    .background(DeckColors.Surface, CircleShape)
                    .border(1.5.dp, DeckColors.TextLabel.copy(alpha = 0.6f), CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(size * 0.38f).background(Color.White, CircleShape))
    }
}

// ───────────────────────── 按钮 ─────────────────────────

enum class DeckButtonStyle { Primary, Secondary, Danger, Ghost }

/**
 * 胶囊按钮。
 * - Primary：品牌渐变 + 白色粗体
 * - Secondary：SurfaceAlt 底 + 主文字色
 * - Danger：AccentSoft 底 + 强调色文字
 * - Ghost：透明底 + 次要文字色
 *
 * 点击由 [modifier] 提供（dpadNavItem / gamepadClickable / soundClickable）。
 * [focused] 用于未使用 dpadNavItem 的场景，额外绘制渐变焦点环。
 */
@Composable
fun DeckButton(
    text: String,
    modifier: Modifier = Modifier,
    style: DeckButtonStyle = DeckButtonStyle.Primary,
    icon: ImageVector? = null,
    focused: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    textStyle: TextStyle? = null,
) {
    val fg = when (style) {
        DeckButtonStyle.Primary -> Color.White
        DeckButtonStyle.Secondary -> DeckColors.TextPrimary
        DeckButtonStyle.Danger -> DeckColors.Ruby
        DeckButtonStyle.Ghost -> DeckColors.TextSecondary
    }
    val bgMod = when (style) {
        DeckButtonStyle.Primary -> Modifier.brandBackground(DeckPillShape)
        DeckButtonStyle.Secondary -> Modifier.background(DeckColors.SurfaceAlt, DeckPillShape)
        DeckButtonStyle.Danger -> Modifier.background(DeckColors.AccentSoft, DeckPillShape)
        DeckButtonStyle.Ghost -> Modifier
    }
    Row(
        Modifier
            .brandFocusRing(focused, cornerRadius = 50.dp, glow = style == DeckButtonStyle.Primary)
            .then(modifier)
            .clip(DeckPillShape)
            .then(bgMod)
            .heightIn(min = if (compact) 30.dp else 38.dp)
            .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 5.dp else 8.dp)
            .then(if (enabled) Modifier else Modifier.background(Color.Transparent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val a = if (enabled) 1f else 0.45f
        if (icon != null) DeckIcon(icon, tint = fg.copy(alpha = a), size = if (compact) 14.dp else 16.dp)
        Text(
            text,
            style = (textStyle ?: DeckType.menuButton).copy(
                fontWeight = if (style == DeckButtonStyle.Primary) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = if (compact) 13.sp else 15.sp,
            ),
            color = fg.copy(alpha = a),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 圆形图标按钮（返回、关闭、刷新等）。点击由 [modifier] 提供。 */
@Composable
fun DeckIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = size * 0.5f,
    tint: Color = DeckColors.TextPrimary,
    focused: Boolean = false,
    filled: Boolean = false,
    elevated: Boolean = false,
) {
    Box(
        Modifier
            .brandFocusRing(focused, cornerRadius = size / 2, glow = false)
            .then(modifier)
            .size(size)
            .then(
                when {
                    filled -> Modifier.brandBackground(CircleShape)
                    elevated -> Modifier.cardSurface(CircleShape, elevation = 2.dp)
                    else -> Modifier.background(DeckColors.SurfaceAlt, CircleShape)
                },
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DeckIcon(icon, tint = if (filled) Color.White else tint, size = iconSize)
    }
}

// ───────────────────────── 卡片 / 分组 ─────────────────────────

/** 白卡（深色模式紫卡）分组容器。 */
@Composable
fun DeckCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(DeckBrand.RadiusL),
    elevation: Dp = 2.dp,
    contentPadding: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .cardSurface(shape, elevation)
            .clip(shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** 小号大写灰色分组标题。 */
@Composable
fun DeckSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = DeckSectionHeaderStyle,
            color = DeckColors.TextLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

val DeckSectionHeaderStyle: TextStyle
    @Composable get() = DeckType.settingsLabel.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )

/** 发丝分隔线（Line 色）。 */
@Composable
fun DeckDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    Box(
        modifier
            .padding(start = startIndent)
            .fillMaxWidth()
            .height(1.dp)
            .background(DeckColors.Line),
    )
}

/**
 * 卡片内标准行：可选前置图标 + 标题/副标题 + 末尾内容。
 * 焦点由 [focused] 驱动（brandFocusRow），点击由 [modifier] 提供。
 */
@Composable
fun DeckRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = DeckColors.Ruby,
    focused: Boolean = false,
    titleColor: Color = DeckColors.TextPrimary,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .brandFocusRow(focused)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) DeckIconBadge(icon, tint = iconTint)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** 行首小图标底座：AccentSoft 圆角方块 + 着色图标。 */
@Composable
fun DeckIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = DeckColors.Ruby,
    size: Dp = 32.dp,
) {
    Box(
        modifier
            .size(size)
            .background(DeckColors.AccentSoft, RoundedCornerShape(DeckBrand.RadiusS)),
        contentAlignment = Alignment.Center,
    ) {
        DeckIcon(icon, tint = tint, size = size * 0.55f)
    }
}

// ───────────────────────── 进度条 / 标签 ─────────────────────────

/** 品牌渐变进度条；[progress] 为 null 时显示不确定动画以外的满格淡色轨道。 */
@Composable
fun DeckProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val p = LocalDeckColors.current
    val frac = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val r = CornerRadius(size.height / 2f)
                val track = if (p.isDark) Color(0xFF3A2D48) else Color(0xFFF3E6E1)
                val brush = Brush.linearGradient(
                    listOf(p.brandStart, p.brandMid, p.brandEnd),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                )
                onDrawBehind {
                    drawRoundRect(color = track, cornerRadius = r)
                    if (frac > 0f) {
                        drawRoundRect(
                            brush = brush,
                            size = Size(size.width * frac, size.height),
                            cornerRadius = r,
                        )
                    }
                }
            },
    )
}

/**
 * 胶囊标签（面包屑、筛选、模式切换）。选中为渐变 + 白字，未选中为白卡 + 次要文字。
 * 点击由 [modifier] 提供。
 */
@Composable
fun DeckChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    focused: Boolean = false,
    elevated: Boolean = true,
) {
    val fg = if (selected) Color.White else DeckColors.TextSecondary
    Row(
        Modifier
            .brandFocusRing(focused, cornerRadius = 50.dp, glow = false)
            .then(modifier)
            .then(
                when {
                    selected -> Modifier.brandBackground(DeckPillShape)
                    elevated -> Modifier.cardSurface(DeckPillShape, elevation = 1.dp)
                    else -> Modifier.background(DeckColors.SurfaceAlt, DeckPillShape)
                },
            )
            .clip(DeckPillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) DeckIcon(icon, tint = fg, size = 14.dp)
        Text(
            text,
            style = DeckType.settingsLabel.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 小号状态徽标（如“在线”“已隐藏”）。 */
@Composable
fun DeckBadge(text: String, modifier: Modifier = Modifier, accent: Boolean = true) {
    Text(
        text,
        style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
        color = if (accent) DeckColors.Ruby else DeckColors.TextSecondary,
        maxLines = 1,
        modifier = modifier
            .background(if (accent) DeckColors.AccentSoft else DeckColors.SurfaceAlt, DeckPillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ───────────────────────── 全屏页头 ─────────────────────────

/**
 * 全屏面板页头：圆形返回按钮 + 品牌图标 + 粗体标题 + 末尾操作区。
 * [backModifier] 负责返回点击与焦点。
 */
@Composable
fun DeckScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    backModifier: Modifier? = null,
    backFocused: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (backModifier != null) {
            DeckIconButton(
                PanelIcons.ChevronLeft,
                modifier = backModifier,
                size = 38.dp,
                iconSize = 20.dp,
                focused = backFocused,
                elevated = true,
            )
        }
        leadingIcon?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = DeckType.settingsSection.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** 弹窗/卡片内标题行：可选图标 + 标题。 */
@Composable
fun DeckSheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) DeckIconBadge(icon, size = 34.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                color = DeckColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 空状态：图标 + 一行说明。 */
@Composable
fun DeckEmptyState(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(DeckColors.AccentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) { DeckIcon(icon, tint = DeckColors.Ruby, size = 24.dp) }
        Text(text, style = DeckType.settingsLabel.copy(fontSize = 13.sp), color = DeckColors.TextSecondary)
    }
}


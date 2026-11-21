package com.pocketdeck.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.brandBorder

/**
 * 可在工具栏 / 筛选栏内原地展开的紧凑搜索胶囊：
 * - 收起态：固定宽度（默认 38.dp）的放大镜图标按钮，与相邻胶囊等高（30.dp）；
 * - 展开态：放大镜锚定在左侧不跳动，胶囊向右平滑伸展并推开右侧按钮，淡入输入框与右侧 ✕ 关闭按钮。
 */
@Composable
fun InlineExpandableSearchPill(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    expandedWidth: Dp = 184.dp,
    collapsedWidth: Dp = 38.dp,
    height: Dp = 30.dp,
    focused: Boolean = false,
    fieldFocused: Boolean = false,
    onFieldFocusChange: (Boolean) -> Unit = {},
    onSearchAction: () -> Unit = {},
    collapsedBackground: Color = DeckColors.Surface,
    collapsedIconTint: Color = DeckColors.TextSecondary,
    collapsedBorderColor: Color = DeckColors.Line,
    collapsedBorderWidth: Dp = 0.6.dp,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val reduceMotion = LocalReduceMotion.current

    val pillWidth by animateDpAsState(
        targetValue = if (expanded) expandedWidth else collapsedWidth,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = 0.86f,
            stiffness = 500f,
        ),
        label = "inlineSearchPillWidth",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            tween(
                durationMillis = if (expanded) 170 else 80,
                delayMillis = if (expanded) 40 else 0,
            )
        },
        label = "inlineSearchContentAlpha",
    )

    val bgColor by animateColorAsState(
        targetValue = if (expanded) DeckColors.Surface else collapsedBackground,
        animationSpec = if (reduceMotion) snap() else tween(160),
        label = "inlineSearchBg",
    )

    val iconTint by animateColorAsState(
        targetValue = if (expanded) DeckColors.TextSecondary else collapsedIconTint,
        animationSpec = if (reduceMotion) snap() else tween(160),
        label = "inlineSearchTint",
    )

    val borderActive = focused || fieldFocused || expanded

    Box(
        modifier = modifier
            .width(pillWidth)
            .height(height)
            .clip(DeckShapes.tabPill)
            .background(bgColor)
            .brandBorder(
                active = borderActive,
                shape = DeckShapes.tabPill,
                activeWidth = 1.3.dp,
                inactiveWidth = collapsedBorderWidth,
                inactiveColor = collapsedBorderColor,
            )
            .then(
                if (expanded) {
                    Modifier.clickable { focusRequester.requestFocusSafely() }
                } else {
                    Modifier.soundClickable(onClick = onOpen)
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = if (expanded) 5.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckIcon(
                icon = DeckIcons.Search,
                tint = iconTint,
                size = 18.dp,
            )

            if (expanded || pillWidth > collapsedWidth + 16.dp) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = contentAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (expanded) {
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                                textStyle = DeckType.settingsLabel.copy(
                                    fontSize = 13.sp,
                                    color = DeckColors.TextPrimary,
                                ),
                                cursorBrush = SolidColor(DeckColors.Ruby),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { onFieldFocusChange(it.isFocused) },
                            )
                        } else if (query.isNotEmpty()) {
                            Text(
                                text = query,
                                style = DeckType.settingsLabel.copy(fontSize = 13.sp),
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (trailingContent != null) {
                        Spacer(Modifier.width(4.dp))
                        trailingContent()
                    }

                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .then(
                                if (expanded) Modifier.soundClickable(onClick = onClose) else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        DeckIcon(
                            icon = DeckIcons.Close,
                            tint = DeckColors.TextSecondary,
                            size = 13.dp,
                        )
                    }
                }
            }
        }
    }
}

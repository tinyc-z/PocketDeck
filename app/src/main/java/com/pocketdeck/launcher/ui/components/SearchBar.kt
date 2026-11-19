package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType

/**
 * 搜索栏：置顶显示（避免被软键盘或遮挡搜索结果），支持一键收起键盘并用方向键选择结果。
 */
@Composable
fun SearchBar(
    query: String,
    imeVisible: Boolean,
    onQueryChange: (String) -> Unit,
    onFieldFocusChange: (Boolean) -> Unit,
    onHideKeyboard: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val primaryTint = DeckColors.TextPrimary
    val secondaryTint = DeckColors.TextSecondary

    Row(
        modifier
            .clip(DeckShapes.searchBar)
            .background(DeckColors.Surface)
            .height(44.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .drawBehind { drawMiniSearchIcon(secondaryTint) },
        )
        Spacer(Modifier.width(10.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFieldFocusChange(it.isFocused) },
            textStyle = DeckType.searchHint.copy(color = DeckColors.TextPrimary),
            singleLine = true,
            cursorBrush = SolidColor(DeckColors.Ruby),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    onHideKeyboard()
                },
            ),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = DeckType.searchHint,
                            color = DeckColors.TextSecondary,
                        )
                    }
                    inner()
                }
            },
        )

        if (imeVisible) {
            Spacer(Modifier.width(6.dp))
            // 收起键盘按钮：保留搜索结果，直接切回手柄方向键选择游戏
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(DeckColors.PillSelected)
                    .soundClickable {
                        keyboard?.hide()
                        onHideKeyboard()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .drawBehind { drawChevronDownIcon(primaryTint) },
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // 关闭搜索按钮
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .soundClickable(onClick = {
                    keyboard?.hide()
                    onClose()
                }),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✕",
                style = DeckType.bottomBar,
                color = DeckColors.TextSecondary,
            )
        }
    }
}

private fun DrawScope.drawMiniSearchIcon(color: Color) {
    val sw = 1.8f.dp.toPx()
    val cx = size.width * 0.42f
    val cy = size.height * 0.42f
    val r = size.width * 0.28f
    drawCircle(color, r, Offset(cx, cy), style = Stroke(sw))
    drawLine(
        color,
        Offset(cx + r * 0.72f, cy + r * 0.72f),
        Offset(size.width * 0.88f, size.height * 0.88f),
        sw,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawChevronDownIcon(color: Color) {
    val sw = 1.8f.dp.toPx()
    val p = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.34f)
        lineTo(size.width * 0.50f, size.height * 0.68f)
        lineTo(size.width * 0.82f, size.height * 0.34f)
    }
    drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

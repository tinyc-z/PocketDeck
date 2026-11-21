package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.cardSurface

/**
 * 搜索栏（Sunset Arcade）：圆角白卡浮起 + 线性图标。
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
    searchAllCategories: Boolean = false,
    onToggleSearchAllCategories: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocusSafely()
        keyboard?.show()
    }

    Row(
        modifier
            .cardSurface(shape = CircleShape, elevation = 5.dp)
            .border(0.8.dp, DeckColors.Line, CircleShape)
            .height(42.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckIcon(HomeIcons.Search, size = 17.dp, tint = DeckColors.TextSecondary)
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
                            color = DeckColors.TextLabel,
                        )
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .then(
                    if (searchAllCategories) {
                        Modifier.brandBackground(CircleShape)
                    } else {
                        Modifier
                            .background(DeckColors.SurfaceAlt)
                            .border(0.7.dp, DeckColors.Line, CircleShape)
                    },
                )
                .soundClickable(onClick = onToggleSearchAllCategories)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.search_all_categories),
                style = DeckType.metaLabel.copy(
                    fontWeight = if (searchAllCategories) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (searchAllCategories) Color.White else DeckColors.TextSecondary,
                maxLines = 1,
            )
        }

        if (imeVisible) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(DeckColors.AccentSoft)
                    .soundClickable {
                        keyboard?.hide()
                        onHideKeyboard()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                DeckIcon(HomeIcons.ChevronDown, size = 14.dp, tint = DeckColors.Ruby)
            }
        }

        Spacer(Modifier.width(4.dp))

        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(DeckColors.SurfaceAlt)
                .soundClickable(onClick = {
                    keyboard?.hide()
                    onClose()
                }),
            contentAlignment = Alignment.Center,
        ) {
            DeckIcon(HomeIcons.Close, size = 14.dp, tint = DeckColors.TextSecondary)
        }
    }
}

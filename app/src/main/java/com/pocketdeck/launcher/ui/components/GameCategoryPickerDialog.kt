package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType

/**
 * 单游戏所属自定义标签分类选择弹窗：
 * 支持勾选/取消该游戏的自定义标签分类（自动展示由平台归入的分类）、手柄十字键导航，以及快速新建分类。
 */
@Composable
fun GameCategoryPickerDialog(
    game: Game,
    categories: List<Category>,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onCreateCategory: ((String) -> Unit)? = null,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIds by remember(game.id) {
        mutableStateOf(categories.filter { game.id in it.games }.map { it.id }.toSet())
    }
    var knownCatIds by remember(game.id) {
        mutableStateOf(categories.map { it.id }.toSet())
    }
    LaunchedEffect(categories) {
        val newlyAdded = categories.filter { it.id !in knownCatIds && game.id in it.games }.map { it.id }
        if (newlyAdded.isNotEmpty()) {
            selectedIds = selectedIds + newlyAdded
        }
        knownCatIds = categories.map { it.id }.toSet()
    }

    var showNewInput by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }
    val newInputFocus = remember { FocusRequester() }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val hasCreateBtn = onCreateCategory != null
    // 0..categories.lastIndex = 分类项; (若有新建) categories.size = + 新建; 最后两项 = 取消 / 确定
    val createBtnIdx = if (hasCreateBtn) categories.size else -1
    val cancelIdx = if (hasCreateBtn) categories.size + 1 else categories.size
    val okIdx = cancelIdx + 1
    val totalFocus = okIdx + 1
    var focusIdx by remember { mutableIntStateOf(if (categories.isEmpty() && hasCreateBtn) createBtnIdx else 0) }

    LaunchedEffect(showNewInput) {
        if (showNewInput) {
            newInputFocus.requestFocus()
        } else {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(focusIdx) {
        if (focusIdx in categories.indices) {
            listState.animateScrollToItem((focusIdx - 2).coerceAtLeast(0))
        }
    }

    fun submitNewCategory() {
        val trimmed = newCatName.trim()
        if (trimmed.isNotEmpty() && onCreateCategory != null) {
            onCreateCategory(trimmed)
        }
        newCatName = ""
        showNewInput = false
    }

    androidx.activity.compose.BackHandler(enabled = showNewInput) { showNewInput = false }
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showNewInput) {
                    if (event.type == KeyEventType.KeyDown &&
                        (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings))
                    ) {
                        showNewInput = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        focusIdx = (focusIdx - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        focusIdx = (focusIdx + 1).coerceAtMost(totalFocus - 1)
                        true
                    }
                    event.key == Key.DirectionLeft && focusIdx == okIdx -> {
                        focusIdx = cancelIdx
                        true
                    }
                    event.key == Key.DirectionRight && focusIdx == cancelIdx -> {
                        focusIdx = okIdx
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when {
                            focusIdx in categories.indices -> {
                                val cat = categories[focusIdx]
                                val viaPlatform = game.platformId in cat.sources
                                if (!viaPlatform) {
                                    selectedIds = if (cat.id in selectedIds) selectedIds - cat.id else selectedIds + cat.id
                                }
                            }
                            hasCreateBtn && focusIdx == createBtnIdx -> showNewInput = true
                            focusIdx == cancelIdx -> onDismiss()
                            else -> onConfirm(selectedIds)
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.86f).dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.lib_cat_picker_title, game.title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.lib_cat_empty),
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                val viaPlatText = stringResource(R.string.lib_cat_via_platform)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(categories, key = { _, c -> c.id }) { idx, cat ->
                        val viaPlatform = game.platformId in cat.sources
                        val checked = viaPlatform || cat.id in selectedIds
                        val hl = focusIdx == idx && !showNewInput
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.settingsDropdown)
                                .background(
                                    when {
                                        hl -> DeckColors.Ruby.copy(alpha = 0.16f)
                                        checked -> DeckColors.Ruby.copy(alpha = 0.08f)
                                        else -> DeckColors.Surface
                                    },
                                )
                                .border(
                                    width = if (hl) 1.5.dp else 0.dp,
                                    color = if (hl) DeckColors.Ruby else Color.Transparent,
                                    shape = DeckShapes.settingsDropdown,
                                )
                                .soundClickable(enabled = !viaPlatform) {
                                    focusIdx = idx
                                    selectedIds = if (cat.id in selectedIds) selectedIds - cat.id else selectedIds + cat.id
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    cat.name,
                                    style = DeckType.settingsValue,
                                    color = if (checked || hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                )
                                if (viaPlatform) {
                                    Text(viaPlatText, style = DeckType.metaLabel, color = DeckColors.TextSecondary)
                                }
                            }
                            Text(
                                if (checked) "✓" else "—",
                                style = DeckType.settingsValue,
                                color = if (checked || hl) DeckColors.Ruby else DeckColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            if (hasCreateBtn) {
                Spacer(Modifier.height(10.dp))
                if (showNewInput) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.settingsDropdown)
                            .background(DeckColors.Surface)
                            .border(1.5.dp, DeckColors.Ruby, DeckShapes.settingsDropdown)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (newCatName.isEmpty()) {
                                Text(
                                    stringResource(R.string.lib_cat_new_placeholder),
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextSecondary,
                                )
                            }
                            BasicTextField(
                                value = newCatName,
                                onValueChange = { if (it.length <= 40) newCatName = it },
                                singleLine = true,
                                textStyle = DeckType.settingsValue.copy(color = DeckColors.TextPrimary),
                                cursorBrush = SolidColor(DeckColors.Ruby),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitNewCategory() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(newInputFocus),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.action_ok),
                            style = DeckType.menuButton,
                            color = if (newCatName.isNotBlank()) DeckColors.Ruby else DeckColors.TextSecondary,
                            modifier = Modifier
                                .clip(DeckShapes.menuButton)
                                .soundClickable(enabled = newCatName.isNotBlank()) { submitNewCategory() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Text(
                            stringResource(R.string.menu_cancel),
                            style = DeckType.menuButton,
                            color = DeckColors.TextSecondary,
                            modifier = Modifier
                                .clip(DeckShapes.menuButton)
                                .soundClickable {
                                    newCatName = ""
                                    showNewInput = false
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    val createHl = focusIdx == createBtnIdx
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.settingsDropdown)
                            .background(if (createHl) DeckColors.Ruby.copy(alpha = 0.14f) else DeckColors.Surface)
                            .border(
                                width = if (createHl) 1.5.dp else 1.dp,
                                color = if (createHl) DeckColors.Ruby else DeckColors.DialogBorder,
                                shape = DeckShapes.settingsDropdown,
                            )
                            .soundClickable {
                                focusIdx = createBtnIdx
                                showNewInput = true
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.lib_cat_new),
                            style = DeckType.settingsValue,
                            color = DeckColors.Ruby,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val cancelHl = focusIdx == cancelIdx && !showNewInput
                val okHl = focusIdx == okIdx && !showNewInput
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = if (cancelHl) DeckColors.Ruby else DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .border(if (cancelHl) 1.5.dp else 0.dp, if (cancelHl) DeckColors.Ruby else Color.Transparent, DeckShapes.menuButton)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_ok),
                    style = DeckType.menuButton,
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby)
                        .border(if (okHl) 2.dp else 0.dp, if (okHl) DeckColors.TextPrimary else Color.Transparent, DeckShapes.menuButton)
                        .clickable { onConfirm(selectedIds) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

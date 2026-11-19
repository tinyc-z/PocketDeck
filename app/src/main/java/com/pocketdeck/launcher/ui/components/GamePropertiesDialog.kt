package com.pocketdeck.launcher.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CoverMatchMode
import com.pocketdeck.launcher.data.CoverResolutionInfo
import com.pocketdeck.launcher.data.MetaMatchMode
import com.pocketdeck.launcher.data.MetadataResolutionInfo
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.home.formatFileSize
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat

/**
 * 游戏「属性」详情弹窗：展示元信息、文件信息（路径/大小/CRC32）、元数据提供源与识别模式、封面提供源与识别模式、启动器等。
 */
@Composable
fun GamePropertiesDialog(
    game: Game,
    platform: Platform,
    metadata: GameMetadata?,
    officialName: String?,
    metaInfo: MetadataResolutionInfo?,
    coverInfo: CoverResolutionInfo?,
    isFavorite: Boolean,
    categoryNames: List<String>,
    effectiveLauncherId: String?,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    computeCrc32: suspend (String) -> String?,
    onRefreshMetadata: (suspend () -> Boolean)? = null,
    onLocateFile: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var refreshing by remember(game.id) { mutableStateOf(false) }
    val isApk = game.id.startsWith(GameLauncher.APK_PREFIX)
    val canLocate = !isApk && onLocateFile != null
    // -1 = neutral (no bottom button highlighted), 0 = Locate file, 1 = Refresh metadata
    var footerFocus by remember(game.id) { mutableIntStateOf(-1) }

    fun triggerRefresh() {
        val cb = onRefreshMetadata ?: return
        if (refreshing) return
        refreshing = true
        scope.launch {
            val ok = runCatching { cb() }.getOrDefault(false)
            refreshing = false
            val msgRes = if (ok) R.string.prop_refresh_meta_ok else R.string.prop_refresh_meta_fail
            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
        }
    }

    var crc32 by remember(game.id, metaInfo?.crc32) { mutableStateOf(metaInfo?.crc32) }
    LaunchedEffect(game.id) {
        if (!isApk && crc32 == null) {
            crc32 = computeCrc32(game.id)
        }
        focusRequester.requestFocus()
    }

    val noneLabel = stringResource(R.string.prop_none)
    val metaProvidersText = if (metaInfo == null || metaInfo.providers.isEmpty()) {
        noneLabel
    } else {
        metaInfo.providers.joinToString(", ") { id ->
            ScraperCatalog.metaDescriptor(id)?.let { context.getString(it.nameRes) } ?: id
        }
    }
    val metaModesText = if (metaInfo == null || metaInfo.matchModes.isEmpty()) {
        noneLabel
    } else {
        metaInfo.matchModes.joinToString(", ") { mode ->
            when (mode) {
                MetaMatchMode.Crc32Hash -> context.getString(R.string.match_mode_crc32)
                MetaMatchMode.ExactFilename -> context.getString(R.string.match_mode_exact_name)
                MetaMatchMode.TitleMatch -> context.getString(R.string.match_mode_title)
                MetaMatchMode.ApiHash -> context.getString(R.string.match_mode_api_hash)
                MetaMatchMode.ApiRomName -> context.getString(R.string.match_mode_api_rom)
                MetaMatchMode.ApiNameSearch -> context.getString(R.string.match_mode_api_search)
            }
        }
    }

    val coverProviderText = when (coverInfo?.providerId) {
        null -> if (isApk) stringResource(R.string.cover_src_app_icon) else stringResource(R.string.cover_src_generated)
        "custom" -> stringResource(R.string.cover_src_custom)
        "local" -> stringResource(R.string.cover_src_local)
        else -> ScraperCatalog.coverDescriptor(coverInfo.providerId)?.let { stringResource(it.nameRes) }
            ?: coverInfo.providerId
    }
    val coverModeText = when (coverInfo?.matchMode) {
        null -> if (isApk) stringResource(R.string.match_mode_app_icon) else noneLabel
        CoverMatchMode.CustomFile -> stringResource(R.string.match_mode_custom)
        CoverMatchMode.LocalFile -> stringResource(R.string.match_mode_local)
        CoverMatchMode.IndexExact -> stringResource(R.string.match_mode_index_exact)
        CoverMatchMode.IndexFuzzy -> stringResource(R.string.match_mode_index_fuzzy)
        CoverMatchMode.DirectProbe -> stringResource(R.string.match_mode_direct_probe)
        CoverMatchMode.ApiHash -> stringResource(R.string.match_mode_api_hash)
        CoverMatchMode.ApiSearch -> stringResource(R.string.match_mode_api_search)
        CoverMatchMode.AppIcon -> stringResource(R.string.match_mode_app_icon)
    }

    val actualSize = remember(game.id, game.fileSize) {
        if (game.fileSize > 0L) game.fileSize
        else if (!isApk) runCatching { File(game.id).length() }.getOrDefault(0L) else 0L
    }
    val sizeText = if (actualSize > 0L) {
        "${formatFileSize(actualSize)} (${NumberFormat.getIntegerInstance().format(actualSize)} B)"
    } else noneLabel

    val launcherText = remember(platform.id, effectiveLauncherId) {
        GameLauncher.describeLauncher(context, platform.id, effectiveLauncherId)
    } ?: noneLabel

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) ||
                        GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when {
                            footerFocus == 0 && canLocate -> onLocateFile()
                            footerFocus == 1 && onRefreshMetadata != null -> triggerRefresh()
                            else -> onDismiss()
                        }
                        true
                    }
                    GamepadKeys.isMenu(event.key, swapAbxy, keyBindings) && onRefreshMetadata != null -> {
                        triggerRefresh()
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        footerFocus = when {
                            canLocate -> 0
                            onRefreshMetadata != null -> 1
                            else -> -1
                        }
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        footerFocus = when {
                            onRefreshMetadata != null -> 1
                            canLocate -> 0
                            else -> -1
                        }
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        scope.launch { scrollState.animateScrollTo((scrollState.value + 160).coerceAtMost(scrollState.maxValue)) }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        scope.launch { scrollState.animateScrollTo((scrollState.value - 160).coerceAtLeast(0)) }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            contentPadding = PaddingValues(24.dp),
            closeFocused = false,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp),
            ) {
                Text(
                    stringResource(R.string.prop_title),
                    style = DeckType.settingsLabel,
                    color = DeckColors.Ruby,
                )
                Text(
                    game.title,
                    style = DeckType.menuTitle,
                    color = DeckColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DeckColors.DialogBorder)

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PropSectionHeader(stringResource(R.string.prop_sec_game))
                if (!officialName.isNullOrBlank() && officialName != game.title) {
                    PropRow(stringResource(R.string.prop_official_name), officialName)
                }
                PropRow(stringResource(R.string.meta_platform), "${platform.fullName} (${platform.shortName})")
                PropRow(stringResource(R.string.meta_release_date), metadata?.releaseDate ?: noneLabel)
                PropRow(stringResource(R.string.meta_genre), metadata?.genre ?: noneLabel)
                PropRow(stringResource(R.string.meta_developer), metadata?.developer ?: noneLabel)
                PropRow(stringResource(R.string.meta_publisher), metadata?.publisher ?: noneLabel)
                PropRow(stringResource(R.string.prop_launcher), launcherText)
                val statusList = buildList {
                    if (isFavorite) add(stringResource(R.string.prop_favorited))
                    addAll(categoryNames)
                }
                if (statusList.isNotEmpty()) {
                    PropRow(stringResource(R.string.prop_tags), statusList.joinToString(" · "))
                }

                HorizontalDivider(color = DeckColors.DialogBorder, modifier = Modifier.padding(vertical = 4.dp))
                PropSectionHeader(stringResource(R.string.prop_sec_file))
                PropRow(
                    if (isApk) stringResource(R.string.prop_package) else stringResource(R.string.prop_file_name),
                    game.fileName,
                )
                PropRow(stringResource(R.string.meta_file_size), sizeText)
                if (!isApk) {
                    PropRow(stringResource(R.string.prop_file_path), game.id)
                    PropRow("CRC32", crc32 ?: noneLabel)
                }

                HorizontalDivider(color = DeckColors.DialogBorder, modifier = Modifier.padding(vertical = 4.dp))
                PropSectionHeader(stringResource(R.string.prop_sec_scraping))
                PropRow(stringResource(R.string.prop_meta_provider), metaProvidersText)
                PropRow(stringResource(R.string.prop_meta_mode), metaModesText)
                PropRow(stringResource(R.string.prop_cover_provider), coverProviderText)
                PropRow(stringResource(R.string.prop_cover_mode), coverModeText)
                coverInfo?.source?.takeIf { it.isNotBlank() }?.let { src ->
                    PropRow(stringResource(R.string.prop_cover_source), src)
                }
            }

            if (canLocate || onRefreshMetadata != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canLocate) {
                        val locateFocused = footerFocus == 0
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(DeckShapes.menuButton)
                                .background(
                                    if (locateFocused) DeckColors.Ruby.copy(alpha = 0.16f)
                                    else DeckColors.Surface,
                                )
                                .border(
                                    width = if (locateFocused) 1.5.dp else 1.dp,
                                    color = if (locateFocused) DeckColors.Ruby else DeckColors.DialogBorder,
                                    shape = DeckShapes.menuButton,
                                )
                                .soundClickable { onLocateFile() }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.edit_locate_file),
                                style = DeckType.menuButton,
                                color = if (locateFocused) DeckColors.Ruby else DeckColors.TextPrimary,
                            )
                        }
                    }
                    if (onRefreshMetadata != null) {
                        val refreshFocused = footerFocus == 1
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(DeckShapes.menuButton)
                                .background(
                                    if (refreshFocused) DeckColors.Ruby.copy(alpha = 0.16f)
                                    else DeckColors.Surface,
                                )
                                .border(
                                    width = if (refreshFocused) 1.5.dp else 1.dp,
                                    color = if (refreshFocused) DeckColors.Ruby else DeckColors.DialogBorder,
                                    shape = DeckShapes.menuButton,
                                )
                                .soundClickable(enabled = !refreshing) { triggerRefresh() }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = DeckColors.Ruby,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = if (refreshing) {
                                    stringResource(R.string.prop_refreshing_meta)
                                } else {
                                    stringResource(R.string.prop_refresh_meta)
                                },
                                style = DeckType.menuButton,
                                color = if (refreshFocused || refreshing) DeckColors.Ruby else DeckColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropSectionHeader(title: String) {
    Text(
        title,
        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
        color = DeckColors.Ruby,
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DeckShapes.settingsDropdown)
            .background(DeckColors.Surface)
            .border(0.5.dp, DeckColors.DialogBorder, DeckShapes.settingsDropdown)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = DeckType.settingsLabel,
            color = DeckColors.TextSecondary,
            modifier = Modifier.width(126.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = DeckType.settingsLabel,
            color = DeckColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

package com.pocketdeck.launcher.ui.library

import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.pocketdeck.launcher.data.formatCompactSize
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.ui.components.BrandTileIcon
import com.pocketdeck.launcher.ui.components.FavoriteHeartIcon
import com.pocketdeck.launcher.ui.components.soundClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.pocketdeck.launcher.ui.components.CircularProgressIndicator
import com.pocketdeck.launcher.ui.components.HorizontalDivider
import com.pocketdeck.launcher.ui.components.LinearProgressIndicator
import com.pocketdeck.launcher.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.InlineExpandableSearchPill
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CoverResolutionInfo
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.FileTask
import com.pocketdeck.launcher.data.FileTaskKind
import com.pocketdeck.launcher.data.FileTaskSource
import com.pocketdeck.launcher.data.MetadataResolutionInfo
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.LanServerManager
import com.pocketdeck.launcher.lan.QuickShareService
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.GameCategoryPickerDialog
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.components.GamePropertiesDialog
import com.pocketdeck.launcher.ui.edit.DeleteFileConfirmDialog
import com.pocketdeck.launcher.ui.edit.EditGameScreen
import android.content.ClipData
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.pocketdeck.launcher.ui.lan.LanServiceControlDialog
import com.pocketdeck.launcher.ui.lan.ShareMethodDialog
import com.pocketdeck.launcher.ui.lan.SharePeerPickerDialog
import com.pocketdeck.launcher.ui.settings.ActionItem
import com.pocketdeck.launcher.ui.settings.ActionMenuDialog
import com.pocketdeck.launcher.ui.settings.ActionMenuState
import com.pocketdeck.launcher.ui.settings.NameDialog
import com.pocketdeck.launcher.ui.settings.NameDialogSpec
import com.pocketdeck.launcher.ui.settings.NameDialogState
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.cardSurface
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.brandPillBackground
import com.pocketdeck.launcher.ui.theme.DeckFontFamily
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean


@Composable
internal fun LibraryGamesTab(
    filterRowState: LazyListState,
    listState: LazyListState,
    gameSearchOpen: Boolean,
    searchQuery: String,
    searchFieldFr: FocusRequester,
    searchFieldFocused: Boolean,
    focusZone: LibraryZone,
    filterChipIdx: Int,
    gamesFilter: GamesFilter,
    hiddenCount: Int,
    selectedPlatformId: String?,
    allPlatforms: List<Platform>,
    filteredGames: List<Game>,
    selectedIdx: Int,
    hiddenIds: Set<String>,
    favorites: Set<String>,
    categorizedPlatforms: Set<String>,
    categorizedGames: Set<String>,
    covers: CoverSource,
    officialNames: Map<String, String>,
    platformFor: (Game) -> Platform,
    onSearchQueryChange: (String) -> Unit,
    onOpenGameSearch: () -> Unit,
    onCloseGameSearch: () -> Unit,
    onSearchFieldFocusChange: (Boolean) -> Unit,
    onSearchAction: () -> Unit,
    onSelectFilter: (GamesFilter) -> Unit,
    onSelectPlatform: (String?) -> Unit,
    onClickGame: (Int, Game) -> Unit,
    onUnhideGame: (String) -> Unit,
) {
    // 内联可展开搜索胶囊 + 状态筛选 + 平台筛选
    LazyRow(
        state = filterRowState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "search") {
            val searchExpanded = gameSearchOpen || searchQuery.isNotBlank()
            InlineExpandableSearchPill(
                expanded = searchExpanded,
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.lib_search_hint),
                onOpen = {
                    onOpenGameSearch()
                },
                onClose = {
                    onCloseGameSearch()
                },
                focusRequester = searchFieldFr,
                expandedWidth = 180.dp,
                focused = focusZone == LibraryZone.Filter && filterChipIdx == 0,
                fieldFocused = searchFieldFocused,
                onFieldFocusChange = onSearchFieldFocusChange,
                onSearchAction = {
                    onSearchAction()
                },
            )
        }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_all),
                    selected = gamesFilter == GamesFilter.All,
                    focused = focusZone == LibraryZone.Filter && filterChipIdx == 1,
                    onClick = { onSelectFilter(GamesFilter.All) },
                )
            }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_visible),
                    selected = gamesFilter == GamesFilter.Visible,
                    focused = focusZone == LibraryZone.Filter && filterChipIdx == 2,
                    onClick = { onSelectFilter(GamesFilter.Visible) },
                )
            }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_hidden, hiddenCount),
                    selected = gamesFilter == GamesFilter.Hidden,
                    focused = focusZone == LibraryZone.Filter && filterChipIdx == 3,
                    onClick = { onSelectFilter(GamesFilter.Hidden) },
                )
            }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_uncategorized),
                    selected = gamesFilter == GamesFilter.Uncategorized,
                    focused = focusZone == LibraryZone.Filter && filterChipIdx == 4,
                    onClick = { onSelectFilter(GamesFilter.Uncategorized) },
                )
            }
            item {
                Box(Modifier.width(1.dp).height(18.dp).background(DeckColors.Line))
            }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_platform_all),
                    selected = selectedPlatformId == null,
                    focused = focusZone == LibraryZone.Filter && filterChipIdx == 5,
                    onClick = { onSelectPlatform(null) },
                )
            }
            itemsIndexed(allPlatforms, key = { _, p -> p.id }) { pIdx, plat ->
                FilterChipPill(
                    label = plat.shortName,
                    selected = selectedPlatformId == plat.id,
                    focused = focusZone == LibraryZone.Filter && filterChipIdx == 6 + pIdx,
                    onClick = {
                        onSelectPlatform(if (selectedPlatformId == plat.id) null else plat.id)
                    },
                )
            }
        }

    if (filteredGames.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.lib_empty_games),
                style = DeckType.emptyMessage,
                color = DeckColors.TextSecondary,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(filteredGames, key = { _, g -> g.id }) { idx, game ->
                val plat = platformFor(game)
                val isHidden = game.id in hiddenIds
                val isFav = game.id in favorites
                val isUncat = game.platformId !in categorizedPlatforms && game.id !in categorizedGames
                val hl = focusZone == LibraryZone.List && idx == selectedIdx

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                        .brandBorder(active = hl, shape = RoundedCornerShape(12.dp), activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                        .soundClickable {
                            onClickGame(idx, game)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val h = 44.dp
                    val w = (h * plat.coverAspect).coerceIn(32.dp, 62.dp)
                    GameCoverCard(
                        game = game,
                        platform = plat,
                        covers = covers,
                        officialName = officialNames[game.id],
                        width = w,
                        height = h,
                        isFocused = false,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = game.title,
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.Medium),
                                color = if (isHidden) DeckColors.TextSecondary else DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isFav) FavoriteHeartIcon(filled = true, color = DeckColors.Ruby, size = 14.dp)
                            if (isHidden) TagBadge(stringResource(R.string.lib_badge_hidden), accent = true)
                            if (isUncat) TagBadge(stringResource(R.string.lib_badge_uncategorized), accent = false)
                        }
                        Spacer(Modifier.height(2.dp))
                        val sizeTag = formatCompactSize(game.fileSize)
                        Text(
                            text = listOfNotNull(plat.shortName, sizeTag, game.fileName).joinToString(" · "),
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isHidden) {
                        Text(
                            stringResource(R.string.lib_action_unhide),
                            style = DeckType.settingsLabel,
                            color = DeckColors.Ruby,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                .soundClickable { onUnhideGame(game.id) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .background(DeckColors.Background)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DeckIcon(PanelIcons.MoreHorizontal, tint = DeckColors.TextSecondary, size = 15.dp)
                    }
                }
            }
        }
    }
}

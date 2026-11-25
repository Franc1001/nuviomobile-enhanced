@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuvio.app.features.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.background.appGlassBackdrop
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.player.MiniPlayerController
import com.nuvio.app.features.player.PlatformPlayerSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.live_tv_action_clear_recents
import nuvio.composeapp.generated.resources.live_tv_no_matching_channels_message
import nuvio.composeapp.generated.resources.live_tv_no_matching_channels_title
import nuvio.composeapp.generated.resources.live_tv_preview_hint
import nuvio.composeapp.generated.resources.live_tv_preview_next
import nuvio.composeapp.generated.resources.live_tv_preview_unavailable
import nuvio.composeapp.generated.resources.live_tv_sidebar_channel_count
import nuvio.composeapp.generated.resources.live_tv_sort_categories
import nuvio.composeapp.generated.resources.live_tv_versions_count
import nuvio.composeapp.generated.resources.live_tv_versions_default
import nuvio.composeapp.generated.resources.live_tv_versions_title
import org.jetbrains.compose.resources.stringResource

internal class LiveTvSidebarEntry(
    val key: String,
    val label: String,
    val count: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

private val CategoryMinWidth = 84.dp
private val CategoryMaxWidth = 220.dp
private val PreviewMinWidth = 124.dp
private val PreviewMaxWidth = 460.dp
private val ColumnGap = 8.dp
private val PanelShape = RoundedCornerShape(16.dp)
private val RowShape = RoundedCornerShape(12.dp)

/** How long the highlight must rest on a channel before its preview starts, so scrolling stays smooth. */
private const val PREVIEW_START_DELAY_MS = 450L

/**
 * The category sidebar layout: categories, the channels of the selected one, and (when enabled) a
 * muted live preview of the highlighted channel with what is on now and next.
 *
 * A channel is highlighted by tapping it or by scrolling: when the list comes to rest, the channel at
 * its middle is highlighted. Tapping the highlighted channel, or Watch, opens it in the player.
 */
@Composable
internal fun LiveTvSidebarLayout(
    entries: List<LiveTvSidebarEntry>,
    rows: List<LiveTvMergedChannel>,
    favoriteChannelIds: Set<String>,
    epgPrograms: Map<String, List<LiveTvEpgProgram>>,
    nowMillis: Long,
    previewEnabled: Boolean,
    glassEnabled: Boolean,
    onPlay: (LiveTvChannel) -> Unit,
    onFavoriteClick: (LiveTvChannel) -> Unit,
    onGuideClick: (LiveTvChannel) -> Unit,
    onVersionChosen: (LiveTvMergedChannel, LiveTvChannel) -> Unit,
    onSortCategoriesClick: () -> Unit,
    onClearRecents: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val selectedEntry = entries.firstOrNull { it.selected } ?: entries.firstOrNull()
    val channelListState = rememberLazyListState()
    var highlightedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var versionSheetKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Stops the preview before the player opens, so the account never streams twice at once.
    var previewSuspended by remember { mutableStateOf(false) }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(isResumed) { if (isResumed) previewSuspended = false }

    // A new category starts at its top, with its first channel highlighted.
    LaunchedEffect(selectedEntry?.key) {
        channelListState.scrollToItem(0)
        highlightedKey = rows.firstOrNull()?.key
    }
    LaunchedEffect(rows) {
        if (rows.none { it.key == highlightedKey }) highlightedKey = rows.firstOrNull()?.key
    }
    // When a scroll comes to rest, the channel in the middle of the list is highlighted. This reads
    // the layout once per stop, never per frame.
    LaunchedEffect(channelListState, rows) {
        snapshotFlow { channelListState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect { highlightedKey = channelListState.middleItemKey(rows) ?: highlightedKey }
    }

    val highlighted = rows.firstOrNull { it.key == highlightedKey } ?: rows.firstOrNull()
    val previewTarget by produceState<LiveTvMergedChannel?>(null, highlighted?.key, highlighted?.primary?.id) {
        delay(PREVIEW_START_DELAY_MS)
        value = highlighted
    }
    val openChannel: (LiveTvChannel) -> Unit = { channel ->
        previewSuspended = true
        onPlay(channel)
    }

    BoxWithConstraints(modifier = modifier) {
        val categoryWidth = (maxWidth * (if (previewEnabled) 0.24f else 0.32f))
            .coerceIn(CategoryMinWidth, CategoryMaxWidth)
        val previewWidth = if (previewEnabled) (maxWidth * 0.36f).coerceIn(PreviewMinWidth, PreviewMaxWidth) else 0.dp
        val compactRows = previewEnabled

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        ) {
            // Categories ---------------------------------------------------------------------
            Column(
                modifier = Modifier
                    .width(categoryWidth)
                    .fillMaxHeight()
                    .liveTvPanel(glassEnabled, PanelShape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSortCategoriesClick)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.nuvio.colors.accent,
                    )
                    Text(
                        text = stringResource(Res.string.live_tv_sort_categories),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.nuvio.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 6.dp),
                ) {
                    items(items = entries, key = { it.key }, contentType = { "category" }) { entry ->
                        LiveTvSidebarCategoryItem(entry = entry, glassEnabled = glassEnabled)
                    }
                }
            }

            // Channels -----------------------------------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                ChannelsHeader(
                    title = selectedEntry?.label.orEmpty(),
                    count = rows.size,
                    onClearRecents = onClearRecents,
                )
                if (rows.isEmpty()) {
                    HomeEmptyStateCard(
                        title = stringResource(Res.string.live_tv_no_matching_channels_title),
                        message = stringResource(Res.string.live_tv_no_matching_channels_message),
                    )
                } else {
                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(items = rows, key = { it.key }, contentType = { "channel" }) { row ->
                            val isHighlighted = previewEnabled && row.key == highlighted?.key
                            LiveTvSidebarChannelRow(
                                row = row,
                                isHighlighted = isHighlighted,
                                isFavorite = row.primary.id in favoriteChannelIds,
                                programs = epgPrograms[row.primary.id],
                                nowMillis = nowMillis,
                                compact = compactRows,
                                glassEnabled = glassEnabled,
                                onClick = {
                                    when {
                                        !previewEnabled || isHighlighted -> openChannel(row.primary)
                                        else -> highlightedKey = row.key
                                    }
                                },
                                onFavoriteClick = { onFavoriteClick(row.primary) },
                                onVersionsClick = { versionSheetKey = row.key },
                                onGuideClick = { onGuideClick(row.primary) },
                            )
                        }
                    }
                }
            }

            // Preview ------------------------------------------------------------------------
            if (previewEnabled) {
                LiveTvPreviewPane(
                    row = highlighted,
                    previewRow = previewTarget,
                    playing = isResumed && !previewSuspended && !MiniPlayerController.isActive,
                    isFavorite = highlighted?.primary?.id?.let { it in favoriteChannelIds } == true,
                    programs = highlighted?.let { epgPrograms[it.primary.id] }.orEmpty(),
                    nowMillis = nowMillis,
                    glassEnabled = glassEnabled,
                    onWatch = openChannel,
                    onFavoriteClick = onFavoriteClick,
                    onVersionsClick = { versionSheetKey = it.key },
                    onGuideClick = onGuideClick,
                    modifier = Modifier
                        .width(previewWidth)
                        .fillMaxHeight(),
                )
            }
        }
    }

    val versionRow = versionSheetKey?.let { key -> rows.firstOrNull { it.key == key } }
    if (versionRow != null) {
        LiveTvVersionSheet(
            row = versionRow,
            epgPrograms = epgPrograms,
            nowMillis = nowMillis,
            onChoose = { version ->
                versionSheetKey = null
                previewSuspended = true
                onVersionChosen(versionRow, version)
            },
            onDismiss = { versionSheetKey = null },
        )
    }
}

private fun LazyListState.middleItemKey(rows: List<LiveTvMergedChannel>): String? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    // At the very top, the first channel; otherwise the one across the middle of the list.
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return rows.firstOrNull()?.key
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val item = visible.minByOrNull { item -> kotlin.math.abs(item.offset + item.size / 2 - middle) }
    return item?.key as? String
}

@Composable
private fun ChannelsHeader(title: String, count: Int, onClearRecents: (() -> Unit)?) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.live_tv_sidebar_channel_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }
        if (onClearRecents != null) {
            LiveTvPanelFilterChip(
                label = stringResource(Res.string.live_tv_action_clear_recents),
                selected = false,
                onClick = onClearRecents,
            )
        }
    }
}

@Composable
private fun LiveTvSidebarCategoryItem(entry: LiveTvSidebarEntry, glassEnabled: Boolean) {
    val tokens = MaterialTheme.nuvio
    val selectedFill = if (glassEnabled) tokens.colors.accent.copy(alpha = 0.18f) else tokens.colors.overlaySelected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (entry.selected) selectedFill else Color.Transparent)
            .clickable(onClick = entry.onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (entry.selected) tokens.colors.accent else Color.Transparent),
        )
        Text(
            text = entry.label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (entry.selected) tokens.colors.textPrimary else tokens.colors.textSecondary,
            fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.count.toString(),
            modifier = Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.selected) tokens.colors.accent else tokens.colors.textMuted,
        )
    }
}

@Composable
private fun LiveTvSidebarChannelRow(
    row: LiveTvMergedChannel,
    isHighlighted: Boolean,
    isFavorite: Boolean,
    programs: List<LiveTvEpgProgram>?,
    nowMillis: Long,
    compact: Boolean,
    glassEnabled: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onVersionsClick: () -> Unit,
    onGuideClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val channel = row.primary
    // The EPG lookup runs when the minute or the programs change, not on every recomposition.
    val current = remember(programs, nowMillis) { programs?.currentProgram(nowMillis) }
    val progress = current?.progressAt(nowMillis)
    val border = when {
        isHighlighted -> tokens.colors.accent.copy(alpha = 0.7f)
        glassEnabled -> tokens.colors.textPrimary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(
                when {
                    isHighlighted -> tokens.colors.accent.copy(alpha = 0.16f)
                    glassEnabled -> tokens.colors.textPrimary.copy(alpha = 0.05f)
                    else -> tokens.colors.surface
                },
            )
            .border(1.dp, border, RowShape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = if (compact) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChannelLogo(
            channel = channel,
            modifier = Modifier.size(if (compact) 36.dp else 44.dp),
            shape = RoundedCornerShape(9.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = channel.name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.hasVersions) {
                    VersionBadge(count = row.versions.size, onClick = onVersionsClick)
                }
            }
            if (current != null) {
                Text(
                    text = current.title,
                    modifier = if (compact) Modifier else Modifier.clickable(onClick = onGuideClick),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) LiveProgressBar(progress)
        }
        if (!compact) {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isFavorite) tokens.colors.warning else tokens.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun VersionBadge(count: Int, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tokens.colors.accent.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(11.dp), tint = tokens.colors.accent)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LiveProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(tokens.colors.textPrimary.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(tokens.colors.accent),
        )
    }
}

@Composable
private fun LiveTvPreviewPane(
    row: LiveTvMergedChannel?,
    previewRow: LiveTvMergedChannel?,
    playing: Boolean,
    isFavorite: Boolean,
    programs: List<LiveTvEpgProgram>,
    nowMillis: Long,
    glassEnabled: Boolean,
    onWatch: (LiveTvChannel) -> Unit,
    onFavoriteClick: (LiveTvChannel) -> Unit,
    onVersionsClick: (LiveTvMergedChannel) -> Unit,
    onGuideClick: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier
            .liveTvPanel(glassEnabled, PanelShape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val channel = row?.primary
            if (channel != null) {
                ChannelLogo(
                    channel = channel,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    iconTint = Color.White.copy(alpha = 0.6f),
                )
            }
            val target = previewRow?.primary
            // Only the channel the highlight rests on plays; a fast scroll never starts a player.
            if (playing && target != null && target.id == channel?.id) {
                key(target.id) {
                    LiveTvPreviewPlayer(channel = target, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (row == null) {
            Text(
                text = stringResource(Res.string.live_tv_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            return@Column
        }

        val channel = row.primary
        val current = remember(programs, nowMillis) { programs.currentProgram(nowMillis) }
        val next = remember(programs, nowMillis) { programs.nextProgram(nowMillis) }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (current != null) {
                Column(
                    modifier = Modifier.clickable { onGuideClick(channel) },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = liveTvEpgTimeRange(current),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                    LiveProgressBar(current.progressAt(nowMillis))
                }
            }
            if (next != null) {
                Text(
                    text = stringResource(
                        Res.string.live_tv_preview_next,
                        "${formatLiveTvEpgTime(next.startMillis)} ${next.title}",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.hasVersions) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(tokens.colors.accent.copy(alpha = 0.16f))
                        .clickable { onVersionsClick(row) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.colors.accent)
                    Text(
                        text = stringResource(Res.string.live_tv_versions_count, row.versions.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.accent,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.colors.accent)
                    .clickable { onWatch(channel) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(Res.string.action_play),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.colors.textPrimary.copy(alpha = 0.08f))
                    .clickable { onFavoriteClick(channel) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorite) tokens.colors.warning else tokens.colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** A muted live player for the preview pane. It moves to a backup Xtream server if one fails. */
@Composable
private fun LiveTvPreviewPlayer(channel: LiveTvChannel, modifier: Modifier = Modifier) {
    val prepared by produceState<LiveTvChannel?>(null, channel.id) {
        value = runCatching { LiveTvRepository.prepareForPlayback(channel) }.getOrDefault(channel)
    }
    val playable = prepared ?: return
    var url by remember(playable.id) { mutableStateOf(playable.streamUrl) }
    var isLoading by remember(playable.id) { mutableStateOf(true) }
    var failed by remember(playable.id) { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!failed) {
            key(url) {
                PlatformPlayerSurface(
                    sourceUrl = url,
                    sourceHeaders = playable.headers,
                    streamType = playable.streamType,
                    modifier = Modifier.fillMaxSize(),
                    playWhenReady = true,
                    liveStream = true,
                    onControllerReady = { controller -> controller.setMuted(true) },
                    onSnapshot = { snapshot -> isLoading = snapshot.isLoading },
                    onError = { message ->
                        if (message != null) {
                            val next = LiveTvRepository.nextXtreamServerUrl(url)
                            if (next != null) url = next else failed = true
                        }
                    },
                )
            }
        }
        AnimatedVisibility(visible = isLoading && !failed, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }
        if (failed) {
            Text(
                text = stringResource(Res.string.live_tv_preview_unavailable),
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun LiveTvVersionSheet(
    row: LiveTvMergedChannel,
    epgPrograms: Map<String, List<LiveTvEpgProgram>>,
    nowMillis: Long,
    onChoose: (LiveTvChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = { scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) } }
    NuvioModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.live_tv_versions_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
        )
        row.versions.forEachIndexed { index, version ->
            val isDefault = version.id == row.primary.id
            val current = epgPrograms[version.id]?.currentProgram(nowMillis)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChoose(version) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDefault) tokens.colors.accent else tokens.colors.textPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = liveTvVersionLabel(version, row.versions, index),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDefault) Color.White else tokens.colors.textPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = version.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail = listOfNotNull(
                        version.playlistName,
                        current?.title,
                        if (isDefault) stringResource(Res.string.live_tv_versions_default) else null,
                    ).joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isDefault) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.accent)
                }
            }
        }
    }
}

/**
 * A panel of the sidebar layout. With glass on, a translucent frosted panel with a hairline edge
 * (blurring the app's background when the app's glass theme is on); otherwise a plain surface.
 * Drawn once per layout, so it costs nothing while the lists inside it scroll.
 */
@Composable
internal fun Modifier.liveTvPanel(glassEnabled: Boolean, shape: Shape): Modifier {
    val tokens = MaterialTheme.nuvio
    if (!glassEnabled) return this.clip(shape).background(tokens.colors.surface)
    val edge = tokens.colors.textPrimary
    return this
        .appGlassBackdrop(shape)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(edge.copy(alpha = 0.10f), edge.copy(alpha = 0.04f)),
            ),
        )
        .border(1.dp, edge.copy(alpha = 0.12f), shape)
}


package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.live_tv_sort_az
import nuvio.composeapp.generated.resources.live_tv_sort_categories
import nuvio.composeapp.generated.resources.live_tv_sort_count
import nuvio.composeapp.generated.resources.live_tv_sort_custom
import nuvio.composeapp.generated.resources.live_tv_sort_custom_hint
import nuvio.composeapp.generated.resources.live_tv_sort_playlist
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Chooses how categories are ordered. With Custom order, the categories can be moved with the arrows
 * (or straight to the top), and the arranged order is saved with Done.
 */
@Composable
internal fun LiveTvCategorySortSheet(
    mode: LiveTvCategorySortMode,
    categories: List<String>,
    onModeSelected: (LiveTvCategorySortMode) -> Unit,
    onCustomOrderSaved: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = { scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) } }
    var selectedMode by remember { mutableStateOf(mode) }
    // Starts from the order shown now, so switching to Custom keeps what the person sees.
    val order = remember { mutableStateListOf<String>().apply { addAll(categories) } }

    NuvioModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.live_tv_sort_categories),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.colors.accent)
                    .clickable {
                        if (selectedMode == LiveTvCategorySortMode.Custom) {
                            onCustomOrderSaved(order.toList())
                        } else {
                            onModeSelected(selectedMode)
                        }
                        dismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.action_done),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        LiveTvCategorySortMode.entries.forEach { option ->
            val isSelected = option == selectedMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedMode = option }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(option.label()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) tokens.colors.accent else tokens.colors.textPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.accent)
            }
        }

        if (selectedMode == LiveTvCategorySortMode.Custom) {
            Text(
                text = stringResource(Res.string.live_tv_sort_custom_hint),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                itemsIndexed(order, key = { _, name -> name }) { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier.padding(end = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textMuted,
                        )
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { order.move(index, 0) }, enabled = index > 0) {
                            Icon(Icons.Rounded.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { order.move(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                        }
                        IconButton(onClick = { order.move(index, index + 1) }, enabled = index < order.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(bottom = 12.dp)) {}
    }
}

private fun MutableList<String>.move(from: Int, to: Int) {
    if (from !in indices || to !in indices || from == to) return
    add(to, removeAt(from))
}

private fun LiveTvCategorySortMode.label(): StringResource = when (this) {
    LiveTvCategorySortMode.Playlist -> Res.string.live_tv_sort_playlist
    LiveTvCategorySortMode.Alphabetical -> Res.string.live_tv_sort_az
    LiveTvCategorySortMode.MostChannels -> Res.string.live_tv_sort_count
    LiveTvCategorySortMode.Custom -> Res.string.live_tv_sort_custom
}

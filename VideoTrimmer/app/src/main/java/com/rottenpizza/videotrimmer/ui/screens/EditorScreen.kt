package com.rottenpizza.videotrimmer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.rottenpizza.videotrimmer.model.TrimRange
import com.rottenpizza.videotrimmer.ui.TrimViewModel
import com.rottenpizza.videotrimmer.ui.components.RangeTrimBar
import com.rottenpizza.videotrimmer.ui.components.VideoPreview
import com.rottenpizza.videotrimmer.util.Format
import com.rottenpizza.videotrimmer.util.MediaActions
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun EditorScreen(
    vm: TrimViewModel,
    onPickVideo: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val source = state.source

    if (source == null) {
        EmptyPicker(loading = state.loading, onPickVideo = onPickVideo)
        return
    }

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            playWhenReady = false
        }
    }
    LaunchedEffect(source.uri) {
        player.setMediaItem(MediaItem.fromUri(source.uri))
        player.prepare()
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    var previewing by remember { mutableStateOf(false) }
    LaunchedEffect(previewing) {
        // Play the current selection, then stop at its end handle.
        while (previewing) {
            if (player.currentPosition >= state.draftEndMs ||
                player.playbackState == Player.STATE_ENDED
            ) {
                player.pause()
                previewing = false
            }
            delay(80)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SourceHeader(source.displayName, onClose = { vm.clearVideo() }) }

            item {
                VideoPreview(
                    player = player,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            (source.displayWidth.toFloat() / source.displayHeight.coerceAtLeast(1))
                                .coerceIn(0.4f, 2.5f),
                        )
                        .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(12.dp)),
                )
            }

            item { InfoChips(source.durationLabel(), source.resolutionLabel(), Format.fileSize(source.sizeBytes), source.formatLabel()) }

            item {
                RangeTrimBar(
                    durationMs = source.durationMs,
                    startMs = state.draftStartMs,
                    endMs = state.draftEndMs,
                    onChange = { s, e, active, _ ->
                        vm.setDraft(s, e)
                        previewing = false
                        player.pause()
                        // Preview follows whichever handle the user is dragging.
                        player.seekTo(active)
                    },
                    onChangeFinished = {},
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            player.seekTo(state.draftStartMs)
                            player.play()
                            previewing = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Preview")
                    }
                    FilledTonalButton(
                        onClick = { vm.addCurrentRange() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add cut")
                    }
                }
            }

            if (state.ranges.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Queue (${state.ranges.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { vm.clearRanges() }) { Text("Clear") }
                    }
                }
                items(state.ranges, key = { it.id }) { range ->
                    RangeRow(
                        range = range,
                        onName = { vm.updateRangeName(range.id, it) },
                        onDelete = { vm.removeRange(range.id) },
                    )
                }
            }

            item {
                Button(
                    onClick = { vm.trim() },
                    enabled = state.canTrim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.ContentCut, null)
                    Spacer(Modifier.width(8.dp))
                    val count = if (state.ranges.isEmpty()) 1 else state.ranges.size
                    Text(
                        if (state.ranges.isEmpty()) "Trim selection"
                        else "Trim $count clips",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            state.lastResults?.let { results ->
                val ok = results.filter { it.success }
                if (ok.isNotEmpty()) {
                    item {
                        Text("Saved", style = MaterialTheme.typography.titleMedium)
                    }
                    items(ok) { r ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.VideoLibrary, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    r.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                r.uri?.let { u ->
                                    IconButton(onClick = { MediaActions.share(context, u) }) {
                                        Icon(Icons.Filled.Share, "Share")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = onOpenLibrary) { Text("Open library →") }
                    }
                }
            }
        }

        if (state.isTrimming) {
            TrimmingOverlay(
                fraction = state.progress?.fraction ?: 0f,
                label = state.progress?.let {
                    "Clip ${(it.currentIndex + 1).coerceAtMost(it.total)}/${it.total}  ${it.currentName}"
                } ?: "Trimming…",
            )
        }
    }
}

@Composable
private fun SourceHeader(name: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close video") }
    }
}

@Composable
private fun InfoChips(duration: String, resolution: String, size: String, format: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(duration, resolution, size, format).forEach { text ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeRow(range: TrimRange, onName: (String) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = range.name,
                    onValueChange = onName,
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${Format.durationPrecise(range.startMs)} → ${Format.durationPrecise(range.endMs)}  " +
                        "(${Format.durationPrecise(range.durationMs)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TrimmingOverlay(fraction: Float, label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator()
                Text("Trimming", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyPicker(loading: Boolean, onPickVideo: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Filled.VideoLibrary,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Trim videos, instantly", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Lossless, on-device, no re-encoding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPickVideo, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pick a video")
                }
            }
        }
    }
}

// --- Small label helpers kept local to the editor. ---
private fun com.rottenpizza.videotrimmer.model.VideoInfo.durationLabel() = Format.duration(durationMs)
private fun com.rottenpizza.videotrimmer.model.VideoInfo.resolutionLabel() = "${displayWidth}×${displayHeight}"
private fun com.rottenpizza.videotrimmer.model.VideoInfo.formatLabel() =
    mimeType.substringAfterLast('/').uppercase()

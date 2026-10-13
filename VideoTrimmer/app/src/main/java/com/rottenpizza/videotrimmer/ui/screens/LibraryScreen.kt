package com.rottenpizza.videotrimmer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rottenpizza.videotrimmer.data.ThumbnailCache
import com.rottenpizza.videotrimmer.model.SortDirection
import com.rottenpizza.videotrimmer.model.SortKey
import com.rottenpizza.videotrimmer.model.TrimmedClip
import com.rottenpizza.videotrimmer.ui.LibraryViewModel
import com.rottenpizza.videotrimmer.util.Format
import com.rottenpizza.videotrimmer.util.MediaActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(vm: LibraryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.refresh() }

    var renameTarget by remember { mutableStateOf<TrimmedClip?>(null) }
    var deleteTarget by remember { mutableStateOf<TrimmedClip?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        SortControls(
            sortKey = state.sortKey,
            direction = state.sortDirection,
            onKey = vm::setSortKey,
            onToggleDirection = vm::toggleDirection,
        )

        Spacer(Modifier.height(8.dp))

        when {
            state.loading && state.clips.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.clips.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No trimmed clips yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.sorted, key = { it.uri.toString() }) { clip ->
                        ClipRow(
                            clip = clip,
                            onPlay = { MediaActions.view(context, clip.uri) },
                            onShare = { MediaActions.share(context, clip.uri) },
                            onRename = { renameTarget = clip },
                            onDelete = { deleteTarget = clip },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { clip ->
        RenameDialog(
            currentName = clip.displayName.substringBeforeLast('.', clip.displayName),
            onConfirm = { newName ->
                vm.rename(clip, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { clip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete clip?") },
            text = { Text(clip.displayName) },
            confirmButton = {
                TextButton(onClick = { vm.delete(clip); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SortControls(
    sortKey: SortKey,
    direction: SortDirection,
    onKey: (SortKey) -> Unit,
    onToggleDirection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Sort,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortKey.entries.forEach { key ->
                FilterChip(
                    selected = sortKey == key,
                    onClick = { onKey(key) },
                    label = { Text(key.label) },
                )
            }
        }
        IconButton(onClick = onToggleDirection) {
            Icon(
                if (direction == SortDirection.ASCENDING) Icons.Filled.ArrowUpward
                else Icons.Filled.ArrowDownward,
                contentDescription = "Toggle sort direction",
            )
        }
    }
}

@Composable
private fun ClipRow(
    clip: TrimmedClip,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(clip)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        clip.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${Format.duration(clip.durationMs)} · ${Format.fileSize(clip.sizeBytes)} · ${Format.date(clip.dateAddedMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Play") }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "Rename") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(clip: TrimmedClip) {
    val context = LocalContext.current
    val key = clip.uri.toString() + clip.displayName
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = ThumbnailCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { ThumbnailCache.load(context, clip.uri, key) }
        }
    }
    Box(
        modifier = Modifier
            .size(84.dp, 56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(8.dp)),
            )
        } else {
            Icon(
                Icons.Filled.VideoLibrary,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue(currentName, selection = androidx.compose.ui.text.TextRange(0, currentName.length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename clip") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.text.trim()) },
                enabled = text.text.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

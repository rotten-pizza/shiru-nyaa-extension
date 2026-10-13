package com.rottenpizza.videotrimmer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rottenpizza.videotrimmer.util.Format

/** Which handle the user is currently dragging. */
enum class ActiveHandle { START, END }

/**
 * Two-handle range selector over the full clip duration, with a live
 * start / end / selected-duration readout underneath.
 *
 * [onChange] reports the new range plus the timestamp of the handle that moved,
 * so the caller can seek a preview player to follow it.
 */
@Composable
fun RangeTrimBar(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onChange: (start: Long, end: Long, activeHandleMs: Long, handle: ActiveHandle) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = durationMs.toFloat().coerceAtLeast(1f)
    Column(modifier = modifier.fillMaxWidth()) {
        RangeSlider(
            value = startMs.toFloat().coerceIn(0f, max)..endMs.toFloat().coerceIn(0f, max),
            valueRange = 0f..max,
            onValueChange = { r ->
                val ns = r.start.toLong()
                val ne = r.endInclusive.toLong()
                val handle = if (ns != startMs) ActiveHandle.START else ActiveHandle.END
                val active = if (handle == ActiveHandle.START) ns else ne
                onChange(ns, ne, active, handle)
            },
            onValueChangeFinished = onChangeFinished,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Readout("Start", Format.durationPrecise(startMs), TextAlign.Start, Modifier.weight(1f))
            Readout("Selected", Format.durationPrecise((endMs - startMs).coerceAtLeast(0)), TextAlign.Center, Modifier.weight(1f))
            Readout("End", Format.durationPrecise(endMs), TextAlign.End, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Readout(label: String, value: String, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package com.rottenpizza.videotrimmer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rottenpizza.videotrimmer.data.VideoMetadataReader
import com.rottenpizza.videotrimmer.model.TrimProgress
import com.rottenpizza.videotrimmer.model.TrimRange
import com.rottenpizza.videotrimmer.model.TrimResult
import com.rottenpizza.videotrimmer.model.VideoInfo
import com.rottenpizza.videotrimmer.trim.TrimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val source: VideoInfo? = null,
    val loading: Boolean = false,
    val draftStartMs: Long = 0L,
    val draftEndMs: Long = 0L,
    val ranges: List<TrimRange> = emptyList(),
    val isTrimming: Boolean = false,
    val progress: TrimProgress? = null,
    val lastResults: List<TrimResult>? = null,
    val message: String? = null,
) {
    val draftDurationMs: Long get() = (draftEndMs - draftStartMs).coerceAtLeast(0)
    val canTrim: Boolean get() = source != null && !isTrimming &&
        (ranges.isNotEmpty() || draftDurationMs > 0)
}

class TrimViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TrimRepository(app)
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var nextRangeId = 1L

    fun loadVideo(uri: Uri) {
        _state.update { it.copy(loading = true, message = null, lastResults = null) }
        viewModelScope.launch {
            val info = try {
                withContext(Dispatchers.IO) { VideoMetadataReader.read(getApplication(), uri) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = "Couldn't read video: ${e.message}") }
                return@launch
            }
            nextRangeId = 1L
            _state.update {
                EditorUiState(
                    source = info,
                    draftStartMs = 0L,
                    draftEndMs = info.durationMs,
                )
            }
        }
    }

    fun clearVideo() {
        _state.value = EditorUiState()
    }

    fun setDraft(startMs: Long, endMs: Long) {
        val dur = _state.value.source?.durationMs ?: return
        val s = startMs.coerceIn(0, dur)
        val e = endMs.coerceIn(0, dur)
        _state.update { it.copy(draftStartMs = minOf(s, e), draftEndMs = maxOf(s, e)) }
    }

    fun addCurrentRange() {
        val st = _state.value
        val source = st.source ?: return
        if (st.draftDurationMs <= 0) {
            _state.update { it.copy(message = "Selection is empty") }
            return
        }
        val defaultName = "${source.baseName}_${st.ranges.size + 1}"
        val range = TrimRange(nextRangeId++, st.draftStartMs, st.draftEndMs, defaultName)
        _state.update { it.copy(ranges = it.ranges + range) }
    }

    fun updateRangeName(id: Long, name: String) {
        _state.update { s ->
            s.copy(ranges = s.ranges.map { if (it.id == id) it.copy(name = name) else it })
        }
    }

    fun removeRange(id: Long) {
        _state.update { s -> s.copy(ranges = s.ranges.filterNot { it.id == id }) }
    }

    fun clearRanges() {
        _state.update { it.copy(ranges = emptyList()) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun consumeResults() {
        _state.update { it.copy(lastResults = null) }
    }

    fun trim() {
        val st = _state.value
        val source = st.source ?: return
        if (st.isTrimming) return

        // Queued ranges if any, otherwise the current draft as a single range.
        val ranges = if (st.ranges.isNotEmpty()) {
            st.ranges
        } else {
            listOf(TrimRange(0, st.draftStartMs, st.draftEndMs, "${source.baseName}_trimmed"))
        }
        if (ranges.all { it.durationMs <= 0 }) {
            _state.update { it.copy(message = "Nothing to trim") }
            return
        }

        _state.update { it.copy(isTrimming = true, progress = TrimProgress(0, ranges.size, "", 0f)) }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                repo.trimAll(source, ranges) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
            }
            val ok = results.count { it.success }
            val summary = when {
                ok == results.size -> "Saved $ok clip${if (ok == 1) "" else "s"} to gallery"
                ok == 0 -> "Trim failed"
                else -> "Saved $ok of ${results.size} clips"
            }
            _state.update {
                it.copy(
                    isTrimming = false,
                    progress = null,
                    lastResults = results,
                    message = summary,
                )
            }
        }
    }
}

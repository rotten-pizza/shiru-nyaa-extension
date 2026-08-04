package com.rottenpizza.videotrimmer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rottenpizza.videotrimmer.data.GalleryStore
import com.rottenpizza.videotrimmer.data.ThumbnailCache
import com.rottenpizza.videotrimmer.model.SortDirection
import com.rottenpizza.videotrimmer.model.SortKey
import com.rottenpizza.videotrimmer.model.TrimmedClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val clips: List<TrimmedClip> = emptyList(),
    val sortKey: SortKey = SortKey.DATE,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val loading: Boolean = false,
    val message: String? = null,
) {
    val sorted: List<TrimmedClip>
        get() {
            val cmp: Comparator<TrimmedClip> = when (sortKey) {
                SortKey.DATE -> compareBy { it.dateAddedMs }
                SortKey.SIZE -> compareBy { it.sizeBytes }
                SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                SortKey.DURATION -> compareBy { it.durationMs }
            }
            return if (sortDirection == SortDirection.ASCENDING) clips.sortedWith(cmp)
            else clips.sortedWith(cmp.reversed())
        }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val clips = withContext(Dispatchers.IO) { GalleryStore.queryLibrary(getApplication()) }
            _state.update { it.copy(clips = clips, loading = false) }
        }
    }

    fun setSortKey(key: SortKey) {
        _state.update { it.copy(sortKey = key) }
    }

    fun toggleDirection() {
        _state.update {
            it.copy(
                sortDirection = if (it.sortDirection == SortDirection.ASCENDING)
                    SortDirection.DESCENDING else SortDirection.ASCENDING,
            )
        }
    }

    fun rename(clip: TrimmedClip, newName: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                GalleryStore.rename(getApplication(), clip.uri, newName)
            }
            if (ok) {
                ThumbnailCache.evict(clip.uri.toString() + clip.displayName)
                refresh()
            } else {
                _state.update { it.copy(message = "Rename failed") }
            }
        }
    }

    fun delete(clip: TrimmedClip) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { GalleryStore.delete(getApplication(), clip.uri) }
            if (ok) refresh() else _state.update { it.copy(message = "Delete failed") }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

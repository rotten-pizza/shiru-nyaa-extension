package com.rottenpizza.videotrimmer.trim

import android.content.Context
import com.rottenpizza.videotrimmer.data.AppPrefs
import com.rottenpizza.videotrimmer.data.GalleryStore
import com.rottenpizza.videotrimmer.data.MoovPatcher
import com.rottenpizza.videotrimmer.model.TrimProgress
import com.rottenpizza.videotrimmer.model.TrimRange
import com.rottenpizza.videotrimmer.model.TrimResult
import com.rottenpizza.videotrimmer.model.VideoInfo

/**
 * Orchestrates exporting one or more ranges from a single source in one batch.
 * Each range is a fresh seek-and-stream-copy, so the whole batch stays fast and
 * lossless. Reports batch-wide progress as it goes.
 */
class TrimRepository(private val context: Context) {

    private val prefs = AppPrefs(context)

    /**
     * Exports every [ranges] entry as its own clip. Names are made unique so two
     * ranges never collide. [onProgress] fires across the whole batch.
     */
    fun trimAll(
        source: VideoInfo,
        ranges: List<TrimRange>,
        onProgress: (TrimProgress) -> Unit,
    ): List<TrimResult> {
        val results = ArrayList<TrimResult>(ranges.size)
        val usedNames = HashSet<String>()
        val total = ranges.size

        ranges.forEachIndexed { index, range ->
            val name = uniqueName(range.name.ifBlank { "${source.baseName}_${index + 1}" }, usedNames)
            usedNames += name.lowercase()
            onProgress(TrimProgress(index, total, name, index.toFloat() / total))

            val result = trimSingle(source, range, name) { frac ->
                onProgress(TrimProgress(index, total, name, (index + frac) / total))
            }
            results += result
        }
        onProgress(TrimProgress(total, total, "", 1f))
        return results
    }

    private fun trimSingle(
        source: VideoInfo,
        range: TrimRange,
        name: String,
        onProgress: (Float) -> Unit,
    ): TrimResult {
        val uri = try {
            GalleryStore.createPending(context, name, source.dateTakenMs)
        } catch (e: Exception) {
            return TrimResult(name, null, e.message ?: "Could not create output")
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                StreamCopyTrimmer.trim(
                    context = context,
                    source = source.uri,
                    startUs = range.startMs * 1000L,
                    endUs = range.endMs * 1000L,
                    outputFd = pfd.fileDescriptor,
                    rotationDegrees = source.rotationDegrees,
                    onProgress = onProgress,
                )
            }

            if (prefs.patchMoovTime && source.dateTakenMs > 0) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                        MoovPatcher.patch(pfd.fileDescriptor, source.dateTakenMs)
                    }
                } catch (_: Exception) {
                    // Non-fatal: the clip is already valid with the correct DATE_TAKEN.
                }
            }

            GalleryStore.finalize(context, uri, range.durationMs)
            return TrimResult(name, uri, null)
        } catch (e: Exception) {
            GalleryStore.deleteQuietly(context, uri)
            return TrimResult(name, null, e.message ?: "Trim failed")
        }
    }

    private fun uniqueName(base: String, used: Set<String>): String {
        val clean = base.trim().ifBlank { "clip" }
        if (clean.lowercase() !in used) return clean
        var n = 2
        while ("${clean}_$n".lowercase() in used) n++
        return "${clean}_$n"
    }
}

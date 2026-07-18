package com.rottenpizza.videotrimmer.trim

import android.content.Context
import com.rottenpizza.videotrimmer.data.AppPrefs
import com.rottenpizza.videotrimmer.data.GalleryStore
import com.rottenpizza.videotrimmer.data.MoovPatcher
import com.rottenpizza.videotrimmer.model.TrimProgress
import com.rottenpizza.videotrimmer.model.TrimRange
import com.rottenpizza.videotrimmer.model.TrimResult
import com.rottenpizza.videotrimmer.model.VideoInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

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

        // Mux into fast local storage first, then stream the finished file into
        // the gallery in one sequential pass. Doing the muxing straight to the
        // MediaStore fd is what made large trims crawl.
        //
        // Prefer internal cache (real filesystem, no FUSE) so the muxer's many
        // small writes are fast; fall back to external app-cache only when the
        // estimated output wouldn't fit internally.
        val estimatedBytes = if (source.durationMs > 0) {
            source.sizeBytes * range.durationMs / source.durationMs
        } else {
            source.sizeBytes
        }
        val internal = context.cacheDir
        val cacheRoot = if (internal.usableSpace > estimatedBytes * 13 / 10) {
            internal
        } else {
            context.externalCacheDir ?: internal
        }
        val temp = File(cacheRoot, "trim_${System.nanoTime()}.mp4")
        try {
            StreamCopyTrimmer.trim(
                context = context,
                source = source.uri,
                startUs = range.startMs * 1000L,
                endUs = range.endMs * 1000L,
                output = temp,
                rotationDegrees = source.rotationDegrees,
                // Muxing is the bulk of the work; reserve the last slice for the copy.
                onProgress = { onProgress(it * 0.9f) },
            )

            if (prefs.patchMoovTime && source.dateTakenMs > 0) {
                try {
                    RandomAccessFile(temp, "rw").use { raf ->
                        MoovPatcher.patch(raf.fd, source.dateTakenMs)
                    }
                } catch (_: Exception) {
                    // Non-fatal: the clip is already valid with the correct DATE_TAKEN.
                }
            }

            copyToGallery(temp, uri) { onProgress(0.9f + it * 0.1f) }

            GalleryStore.finalize(context, uri, range.durationMs)
            return TrimResult(name, uri, null)
        } catch (e: Exception) {
            GalleryStore.deleteQuietly(context, uri)
            return TrimResult(name, null, e.message ?: "Trim failed")
        } finally {
            temp.delete()
        }
    }

    /**
     * Copies the finished cache file into the gallery with a single large
     * sequential transfer. NIO's transferTo lets the kernel move the bytes
     * (sendfile) where possible, which handles multi-GB outputs far better than
     * a byte-array read/write loop and stays fast over FUSE.
     */
    private fun copyToGallery(source: File, dest: android.net.Uri, onProgress: (Float) -> Unit) {
        FileInputStream(source).use { input ->
            context.contentResolver.openFileDescriptor(dest, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    val inCh = input.channel
                    val outCh = out.channel
                    val total = inCh.size().coerceAtLeast(1)
                    val chunk = 8L shl 20 // 8 MB per transfer
                    var pos = 0L
                    var lastEmit = -1f
                    while (pos < total) {
                        val moved = inCh.transferTo(pos, chunk, outCh)
                        if (moved <= 0) break
                        pos += moved
                        val frac = (pos.toFloat() / total).coerceIn(0f, 1f)
                        if (frac - lastEmit >= 0.02f) {
                            lastEmit = frac
                            onProgress(frac)
                        }
                    }
                    out.flush()
                }
            } ?: throw java.io.IOException("Could not open gallery file for writing")
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

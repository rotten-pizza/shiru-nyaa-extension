package com.rottenpizza.videotrimmer.trim

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Trims a video by copying encoded samples straight from the source into a new
 * MP4 container. No decode, no encode, no re-compression — this is why a
 * multi-gigabyte clip trims in a second or two and comes out bit-identical.
 *
 * Cuts snap to the nearest keyframe at or before the requested start
 * ([MediaExtractor.SEEK_TO_PREVIOUS_SYNC]); frame accuracy is not a goal.
 */
object StreamCopyTrimmer {

    /** Fallback per-sample buffer when a track doesn't advertise a max input size. */
    private const val DEFAULT_MAX_INPUT_SIZE = 4 * 1024 * 1024 // 4 MB (covers 4K keyframes)

    /**
     * Copies the range [[startUs], [endUs]] of every audio/video track from
     * [source] into the MP4 written to the local file [output].
     *
     * Writing to a real filesystem path (not a MediaStore fd) is deliberate:
     * MediaMuxer emits many small, seeking writes and rewrites the moov atom on
     * stop(), which is pathologically slow over a FUSE-backed content fd. The
     * caller copies the finished file into the gallery in one sequential pass.
     *
     * @param rotationDegrees written to the container via [MediaMuxer.setOrientationHint].
     * @param onProgress fraction in [0f, 1f] for this single range; throttled internally.
     * @throws IOException on unreadable input or an empty selection.
     */
    fun trim(
        context: Context,
        source: Uri,
        startUs: Long,
        endUs: Long,
        output: File,
        rotationDegrees: Int,
        onProgress: (Float) -> Unit = {},
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, source, null)

            // Map source tracks -> muxer tracks, keeping only audio and video.
            val srcToDst = LinkedHashMap<Int, Int>()
            var bufferSize = 0
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                val dst = muxer.addTrack(format)
                srcToDst[i] = dst
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            if (srcToDst.isEmpty()) throw IOException("No audio or video tracks in source")
            if (bufferSize <= 0) bufferSize = DEFAULT_MAX_INPUT_SIZE

            muxer.setOrientationHint(rotationDegrees)
            muxer.start()

            // Select every mapped track and walk the file in storage order with a
            // single seek + advance() loop. This reads the source sequentially —
            // the interleaved on-disk layout is followed as-is — instead of
            // draining one track at a time, which strides randomly across the
            // whole file (twice) and crawls over a content/FUSE source.
            for (src in srcToDst.keys) extractor.selectTrack(src)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            // A single common offset keeps audio and video in sync: the earliest
            // sample time at the seek position, so every written timestamp >= 0.
            val offsetUs = extractor.sampleTime.let { if (it < 0) startUs else it }

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            val rangeUs = (endUs - startUs).coerceAtLeast(1)

            // Each track stops once its samples pass endUs; the loop ends when all
            // tracks are done or the stream is exhausted. Progress is throttled to
            // ~1% steps so the UI isn't flooded with per-sample recompositions.
            val finished = HashSet<Int>()
            var lastProgress = -1f
            while (finished.size < srcToDst.size) {
                val srcTrack = extractor.sampleTrackIndex
                if (srcTrack < 0) break                       // end of stream
                val dst = srcToDst[srcTrack]
                if (dst == null) {
                    if (!extractor.advance()) break
                    continue
                }
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break
                if (sampleTime > endUs) {
                    finished.add(srcTrack)                    // this track is complete
                    if (!extractor.advance()) break
                    continue
                }
                if (srcTrack in finished) {                    // already past its end
                    if (!extractor.advance()) break
                    continue
                }
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0)
                buffer.limit(size)
                info.offset = 0
                info.size = size
                info.presentationTimeUs = (sampleTime - offsetUs).coerceAtLeast(0)
                info.flags = sampleFlagsToBufferFlags(extractor.sampleFlags)
                muxer.writeSampleData(dst, buffer, info)

                val frac = ((sampleTime - startUs).toFloat() / rangeUs).coerceIn(0f, 1f)
                if (frac - lastProgress >= 0.01f) {
                    lastProgress = frac
                    onProgress(frac)
                }

                if (!extractor.advance()) break
            }
            onProgress(1f)
        } finally {
            // stop() must succeed for the moov atom to be written; release always.
            try {
                muxer?.stop()
            } catch (_: Exception) {
                // If stop throws (e.g. no samples written) the output is invalid;
                // the caller treats a thrown trim as a failure.
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    private fun sampleFlagsToBufferFlags(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }
}

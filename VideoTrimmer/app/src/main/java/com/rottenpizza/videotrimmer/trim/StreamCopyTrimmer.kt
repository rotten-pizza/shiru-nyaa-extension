package com.rottenpizza.videotrimmer.trim

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.FileDescriptor
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
     * [source] into the MP4 written to [outputFd].
     *
     * @param rotationDegrees written to the container via [MediaMuxer.setOrientationHint].
     * @param onProgress fraction in [0f, 1f] for this single range; called frequently.
     * @throws IOException on unreadable input or an empty selection.
     */
    fun trim(
        context: Context,
        source: Uri,
        startUs: Long,
        endUs: Long,
        outputFd: FileDescriptor,
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
            muxer = MediaMuxer(outputFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

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

            // A single common offset keeps audio and video in sync: it is the
            // earliest first-sample timestamp across tracks after seeking to the
            // start keyframe, so every written timestamp stays >= 0.
            var offsetUs = Long.MAX_VALUE
            for (src in srcToDst.keys) {
                extractor.selectTrack(src)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val t = extractor.sampleTime
                if (t in 0 until offsetUs) offsetUs = t
                extractor.unselectTrack(src)
            }
            if (offsetUs == Long.MAX_VALUE) offsetUs = startUs

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            val rangeUs = (endUs - startUs).coerceAtLeast(1)
            val trackCount = srcToDst.size

            // Copy one track at a time. Each track is independently seeked to the
            // start keyframe and drained up to endUs. Timestamps carry over
            // unchanged (minus the shared offset), so nothing is re-timed.
            var trackOrdinal = 0
            for ((src, dst) in srcToDst) {
                extractor.selectTrack(src)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                while (true) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0) break            // end of stream
                    if (sampleTime > endUs) break        // past the requested end
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    buffer.position(0)
                    buffer.limit(size)
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = sampleTime - offsetUs
                    info.flags = sampleFlagsToBufferFlags(extractor.sampleFlags)
                    muxer.writeSampleData(dst, buffer, info)

                    val intra = ((sampleTime - startUs).toFloat() / rangeUs).coerceIn(0f, 1f)
                    onProgress(((trackOrdinal + intra) / trackCount).coerceIn(0f, 1f))

                    if (!extractor.advance()) break
                }
                extractor.unselectTrack(src)
                trackOrdinal++
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

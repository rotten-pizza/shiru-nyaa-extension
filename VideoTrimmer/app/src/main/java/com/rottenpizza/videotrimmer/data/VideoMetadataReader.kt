package com.rottenpizza.videotrimmer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.rottenpizza.videotrimmer.model.VideoInfo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Reads display + technical metadata for a source video Uri. */
object VideoMetadataReader {

    fun read(context: Context, uri: Uri): VideoInfo {
        val (name, size) = queryNameAndSize(context, uri)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)

            val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val mime = context.contentResolver.getType(uri)
                ?: meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: "video/mp4"
            val dateTaken = parseCaptureDate(meta(MediaMetadataRetriever.METADATA_KEY_DATE))

            return VideoInfo(
                uri = uri,
                displayName = name,
                durationMs = duration,
                width = width,
                height = height,
                rotationDegrees = ((rotation % 360) + 360) % 360,
                sizeBytes = size,
                mimeType = mime,
                dateTakenMs = dateTaken,
            )
        } finally {
            retriever.release()
        }
    }

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }

        // If the provider gave no name, or a synthetic all-digits id, fall back
        // to the last path segment so the default trim name still reads sensibly.
        if (name.isNullOrBlank() || isSyntheticName(name!!)) {
            val fromPath = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
            if (!fromPath.isNullOrBlank() && !isSyntheticName(fromPath)) name = fromPath
        }

        return (name?.ifBlank { null } ?: "video.mp4") to size
    }

    /** True for a name that is just digits (optionally with an extension). */
    private fun isSyntheticName(name: String): Boolean =
        name.substringBeforeLast('.', name).all { it.isDigit() } &&
            name.substringBeforeLast('.', name).isNotEmpty()

    /**
     * METADATA_KEY_DATE is an ISO-ish string like "20230115T103000.000Z".
     * Returns epoch millis, or 0 if it can't be parsed.
     */
    private fun parseCaptureDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val patterns = listOf(
            "yyyyMMdd'T'HHmmss.SSS'Z'",
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                return fmt.parse(raw.trim())?.time ?: continue
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return 0L
    }
}

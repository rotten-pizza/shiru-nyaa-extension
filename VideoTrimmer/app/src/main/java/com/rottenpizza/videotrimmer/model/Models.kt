package com.rottenpizza.videotrimmer.model

import android.net.Uri

/** Metadata about the loaded source video. */
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val sizeBytes: Long,
    val mimeType: String,
    /** Original capture time in epoch millis, or 0 if unknown. */
    val dateTakenMs: Long,
) {
    /** File name without its extension, used as the default trim name. */
    val baseName: String
        get() = displayName.substringBeforeLast('.', displayName)

    /** Resolution accounting for rotation (what the user actually sees). */
    val displayWidth: Int get() = if (rotationDegrees % 180 == 90) height else width
    val displayHeight: Int get() = if (rotationDegrees % 180 == 90) width else height
}

/** A user-defined range to export, with its own output name. */
data class TrimRange(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val name: String,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

/** A clip already saved to the gallery, shown in the library. */
data class TrimmedClip(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
)

enum class SortKey(val label: String) {
    DATE("Date"),
    SIZE("Size"),
    NAME("Name"),
    DURATION("Duration"),
}

enum class SortDirection { ASCENDING, DESCENDING }

/** Live progress during a batch trim. */
data class TrimProgress(
    val currentIndex: Int,
    val total: Int,
    val currentName: String,
    /** Fraction [0f, 1f] of the whole batch that is complete. */
    val fraction: Float,
)

/** Result of exporting a single range. */
data class TrimResult(
    val name: String,
    val uri: Uri?,
    val error: String?,
) {
    val success: Boolean get() = uri != null
}

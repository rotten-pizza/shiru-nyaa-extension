package com.rottenpizza.videotrimmer.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.rottenpizza.videotrimmer.model.TrimmedClip
import java.io.File

/**
 * All gallery I/O goes through MediaStore so the app stays scoped-storage
 * compliant. Clips are written to Movies/[APP_FOLDER]; the library screen reads
 * back exactly that folder.
 */
object GalleryStore {

    const val APP_FOLDER = "Trimr"
    private val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/$APP_FOLDER"

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    /**
     * Creates a pending MediaStore entry and returns its Uri. The caller writes
     * the MP4 into it (via [Context.getContentResolver] + openFileDescriptor)
     * and then calls [finalize].
     *
     * @param dateTakenMs original capture time so the gallery shows it; 0 = now.
     */
    fun createPending(context: Context, displayName: String, dateTakenMs: Long): Uri {
        val fileName = if (displayName.endsWith(".mp4", ignoreCase = true)) displayName else "$displayName.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            val taken = if (dateTakenMs > 0) dateTakenMs else System.currentTimeMillis()
            put(MediaStore.Video.Media.DATE_TAKEN, taken)
            // DATE_MODIFIED is in seconds. The provider may re-stamp it from the
            // file's mtime, but set it so managed queries can reflect the source.
            put(MediaStore.Video.Media.DATE_MODIFIED, taken / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    APP_FOLDER,
                ).apply { if (!exists()) mkdirs() }
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, File(dir, fileName).absolutePath)
            }
        }
        return context.contentResolver.insert(collection, values)
            ?: throw java.io.IOException("MediaStore rejected new entry for $fileName")
    }

    /** Clears the pending flag (Q+) and refreshes size/duration metadata. */
    fun finalize(context: Context, uri: Uri, durationMs: Long) {
        val values = ContentValues().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            if (durationMs > 0) put(MediaStore.Video.Media.DURATION, durationMs)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    /** Deletes a half-written entry after a failed trim. */
    fun deleteQuietly(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    fun queryLibrary(context: Context): List<TrimmedClip> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%$APP_FOLDER%")
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ?" to arrayOf("%/${Environment.DIRECTORY_MOVIES}/$APP_FOLDER/%")
        }
        val result = ArrayList<TrimmedClip>()
        context.contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                result += TrimmedClip(
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = c.getString(nameIdx) ?: "clip.mp4",
                    durationMs = c.getLong(durIdx),
                    sizeBytes = c.getLong(sizeIdx),
                    dateAddedMs = c.getLong(dateIdx) * 1000L, // DATE_ADDED is seconds
                )
            }
        }
        return result
    }

    /** Renames a clip in place. Returns true on success. */
    fun rename(context: Context, uri: Uri, newDisplayName: String): Boolean {
        val fileName = if (newDisplayName.endsWith(".mp4", ignoreCase = true)) newDisplayName else "$newDisplayName.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        }
        return try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun delete(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        false
    }

    /** Small thumbnail bitmap for the library list. Never throws. */
    fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(0)
                } finally {
                    retriever.release()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

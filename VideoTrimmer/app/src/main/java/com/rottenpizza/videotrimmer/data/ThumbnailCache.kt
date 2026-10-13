package com.rottenpizza.videotrimmer.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache

/** Process-wide small cache so the library list doesn't re-decode thumbnails. */
object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun load(context: Context, uri: Uri, key: String): Bitmap? {
        cache.get(key)?.let { return it }
        val bmp = GalleryStore.loadThumbnail(context, uri) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun evict(key: String) {
        cache.remove(key)
    }
}

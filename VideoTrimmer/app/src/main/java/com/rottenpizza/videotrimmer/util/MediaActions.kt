package com.rottenpizza.videotrimmer.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Share / open helpers for saved MediaStore video URIs. */
object MediaActions {

    fun share(context: Context, uri: Uri, title: String = "Share clip") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun view(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

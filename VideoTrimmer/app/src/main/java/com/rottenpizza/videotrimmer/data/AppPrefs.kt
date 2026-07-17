package com.rottenpizza.videotrimmer.data

import android.content.Context

/** Tiny persisted-settings wrapper. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("trimr_prefs", Context.MODE_PRIVATE)

    /** Experimental: patch the MP4 moov creation_time to the source date. */
    var patchMoovTime: Boolean
        get() = prefs.getBoolean(KEY_PATCH_MOOV, false)
        set(value) = prefs.edit().putBoolean(KEY_PATCH_MOOV, value).apply()

    companion object {
        private const val KEY_PATCH_MOOV = "patch_moov_time"
    }
}

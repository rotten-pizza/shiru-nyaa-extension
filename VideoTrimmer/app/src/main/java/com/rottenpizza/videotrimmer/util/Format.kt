package com.rottenpizza.videotrimmer.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    /** mm:ss or h:mm:ss for durations >= 1h. */
    fun duration(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        val h = TimeUnit.MILLISECONDS.toHours(safe)
        val m = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** mm:ss.d with tenths, for the live trim readout. */
    fun durationPrecise(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        val m = TimeUnit.MILLISECONDS.toMinutes(safe)
        val s = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        val tenths = (safe % 1000) / 100
        return String.format(Locale.US, "%02d:%02d.%d", m, s, tenths)
    }

    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024; i++
        }
        return if (i == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun date(epochMs: Long): String {
        if (epochMs <= 0) return "—"
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
    }
}

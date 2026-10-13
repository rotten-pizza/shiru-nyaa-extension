package com.rottenpizza.videotrimmer.data

import android.system.Os
import java.io.FileDescriptor

/**
 * Best-effort patch of the embedded MP4 timestamps (mvhd / tkhd / mdhd
 * creation_time & modification_time) so the container's own clock matches the
 * source capture time — not just the gallery's DATE_TAKEN.
 *
 * This only overwrites fixed-width time fields in place; it never changes any
 * box size, so a partial or failed run cannot corrupt playback. It is
 * experimental and off by default. Every operation is guarded by the caller.
 */
object MoovPatcher {

    // Seconds between 1904-01-01 (MP4 epoch) and 1970-01-01 (Unix epoch).
    private const val MP4_EPOCH_OFFSET = 2_082_844_800L

    private val CONTAINERS = setOf("moov", "trak", "mdia")
    private val TIME_BOXES = setOf("mvhd", "tkhd", "mdhd")

    /** Returns true if at least one timestamp field was written. */
    fun patch(fd: FileDescriptor, unixMillis: Long): Boolean {
        if (unixMillis <= 0) return false
        val mp4Time = unixMillis / 1000L + MP4_EPOCH_OFFSET
        val fileSize = Os.lseek(fd, 0, android.system.OsConstants.SEEK_END)
        var patched = false
        walk(fd, 0, fileSize) { type, contentStart ->
            if (type in TIME_BOXES) {
                if (patchTimeBox(fd, contentStart, mp4Time)) patched = true
            }
        }
        return patched
    }

    /**
     * Iterates the boxes in [start, end), calling [onBox] for each. Recurses
     * into container boxes so nested time boxes are reached.
     */
    private fun walk(fd: FileDescriptor, start: Long, end: Long, onBox: (String, Long) -> Unit) {
        var pos = start
        val header = ByteArray(8)
        while (pos + 8 <= end) {
            if (!readAt(fd, pos, header, 8)) return
            var size = readU32(header, 0)
            val type = String(header, 4, 4, Charsets.US_ASCII)
            var contentStart = pos + 8
            when (size) {
                1L -> { // 64-bit largesize
                    val large = ByteArray(8)
                    if (!readAt(fd, pos + 8, large, 8)) return
                    size = readU64(large, 0)
                    contentStart = pos + 16
                }
                0L -> size = end - pos // extends to end of range
            }
            if (size < 8 || pos + size > end) return
            onBox(type, contentStart)
            if (type in CONTAINERS) {
                walk(fd, contentStart, pos + size, onBox)
            }
            pos += size
        }
    }

    /** Overwrites creation_time and modification_time in an mvhd/tkhd/mdhd. */
    private fun patchTimeBox(fd: FileDescriptor, contentStart: Long, mp4Time: Long): Boolean {
        val versionByte = ByteArray(1)
        if (!readAt(fd, contentStart, versionByte, 1)) return false
        val version = versionByte[0].toInt() and 0xFF
        val timesStart = contentStart + 4 // skip version(1) + flags(3)
        return if (version == 1) {
            val buf = ByteArray(8)
            writeU64(buf, mp4Time)
            writeAt(fd, timesStart, buf, 8) && writeAt(fd, timesStart + 8, buf, 8)
        } else {
            if (mp4Time > 0xFFFFFFFFL) return false // won't fit a 32-bit field
            val buf = ByteArray(4)
            writeU32(buf, mp4Time)
            writeAt(fd, timesStart, buf, 4) && writeAt(fd, timesStart + 4, buf, 4)
        }
    }

    private fun readAt(fd: FileDescriptor, offset: Long, dst: ByteArray, count: Int): Boolean = try {
        var read = 0
        while (read < count) {
            val n = Os.pread(fd, dst, read, count - read, offset + read)
            if (n <= 0) break
            read += n
        }
        read == count
    } catch (_: Exception) {
        false
    }

    private fun writeAt(fd: FileDescriptor, offset: Long, src: ByteArray, count: Int): Boolean = try {
        var wrote = 0
        while (wrote < count) {
            val n = Os.pwrite(fd, src, wrote, count - wrote, offset + wrote)
            if (n <= 0) break
            wrote += n
        }
        wrote == count
    } catch (_: Exception) {
        false
    }

    private fun readU32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun readU64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }

    private fun writeU32(b: ByteArray, v: Long) {
        b[0] = (v ushr 24).toByte(); b[1] = (v ushr 16).toByte()
        b[2] = (v ushr 8).toByte(); b[3] = v.toByte()
    }

    private fun writeU64(b: ByteArray, v: Long) {
        for (i in 0 until 8) b[i] = (v ushr (8 * (7 - i))).toByte()
    }
}

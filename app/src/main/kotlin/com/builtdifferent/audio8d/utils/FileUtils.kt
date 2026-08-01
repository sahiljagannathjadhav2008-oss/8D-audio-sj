package com.builtdifferent.audio8d.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    /** App-private, permanent storage for converted files (survives reboot, app restart). */
    fun convertedDir(context: Context): File =
        File(context.filesDir, "converted").apply { if (!exists()) mkdirs() }

    fun cacheImportDir(context: Context): File =
        File(context.cacheDir, "imports").apply { if (!exists()) mkdirs() }

    /** Copies a SAF content:// Uri into a real file FFmpeg can read by path. */
    fun copyUriToCache(context: Context, uri: Uri, displayName: String): File {
        val target = File(cacheImportDir(context), "src_${System.nanoTime()}_$displayName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target
    }

    fun queryDisplayName(context: Context, uri: Uri): String {
        var name = "audio_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    fun humanReadableSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return "%.1f %s".format(value, units[unitIndex])
    }
}

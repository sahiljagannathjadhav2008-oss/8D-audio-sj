package com.builtdifferent.audio8d.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

data class AudioMetadata(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkFile: File?
)

object MetadataExtractor {

    fun extract(context: Context, uri: Uri, displayName: String, cacheDir: File): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: displayName.substringBeforeLast(".")
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val artFile = retriever.embeddedPicture?.let { bytes ->
                File(cacheDir, "art_${System.nanoTime()}.jpg").apply {
                    FileOutputStream(this).use { it.write(bytes) }
                }
            }

            AudioMetadata(title, artist, album, duration, artFile)
        } catch (e: Exception) {
            AudioMetadata(displayName.substringBeforeLast("."), null, null, 0L, null)
        } finally {
            retriever.release()
        }
    }
}

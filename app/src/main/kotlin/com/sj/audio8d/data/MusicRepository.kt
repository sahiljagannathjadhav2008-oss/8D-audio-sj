package com.sj.audio8d.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the device's local audio library through [MediaStore] only — the app
 * never walks the raw filesystem. Every field defensively falls back to a
 * safe default so a single row with missing/corrupted metadata can never
 * crash the scan or hide the rest of the library.
 */
class MusicRepository(private val context: Context) {

    suspend fun loadAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    runCatching {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(collection, id)
                        val albumId = cursor.getLong(albumIdCol)
                        val albumArtUri = ContentUris.withAppendedId(
                            android.net.Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        )
                        songs.add(
                            Song(
                                id = id,
                                title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                                artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                    ?: "Unknown artist",
                                album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                                durationMs = cursor.getLong(durationCol),
                                contentUriString = contentUri.toString(),
                                albumArtUriString = albumArtUri.toString(),
                                mimeType = cursor.getString(mimeCol)
                            )
                        )
                    }
                    // A single malformed row is skipped; the scan continues.
                }
            }
        }
        songs
    }
}

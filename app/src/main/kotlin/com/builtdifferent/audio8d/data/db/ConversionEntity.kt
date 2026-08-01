package com.builtdifferent.audio8d.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ConversionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
}

enum class OutputFormat(val extension: String, val displayName: String) {
    MP3_320("mp3", "MP3 320 kbps"),
    AAC("m4a", "AAC"),
    WAV("wav", "WAV")
}

/**
 * One row per source file the user has converted (or attempted to convert).
 * originalUri is the stable key used to detect "already converted" source files,
 * so re-selecting the same song opens the existing converted copy instead of
 * re-running the DSP pipeline.
 */
@Entity(tableName = "conversions")
data class ConversionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUri: String,
    val originalDisplayName: String,
    /** Local cached copy of the source file — FFmpeg needs a real file path, not a content:// Uri. */
    val originalLocalPath: String,
    val convertedFilePath: String?,
    val title: String,
    val album: String?,
    val artist: String?,
    val durationMs: Long,
    val outputFormat: String,
    val fileSizeBytes: Long,
    val dateConvertedEpochMs: Long,
    val status: String,
    val isFavorite: Boolean = false,
    val artworkUri: String? = null
)

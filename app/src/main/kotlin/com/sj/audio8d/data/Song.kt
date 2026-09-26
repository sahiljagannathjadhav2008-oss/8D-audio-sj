package com.sj.audio8d.data

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUriString: String,
    val albumArtUriString: String? = null,
    val mimeType: String? = null
) {
    val contentUri: Uri get() = Uri.parse(contentUriString)
    val albumArtUri: Uri? get() = albumArtUriString?.let { Uri.parse(it) }
}

package com.builtdifferent.audio8d.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.audio8d.data.db.ConversionEntity
import com.builtdifferent.audio8d.data.repository.ConversionRepository
import com.builtdifferent.audio8d.utils.FileUtils
import com.builtdifferent.audio8d.utils.MetadataExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportResult {
    data class AlreadyConverted(val conversionId: Long) : ImportResult()
    data class NeedsConversion(val conversionId: Long) : ImportResult()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ConversionRepository
) : ViewModel() {

    val recentConversions = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalStorageBytes = repository.observeTotalStorageBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun importAudio(uri: Uri, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val originalUriString = uri.toString()

            // Detect "already converted" so the same song never gets re-processed.
            val existing = repository.findExistingConversion(originalUriString)
            if (existing != null) {
                onResult(ImportResult.AlreadyConverted(existing.id))
                return@launch
            }

            val displayName = FileUtils.queryDisplayName(context, uri)
            val cachedFile = FileUtils.copyUriToCache(context, uri, displayName)
            val metadata = MetadataExtractor.extract(context, uri, displayName, context.cacheDir)

            val entity = ConversionEntity(
                originalUri = originalUriString,
                originalDisplayName = displayName,
                originalLocalPath = cachedFile.absolutePath,
                convertedFilePath = null,
                title = metadata.title,
                album = metadata.album,
                artist = metadata.artist,
                durationMs = metadata.durationMs,
                outputFormat = "MP3_320",
                fileSizeBytes = 0L,
                dateConvertedEpochMs = System.currentTimeMillis(),
                status = "PENDING",
                artworkUri = metadata.artworkFile?.absolutePath
            )
            val id = repository.upsert(entity)
            onResult(ImportResult.NeedsConversion(id))
        }
    }
}

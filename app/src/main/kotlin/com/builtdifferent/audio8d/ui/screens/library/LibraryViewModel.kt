package com.builtdifferent.audio8d.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.audio8d.data.db.ConversionEntity
import com.builtdifferent.audio8d.data.repository.ConversionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { NEWEST, TITLE_AZ, SIZE }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ConversionRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder = _sortOrder

    val libraryItems = combine(
        repository.observeCompleted(),
        _query
    ) { items, query ->
        val filtered = if (query.isBlank()) items else items.filter {
            it.title.contains(query, ignoreCase = true) || (it.artist?.contains(query, ignoreCase = true) == true)
        }
        when (_sortOrder.value) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.dateConvertedEpochMs }
            SortOrder.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.SIZE -> filtered.sortedByDescending { it.fileSizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) { _query.value = query }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }

    fun rename(entity: ConversionEntity, newTitle: String) {
        viewModelScope.launch { repository.update(entity.copy(title = newTitle)) }
    }

    fun toggleFavorite(entity: ConversionEntity) {
        viewModelScope.launch { repository.update(entity.copy(isFavorite = !entity.isFavorite)) }
    }

    fun delete(entity: ConversionEntity) {
        viewModelScope.launch {
            entity.convertedFilePath?.let { java.io.File(it).delete() }
            repository.delete(entity)
        }
    }
}

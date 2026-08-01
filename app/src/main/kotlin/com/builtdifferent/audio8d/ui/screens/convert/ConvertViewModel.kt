package com.builtdifferent.audio8d.ui.screens.convert

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.builtdifferent.audio8d.data.db.ConversionEntity
import com.builtdifferent.audio8d.data.db.OutputFormat
import com.builtdifferent.audio8d.data.repository.ConversionRepository
import com.builtdifferent.audio8d.work.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ConvertUiState(
    val entity: ConversionEntity? = null,
    val stage: String = "Reading audio...",
    val percent: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConvertViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ConversionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConvertUiState())
    val uiState: StateFlow<ConvertUiState> = _uiState.asStateFlow()

    private val workManager = WorkManager.getInstance(context)
    private var workId: UUID? = null

    fun start(conversionId: Long, outputFormat: OutputFormat = OutputFormat.MP3_320) {
        viewModelScope.launch {
            val entity = repository.findById(conversionId) ?: return@launch
            _uiState.value = ConvertUiState(entity = entity)

            val request = OneTimeWorkRequestBuilder<ConversionWorker>()
                .setInputData(
                    workDataOf(
                        ConversionWorker.KEY_CONVERSION_ID to conversionId,
                        ConversionWorker.KEY_INPUT_PATH to entity.originalLocalPath,
                        ConversionWorker.KEY_DURATION_MS to entity.durationMs,
                        ConversionWorker.KEY_OUTPUT_FORMAT to outputFormat.name
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            workId = request.id
            workManager.enqueue(request)
            observeWork(request.id, conversionId)
        }
    }

    private fun observeWork(id: UUID, conversionId: Long) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                        val stage = info.progress.getString(ConversionWorker.KEY_STAGE) ?: _uiState.value.stage
                        val percent = info.progress.getInt(ConversionWorker.KEY_PERCENT, _uiState.value.percent)
                        _uiState.value = _uiState.value.copy(stage = stage, percent = percent)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _uiState.value = _uiState.value.copy(stage = "Completed", percent = 100, isComplete = true)
                    }
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString(ConversionWorker.KEY_ERROR) ?: "Conversion failed"
                        _uiState.value = _uiState.value.copy(error = error)
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.value = _uiState.value.copy(error = "Cancelled")
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancel() {
        workId?.let { workManager.cancelWorkById(it) }
    }
}

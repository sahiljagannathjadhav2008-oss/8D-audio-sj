package com.builtdifferent.audio8d.work

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.builtdifferent.audio8d.App
import com.builtdifferent.audio8d.MainActivity
import com.builtdifferent.audio8d.R
import com.builtdifferent.audio8d.audio.AudioConverter
import com.builtdifferent.audio8d.audio.ConversionProgress
import com.builtdifferent.audio8d.data.db.OutputFormat
import com.builtdifferent.audio8d.data.repository.ConversionRepository
import com.builtdifferent.audio8d.utils.FileUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import java.io.File

/**
 * Runs one 8D conversion in the background via WorkManager. Declares itself
 * as a foreground worker (dataSync type, required explicitly on Android 14+)
 * so the job survives the screen turning off and shows a persistent,
 * progress-updating notification for the whole run.
 */
@HiltWorker
class ConversionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ConversionRepository,
    private val audioConverter: AudioConverter
) : CoroutineWorker(appContext, params) {

    private fun buildNotification(stage: String, percent: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, App.CHANNEL_CONVERSION)
            .setContentTitle("Converting to 8D audio")
            .setContentText(stage)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun foregroundInfo(stage: String, percent: Int): ForegroundInfo {
        val notification = buildNotification(stage, percent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo("Starting conversion...", 0)

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        val conversionId = inputData.getLong(KEY_CONVERSION_ID, -1L)
        val inputPath = inputData.getString(KEY_INPUT_PATH) ?: return Result.failure()
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)
        val formatName = inputData.getString(KEY_OUTPUT_FORMAT) ?: OutputFormat.MP3_320.name
        val format = OutputFormat.valueOf(formatName)

        val entity = repository.findById(conversionId) ?: return Result.failure()

        val outputFile = File(
            FileUtils.convertedDir(applicationContext),
            "${entity.title.replace(Regex("[^A-Za-z0-9 _-]"), "_")}_8D_${System.currentTimeMillis()}.${format.extension}"
        )

        var finalResult = Result.failure()

        audioConverter.convert(
            inputPath = inputPath,
            outputFile = outputFile,
            durationSeconds = durationMs / 1000.0,
            outputFormat = format
        ).collect { progress ->
            when (progress) {
                is ConversionProgress.Stage -> {
                    setProgressAsync(
                        workDataOf(KEY_STAGE to progress.label, KEY_PERCENT to progress.percent)
                    )
                    setForeground(foregroundInfo(progress.label, progress.percent))
                }
                is ConversionProgress.Done -> {
                    repository.update(
                        entity.copy(
                            convertedFilePath = progress.outputFile.absolutePath,
                            status = "COMPLETED",
                            fileSizeBytes = progress.outputFile.length(),
                            dateConvertedEpochMs = System.currentTimeMillis()
                        )
                    )
                    finalResult = Result.success(
                        workDataOf(KEY_OUTPUT_PATH to progress.outputFile.absolutePath)
                    )
                }
                is ConversionProgress.Error -> {
                    repository.update(entity.copy(status = "FAILED"))
                    finalResult = Result.failure(workDataOf(KEY_ERROR to progress.message))
                }
            }
        }

        return finalResult
    }

    companion object {
        const val KEY_CONVERSION_ID = "conversion_id"
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_OUTPUT_FORMAT = "output_format"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR = "error"
        const val NOTIFICATION_ID = 1001
    }
}

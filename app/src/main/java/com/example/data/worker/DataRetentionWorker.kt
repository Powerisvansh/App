package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.DailyTrackApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DataRetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DailyTrackApplication
            val retentionDays = app.preferencesRepository.autoDeleteDays.first()

            if (retentionDays > 0) {
                app.locationRepository.deleteOlderThanDays(retentionDays)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_track_retention_work"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<DataRetentionWorker>(
                24, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}

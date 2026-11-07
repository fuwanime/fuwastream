package com.fuwanime.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fuwanime.FuwaApp
import java.util.concurrent.TimeUnit

/** Writes the backup file once a day; a failure is shown in Settings instead of being retried. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when ((applicationContext as FuwaApp).container.backupRepository.backupNow()) {
            is BackupOutcome.Failed -> Result.failure()
            else -> Result.success()
        }

    companion object {
        private const val DAILY = "backup-daily"

        /** Safe on every app start (KEEP policy). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(DAILY)
    }
}

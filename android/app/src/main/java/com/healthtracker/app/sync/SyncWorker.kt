package com.healthtracker.app.sync

import android.content.Context
import androidx.work.*
import com.healthtracker.app.HealthTrackerApp
import java.util.concurrent.TimeUnit

/**
 * Runs every 6 hours to:
 * 1. Read latest Health Connect data and save locally
 * 2. Upload all unsynced entries and snapshots to the backend
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HealthTrackerApp
        val prefs = applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return Result.success() // Not logged in yet

        return try {
            // 1. Read Health Connect
            if (app.healthConnectManager.isAvailable() && app.healthConnectManager.hasPermissions()) {
                val snapshot = app.healthConnectManager.readTodaySnapshot()
                app.repository.saveSnapshot(snapshot)
            }

            // 2. Sync to backend
            app.repository.syncPendingEntries(token)
            app.repository.syncPendingSnapshots(token)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "health_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

package com.healthtracker.app.sync

import android.content.Context
import androidx.work.*
import com.healthtracker.app.HealthTrackerApp
import java.util.concurrent.TimeUnit

/**
 * Runs every 3 hours, and on demand when the app is opened, to:
 * 1. Backfill history once, the first time history access is available
 * 2. Read latest Health Connect data and save locally
 * 3. Upload all unsynced entries and snapshots to the backend
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
            val health = app.healthConnectManager
            if (health.isAvailable() && health.hasPermissions()) {
                // One-off historical import. Guarded by a flag because walking a
                // year of days is slow and only worth doing once.
                if (!prefs.getBoolean(KEY_BACKFILLED, false)) {
                    val history = health.readHistory()
                    app.repository.saveSnapshots(history)
                    // Only latch this off once something actually came back. An
                    // empty result usually means history access wasn't granted
                    // yet, and marking it done would disable backfill forever.
                    if (history.isNotEmpty()) {
                        prefs.edit().putBoolean(KEY_BACKFILLED, true).apply()
                    }
                }
                app.repository.saveSnapshot(health.readTodaySnapshot())
            }

            app.repository.syncPendingEntries(token)
            app.repository.syncPendingSnapshots(token)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "health_sync"
        private const val ONE_OFF_WORK_NAME = "health_sync_now"
        private const val KEY_BACKFILLED = "history_backfilled"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // UPDATE rather than KEEP so existing installs pick up interval changes
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Kick off a sync immediately, e.g. when the app is opened. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_OFF_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Let a fresh Health Connect grant re-run the historical import. */
        fun resetBackfill(context: Context) {
            context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BACKFILLED, false).apply()
        }

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

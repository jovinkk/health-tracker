package com.healthtracker.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.healthtracker.app.HealthTrackerApp
import kotlinx.coroutines.launch

enum class SyncState { IDLE, SYNCING, DONE, ERROR }

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as HealthTrackerApp).repository

    val latestSnapshot = repo.latestSnapshot
    val recentEntries = repo.allEntries
    val syncState = MutableLiveData(SyncState.IDLE)

    fun syncNow(token: String) = viewModelScope.launch {
        syncState.value = SyncState.SYNCING
        try {
            val app = getApplication<HealthTrackerApp>()
            if (app.healthConnectManager.isAvailable() && app.healthConnectManager.hasPermissions()) {
                val snapshot = app.healthConnectManager.readTodaySnapshot()
                repo.saveSnapshot(snapshot)
            }
            repo.syncPendingEntries(token)
            repo.syncPendingSnapshots(token)
            syncState.value = SyncState.DONE
        } catch (e: Exception) {
            syncState.value = SyncState.ERROR
        }
    }
}

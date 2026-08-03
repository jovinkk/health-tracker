package com.healthtracker.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.data.local.entity.WearableSnapshot
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class SyncState { IDLE, SYNCING, DONE, ERROR }

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as HealthTrackerApp).repository
    private val dao = (app as HealthTrackerApp).database.wearableSnapshotDao()

    val recentEntries = repo.allEntries
    val syncState = MutableLiveData(SyncState.IDLE)

    val selectedDate = MutableLiveData(LocalDate.now())

    /** Snapshot for whichever day is being viewed, not just the newest one. */
    val snapshot: LiveData<WearableSnapshot?> = selectedDate.switchMap { day ->
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        dao.observeForDay(start, end)
    }

    val isToday: Boolean get() = selectedDate.value == LocalDate.now()

    fun selectDate(date: LocalDate) {
        // Never page into the future; there is nothing to show there.
        val capped = if (date.isAfter(LocalDate.now())) LocalDate.now() else date
        if (selectedDate.value != capped) selectedDate.value = capped
    }

    fun shiftDay(days: Long) {
        selectDate((selectedDate.value ?: LocalDate.now()).plusDays(days))
    }

    fun syncNow(token: String) = viewModelScope.launch {
        syncState.value = SyncState.SYNCING
        try {
            val app = getApplication<HealthTrackerApp>()
            if (app.healthConnectManager.isAvailable() && app.healthConnectManager.hasPermissions()) {
                repo.saveSnapshot(app.healthConnectManager.readTodaySnapshot())
            }
            repo.syncPendingEntries(token)
            repo.syncPendingSnapshots(token)
            syncState.value = SyncState.DONE
        } catch (e: Exception) {
            syncState.value = SyncState.ERROR
        }
    }
}

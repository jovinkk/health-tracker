package com.healthtracker.app.ui.trends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.data.local.entity.WearableSnapshot
import java.time.LocalDate
import java.time.ZoneId

class TrendsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as HealthTrackerApp).database.wearableSnapshotDao()

    val rangeDays = MutableLiveData(7)

    /** Re-queries whenever the selected range changes. */
    val snapshots: LiveData<List<WearableSnapshot>> = rangeDays.switchMap { days ->
        val since = LocalDate.now()
            .minusDays((days - 1).toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        dao.observeSince(since)
    }

    fun setRange(days: Int) {
        if (rangeDays.value != days) rangeDays.value = days
    }
}

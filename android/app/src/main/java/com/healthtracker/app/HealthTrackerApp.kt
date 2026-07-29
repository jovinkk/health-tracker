package com.healthtracker.app

import android.app.Application
import com.healthtracker.app.data.local.AppDatabase
import com.healthtracker.app.data.remote.ApiService
import com.healthtracker.app.data.remote.GeminiService
import com.healthtracker.app.data.repository.HealthRepository
import com.healthtracker.app.health.HealthConnectManager

class HealthTrackerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    val apiService by lazy { ApiService.create() }

    val geminiService by lazy { GeminiService() }

    val healthConnectManager by lazy { HealthConnectManager(this) }

    val repository by lazy {
        HealthRepository(
            database.healthEntryDao(),
            database.wearableSnapshotDao(),
            apiService,
            geminiService,
        )
    }
}

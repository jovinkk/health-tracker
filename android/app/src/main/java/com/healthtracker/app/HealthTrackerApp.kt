package com.healthtracker.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.healthtracker.app.data.local.AppDatabase
import com.healthtracker.app.data.remote.ApiService
import com.healthtracker.app.data.remote.GeminiService
import com.healthtracker.app.data.repository.HealthRepository
import com.healthtracker.app.health.HealthConnectManager
import com.healthtracker.app.settings.AppSettings

class HealthTrackerApp : Application() {

    val settings by lazy { AppSettings(this) }

    val database by lazy { AppDatabase.getInstance(this) }

    val apiService by lazy { ApiService.create(this) }

    val geminiService by lazy { GeminiService() }

    val healthConnectManager by lazy { HealthConnectManager(this, settings) }

    val repository by lazy {
        HealthRepository(
            database.healthEntryDao(),
            database.wearableSnapshotDao(),
            apiService,
            geminiService,
        )
    }

    override fun onCreate() {
        super.onCreate()
        settings.applyStoredTheme()
        // Wallpaper-derived colours on Android 12+; a no-op below that, where the
        // palette in colors.xml is used instead.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

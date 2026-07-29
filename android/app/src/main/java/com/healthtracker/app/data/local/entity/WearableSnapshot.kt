package com.healthtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wearable_snapshots")
data class WearableSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val deviceName: String? = null,
    val steps: Int? = null,
    val heartRateAvg: Float? = null,
    val heartRateResting: Float? = null,
    val hrvMs: Float? = null,
    val spo2Pct: Float? = null,
    val sleepDurationMin: Int? = null,
    val sleepDeepMin: Int? = null,
    val sleepRemMin: Int? = null,
    val sleepScore: Int? = null,
    val caloriesActive: Float? = null,
    val caloriesTotal: Float? = null,
    val stressScore: Int? = null,
    val skinTempCelsius: Float? = null,
    val synced: Boolean = false,
)

package com.healthtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local mirror of a parsed health log entry.
 * synced = false until successfully uploaded to the backend.
 */
@Entity(tableName = "health_entries")
data class HealthEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val entryType: String,          // stress | pain | mood | nutrition | sleep | medication | exercise | note
    val rawInput: String? = null,   // Original speech transcript
    val dataJson: String,           // Gemini-parsed JSON string
    val numericValue: Float? = null,
    val subCategory: String? = null,
    val source: String = "speech",
    val synced: Boolean = false,
    val serverId: Long? = null,
)

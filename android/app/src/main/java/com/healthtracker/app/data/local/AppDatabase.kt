package com.healthtracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.healthtracker.app.data.local.dao.HealthEntryDao
import com.healthtracker.app.data.local.dao.WearableSnapshotDao
import com.healthtracker.app.data.local.entity.HealthEntry
import com.healthtracker.app.data.local.entity.WearableSnapshot

@Database(
    entities = [HealthEntry::class, WearableSnapshot::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun healthEntryDao(): HealthEntryDao
    abstract fun wearableSnapshotDao(): WearableSnapshotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "health_tracker.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}

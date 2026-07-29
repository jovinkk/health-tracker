package com.healthtracker.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.healthtracker.app.data.local.entity.HealthEntry

@Dao
interface HealthEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HealthEntry): Long

    @Query("SELECT * FROM health_entries ORDER BY timestamp DESC")
    fun observeAll(): LiveData<List<HealthEntry>>

    @Query("SELECT * FROM health_entries WHERE entryType = :type ORDER BY timestamp DESC")
    fun observeByType(type: String): LiveData<List<HealthEntry>>

    @Query("SELECT * FROM health_entries WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<HealthEntry>

    @Query("UPDATE health_entries SET synced = 1, serverId = :serverId WHERE id = :localId")
    suspend fun markSynced(localId: Long, serverId: Long)

    @Query("SELECT * FROM health_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Long): List<HealthEntry>

    @Delete
    suspend fun delete(entry: HealthEntry)
}

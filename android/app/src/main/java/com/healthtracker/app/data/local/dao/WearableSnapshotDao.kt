package com.healthtracker.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.healthtracker.app.data.local.entity.WearableSnapshot

@Dao
interface WearableSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<WearableSnapshot>)

    @Query("SELECT * FROM wearable_snapshots ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): LiveData<WearableSnapshot?>

    @Query("SELECT * FROM wearable_snapshots WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Long): List<WearableSnapshot>

    /** Latest snapshot falling inside a single day, for browsing history. */
    @Query("SELECT * FROM wearable_snapshots WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC LIMIT 1")
    fun observeForDay(start: Long, end: Long): LiveData<WearableSnapshot?>

    /** Ascending series for trend charts. */
    @Query("SELECT * FROM wearable_snapshots WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun observeSince(since: Long): LiveData<List<WearableSnapshot>>

    /** Clears a day before writing a fresher reading, so one row represents one day. */
    @Query("DELETE FROM wearable_snapshots WHERE timestamp >= :start AND timestamp < :end")
    suspend fun deleteForDay(start: Long, end: Long)

    @Query("SELECT * FROM wearable_snapshots WHERE synced = 0")
    suspend fun getUnsynced(): List<WearableSnapshot>

    @Query("UPDATE wearable_snapshots SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

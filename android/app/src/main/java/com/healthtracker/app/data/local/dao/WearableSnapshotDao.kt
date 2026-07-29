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

    @Query("SELECT * FROM wearable_snapshots WHERE synced = 0")
    suspend fun getUnsynced(): List<WearableSnapshot>

    @Query("UPDATE wearable_snapshots SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

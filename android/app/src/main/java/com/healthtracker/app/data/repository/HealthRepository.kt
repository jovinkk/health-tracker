package com.healthtracker.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.healthtracker.app.data.local.dao.HealthEntryDao
import com.healthtracker.app.data.local.dao.WearableSnapshotDao
import com.healthtracker.app.data.local.entity.HealthEntry
import com.healthtracker.app.data.local.entity.WearableSnapshot
import com.healthtracker.app.data.remote.ApiService
import com.healthtracker.app.data.remote.GeminiService
import com.healthtracker.app.data.remote.HealthEntryRequest
import com.healthtracker.app.data.remote.WearableSnapshotRequest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class HealthRepository(
    private val entryDao: HealthEntryDao,
    private val snapshotDao: WearableSnapshotDao,
    private val apiService: ApiService,
    private val geminiService: GeminiService,
) {
    private val gson = Gson()
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

    // ── Live data for UI ───────────────────────────────────────────────────────

    val allEntries = entryDao.observeAll()
    val latestSnapshot = snapshotDao.observeLatest()

    // ── Speech → structured entry ──────────────────────────────────────────────

    suspend fun processAndSaveSpeechInput(transcript: String): HealthEntry {
        val parsed = geminiService.parseHealthInput(transcript)
        val dataJson = gson.toJson(parsed.data)
        val entry = HealthEntry(
            entryType = parsed.entryType,
            rawInput = transcript,
            dataJson = dataJson,
            numericValue = parsed.numericValue,
            subCategory = parsed.subCategory,
            source = "speech",
        )
        val id = entryDao.insert(entry)
        return entry.copy(id = id)
    }

    // ── Edit / delete entries ──────────────────────────────────────────────────

    /**
     * Local store is the source of truth; the backend copy is best-effort, since
     * an entry may not have been uploaded yet (serverId null) or the network may
     * be down. Failed remote edits leave the entry marked unsynced to retry.
     */
    suspend fun updateEntry(entry: HealthEntry, token: String?) {
        entryDao.update(entry.copy(synced = false))
        val serverId = entry.serverId ?: return
        if (token == null) return
        runCatching {
            apiService.updateEntry(
                auth = "Bearer $token",
                id = serverId,
                patch = com.healthtracker.app.data.remote.HealthEntryPatch(
                    rawInput = entry.rawInput,
                    numericValue = entry.numericValue,
                    subCategory = entry.subCategory,
                ),
            )
        }.onSuccess { entryDao.update(entry.copy(synced = true)) }
    }

    suspend fun deleteEntry(entry: HealthEntry, token: String?) {
        entryDao.delete(entry)
        val serverId = entry.serverId ?: return
        if (token == null) return
        runCatching { apiService.deleteEntry("Bearer $token", serverId) }
    }

    // ── Save wearable snapshot locally ─────────────────────────────────────────

    suspend fun saveSnapshot(snapshot: WearableSnapshot) {
        snapshotDao.insertAll(listOf(snapshot))
    }

    suspend fun saveSnapshots(snapshots: List<WearableSnapshot>) {
        if (snapshots.isNotEmpty()) snapshotDao.insertAll(snapshots)
    }

    // ── Sync unsynced data to backend ──────────────────────────────────────────

    suspend fun syncPendingEntries(authToken: String) {
        val unsynced = entryDao.getUnsynced()
        for (entry in unsynced) {
            try {
                @Suppress("UNCHECKED_CAST")
                val data = gson.fromJson(entry.dataJson, Map::class.java) as Map<String, Any>
                val response = apiService.createEntry(
                    auth = "Bearer $authToken",
                    entry = HealthEntryRequest(
                        timestamp = isoFormatter.format(Instant.ofEpochMilli(entry.timestamp)),
                        entryType = entry.entryType,
                        rawInput = entry.rawInput,
                        data = data,
                        numericValue = entry.numericValue,
                        subCategory = entry.subCategory,
                        source = entry.source,
                    )
                )
                entryDao.markSynced(entry.id, response.id)
            } catch (_: Exception) {
                // Will retry on next sync
            }
        }
    }

    suspend fun syncPendingSnapshots(authToken: String) {
        val unsynced = snapshotDao.getUnsynced()
        if (unsynced.isEmpty()) return
        try {
            val requests = unsynced.map { s ->
                WearableSnapshotRequest(
                    timestamp = isoFormatter.format(Instant.ofEpochMilli(s.timestamp)),
                    deviceName = s.deviceName,
                    steps = s.steps,
                    heartRateAvg = s.heartRateAvg,
                    heartRateResting = s.heartRateResting,
                    hrvMs = s.hrvMs,
                    spo2Pct = s.spo2Pct,
                    sleepDurationMin = s.sleepDurationMin,
                    sleepDeepMin = s.sleepDeepMin,
                    sleepRemMin = s.sleepRemMin,
                    sleepScore = s.sleepScore,
                    caloriesActive = s.caloriesActive,
                    caloriesTotal = s.caloriesTotal,
                    stressScore = s.stressScore,
                    skinTempCelsius = s.skinTempCelsius,
                )
            }
            apiService.uploadWearableBatch("Bearer $authToken", requests)
            snapshotDao.markSynced(unsynced.map { it.id })
        } catch (_: Exception) { }
    }
}

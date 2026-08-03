package com.healthtracker.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthtracker.app.data.local.entity.WearableSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    /**
     * Without this, Health Connect only returns the last 30 days regardless of what
     * the source app has stored. Requested alongside the read permissions, but the
     * app still works if the user declines it — history is just capped at 30 days.
     */
    val historyPermission = HISTORY_PERMISSION

    val allPermissions = requiredPermissions + historyPermission

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    suspend fun hasHistoryPermission(): Boolean =
        client.permissionController.getGrantedPermissions().contains(historyPermission)

    /** Apps that have written step data, for showing the user where numbers come from. */
    suspend fun stepDataSources(): Set<String> = runCatching {
        val range = TimeRangeFilter.between(Instant.now().minus(7, ChronoUnit.DAYS), Instant.now())
        client.readRecords(ReadRecordsRequest(StepsRecord::class, range))
            .records.map { it.metadata.dataOrigin.packageName }.toSet()
    }.getOrDefault(emptySet())

    suspend fun readTodaySnapshot(): WearableSnapshot = readDaySnapshot(LocalDate.now())

    /**
     * Read one local calendar day and return a WearableSnapshot (unsaved, id=0).
     *
     * Totals come from Health Connect's aggregate API rather than summing raw
     * records: when several apps write the same metric (a phone's own pedometer
     * alongside a watch, say) their records overlap, and only aggregation
     * de-duplicates them. Summing records double-counts.
     */
    suspend fun readDaySnapshot(day: LocalDate): WearableSnapshot {
        val zone = ZoneId.systemDefault()
        val dayStart = day.atStartOfDay(zone).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
        // Don't ask for the future; Health Connect rejects ranges beyond now.
        val end = if (dayEnd.isAfter(Instant.now())) Instant.now() else dayEnd
        if (!end.isAfter(dayStart)) return emptySnapshot(dayStart)
        val range = TimeRangeFilter.between(dayStart, end)

        val totals = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        RestingHeartRateRecord.BPM_AVG,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    ),
                    timeRangeFilter = range,
                )
            )
        }.getOrNull()

        val steps = totals?.get(StepsRecord.COUNT_TOTAL)?.toInt()
        val hrAvg = totals?.get(HeartRateRecord.BPM_AVG)?.toFloat()
        val rhr = totals?.get(RestingHeartRateRecord.BPM_AVG)?.toFloat()
        val activeCal = totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.toFloat()
        val totalCal = totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toFloat()

        // No aggregate metric exists for these, so take the day's last reading.
        val hrv = runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range))
                .records.maxByOrNull { it.time }?.heartRateVariabilityMillis?.toFloat()
        }.getOrNull()

        val spo2 = runCatching {
            client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range))
                .records.maxByOrNull { it.time }?.percentage?.value?.toFloat()
        }.getOrNull()

        // A night's sleep is attributed to the day it ends on, so look back from
        // the end of the day rather than only inside it.
        val sleepData = runCatching {
            val sleepRange = TimeRangeFilter.between(dayStart.minus(24, ChronoUnit.HOURS), end)
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, sleepRange)).records
                .filter { it.endTime.isAfter(dayStart) }
                .maxByOrNull { it.endTime }
        }.getOrNull()

        val sleepDurationMin = sleepData?.let {
            ChronoUnit.MINUTES.between(it.startTime, it.endTime).toInt()
        }

        val deepMin = sleepData?.stages
            ?.filter { it.stage == SleepSessionRecord.STAGE_TYPE_DEEP }
            ?.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
            ?.toInt()

        val remMin = sleepData?.stages
            ?.filter { it.stage == SleepSessionRecord.STAGE_TYPE_REM }
            ?.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
            ?.toInt()

        return WearableSnapshot(
            // Stamp the day itself, not "now", so historical days sort correctly.
            timestamp = if (day == LocalDate.now()) System.currentTimeMillis() else dayStart.toEpochMilli(),
            deviceName = "Health Connect",
            steps = steps,
            heartRateAvg = hrAvg,
            heartRateResting = rhr,
            hrvMs = hrv,
            spo2Pct = spo2,
            sleepDurationMin = sleepDurationMin,
            sleepDeepMin = deepMin,
            sleepRemMin = remMin,
            caloriesActive = activeCal,
            caloriesTotal = totalCal,
        )
    }

    /**
     * Walk backwards a day at a time collecting snapshots. Health Connect has no
     * "how far back does data go" query, so this stops after a stretch of empty
     * days rather than always querying the full window.
     */
    suspend fun readHistory(
        maxDays: Int = MAX_BACKFILL_DAYS,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): List<WearableSnapshot> {
        val snapshots = mutableListOf<WearableSnapshot>()
        var emptyRun = 0
        for (offset in 1..maxDays) {
            val day = LocalDate.now().minusDays(offset.toLong())
            val snapshot = runCatching { readDaySnapshot(day) }.getOrNull()
            onProgress?.invoke(offset, maxDays)
            if (snapshot == null || snapshot.isEmpty()) {
                if (++emptyRun >= EMPTY_DAY_RUN_LIMIT) break
                continue
            }
            emptyRun = 0
            snapshots += snapshot
        }
        return snapshots
    }

    private fun emptySnapshot(at: Instant) =
        WearableSnapshot(timestamp = at.toEpochMilli(), deviceName = "Health Connect")

    private fun WearableSnapshot.isEmpty(): Boolean =
        steps == null && heartRateAvg == null && heartRateResting == null &&
            hrvMs == null && spo2Pct == null && sleepDurationMin == null &&
            caloriesActive == null && caloriesTotal == null

    companion object {
        // Raw string rather than the SDK constant so this compiles across
        // connect-client versions; matches the manifest declaration.
        const val HISTORY_PERMISSION = "android.permission.health.READ_HEALTH_DATA_HISTORY"

        private const val MAX_BACKFILL_DAYS = 365
        private const val EMPTY_DAY_RUN_LIMIT = 21
    }
}

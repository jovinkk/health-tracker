package com.healthtracker.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthtracker.app.data.local.entity.WearableSnapshot
import com.healthtracker.app.settings.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

class HealthConnectManager(
    private val context: Context,
    private val settings: AppSettings,
) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /**
     * Restricts aggregation to one writing app when the user has pinned a source.
     *
     * Health Connect only de-duplicates overlapping records where its own app
     * priority list resolves them; with a watch app and a phone pedometer both
     * writing steps for the same hours and no priority set, aggregate() returns
     * the sum of both. Pinning a source is the dependable way to match what the
     * source app itself reports.
     */
    private fun originFilter(): Set<DataOrigin> =
        settings.stepSourcePackage?.let { setOf(DataOrigin(it)) } ?: emptySet()

    /** Aggregate a single metric, returning null rather than failing the caller. */
    private suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        range: TimeRangeFilter,
        origins: Set<DataOrigin> = emptySet(),
    ): T? = runCatching {
        client.aggregate(AggregateRequest(setOf(metric), range, origins))[metric]
    }.getOrNull()

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

    /**
     * Apps that have written step data recently, with the step count each one
     * claims, so the user can see which source matches their watch app.
     */
    suspend fun stepDataSources(): List<StepSource> = runCatching {
        val end = Instant.now()
        val range = TimeRangeFilter.between(end.minus(7, ChronoUnit.DAYS), end)
        client.readRecords(ReadRecordsRequest(StepsRecord::class, range))
            .records
            .groupBy { it.metadata.dataOrigin.packageName }
            .map { (pkg, records) -> StepSource(pkg, records.sumOf { it.count }) }
            .sortedByDescending { it.stepsLast7Days }
    }.getOrDefault(emptyList())

    data class StepSource(val packageName: String, val stepsLast7Days: Long)

    /**
     * Per-type report of what Health Connect actually holds.
     *
     * A metric can be missing for three different reasons — permission not
     * granted, no app writing that type, or an app writing it that the source
     * filter excludes — and they are indistinguishable from a blank dashboard.
     */
    suspend fun diagnostics(days: Long = 7): List<DataTypeStatus> {
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        val end = Instant.now()
        val range = TimeRangeFilter.between(end.minus(days, ChronoUnit.DAYS), end)
        return listOf(
            typeStatus("Steps", StepsRecord::class, granted, range),
            typeStatus("Heart rate", HeartRateRecord::class, granted, range),
            typeStatus("Resting heart rate", RestingHeartRateRecord::class, granted, range),
            typeStatus("HRV", HeartRateVariabilityRmssdRecord::class, granted, range),
            typeStatus("Blood oxygen", OxygenSaturationRecord::class, granted, range),
            typeStatus("Sleep", SleepSessionRecord::class, granted, range),
            typeStatus("Active calories", ActiveCaloriesBurnedRecord::class, granted, range),
            typeStatus("Total calories", TotalCaloriesBurnedRecord::class, granted, range),
        )
    }

    private suspend fun <T : Record> typeStatus(
        label: String,
        type: KClass<T>,
        granted: Set<String>,
        range: TimeRangeFilter,
    ): DataTypeStatus {
        val isGranted = HealthPermission.getReadPermission(type) in granted
        if (!isGranted) return DataTypeStatus(label, granted = false, recordCount = 0, sources = emptyList())
        val records = runCatching {
            client.readRecords(ReadRecordsRequest(type, range)).records
        }.getOrDefault(emptyList())
        return DataTypeStatus(
            label = label,
            granted = true,
            recordCount = records.size,
            sources = records.map { it.metadata.dataOrigin.packageName }.distinct(),
        )
    }

    data class DataTypeStatus(
        val label: String,
        val granted: Boolean,
        val recordCount: Int,
        val sources: List<String>,
    )

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

        // One request per metric: a single unsupported or unpermitted metric makes
        // the whole batch throw, which previously wiped every reading rather than
        // just the offending one. The source filter is a *step* preference, so it
        // must not restrict calories or heart rate to that same app.
        val steps = aggregate(StepsRecord.COUNT_TOTAL, range, originFilter())?.toInt()
        val hrAvg = aggregate(HeartRateRecord.BPM_AVG, range)?.toFloat()
        val rhr = aggregate(RestingHeartRateRecord.BPM_AVG, range)?.toFloat()
        val activeCal = aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, range)?.inKilocalories?.toFloat()
        val totalCal = aggregate(TotalCaloriesBurnedRecord.ENERGY_TOTAL, range)?.inKilocalories?.toFloat()

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

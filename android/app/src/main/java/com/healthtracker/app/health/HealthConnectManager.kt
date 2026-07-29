package com.healthtracker.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthtracker.app.data.local.entity.WearableSnapshot
import java.time.Instant
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

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    /**
     * Read today's health data and return a WearableSnapshot (unsaved, id=0).
     */
    suspend fun readTodaySnapshot(): WearableSnapshot {
        val end = Instant.now()
        val start = end.truncatedTo(ChronoUnit.DAYS)
        val range = TimeRangeFilter.between(start, end)

        val steps = runCatching {
            client.readRecords(ReadRecordsRequest(StepsRecord::class, range))
                .records.sumOf { it.count }.toInt()
        }.getOrNull()

        val hrAvg = runCatching {
            val records = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records
            if (records.isEmpty()) null
            else records.flatMap { it.samples }.map { it.beatsPerMinute }.average().toFloat()
        }.getOrNull()

        val rhr = runCatching {
            client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range))
                .records.lastOrNull()?.beatsPerMinute?.toFloat()
        }.getOrNull()

        val hrv = runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range))
                .records.lastOrNull()?.heartRateVariabilityMillis?.toFloat()
        }.getOrNull()

        val spo2 = runCatching {
            client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range))
                .records.lastOrNull()?.percentage?.value?.toFloat()
        }.getOrNull()

        val sleepData = runCatching {
            // Read last 36h for sleep sessions that may have started yesterday
            val sleepRange = TimeRangeFilter.between(end.minus(36, ChronoUnit.HOURS), end)
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, sleepRange)).records
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

        val activeCal = runCatching {
            client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, range))
                .records.sumOf { it.energy.inKilocalories }.toFloat()
        }.getOrNull()

        val totalCal = runCatching {
            client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, range))
                .records.sumOf { it.energy.inKilocalories }.toFloat()
        }.getOrNull()

        return WearableSnapshot(
            timestamp = System.currentTimeMillis(),
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
}

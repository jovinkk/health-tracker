package com.healthtracker.app.data.remote

import com.healthtracker.app.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ── Data models ───────────────────────────────────────────────────────────────

data class CredentialsRequest(val username: String, val password: String)
data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
)
data class UserResponse(
    val id: Long,
    val username: String,
    @SerializedName("created_at") val createdAt: String,
)

data class HealthEntryRequest(
    val timestamp: String?,
    @SerializedName("entry_type") val entryType: String,
    @SerializedName("raw_input") val rawInput: String?,
    val data: Map<String, Any>,
    @SerializedName("numeric_value") val numericValue: Float?,
    @SerializedName("sub_category") val subCategory: String?,
    val source: String,
)

data class HealthEntryResponse(
    val id: Long,
    @SerializedName("user_id") val userId: Long,
    val timestamp: String,
    @SerializedName("entry_type") val entryType: String,
    @SerializedName("raw_input") val rawInput: String?,
    val data: Map<String, Any>,
    @SerializedName("numeric_value") val numericValue: Float?,
)

data class WearableSnapshotRequest(
    val timestamp: String,
    @SerializedName("device_name") val deviceName: String?,
    val steps: Int?,
    @SerializedName("heart_rate_avg") val heartRateAvg: Float?,
    @SerializedName("heart_rate_resting") val heartRateResting: Float?,
    @SerializedName("hrv_ms") val hrvMs: Float?,
    @SerializedName("spo2_pct") val spo2Pct: Float?,
    @SerializedName("sleep_duration_min") val sleepDurationMin: Int?,
    @SerializedName("sleep_deep_min") val sleepDeepMin: Int?,
    @SerializedName("sleep_rem_min") val sleepRemMin: Int?,
    @SerializedName("sleep_score") val sleepScore: Int?,
    @SerializedName("calories_active") val caloriesActive: Float?,
    @SerializedName("calories_total") val caloriesTotal: Float?,
    @SerializedName("stress_score") val stressScore: Int?,
    @SerializedName("skin_temp_celsius") val skinTempCelsius: Float?,
)

data class PatternAlert(
    @SerializedName("pattern_id") val patternId: String,
    val title: String,
    val description: String,
    val severity: String,
    @SerializedName("science_note") val scienceNote: String,
    @SerializedName("days_observed") val daysObserved: Int,
)

// ── Retrofit interface ─────────────────────────────────────────────────────────

interface ApiService {

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): TokenResponse

    @POST("auth/register")
    suspend fun register(@Body credentials: CredentialsRequest): UserResponse

    @POST("entries")
    suspend fun createEntry(
        @Header("Authorization") auth: String,
        @Body entry: HealthEntryRequest,
    ): HealthEntryResponse

    @POST("wearable/batch")
    suspend fun uploadWearableBatch(
        @Header("Authorization") auth: String,
        @Body snapshots: List<WearableSnapshotRequest>,
    ): Map<String, Int>

    @GET("analysis/patterns")
    suspend fun getPatterns(
        @Header("Authorization") auth: String,
        @Query("days") days: Int = 30,
    ): List<PatternAlert>

    companion object {
        fun create(): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
            return Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

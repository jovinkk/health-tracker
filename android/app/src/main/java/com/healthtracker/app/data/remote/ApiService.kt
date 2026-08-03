package com.healthtracker.app.data.remote

import android.content.Context
import com.healthtracker.app.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

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

/** Only the fields the backend's PATCH accepts; nulls are omitted by Gson. */
data class HealthEntryPatch(
    @SerializedName("raw_input") val rawInput: String? = null,
    @SerializedName("numeric_value") val numericValue: Float? = null,
    @SerializedName("sub_category") val subCategory: String? = null,
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

    @PATCH("entries/{id}")
    suspend fun updateEntry(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body patch: HealthEntryPatch,
    ): HealthEntryResponse

    @DELETE("entries/{id}")
    suspend fun deleteEntry(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
    )

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
        fun create(context: Context): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // A 401 on a normal call means the stored token no longer resolves to a
            // user, so drop it and bounce to sign-in rather than surfacing a raw
            // HTTP code on whichever screen happened to make the request.
            val sessionInterceptor = Interceptor { chain ->
                val response = chain.proceed(chain.request())
                val isAuthCall = chain.request().url.encodedPath.startsWith("/auth/")
                if (response.code == 401 && !isAuthCall) {
                    SessionExpiry.handle(context)
                }
                response
            }
            // The backend runs on a free Render instance that sleeps after ~15
            // minutes idle and takes up to a minute to wake, so the 10s OkHttp
            // defaults would time out on the first request every time.
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(sessionInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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

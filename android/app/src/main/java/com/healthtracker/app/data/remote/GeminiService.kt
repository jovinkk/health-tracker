package com.healthtracker.app.data.remote

import com.healthtracker.app.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends a speech transcript to Gemini 1.5 Flash and returns a ParsedEntry.
 */
class GeminiService {

    private val client = OkHttpClient()
    private val gson = Gson()

    private val systemPrompt = """
You are a health data parser. The user will describe their health status, what they ate, how they feel,
pain levels, stress, mood, medications, exercise, or any health-related information via voice.

Parse their message into a structured JSON object. Return ONLY valid JSON — no markdown, no explanation.

The JSON must have these fields:
- "entry_type": one of: stress | pain | mood | nutrition | sleep | medication | exercise | note
- "numeric_value": a number 1-10 if the entry is stress/pain/mood/sleep quality (null otherwise)
- "sub_category": a string for specificity (e.g. "lower back" for pain, "anxiety" for stress, null if N/A)
- "data": an object with any additional structured fields relevant to the entry type

Examples of data fields by type:
- nutrition: {"meal": "breakfast", "foods": ["oatmeal", "banana"], "calories_est": 350, "protein_g": 12}
- pain: {"location": "lower back", "character": "dull ache", "scale": 6, "duration_min": 30}
- stress: {"triggers": ["work deadline"], "scale": 7, "physical_symptoms": ["tension headache"]}
- mood: {"scale": 8, "emotions": ["calm", "focused"], "notes": "good morning"}
- sleep: {"hours": 7.5, "quality": 6, "wake_count": 2, "notes": "vivid dreams"}
- medication: {"name": "ibuprofen", "dose_mg": 400, "reason": "headache"}
- exercise: {"type": "walk", "duration_min": 30, "intensity": "moderate", "steps": 4000}
- note: {"text": "..."}

Always include a "summary" field: one sentence summarising the entry.
""".trimIndent()

    data class ParsedEntry(
        val entryType: String,
        val numericValue: Float?,
        val subCategory: String?,
        val data: Map<String, Any>,
        val summary: String,
        val rawInput: String,
    )

    suspend fun parseHealthInput(transcript: String): ParsedEntry = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent" +
                "?key=${BuildConfig.GEMINI_API_KEY}"

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", transcript))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from Gemini")

        if (!response.isSuccessful) {
            throw Exception("Gemini API error ${response.code}: $responseBody")
        }

        val jsonResponse = JSONObject(responseBody)
        val text = jsonResponse
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val parsed = JSONObject(text)

        @Suppress("UNCHECKED_CAST")
        ParsedEntry(
            entryType = parsed.optString("entry_type", "note"),
            numericValue = if (parsed.isNull("numeric_value")) null else parsed.getDouble("numeric_value").toFloat(),
            subCategory = if (parsed.isNull("sub_category")) null else parsed.optString("sub_category"),
            data = gson.fromJson(parsed.getJSONObject("data").toString(), Map::class.java) as Map<String, Any>,
            summary = parsed.optString("summary", transcript),
            rawInput = transcript,
        )
    }
}

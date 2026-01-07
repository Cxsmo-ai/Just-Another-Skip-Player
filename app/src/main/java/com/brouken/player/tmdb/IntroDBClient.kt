package com.brouken.player.tmdb

import com.brouken.player.utils.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Client for IntroDB submission API
 * 
 * API: POST https://api.introdb.app/submit
 * Auth: X-API-Key header (format: idb_...)
 */
class IntroDBClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val API_URL = "https://api.introdb.app/submit"
    }
    
    /**
     * Submit intro timestamps to IntroDB
     * 
     * @param apiKey IntroDB API key (format: idb_...)
     * @param imdbId IMDB ID (format: tt0903747)
     * @param season Season number (1-based)
     * @param episode Episode number (1-based)
     * @param startSec Start time in seconds
     * @param endSec End time in seconds
     * @return true if submission was successful
     */
    fun submit(
        apiKey: String,
        imdbId: String,
        season: Int,
        episode: Int,
        startSec: Double,
        endSec: Double
    ): SubmissionResult {
        DebugLogger.log("IntroDB", "=== SUBMIT CALLED ===")
        DebugLogger.log("IntroDB", "IMDB: $imdbId, Season: $season, Episode: $episode")
        DebugLogger.log("IntroDB", "Times: ${startSec}s - ${endSec}s")
        
        // Trim and validate API key
        val cleanApiKey = apiKey.trim()
        DebugLogger.log("IntroDB", "API Key received length: ${apiKey.length}")
        DebugLogger.log("IntroDB", "API Key trimmed length: ${cleanApiKey.length}")
        DebugLogger.log("IntroDB", "API Key preview: ${cleanApiKey.take(15)}...")
        DebugLogger.log("IntroDB", "API Key starts with 'idb_': ${cleanApiKey.startsWith("idb_")}")
        
        if (cleanApiKey.isEmpty()) {
            DebugLogger.log("IntroDB", "ERROR: API key is empty!")
            return SubmissionResult(false, "API key is empty")
        }
        
        if (!cleanApiKey.startsWith("idb_")) {
            DebugLogger.log("IntroDB", "WARNING: API key doesn't start with 'idb_'")
        }
        
        return try {
            // Validate duration (5-180 seconds)
            val duration = endSec - startSec
            if (duration < 5 || duration > 180) {
                DebugLogger.log("IntroDB", "ERROR: duration ${duration}s must be 5-180 seconds")
                return SubmissionResult(false, "Duration must be 5-180 seconds (got ${duration}s)")
            }
            
            // Build JSON body
            val bodyJson = """
                {
                    "imdb_id": "$imdbId",
                    "season": $season,
                    "episode": $episode,
                    "start_sec": $startSec,
                    "end_sec": $endSec
                }
            """.trimIndent()
            
            DebugLogger.log("IntroDB", "Request body: $bodyJson")
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
            
            DebugLogger.log("IntroDB", "POST to: $API_URL")
            DebugLogger.log("IntroDB", "Header X-API-Key: ${cleanApiKey.take(15)}...${cleanApiKey.takeLast(4)}")
            
            val request = Request.Builder()
                .url(API_URL)
                .header("X-API-Key", cleanApiKey) // Use trimmed key
                .header("Content-Type", "application/json")
                .header("User-Agent", "Just-Player/1.0")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                DebugLogger.log("IntroDB", "Response code: ${response.code}")
                val responseBody = response.body?.string() ?: ""
                DebugLogger.log("IntroDB", "Response body: $responseBody")
                
                when (response.code) {
                    200 -> {
                        val element = json.parseToJsonElement(responseBody)
                        if (element is JsonObject) {
                            val ok = element["ok"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (ok) {
                                DebugLogger.log("IntroDB", " Submission successful")
                                return SubmissionResult(true, "Submission successful")
                            }
                        }
                        DebugLogger.log("IntroDB", " Unexpected response format")
                        SubmissionResult(false, "Unexpected response")
                    }
                    400 -> {
                        DebugLogger.log("IntroDB", " Bad request: $responseBody")
                        SubmissionResult(false, "Invalid request: $responseBody")
                    }
                    401 -> {
                        DebugLogger.log("IntroDB", " Unauthorized: Invalid API key")
                        SubmissionResult(false, "Invalid API key")
                    }
                    429 -> {
                        DebugLogger.log("IntroDB", " Rate limited")
                        SubmissionResult(false, "Rate limited (1/episode/10min)")
                    }
                    else -> {
                        DebugLogger.log("IntroDB", " Error ${response.code}")
                        SubmissionResult(false, "Error ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("IntroDB", " Error: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            SubmissionResult(false, "Network error: ${e.message}")
        }
    }
    
    data class SubmissionResult(
        val success: Boolean,
        val message: String
    )
}
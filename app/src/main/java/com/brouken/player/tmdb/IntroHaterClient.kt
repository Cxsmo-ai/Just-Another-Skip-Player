package com.brouken.player.tmdb

import com.brouken.player.utils.DebugLogger
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * IntroHater API Client
 * 
 * API: https://introhater.com/api
 * Docs: https://introhater.com/api.html
 * 
 * IMPORTANT: IntroHater does NOT have its own API key!
 * Use your DEBRID SERVICE API key (TorBox, Real-Debrid, AllDebrid, or Premiumize).
 * IntroHater uses the debrid key for authentication - get yours from:
 *   - TorBox: torbox.app → Account Settings → API
 *   - Real-Debrid: real-debrid.com/apitoken
 *   - AllDebrid: alldebrid.com → Account → API Keys
 *   - Premiumize: premiumize.me → Account → API Key
 * 
 * Priority 2 in skip fallback chain: Anime Skip → IntroHater → AniSkip → IntroDB
 */
class IntroHaterClient(private val apiKey: String) {

    companion object {
        private const val TAG = "IntroHater"
        private const val BASE_URL = "https://introhater.com/api"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Segment returned by IntroHater API
     */
    data class Segment(
        val startTime: Double,
        val endTime: Double,
        val category: String  // intro, outro, credits, recap
    )
    
    data class SkipResult(
        val segments: List<Segment>,
        val introSegment: Pair<Double, Double>?  // For IntroDB auto-submit
    )
    
    /**
     * Get skip segments for a video
     * 
     * @param imdbId IMDB ID (format: tt0903747)
     * @param season Season number (1-based)
     * @param episode Episode number (1-based)
     * @return List of segments or null if not found/error
     */
    fun getSegments(imdbId: String, season: Int, episode: Int): SkipResult? {
        if (apiKey.isEmpty()) {
            DebugLogger.log(TAG, "  ✗ No API key configured, skipping IntroHater")
            return null
        }
        
        // Build video ID: tt1234567:1:1 (imdb:season:episode)
        val videoId = "$imdbId:$season:$episode"
        val url = "$BASE_URL/segments/$videoId"
        
        DebugLogger.log(TAG, "╔═══════════════════════════════════════════════════════════════╗")
        DebugLogger.log(TAG, "║  INTROHATER SKIP DATA REQUEST                                 ║")
        DebugLogger.log(TAG, "╚═══════════════════════════════════════════════════════════════╝")
        DebugLogger.log(TAG, "  URL: $url")
        DebugLogger.log(TAG, "  Video ID: $videoId")
        DebugLogger.log(TAG, "  API Key: ${apiKey.take(10)}...${apiKey.takeLast(4)}")
        
        return try {
            val request = Request.Builder()
                .url(url)
                .header("X-API-Key", apiKey)
                .header("User-Agent", "Just-Player/1.0 Stremio-Android")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                
                DebugLogger.log(TAG, "  Response code: ${response.code}")
                DebugLogger.log(TAG, "  Response body: ${body?.take(500) ?: "null"}")
                
                when (response.code) {
                    200 -> {
                        if (body != null) {
                            parseResponse(body)
                        } else {
                            DebugLogger.log(TAG, "  ✗ Empty response body")
                            null
                        }
                    }
                    401 -> {
                        DebugLogger.log(TAG, "  ✗ Unauthorized - Invalid API key")
                        null
                    }
                    404 -> {
                        DebugLogger.log(TAG, "  ✗ No skip data found for $videoId")
                        null
                    }
                    429 -> {
                        DebugLogger.log(TAG, "  ✗ Rate limited")
                        null
                    }
                    else -> {
                        DebugLogger.log(TAG, "  ✗ Error ${response.code}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "  ✗ Exception: ${e.message}")
            null
        }
    }
    
    /**
     * Parse IntroHater API response
     * Actual format: [{ "start": 123, "end": 213, "label": "Intro", ... }]
     * (Array of segments, not wrapped in object)
     */
    private fun parseResponse(body: String): SkipResult? {
        return try {
            val jsonElement = json.parseToJsonElement(body)
            
            // API returns a direct array, not {"segments": [...]}
            val segmentsArray = when (jsonElement) {
                is JsonArray -> jsonElement
                is JsonObject -> jsonElement["segments"] as? JsonArray ?: return null
                else -> return null
            }
            
            if (segmentsArray.isEmpty()) {
                DebugLogger.log(TAG, "  ✗ No segments in response")
                return null
            }
            
            val segments = mutableListOf<Segment>()
            var introSegment: Pair<Double, Double>? = null
            
            for (item in segmentsArray) {
                val obj = item as? JsonObject ?: continue
                
                // API uses "start"/"end" (integers in seconds), not "startTime"/"endTime"
                val startTime = obj["start"]?.jsonPrimitive?.doubleOrNull 
                    ?: obj["startTime"]?.jsonPrimitive?.doubleOrNull 
                    ?: continue
                val endTime = obj["end"]?.jsonPrimitive?.doubleOrNull 
                    ?: obj["endTime"]?.jsonPrimitive?.doubleOrNull 
                    ?: continue
                // API uses "label" not "category"
                val category = obj["label"]?.jsonPrimitive?.contentOrNull 
                    ?: obj["category"]?.jsonPrimitive?.contentOrNull 
                    ?: "unknown"
                
                val segment = Segment(startTime, endTime, category)
                segments.add(segment)
                
                DebugLogger.log(TAG, "    └─ Segment: $category [${String.format("%.2f", startTime)}s - ${String.format("%.2f", endTime)}s]")
                
                // Track intro for IntroDB auto-submit
                if (category.equals("intro", ignoreCase = true) && introSegment == null) {
                    introSegment = startTime to endTime
                }
            }
            
            DebugLogger.log(TAG, "  ✓ Found ${segments.size} segments")
            if (introSegment != null) {
                DebugLogger.log(TAG, "  ✓ Intro segment: ${introSegment.first}s - ${introSegment.second}s (will auto-submit)")
            }
            
            SkipResult(segments, introSegment)
            
        } catch (e: Exception) {
            DebugLogger.log(TAG, "  ✗ Parse error: ${e.message}")
            null
        }
    }
    
    /**
     * Convert segments to skip pairs (start, end)
     */
    fun toSkipPairs(result: SkipResult): List<Pair<Double, Double>> {
        return result.segments.map { it.startTime to it.endTime }
    }
    
    /**
     * Check if API key is configured
     */
    fun hasApiKey(): Boolean = apiKey.isNotEmpty()
}

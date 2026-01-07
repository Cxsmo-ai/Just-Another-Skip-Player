package com.brouken.player.tmdb

import com.brouken.player.utils.DebugLogger
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * SkipDB Client - Community skip database
 * Fetches live from API with short cache for updates
 * 
 * Fallback position: AnimeSkip → SkipDB → IntroHater → AniSkip → IntroDB
 */
class SkipDBClient {

    companion object {
        private const val TAG = "SkipDB"
        private const val API_URL = "https://busy-jacinta-shugi-c2885b2e.koyeb.app/download-db"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes cache
        
        // STATIC cache - shared across all instances
        @Volatile
        private var cachedData: List<SkipEntry>? = null
        @Volatile
        private var cacheTimestamp: Long = 0
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    data class SkipEntry(
        val episodeId: String,  // "tt1234567:1" or "tmdb:12345:1"
        val start: Double,
        val end: Double,
        val title: String?
    )

    /**
     * Get skip times by IMDB ID and episode number
     * Tries multiple formats:
     * - tt1234567:episodeNum (absolute episode)
     * - Match by title pattern "ShowName SxxExx"
     */
    fun getSkipTimes(imdbId: String, season: Int, episode: Int): Pair<Double, Double>? {
        DebugLogger.log(TAG, "╔═══════════════════════════════════════════════════════════════╗")
        DebugLogger.log(TAG, "║  SKIPDB LOOKUP                                                ║")
        DebugLogger.log(TAG, "╚═══════════════════════════════════════════════════════════════╝")
        DebugLogger.log(TAG, "  IMDB: $imdbId, Season: $season, Episode: $episode")
        
        val entries = fetchDatabase()
        if (entries == null || entries.isEmpty()) {
            DebugLogger.log(TAG, "  ✗ Database empty or fetch failed")
            return null
        }
        
        DebugLogger.log(TAG, "  Database has ${entries.size} entries")
        
        // Clean IMDB ID
        val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        
        // Try 1: Exact match with absolute episode number
        val searchId1 = "$cleanImdb:$episode"
        DebugLogger.log(TAG, "  Trying format 1: $searchId1")
        var entry = entries.find { it.episodeId == searchId1 }
        
        if (entry == null) {
            // Try 2: Search by title pattern SxxExx
            val seasonEpPattern = "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
            DebugLogger.log(TAG, "  Trying format 2: title contains '$seasonEpPattern'")
            entry = entries.find { 
                it.episodeId.startsWith(cleanImdb) && 
                it.title?.contains(seasonEpPattern, ignoreCase = true) == true 
            }
        }
        
        if (entry == null) {
            // Try 3: Just episode number starting with this IMDB
            DebugLogger.log(TAG, "  Trying format 3: any match starting with $cleanImdb")
            val matchingEntries = entries.filter { it.episodeId.startsWith(cleanImdb) }
            DebugLogger.log(TAG, "    Found ${matchingEntries.size} entries for this show")
            matchingEntries.take(5).forEach { e ->
                DebugLogger.log(TAG, "      - ${e.episodeId}: ${e.title}")
            }
        }
        
        if (entry != null) {
            DebugLogger.log(TAG, "  ✓ FOUND: ${entry.title}")
            DebugLogger.log(TAG, "    Skip: ${entry.start}s - ${entry.end}s")
            return entry.start to entry.end
        }
        
        DebugLogger.log(TAG, "  ✗ No match found")
        return null
    }

    /**
     * Get skip times by TMDB ID and episode number
     * Format: tmdb:12345:episodeNum
     */
    fun getSkipTimesByTmdb(tmdbId: Int, season: Int, episode: Int): Pair<Double, Double>? {
        DebugLogger.log(TAG, "Looking up TMDB: $tmdbId S${season}E$episode")
        
        val entries = fetchDatabase() ?: return null
        
        val searchId = "tmdb:$tmdbId:$episode"
        DebugLogger.log(TAG, "  Trying: $searchId")
        val entry = entries.find { it.episodeId == searchId }
        
        if (entry != null) {
            DebugLogger.log(TAG, "  ✓ Found: ${entry.title} [${entry.start}s - ${entry.end}s]")
            return entry.start to entry.end
        }
        
        DebugLogger.log(TAG, "  ✗ No entry for $searchId")
        return null
    }

    /**
     * Fetch database with caching
     * Cache refreshes every 5 minutes for live updates
     */
    private fun fetchDatabase(): List<SkipEntry>? {
        val now = System.currentTimeMillis()
        
        // Return cached if still valid
        if (cachedData != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
            DebugLogger.log(TAG, "  Using cached data (${cachedData?.size} entries)")
            return cachedData
        }

        DebugLogger.log(TAG, "  Fetching fresh database from API...")
        
        return try {
            val request = Request.Builder()
                .url(API_URL)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DebugLogger.log(TAG, "  API error: ${response.code}")
                    return cachedData // Return stale cache if available
                }

                val body = response.body?.string() ?: return cachedData
                DebugLogger.log(TAG, "  Received ${body.length} bytes")
                
                val entries = parseDatabase(body)
                
                // Update static cache
                cachedData = entries
                cacheTimestamp = now
                
                DebugLogger.log(TAG, "  ✓ Loaded ${entries.size} entries")
                entries
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "  Fetch error: ${e.message}")
            e.printStackTrace()
            cachedData // Return stale cache on error
        }
    }

    private fun parseDatabase(jsonString: String): List<SkipEntry> {
        return try {
            val array = json.parseToJsonElement(jsonString).jsonArray
            
            array.mapNotNull { element ->
                val obj = element.jsonObject
                
                val episodeId = obj["episodeId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val start = obj["start"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val end = obj["end"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull
                
                SkipEntry(episodeId, start, end, title)
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "  Parse error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clear cache to force fresh fetch
     */
    fun clearCache() {
        cachedData = null
        cacheTimestamp = 0
    }
}

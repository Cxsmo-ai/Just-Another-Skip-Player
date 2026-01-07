package com.brouken.player.tmdb

import com.brouken.player.utils.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SkipManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    
    // API Endpoints
    private val INTRODB_URL = "https://api.introdb.app/intro"
    private val ANISKIP_URL = "https://api.aniskip.com/v2"
    
    // Cache: "malId-S-E" -> List<Pair<Double, Double>> and "imdbId-S-E" -> List<Pair<Double, Double>>
    private val cacheMal = mutableMapOf<String, List<Pair<Double, Double>>>()
    private val cacheImdb = mutableMapOf<String, List<Pair<Double, Double>>>()
    
    // Source tracking
    enum class SkipSource {
        ANIMESKIP,  // New: Primary source for anime with community timestamps
        ANISKIP,    // Fallback: AniSkip API for anime
        INTRODB,    // Universal fallback for all content
        MAL
    }
    
    data class SkipResult(
        val segments: List<Pair<Double, Double>>,
        val source: SkipSource
    )

    /**
     * Get skip times from multiple sources with 5-tier fallback:
     * AnimeSkip → SkipDB → IntroHater → AniSkip → IntroDB
     */
    fun getSkipTimes(
        malId: Int?, 
        imdbId: String?, 
        season: Int, 
        episode: Int, 
        showName: String? = null,
        episodeName: String? = null,
        introHaterApiKey: String? = null,
        introDbApiKey: String? = null, 
        onAutoSubmitCallback: Runnable? = null
    ): List<Pair<Double, Double>>? {
        DebugLogger.log("SkipManager", "╔═══════════════════════════════════════════════════════════════╗")
        DebugLogger.log("SkipManager", "║  5-TIER SKIP DATA REQUEST                                     ║")
        DebugLogger.log("SkipManager", "║  AnimeSkip → SkipDB → IntroHater → AniSkip → IntroDB         ║")
        DebugLogger.log("SkipManager", "╚═══════════════════════════════════════════════════════════════╝")
        DebugLogger.log("SkipManager", "  Show: $showName")
        DebugLogger.log("SkipManager", "  Episode Name: $episodeName")
        DebugLogger.log("SkipManager", "  Season: $season, Episode: $episode")
        DebugLogger.log("SkipManager", "  MAL ID: $malId, IMDB ID: $imdbId")
        DebugLogger.log("SkipManager", "  IntroHater API Key: ${if (!introHaterApiKey.isNullOrEmpty()) "***SET***" else "NULL"}")
        DebugLogger.log("SkipManager", "  IntroDB API Key: ${if (!introDbApiKey.isNullOrEmpty()) "***SET***" else "NULL"}")
        DebugLogger.log("SkipManager", "")
        
        // Priority 1: Try AnimeSkip (anime community timestamps)
        if (!showName.isNullOrEmpty() && !episodeName.isNullOrEmpty()) {
            DebugLogger.log("SkipManager", "[PRIORITY 1] Trying AnimeSkip...")
            try {
                val animeSkipClient = AnimeSkipClient()
                val result = animeSkipClient.getTimestamps(showName, episodeName)
                
                if (result != null && result.timestamps.isNotEmpty()) {
                    DebugLogger.log("SkipManager", "  ✓ AnimeSkip SUCCESS: Found ${result.timestamps.size} timestamps")
                    
                    // Filter to intro types only for skip segments
                    val introTimestamps = result.timestamps.filter { animeSkipClient.isIntroType(it.typeId) }
                    if (introTimestamps.isNotEmpty()) {
                        val segments = introTimestamps.map { ts ->
                            val endAt = ts.at + 90.0 // Default 90 second intro
                            ts.at to endAt
                        }
                        
                        // Auto-submit to IntroDB
                        if (!introDbApiKey.isNullOrEmpty() && !imdbId.isNullOrEmpty() && segments.isNotEmpty()) {
                            DebugLogger.log("SkipManager", "  → Auto-submitting AnimeSkip intro to IntroDB")
                            Thread {
                                try {
                                    val seg = segments.first()
                                    IntroDBClient().submit(introDbApiKey, imdbId, season, episode, seg.first, seg.second)
                                    DebugLogger.log("SkipManager", "  ✓ AnimeSkip intro submitted to IntroDB")
                                    onAutoSubmitCallback?.run()
                                } catch (e: Exception) {
                                    DebugLogger.log("SkipManager", "  ✗ IntroDB submit error: ${e.message}")
                                }
                            }.start()
                        }
                        
                        return segments
                    }
                }
            } catch (e: Exception) {
                DebugLogger.log("SkipManager", "  ✗ AnimeSkip error: ${e.message}")
            }
        } else {
            DebugLogger.log("SkipManager", "  → Skipping AnimeSkip (no show/episode name)")
        }
        
        // Priority 2: Try SkipDB (community database - works for all content with IMDB ID)
        if (imdbId != null && imdbId.isNotEmpty()) {
            DebugLogger.log("SkipManager", "[PRIORITY 2] Trying SkipDB...")
            try {
                val skipDbClient = SkipDBClient()
                val segment = skipDbClient.getSkipTimes(imdbId, season, episode)
                
                if (segment != null) {
                    DebugLogger.log("SkipManager", "  ✓ SkipDB SUCCESS: Found segment [${segment.first}s - ${segment.second}s]")
                    val segments = listOf(segment)
                    
                    // Auto-submit to IntroDB
                    if (!introDbApiKey.isNullOrEmpty()) {
                        DebugLogger.log("SkipManager", "  → Auto-submitting SkipDB intro to IntroDB")
                        Thread {
                            try {
                                IntroDBClient().submit(introDbApiKey, imdbId, season, episode,
                                    segment.first, segment.second)
                                DebugLogger.log("SkipManager", "  ✓ SkipDB intro submitted to IntroDB")
                                onAutoSubmitCallback?.run()
                            } catch (e: Exception) {
                                DebugLogger.log("SkipManager", "  ✗ IntroDB submit error: ${e.message}")
                            }
                        }.start()
                    }
                    
                    return segments
                } else {
                    DebugLogger.log("SkipManager", "  ✗ SkipDB returned NO results - will try IntroDB")
                }
            } catch (e: Exception) {
                DebugLogger.log("SkipManager", "  ✗ SkipDB error: ${e.message}")
            }
        }
        
        // Priority 3: Try IntroHater (if API key configured)
        if (!introHaterApiKey.isNullOrEmpty() && !imdbId.isNullOrEmpty()) {
            DebugLogger.log("SkipManager", "[PRIORITY 3] Trying IntroHater...")
            try {
                val introHaterClient = IntroHaterClient(introHaterApiKey)
                val result = introHaterClient.getSegments(imdbId, season, episode)
                
                if (result != null && result.segments.isNotEmpty()) {
                    DebugLogger.log("SkipManager", "  ✓ IntroHater SUCCESS: Found ${result.segments.size} segments")
                    
                    // Auto-submit to IntroDB
                    if (result.introSegment != null && !introDbApiKey.isNullOrEmpty()) {
                        DebugLogger.log("SkipManager", "  → Auto-submitting IntroHater intro to IntroDB")
                        Thread {
                            try {
                                IntroDBClient().submit(introDbApiKey, imdbId, season, episode,
                                    result.introSegment.first, result.introSegment.second)
                                DebugLogger.log("SkipManager", "  ✓ IntroHater intro submitted to IntroDB")
                                onAutoSubmitCallback?.run()
                            } catch (e: Exception) {
                                DebugLogger.log("SkipManager", "  ✗ IntroDB submit error: ${e.message}")
                            }
                        }.start()
                    }
                    
                    return introHaterClient.toSkipPairs(result)
                }
            } catch (e: Exception) {
                DebugLogger.log("SkipManager", "  ✗ IntroHater error: ${e.message}")
            }
        } else {
            DebugLogger.log("SkipManager", "  → Skipping IntroHater (API key: ${!introHaterApiKey.isNullOrEmpty()}, IMDB: ${!imdbId.isNullOrEmpty()})")
        }
        
        // Priority 4: Try AniSkip (anime only - requires MAL ID)
        if (malId != null) {
            val keyMal = "$malId-$season-$episode"
            DebugLogger.log("SkipManager", "[PRIORITY 4] Trying AniSkip...")
            if (cacheMal.containsKey(keyMal)) {
                DebugLogger.log("SkipManager", "  ✓ CACHE HIT: AniSkip data found")
                return cacheMal[keyMal]
            }

            val result = tryAniSkip(malId, season, episode)
            if (result != null && result.segments.isNotEmpty()) {
                cacheMal[keyMal] = result.segments
                DebugLogger.log("SkipManager", "  ✓ AniSkip SUCCESS: Found ${result.segments.size} segments")
                
                // Auto-submit to IntroDB
                if (!imdbId.isNullOrEmpty() && !introDbApiKey.isNullOrEmpty()) {
                    DebugLogger.log("SkipManager", "  → Auto-submitting AniSkip intro to IntroDB")
                    Thread {
                        try {
                            val seg = result.segments.first()
                            IntroDBClient().submit(introDbApiKey, imdbId, season, episode, seg.first, seg.second)
                            DebugLogger.log("SkipManager", "  ✓ AniSkip intro submitted to IntroDB")
                            onAutoSubmitCallback?.run()
                        } catch (e: Exception) {
                            DebugLogger.log("SkipManager", "  ✗ IntroDB submit error: ${e.message}")
                        }
                    }.start()
                }
                
                return result.segments
            } else {
                DebugLogger.log("SkipManager", "  ✗ AniSkip returned NO results")
            }
        } else {
            DebugLogger.log("SkipManager", "  → Skipping AniSkip (no MAL ID)")
        }
        
        // Priority 5: Try IntroDB (final fallback)
        if (imdbId != null && imdbId.isNotEmpty()) {
            val keyImdb = "$imdbId-$season-$episode"
            DebugLogger.log("SkipManager", "[PRIORITY 5] Trying IntroDB...")
            if (cacheImdb.containsKey(keyImdb)) {
                DebugLogger.log("SkipManager", "  ✓ CACHE HIT: IntroDB data found for $keyImdb")
                val cached = cacheImdb[keyImdb]
                cached?.forEach { DebugLogger.log("SkipManager", "    └─ Segment: ${String.format("%.2f", it.first)}s - ${String.format("%.2f", it.second)}s") }
                DebugLogger.log("SkipManager", "  Returning cached segments (${cached?.size} total)")
                return cached
            }

            DebugLogger.log("SkipManager", "  → Calling IntroDB API for $imdbId S${season}E${episode}...")
            val result = tryIntroDB(imdbId, season, episode)
            if (result != null && result.segments.isNotEmpty()) {
                cacheImdb[keyImdb] = result.segments
                DebugLogger.log("SkipManager", "  ✓ IntroDB SUCCESS: Found ${result.segments.size} segments from source: ${result.source}")
                result.segments.forEach { DebugLogger.log("SkipManager", "    └─ Segment: ${String.format("%.2f", it.first)}s - ${String.format("%.2f", it.second)}s") }
                DebugLogger.log("SkipManager", "  Returning IntroDB segments")
                return result.segments
            } else {
                DebugLogger.log("SkipManager", "  ✗ IntroDB returned NO results")
            }
        } else {
            DebugLogger.log("SkipManager", "  → Skipping IntroDB - IMDB ID is null/empty")
        }
        
        DebugLogger.log("SkipManager", "╔═══════════════════════════════════════════════════════════════╗")
        DebugLogger.log("SkipManager", "║  ❌ NO SKIP DATA FOUND FROM ANY SOURCE                       ║")
        DebugLogger.log("SkipManager", "╚═══════════════════════════════════════════════════════════════╝")
        DebugLogger.log("SkipManager", "")
        return null
    }
    
    /**
     * Auto-submit AniSkip data to IntroDB
     */
    private fun autoSubmitToIntroDB(apiKey: String, imdbId: String, season: Int, episode: Int, segments: List<Pair<Double, Double>>, onCallback: Runnable? = null) {
        try {
            if (segments.isNotEmpty()) {
                val (start, end) = segments[0]
                DebugLogger.log("SkipManager", "=>> Submitting to IntroDB: $imdbId S${season}E${episode} [${start}s - ${end}s]")
                val client = IntroDBClient()
                val result = client.submit(apiKey, imdbId, season, episode, start, end)
                DebugLogger.log("SkipManager", "=>> IntroDB Submit Result: ${result.success} - ${result.message}")
                
                if (result.success) {
                    onCallback?.run()
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("SkipManager", "=>> IntroDB Submit ERROR: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Try AniSkip API
     */
    private fun tryAniSkip(malId: Int, season: Int, episode: Int): SkipResult? {
        return try {
            // AniSkip API requires episodeLength parameter (use 0 to let server use default)
            val url = "$ANISKIP_URL/skip-times/$malId/$episode?types[]=op&types[]=ed&episodeLength=1440"
            DebugLogger.log("AniSkip", "GET Request (Fixed): $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Just-Player/1.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                DebugLogger.log("AniSkip", "Response Code: ${response.code}")
                
                val body = response.body?.string()
                if (body != null) {
                    DebugLogger.log("AniSkip", "Response Body: ${body}")
                    
                    if (response.isSuccessful) {
                        try {
                            val element = json.parseToJsonElement(body)
                            val segments = parseAniSkipResponse(element)
                            DebugLogger.log("AniSkip", "Parsed Segments: ${segments.size}")
                            segments.forEach { DebugLogger.log("AniSkip", "  - ${it.first}s to ${it.second}s") }
                            
                            if (segments.isNotEmpty()) {
                                return SkipResult(segments, SkipSource.ANISKIP)
                            }
                        } catch (e: Exception) {
                            DebugLogger.log("AniSkip", "JSON Parse Error: ${e.message}")
                        }
                    }
                } else {
                    DebugLogger.log("AniSkip", "Response Body is NULL")
                }
            }
            null
        } catch (e: Exception) {
            DebugLogger.log("AniSkip", "Exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse AniSkip API response
     */
    private fun parseAniSkipResponse(element: JsonElement): List<Pair<Double, Double>> {
        val segments = mutableListOf<Pair<Double, Double>>()
        
        if (element is JsonObject) {
            val results = element["results"] as? JsonArray
            results?.let { array ->
                for (item in array) {
                    if (item is JsonObject) {
                        val interval = item["interval"] as? JsonObject
                        val start = interval?.get("startTime")?.jsonPrimitive?.doubleOrNull
                        val end = interval?.get("endTime")?.jsonPrimitive?.doubleOrNull
                        val skipType = item["skipType"]?.jsonPrimitive?.content
                        
                        DebugLogger.log("AniSkip", "  Found item: Type=$skipType, Start=$start, End=$end")
                        
                        if (start != null && end != null && (skipType == "op" || skipType == "ed")) {
                            segments.add(start to end)
                        }
                    }
                }
            }
        }
        return segments
    }

    /**
     * Try IntroDB API
     */
    private fun tryIntroDB(imdbId: String, season: Int, episode: Int): SkipResult? {
        return try {
            val url = "$INTRODB_URL?imdb_id=$imdbId&season=$season&episode=$episode"
            DebugLogger.log("IntroDB", "GET Request: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Just-Player/1.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                DebugLogger.log("IntroDB", "Response Code: ${response.code}")
                
                val body = response.body?.string()
                if (body != null) {
                    DebugLogger.log("IntroDB", "Response Body: ${body}")
                    
                    if (response.isSuccessful) {
                        try {
                            val element = json.parseToJsonElement(body)
                            val segments = parseSegments(element)
                            DebugLogger.log("IntroDB", "Parsed Segments: ${segments.size}")
                            segments.forEach { DebugLogger.log("IntroDB", "  - ${it.first}s to ${it.second}s") }
                            
                            if (segments.isNotEmpty()) {
                                return SkipResult(segments, SkipSource.INTRODB)
                            }
                        } catch (e: Exception) {
                            DebugLogger.log("IntroDB", "JSON Parse Error: ${e.message}")
                        }
                    }
                } else {
                    DebugLogger.log("IntroDB", "Response Body is NULL")
                }
            }
            null
        } catch (e: Exception) {
            DebugLogger.log("IntroDB", "Exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Generic segment parser for IntroDB-like responses
     */
    private fun parseSegments(element: JsonElement): List<Pair<Double, Double>> {
        val segments = mutableListOf<Pair<Double, Double>>()
        
        fun extract(obj: JsonObject): Pair<Double, Double>? {
            val s = obj["start"]?.jsonPrimitive?.doubleOrNull 
                ?: obj["start_sec"]?.jsonPrimitive?.doubleOrNull
                ?: obj["startTime"]?.jsonPrimitive?.doubleOrNull
            val e = obj["end"]?.jsonPrimitive?.doubleOrNull 
                ?: obj["end_sec"]?.jsonPrimitive?.doubleOrNull
                ?: obj["endTime"]?.jsonPrimitive?.doubleOrNull
            
            DebugLogger.log("IntroDB", "  Parse check: start=$s, end=$e")
            if (s != null && e != null) return s to e
            return null
        }

        if (element is JsonObject) {
            val single = extract(element)
            if (single != null) {
                segments.add(single)
            } else {
                for (entry in element.entries) {
                    val v = entry.value
                    if (v is JsonObject) {
                        extract(v)?.let { segments.add(it) }
                    }
                }
            }
        } else if (element is JsonArray) {
            for (item in element) {
                if (item is JsonObject) {
                    extract(item)?.let { segments.add(it) }
                }
            }
        }
        return segments
    }
    
    fun shouldSkip(positionSec: Double, segments: List<Pair<Double, Double>>?, timeShiftSec: Int = 0): Double? {
        if (segments.isNullOrEmpty()) {
            DebugLogger.log("SkipManager", "shouldSkip: segments is null or empty, returning null")
            return null
        }
        val shift = timeShiftSec.toDouble()
        DebugLogger.log("SkipManager", "shouldSkip: Checking position ${String.format("%.2f", positionSec)}s against ${segments.size} segments (shift: ${timeShiftSec}s)")
        for ((index, seg) in segments.withIndex()) {
            val (rawStart, rawEnd) = seg
            val start = rawStart + shift
            val end = rawEnd + shift
            val inSegment = positionSec >= start && positionSec < end
            DebugLogger.log("SkipManager", "  Segment #$index: [${String.format("%.2f", start)}s - ${String.format("%.2f", end)}s] - ${if (inSegment) "⚠️ IN SEGMENT" else "not in segment"}")
            if (inSegment) {
                DebugLogger.log("SkipManager", "  → SKIP RECOMMENDED: Seek to ${String.format("%.2f", end)}s")
                return end
            }
        }
        DebugLogger.log("SkipManager", "  → No skip needed for current position")
        return null
    }
    
    fun getUpcomingSegment(positionSec: Double, segments: List<Pair<Double, Double>>?, timeShiftSec: Int = 0): Pair<Double, Double>? {
        if (segments.isNullOrEmpty()) {
            DebugLogger.log("SkipManager", "getUpcomingSegment: segments is null or empty, returning null")
            return null
        }
        val shift = timeShiftSec.toDouble()
        DebugLogger.log("SkipManager", "getUpcomingSegment: Checking for upcoming segments near ${String.format("%.2f", positionSec)}s (shift: ${timeShiftSec}s)")
        for ((index, seg) in segments.withIndex()) {
            val (rawStart, rawEnd) = seg
            val start = rawStart + shift
            val end = rawEnd + shift
            val isUpcoming = start > positionSec && start <= positionSec + 5
            val distance = start - positionSec
            DebugLogger.log("SkipManager", "  Segment #$index: [${String.format("%.2f", start)}s - ${String.format("%.2f", end)}s] - ${if (isUpcoming) "⚡ UPCOMING in ${String.format("%.2f", distance)}s" else "${String.format("%.2f", distance)}s away"}")
            if (isUpcoming) {
                DebugLogger.log("SkipManager", "  → UPCOMING SEGMENT FOUND: Will start in ${String.format("%.2f", distance)}s")
                return start to end  // Return shifted times
            }
        }
        DebugLogger.log("SkipManager", "  → No upcoming segments within 5 seconds")
        return null
    }
    
    fun clearCache() {
        cacheMal.clear()
        cacheImdb.clear()
    }

    /**
     * Extended method that tries Anime Skip first, then falls back to AniSkip/IntroDB.
     * Auto-submits INTRO ONLY times to IntroDB.
     * 
     * @param showName The anime show name
     * @param episodeName The episode identifier (e.g., "Episode 1")
     * @param prefs User preferences for which segment types to skip
     * @param malId MAL ID for AniSkip fallback
     * @param imdbId IMDB ID for IntroDB fallback
     * @param season Season number
     * @param episode Episode number
     * @param introDbApiKey API key for IntroDB auto-submit
     */
    fun getSkipTimesWithAnimeSkip(
        showName: String?,
        episodeName: String?,
        skipBranding: Boolean,
        skipRecaps: Boolean,
        skipTitleCard: Boolean,
        skipIntros: Boolean,
        skipNewIntros: Boolean,
        skipMixedIntros: Boolean,
        skipCanon: Boolean,
        skipFiller: Boolean,
        skipTransitions: Boolean,
        skipCredits: Boolean,
        skipNewCredits: Boolean,
        skipMixedCredits: Boolean,
        skipPreview: Boolean,
        malId: Int?,
        imdbId: String?,
        season: Int,
        episode: Int,
        introHaterApiKey: String? = null,
        introDbApiKey: String? = null
    ): List<Pair<Double, Double>>? {
        DebugLogger.log("SkipManager", "╔═══════════════════════════════════════════════════════════════╗")
        DebugLogger.log("SkipManager", "║  ANIME SKIP EXTENDED REQUEST                                  ║")
        DebugLogger.log("SkipManager", "╚═══════════════════════════════════════════════════════════════╝")
        DebugLogger.log("SkipManager", "  Show: $showName")
        DebugLogger.log("SkipManager", "  Episode: $episodeName")
        
        // Priority 1: Try Anime Skip API
        if (!showName.isNullOrEmpty() && !episodeName.isNullOrEmpty()) {
            try {
                val animeSkipClient = AnimeSkipClient()
                val result = animeSkipClient.getTimestamps(showName, episodeName)
                
                if (result != null && result.timestamps.isNotEmpty()) {
                    DebugLogger.log("SkipManager", "  ✓ Anime Skip found ${result.timestamps.size} timestamps")
                    
                    // Filter by user preferences  
                    val filtered = animeSkipClient.filterByPreferences(
                        result.timestamps,
                        skipBranding, skipRecaps, skipTitleCard, skipIntros,
                        skipNewIntros, skipMixedIntros, skipCanon, skipFiller,
                        skipTransitions, skipCredits, skipNewCredits, skipMixedCredits, skipPreview
                    )
                    
                    DebugLogger.log("SkipManager", "  After preference filter: ${filtered.size} timestamps to skip")
                    
                    // Convert to skip segments (need to pair starts with ends)
                    // Anime Skip timestamps are start points - find the next timestamp for end
                    val segments = mutableListOf<Pair<Double, Double>>()
                    var introSegment: Pair<Double, Double>? = null
                    
                    for (i in filtered.indices) {
                        val ts = filtered[i]
                        val endAt = if (i + 1 < result.timestamps.size) {
                            result.timestamps[i + 1].at
                        } else {
                            ts.at + 90.0  // Default 90 second segment if no end point
                        }
                        segments.add(ts.at to endAt)
                        
                        // Track intro for IntroDB auto-submit (INTRO ONLY)
                        if (animeSkipClient.isIntroType(ts.typeId)) {
                            introSegment = ts.at to endAt
                        }
                    }
                    
                    // Auto-submit INTRO ONLY to IntroDB
                    if (introSegment != null && !introDbApiKey.isNullOrEmpty() && !imdbId.isNullOrEmpty()) {
                        DebugLogger.log("SkipManager", "  → Auto-submitting INTRO ONLY to IntroDB")
                        Thread {
                            try {
                                val introDbClient = IntroDBClient()
                                introDbClient.submit(introDbApiKey, imdbId, season, episode, 
                                    introSegment.first, introSegment.second)
                                DebugLogger.log("SkipManager", "  ✓ Intro submitted to IntroDB")
                            } catch (e: Exception) {
                                DebugLogger.log("SkipManager", "  ✗ IntroDB submit error: ${e.message}")
                            }
                        }.start()
                    }
                    
                    if (segments.isNotEmpty()) {
                        return segments
                    }
                }
            } catch (e: Exception) {
                DebugLogger.log("SkipManager", "  ✗ Anime Skip error: ${e.message}")
            }
        }
        
        DebugLogger.log("SkipManager", "  → Trying SkipDB fallback...")
        
        // Priority 2: Try SkipDB (community database)
        if (!imdbId.isNullOrEmpty()) {
            try {
                val skipDbClient = SkipDBClient()
                val segment = skipDbClient.getSkipTimes(imdbId, season, episode)
                
                if (segment != null) {
                    DebugLogger.log("SkipManager", "  ✓ SkipDB found: [${segment.first}s - ${segment.second}s]")
                    
                    // Auto-submit to IntroDB
                    if (!introDbApiKey.isNullOrEmpty()) {
                        DebugLogger.log("SkipManager", "  → Auto-submitting SkipDB intro to IntroDB")
                        Thread {
                            try {
                                IntroDBClient().submit(introDbApiKey, imdbId, season, episode,
                                    segment.first, segment.second)
                                DebugLogger.log("SkipManager", "  ✓ SkipDB intro submitted to IntroDB")
                            } catch (e: Exception) {
                                DebugLogger.log("SkipManager", "  ✗ IntroDB submit error: ${e.message}")
                            }
                        }.start()
                    }
                    
                    return listOf(segment)
                }
            } catch (e: Exception) {
                DebugLogger.log("SkipManager", "  ✗ SkipDB error: ${e.message}")
            }
        }
        
        DebugLogger.log("SkipManager", "  → Trying IntroHater fallback...")
        
        // Priority 3: Try IntroHater (if API key configured)
        if (!introHaterApiKey.isNullOrEmpty() && !imdbId.isNullOrEmpty()) {
            try {
                val introHaterClient = IntroHaterClient(introHaterApiKey)
                val result = introHaterClient.getSegments(imdbId, season, episode)
                
                if (result != null && result.segments.isNotEmpty()) {
                    DebugLogger.log("SkipManager", "  ✓ IntroHater found ${result.segments.size} segments")
                    
                    // Auto-submit INTRO ONLY to IntroDB
                    if (result.introSegment != null && !introDbApiKey.isNullOrEmpty()) {
                        DebugLogger.log("SkipManager", "  → Auto-submitting INTRO from IntroHater to IntroDB")
                        Thread {
                            try {
                                IntroDBClient().submit(introDbApiKey, imdbId, season, episode,
                                    result.introSegment.first, result.introSegment.second)
                                DebugLogger.log("SkipManager", "  ✓ Intro submitted to IntroDB")
                            } catch (e: Exception) {
                                DebugLogger.log("SkipManager", "  ✗ IntroDB submit error: ${e.message}")
                            }
                        }.start()
                    }
                    
                    return introHaterClient.toSkipPairs(result)
                }
            } catch (e: Exception) {
                DebugLogger.log("SkipManager", "  ✗ IntroHater error: ${e.message}")
            }
        } else {
            DebugLogger.log("SkipManager", "  → Skipping IntroHater (API key: ${!introHaterApiKey.isNullOrEmpty()}, IMDB: ${!imdbId.isNullOrEmpty()})")
        }
        
        DebugLogger.log("SkipManager", "  → Falling back to standard sources (AniSkip/IntroDB)")
        
        // Priority 3 & 4: Fallback to AniSkip/IntroDB
        return getSkipTimes(malId, imdbId, season, episode, introDbApiKey, null)
    }
    
    /**
     * Extended method with IntroHater API key parameter
     * Full 4-tier fallback: Anime Skip → IntroHater → AniSkip → IntroDB
     */
    fun getSkipTimesWithFullFallback(
        showName: String?,
        episodeName: String?,
        skipBranding: Boolean,
        skipRecaps: Boolean,
        skipTitleCard: Boolean,
        skipIntros: Boolean,
        skipNewIntros: Boolean,
        skipMixedIntros: Boolean,
        skipCanon: Boolean,
        skipFiller: Boolean,
        skipTransitions: Boolean,
        skipCredits: Boolean,
        skipNewCredits: Boolean,
        skipMixedCredits: Boolean,
        skipPreview: Boolean,
        malId: Int?,
        imdbId: String?,
        season: Int,
        episode: Int,
        introHaterApiKey: String? = null,
        introDbApiKey: String? = null
    ): List<Pair<Double, Double>>? {
        // Use the existing method but with IntroHater integrated
        return getSkipTimesWithAnimeSkip(
            showName, episodeName,
            skipBranding, skipRecaps, skipTitleCard, skipIntros,
            skipNewIntros, skipMixedIntros, skipCanon, skipFiller,
            skipTransitions, skipCredits, skipNewCredits, skipMixedCredits, skipPreview,
            malId, imdbId, season, episode, introHaterApiKey, introDbApiKey
        )
    }
}


package com.brouken.player.tmdb

import com.brouken.player.utils.DebugLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for Jikan API (unofficial MAL API)
 * 
 * Jikan provides access to MyAnimeList data without requiring an API key.
 * Used to resolve anime titles to MAL IDs for AniSkip integration.
 * 
 * API Docs: https://docs.api.jikan.moe/
 */
class JikanClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    
    private val BASE_URL = "https://api.jikan.moe/v4"
    
    @Serializable
    data class AnimeSearchResponse(
        val data: List<AnimeData> = emptyList()
    )
    
    @Serializable
    data class AnimeData(
        val mal_id: Int,
        val title: String,
        val title_english: String? = null,
        val title_japanese: String? = null,
        val type: String? = null, // TV, Movie, OVA, etc.
        val episodes: Int? = null,
        val year: Int? = null, // Season year (e.g. 2023)
        val aired: Aired? = null
    )
    
    @Serializable
    data class Aired(
        val from: String? = null,
        val to: String? = null
    )
    
    /**
     * Search for anime by title and return MAL ID
     * 
     * @param query Anime title to search for
     * @param year Optional release year to filter/score results
     * @return MAL ID of the best matching result, or null if not found
     */
    fun searchMalId(query: String, year: Int? = null): Int? {
        DebugLogger.log("Jikan", "searchMalId called with query: '$query', year: $year")
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // Search for TV/OVA/Movie/Special. removed &type=tv to support Movies too if needed.
            // But typical AniSkip use is for TV. Keeping type=tv for now as strict default, or removing it?
            // User might watch movies: "One Piece Film Red". Let's remove &type=tv to support all.
            val url = "$BASE_URL/anime?q=$encodedQuery&limit=5" 
            DebugLogger.log("Jikan", "Requesting Jikan API: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Just-Player/1.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                DebugLogger.log("Jikan", "Response code: ${response.code}")
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    // DebugLogger.log("Jikan", "Response body: ${body.take(300)}...")
                    val searchResult = json.decodeFromString<AnimeSearchResponse>(body)
                    
                    if (searchResult.data.isNotEmpty()) {
                        DebugLogger.log("Jikan", "Processing ${searchResult.data.size} candidates...")
                        
                        var bestMatch: AnimeData? = null
                        var bestScore = -1
                        
                        for (anime in searchResult.data) {
                            var score = 0
                            
                            // 1. Year Matching (Highest Priority)
                            if (year != null) {
                                if (anime.year == year) {
                                    score += 100
                                } else {
                                    val airedYear = parseYear(anime.aired?.from)
                                    if (airedYear != null && kotlin.math.abs(airedYear - year) <= 1) {
                                        score += 50
                                    }
                                }
                            }
                            
                            // 2. Title Matching
                            val normTitle = anime.title.lowercase()
                            val normQuery = query.lowercase()
                            
                            if (normTitle == normQuery) {
                                score += 30
                            } else if (normTitle.contains(normQuery) || normQuery.contains(normTitle)) {
                                score += 10
                            }
                            
                            // Debug logging for scoring
                            DebugLogger.log("Jikan", "  Candidate: '${anime.title}' (${anime.year ?: parseYear(anime.aired?.from)}) - Score: $score")
                            
                            if (score > bestScore) {
                                bestScore = score
                                bestMatch = anime
                            }
                        }
                        
                        if (bestMatch != null) {
                            DebugLogger.log("Jikan", "  >>> Best Match: '${bestMatch.title}' (ID: ${bestMatch.mal_id}, Score: $bestScore)")
                            return bestMatch.mal_id
                        }
                        
                        // Fallback: Default to first result if no clear winner (and no year constraint failed strictly?)
                        // If year was requested but no match found, use first result anyway?
                        // User said "if it matches... good". But usually better to return *something* than nothing.
                        val first = searchResult.data[0]
                        DebugLogger.log("Jikan", "  No high-score match, defaulting to first: '${first.title}'")
                        return first.mal_id
                    } else {
                        DebugLogger.log("Jikan", "  No results found for query: '$query'")
                    }
                } else {
                    DebugLogger.log("Jikan", "  Request failed: ${response.code} ${response.message}")
                }
            }
            null
        } catch (e: Exception) {
            DebugLogger.log("Jikan", "  Error: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun parseYear(dateStr: String?): Int? {
        if (dateStr == null || dateStr.length < 4) return null
        return dateStr.substring(0, 4).toIntOrNull()
    }
    
    /**
     * Search for anime with more details
     */
    fun searchAnime(query: String): AnimeData? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/anime?q=$encodedQuery&limit=3"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Just-Player/1.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val searchResult = json.decodeFromString<AnimeSearchResponse>(body)
                    if (searchResult.data.isNotEmpty()) {
                        return searchResult.data[0]
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
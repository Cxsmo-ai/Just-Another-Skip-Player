package com.brouken.player.tmdb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for Stremio Cinemeta addon API
 * 
 * Cinemeta provides IMDB IDs for movies and series without requiring an API key.
 * 
 * API Endpoints:
 * - Search: /catalog/{type}/top/search={query}.json
 * - Meta: /meta/{type}/{imdbId}.json
 */
class CinemetaClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Search for a show/movie and return its IMDB ID
     * 
     * @param baseUrl Cinemeta addon URL (e.g., "https://v3-cinemeta.strem.io")
     * @param type Content type ("movie" or "series")
     * @param query Search query (show name)
     * @return IMDB ID (e.g., "tt0903747") or null if not found
     */
    /**
     * Search for a show/movie and return its IMDB ID
     * 
     * @param baseUrl Cinemeta addon URL (e.g., "https://v3-cinemeta.strem.io")
     * @param type Content type ("movie" or "series")
     * @param query Search query (show name)
     * @param year Optional release year for verification
     * @return IMDB ID (e.g., "tt0903747") of best match or null
     */
    fun searchImdbId(baseUrl: String, type: String, query: String, year: Int? = null): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "${baseUrl.trimEnd('/')}/catalog/$type/top/search=$encodedQuery.json"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Just-Player/1.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val element = json.parseToJsonElement(body)
                    
                    if (element is JsonObject) {
                        val metas = element["metas"] as? JsonArray
                        if (metas != null && metas.isNotEmpty()) {
                            
                            var bestId: String? = null
                            var bestScore = -1
                            
                            for (item in metas) {
                                if (item !is JsonObject) continue
                                
                                val id = item["imdb_id"]?.jsonPrimitive?.contentOrNull
                                    ?: item["id"]?.jsonPrimitive?.contentOrNull
                                    ?: continue
                                
                                val title = item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                val releaseInfo = item["releaseInfo"]?.jsonPrimitive?.contentOrNull ?: ""
                                
                                // Extract year from releaseInfo (e.g. "2008-2013" -> 2008, "2023" -> 2023)
                                val metaYear = releaseInfo.take(4).toIntOrNull()
                                
                                var score = 0
                                
                                // 1. Year Match (High Priority)
                                if (year != null && metaYear != null) {
                                    if (metaYear == year) {
                                        score += 100
                                    } else if (kotlin.math.abs(metaYear - year) <= 1) {
                                        score += 50
                                    }
                                }
                                
                                // 2. Title Match
                                val normTitle = title.lowercase()
                                val normQuery = query.lowercase()
                                if (normTitle == normQuery) {
                                    score += 30
                                } else if (normTitle.contains(normQuery)) {
                                    score += 10
                                }
                                
                                // Default score for first result if no year provided
                                if (year == null && score == 0) {
                                    score = 1 // Basic score for existence
                                }
                                
                                if (score > bestScore) {
                                    bestScore = score
                                    bestId = id
                                }
                            }
                            
                            return bestId
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get full metadata for a specific IMDB ID
     * Useful for getting episode lists, etc.
     * 
     * @param baseUrl Cinemeta addon URL
     * @param type Content type ("movie" or "series")
     * @param imdbId IMDB ID (e.g., "tt0903747")
     * @return JsonObject with metadata or null
     */
    fun getMeta(baseUrl: String, type: String, imdbId: String): JsonObject? {
        return try {
            val url = "${baseUrl.trimEnd('/')}/meta/$type/$imdbId.json"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Just-Player/1.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val element = json.parseToJsonElement(body)
                    
                    if (element is JsonObject) {
                        return element["meta"]?.jsonObject
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

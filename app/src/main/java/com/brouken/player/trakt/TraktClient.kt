package com.brouken.player.trakt

import android.util.Log
import com.brouken.player.utils.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Trakt API Client
 * 
 * Handles all Trakt.tv API communication:
 * - OAuth Device Code authentication
 * - Scrobbling (start/pause/stop)
 * - Sync/Playback progress
 * 
 * API Documentation: https://trakt.docs.apiary.io/
 */
object TraktClient {
    private const val TAG = "TraktClient"
    private const val BASE_URL = "https://api.trakt.tv"
    private const val API_VERSION = "2"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    // ==================== DEVICE CODE AUTHENTICATION ====================

    /**
     * Step 1: Generate device codes
     * POST /oauth/device/code
     * 
     * @return DeviceCodeResponse with user_code to display and device_code for polling
     */
    fun generateDeviceCode(clientId: String): DeviceCodeResponse? {
        DebugLogger.log(TAG, "Generating device code...")
        
        val body = """{"client_id":"$clientId"}"""
        val request = Request.Builder()
            .url("$BASE_URL/oauth/device/code")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    DebugLogger.log(TAG, "Device code response: $responseBody")
                    responseBody?.let { json.decodeFromString<DeviceCodeResponse>(it) }
                } else {
                    DebugLogger.log(TAG, "Failed to generate device code: ${response.code}")
                    null
                }
            }
        } catch (e: IOException) {
            DebugLogger.log(TAG, "Network error generating device code: ${e.message}")
            null
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error parsing device code response: ${e.message}")
            null
        }
    }

    /**
     * Step 2: Poll for access token
     * POST /oauth/device/token
     * 
     * Poll every `interval` seconds until success, expired, or denied
     * 
     * Response Codes:
     * - 200: Success - tokens returned
     * - 400: Pending - user hasn't authorized yet
     * - 404: Invalid device_code
     * - 409: Already approved
     * - 410: Code expired
     * - 418: User denied access
     * - 429: Polling too fast
     */
    fun pollForToken(
        deviceCode: String,
        clientId: String,
        clientSecret: String
    ): TokenPollResult {
        DebugLogger.log(TAG, "Polling for token...")
        
        val body = """
            {
                "code": "$deviceCode",
                "client_id": "$clientId",
                "client_secret": "$clientSecret"
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("$BASE_URL/oauth/device/token")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                DebugLogger.log(TAG, "Poll response code: ${response.code}")
                when (response.code) {
                    200 -> {
                        val responseBody = response.body?.string()
                        DebugLogger.log(TAG, "Token received!")
                        responseBody?.let {
                            TokenPollResult.Success(json.decodeFromString(it))
                        } ?: TokenPollResult.Error
                    }
                    400 -> TokenPollResult.Pending
                    404 -> TokenPollResult.InvalidCode
                    409 -> TokenPollResult.AlreadyApproved
                    410 -> TokenPollResult.Expired
                    418 -> TokenPollResult.Denied
                    429 -> TokenPollResult.SlowDown
                    else -> {
                        DebugLogger.log(TAG, "Unexpected response: ${response.code}")
                        TokenPollResult.Error
                    }
                }
            }
        } catch (e: IOException) {
            DebugLogger.log(TAG, "Network error polling for token: ${e.message}")
            TokenPollResult.Error
        }
    }

    /**
     * Refresh expired access token
     * POST /oauth/token
     * 
     * Access tokens expire after ~90 days. Use refresh_token to get new ones.
     */
    fun refreshToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String
    ): TokenResponse? {
        DebugLogger.log(TAG, "Refreshing token...")
        
        val body = """
            {
                "refresh_token": "$refreshToken",
                "client_id": "$clientId",
                "client_secret": "$clientSecret",
                "redirect_uri": "urn:ietf:wg:oauth:2.0:oob",
                "grant_type": "refresh_token"
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("$BASE_URL/oauth/token")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    DebugLogger.log(TAG, "Token refreshed!")
                    responseBody?.let { json.decodeFromString<TokenResponse>(it) }
                } else {
                    DebugLogger.log(TAG, "Failed to refresh token: ${response.code}")
                    null
                }
            }
        } catch (e: IOException) {
            DebugLogger.log(TAG, "Network error refreshing token: ${e.message}")
            null
        }
    }

    // ==================== SCROBBLING ====================

    /**
     * Start watching - call when playback begins or resumes
     * POST /scrobble/start
     */
    fun scrobbleStart(
        accessToken: String,
        clientId: String,
        mediaType: TraktMediaType,
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        progress: Float
    ): ScrobbleResponse? {
        return scrobble(
            "$BASE_URL/scrobble/start",
            accessToken, clientId, mediaType, imdbId, tmdbId, season, episode, progress
        )
    }

    /**
     * Pause watching - call when user pauses playback
     * POST /scrobble/pause
     */
    fun scrobblePause(
        accessToken: String,
        clientId: String,
        mediaType: TraktMediaType,
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        progress: Float
    ): ScrobbleResponse? {
        return scrobble(
            "$BASE_URL/scrobble/pause",
            accessToken, clientId, mediaType, imdbId, tmdbId, season, episode, progress
        )
    }

    /**
     * Stop watching - call when playback ends or user exits
     * POST /scrobble/stop
     * 
     * Behavior:
     * - Progress > 80%: Marked as WATCHED (action = "scrobble")
     * - Progress 1-79%: Saved as paused (action = "pause")  
     * - Progress < 1%: Ignored (returns 422)
     */
    fun scrobbleStop(
        accessToken: String,
        clientId: String,
        mediaType: TraktMediaType,
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        progress: Float
    ): ScrobbleResponse? {
        return scrobble(
            "$BASE_URL/scrobble/stop",
            accessToken, clientId, mediaType, imdbId, tmdbId, season, episode, progress
        )
    }

    private fun scrobble(
        url: String,
        accessToken: String,
        clientId: String,
        mediaType: TraktMediaType,
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        progress: Float
    ): ScrobbleResponse? {
        DebugLogger.log(TAG, "╔══════════════════════════════════════════════════════════════════╗")
        DebugLogger.log(TAG, "║                    TRAKT SCROBBLE REQUEST                        ║")
        DebugLogger.log(TAG, "╚══════════════════════════════════════════════════════════════════╝")
        DebugLogger.log(TAG, "  URL: $url")
        DebugLogger.log(TAG, "  Media Type: $mediaType")
        DebugLogger.log(TAG, "  IMDB ID: ${imdbId ?: "NULL"}")
        DebugLogger.log(TAG, "  TMDB ID: ${tmdbId ?: "NULL"}")
        DebugLogger.log(TAG, "  Season: ${season ?: "NULL"}")
        DebugLogger.log(TAG, "  Episode: ${episode ?: "NULL"}")
        DebugLogger.log(TAG, "  Progress: $progress%")
        DebugLogger.log(TAG, "  Access Token: ${if (accessToken.isNotBlank()) "${accessToken.take(10)}..." else "EMPTY"}")
        DebugLogger.log(TAG, "  Client ID: ${if (clientId.isNotBlank()) "${clientId.take(10)}..." else "EMPTY"}")
        
        // Skip if no valid IDs
        if (imdbId.isNullOrBlank() && tmdbId == null) {
            DebugLogger.log(TAG, "╔══════════════════════════════════════════════════════════════════╗")
            DebugLogger.log(TAG, "║  ERROR: No valid IMDB or TMDB ID - CANNOT SCROBBLE!              ║")
            DebugLogger.log(TAG, "╚══════════════════════════════════════════════════════════════════╝")
            return null
        }
        
        val body = when (mediaType) {
            TraktMediaType.MOVIE -> buildMovieScrobbleBody(imdbId, tmdbId, progress)
            TraktMediaType.EPISODE -> buildEpisodeScrobbleBody(imdbId, tmdbId, season, episode, progress)
        }
        
        DebugLogger.log(TAG, "  Request Body: $body")
        
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .header("trakt-api-version", API_VERSION)
            .header("trakt-api-key", clientId)
            .build()
        
        return try {
            DebugLogger.log(TAG, "  Sending request...")
            client.newCall(request).execute().use { response ->
                DebugLogger.log(TAG, "  Response Code: ${response.code}")
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    DebugLogger.log(TAG, "  ✓ SUCCESS! Response: $responseBody")
                    responseBody?.let { json.decodeFromString<ScrobbleResponse>(it) }
                } else {
                    val errorBody = response.body?.string()
                    DebugLogger.log(TAG, "  ✗ FAILED! Code: ${response.code}")
                    DebugLogger.log(TAG, "  Error Body: $errorBody")
                    null
                }
            }
        } catch (e: IOException) {
            DebugLogger.log(TAG, "  ✗ NETWORK ERROR: ${e.message}")
            null
        } catch (e: Exception) {
            DebugLogger.log(TAG, "  ✗ EXCEPTION: ${e.message}")
            null
        }
    }

    private fun buildMovieScrobbleBody(imdbId: String?, tmdbId: Int?, progress: Float): String {
        val ids = mutableListOf<String>()
        imdbId?.let { ids.add(""""imdb":"$it"""") }
        tmdbId?.let { ids.add(""""tmdb":$it""") }
        return """{"movie":{"ids":{${ids.joinToString(",")}}},"progress":$progress}"""
    }

    private fun buildEpisodeScrobbleBody(
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        progress: Float
    ): String {
        val ids = mutableListOf<String>()
        imdbId?.let { ids.add(""""imdb":"$it"""") }
        tmdbId?.let { ids.add(""""tmdb":$it""") }
        return """{"show":{"ids":{${ids.joinToString(",")}}},"episode":{"season":${season ?: 1},"number":${episode ?: 1}},"progress":$progress}"""
    }

    // ==================== SYNC / PLAYBACK ====================

    /**
     * Get playback progress for resume support
     * GET /sync/playback
     */
    fun getPlaybackProgress(accessToken: String, clientId: String): List<PlaybackItem>? {
        DebugLogger.log(TAG, "Getting playback progress...")
        
        val request = Request.Builder()
            .url("$BASE_URL/sync/playback")
            .get()
            .header("Authorization", "Bearer $accessToken")
            .header("trakt-api-version", API_VERSION)
            .header("trakt-api-key", clientId)
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let { json.decodeFromString<List<PlaybackItem>>(it) }
                } else {
                    DebugLogger.log(TAG, "Failed to get playback progress: ${response.code}")
                    null
                }
            }
        } catch (e: IOException) {
            DebugLogger.log(TAG, "Network error getting playback progress: ${e.message}")
            null
        }
    }

    /**
     * Remove a playback item (clear resume point)
     * DELETE /sync/playback/{id}
     * 
     * @param playbackId The `id` field from PlaybackItem, NOT the media ID
     */
    fun removePlaybackItem(accessToken: String, clientId: String, playbackId: Int): Boolean {
        DebugLogger.log(TAG, "Removing playback item $playbackId...")
        
        val request = Request.Builder()
            .url("$BASE_URL/sync/playback/$playbackId")
            .delete()
            .header("Authorization", "Bearer $accessToken")
            .header("trakt-api-version", API_VERSION)
            .header("trakt-api-key", clientId)
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                response.code == 204
            }
        } catch (e: IOException) {
            DebugLogger.log(TAG, "Network error removing playback item: ${e.message}")
            false
        }
    }

    /**
     * Save playback progress for an episode or movie
     * POST /sync/playback
     * 
     * This saves the current playback position so it shows up in the user's 
     * progress/continue watching section on Trakt.
     * 
     * Format: { "shows": [{ "ids": {...}, "seasons": [{ "number": X, "episodes": [{ "number": Y, "progress": Z }] }] }] }
     */
    suspend fun syncPlaybackProgress(
        accessToken: String,
        clientId: String,
        mediaType: TraktMediaType,
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        progress: Float
    ): Boolean {
        DebugLogger.log(TAG, "╔══════════════════════════════════════════════════════════════════╗")
        DebugLogger.log(TAG, "║                    SYNC PLAYBACK PROGRESS                        ║")
        DebugLogger.log(TAG, "╚══════════════════════════════════════════════════════════════════╝")
        DebugLogger.log(TAG, "  IMDB ID: ${imdbId ?: "NULL"}")
        DebugLogger.log(TAG, "  Season: $season, Episode: $episode")
        DebugLogger.log(TAG, "  Progress: $progress%")
        
        if (imdbId == null && tmdbId == null) {
            DebugLogger.log(TAG, "  ✗ No valid media IDs")
            return false
        }
        
        // Trakt /sync/playback expects array format
        val idsJson = if (imdbId != null) "\"imdb\":\"$imdbId\"" else "\"tmdb\":$tmdbId"
        val body = when (mediaType) {
            TraktMediaType.EPISODE -> {
                """{"shows":[{"ids":{$idsJson},"seasons":[{"number":${season ?: 1},"episodes":[{"number":${episode ?: 1},"progress":$progress}]}]}]}"""
            }
            TraktMediaType.MOVIE -> {
                """{"movies":[{"ids":{$idsJson},"progress":$progress}]}"""
            }
        }
        
        DebugLogger.log(TAG, "  Request Body: $body")
        
        val request = Request.Builder()
            .url("$BASE_URL/sync/playback")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $accessToken")
            .header("trakt-api-version", API_VERSION)
            .header("trakt-api-key", clientId)
            .header("Content-Type", "application/json")
            .build()
        
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    DebugLogger.log(TAG, "  Response Code: ${response.code}")
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        DebugLogger.log(TAG, "  ✓ Playback progress saved! Response: $responseBody")
                        true
                    } else {
                        val errorBody = response.body?.string()
                        DebugLogger.log(TAG, "  ✗ Failed! Error: $errorBody")
                        false
                    }
                }
            } catch (e: IOException) {
                DebugLogger.log(TAG, "  ✗ Network error: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Add episode or movie to watch history
     * POST /sync/history
     * 
     * This adds the item to the user's watch history with a timestamp.
     */
    suspend fun syncToHistory(
        accessToken: String,
        clientId: String,
        mediaType: TraktMediaType,
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?
    ): Boolean {
        DebugLogger.log(TAG, "╔══════════════════════════════════════════════════════════════════╗")
        DebugLogger.log(TAG, "║                    SYNC TO HISTORY                               ║")
        DebugLogger.log(TAG, "╚══════════════════════════════════════════════════════════════════╝")
        DebugLogger.log(TAG, "  IMDB ID: ${imdbId ?: "NULL"}")
        DebugLogger.log(TAG, "  Season: $season, Episode: $episode")
        
        if (imdbId == null && tmdbId == null) {
            DebugLogger.log(TAG, "  ✗ No valid media IDs")
            return false
        }
        
        val idsJson = if (imdbId != null) "\"imdb\":\"$imdbId\"" else "\"tmdb\":$tmdbId"
        val timestamp = java.time.Instant.now().toString()
        
        val body = when (mediaType) {
            TraktMediaType.EPISODE -> {
                """{"shows":[{"ids":{$idsJson},"seasons":[{"number":${season ?: 1},"episodes":[{"number":${episode ?: 1},"watched_at":"$timestamp"}]}]}]}"""
            }
            TraktMediaType.MOVIE -> {
                """{"movies":[{"ids":{$idsJson},"watched_at":"$timestamp"}]}"""
            }
        }
        
        DebugLogger.log(TAG, "  Request Body: $body")
        
        val request = Request.Builder()
            .url("$BASE_URL/sync/history")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $accessToken")
            .header("trakt-api-version", API_VERSION)
            .header("trakt-api-key", clientId)
            .header("Content-Type", "application/json")
            .build()
        
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    DebugLogger.log(TAG, "  Response Code: ${response.code}")
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        DebugLogger.log(TAG, "  ✓ Added to history! Response: $responseBody")
                        true
                    } else {
                        val errorBody = response.body?.string()
                        DebugLogger.log(TAG, "  ✗ Failed! Error: $errorBody")
                        false
                    }
                }
            } catch (e: IOException) {
                DebugLogger.log(TAG, "  ✗ Network error: ${e.message}")
                false
            }
        }
    }
}

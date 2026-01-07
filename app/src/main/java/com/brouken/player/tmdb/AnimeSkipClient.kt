package com.brouken.player.tmdb

import com.brouken.player.utils.DebugLogger
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Anime Skip GraphQL API Client
 * Primary source for anime skip timestamps with 13 toggleable segment types.
 * 
 * API: https://api.anime-skip.com/graphql
 * Docs: https://anime-skip.com/docs/api
 */
class AnimeSkipClient {

    companion object {
        private const val TAG = "AnimeSkip"
        private const val API_URL = "https://api.anime-skip.com/graphql"
        private const val CLIENT_ID = "ZGfO0sMF3eCwLYf8yMSCJjlynwNGRXWE" // Shared client ID (rate limited)
        
        // Timestamp Type UUIDs
        object TimestampTypes {
            const val BRANDING = "97e3629a-95e5-4b1a-9411-73a47c0d0e25"
            const val CANON = "9edc0037-fa4e-47a7-a29a-d9c43368daa8"
            const val CREDITS = "2a730a51-a601-439b-bc1f-7b94a640ffb9"
            const val FILLER = "c48f1dce-1890-4394-8ce6-c3f5b2f95e5e"
            const val INTROS = "14550023-2589-46f0-bfb4-152976506b4c"  // Only this submits to IntroDB
            const val MIXED_CREDITS = "6c4ade53-4fee-447f-89e4-3bb29184e87a"
            const val MIXED_INTROS = "cbb42238-d285-4c88-9e91-feab4bb8ae0a"
            const val NEW_CREDITS = "d839cdb1-21b3-455d-9c21-7ffeb37adbec"
            const val NEW_INTROS = "679fb610-ff3c-4cf4-83c0-75bcc7fe8778"
            const val PREVIEW = "c7b1eddb-defa-4bc6-a598-f143081cfe4b"
            const val RECAPS = "f38ac196-0d49-40a9-8fcf-f3ef2f40f127"
            const val TRANSITIONS = "9f0c6532-ccae-4238-83ec-a2804fe5f7b0"
            const val TITLE_CARD = "67321535-a4ea-4f21-8bed-fb3c8286b510"
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // Auth state
    private var authToken: String? = null
    private var refreshToken: String? = null
    
    data class LoginResult(
        val success: Boolean,
        val authToken: String? = null,
        val refreshToken: String? = null,
        val username: String? = null,
        val email: String? = null,
        val error: String? = null
    )
    
    data class Timestamp(
        val at: Double,           // Seconds from start
        val typeId: String,       // UUID of timestamp type
        val typeName: String? = null
    )
    
    data class EpisodeResult(
        val showId: String?,
        val showName: String?,
        val episodeId: String?,
        val episodeName: String?,
        val timestamps: List<Timestamp>
    )
    
    /**
     * Login to Anime Skip
     * @param usernameOrEmail Username or email
     * @param password Plain text password (will be MD5 hashed)
     */
    fun login(usernameOrEmail: String, password: String): LoginResult {
        val passwordHash = md5(password)
        
        val query = """
            query {
                login(usernameEmail: "$usernameOrEmail", passwordHash: "$passwordHash") {
                    authToken
                    refreshToken
                    account {
                        username
                        email
                    }
                }
            }
        """.trimIndent()
        
        DebugLogger.log(TAG, "Login attempt for: $usernameOrEmail")
        
        return try {
            val response = executeQuery(query, requireAuth = false)
            
            if (response != null) {
                val loginData = response["login"] as? JsonObject
                if (loginData != null) {
                    authToken = loginData["authToken"]?.jsonPrimitive?.contentOrNull
                    refreshToken = loginData["refreshToken"]?.jsonPrimitive?.contentOrNull
                    val account = loginData["account"] as? JsonObject
                    
                    DebugLogger.log(TAG, "Login SUCCESS for ${account?.get("username")?.jsonPrimitive?.contentOrNull}")
                    
                    LoginResult(
                        success = true,
                        authToken = authToken,
                        refreshToken = refreshToken,
                        username = account?.get("username")?.jsonPrimitive?.contentOrNull,
                        email = account?.get("email")?.jsonPrimitive?.contentOrNull
                    )
                } else {
                    DebugLogger.log(TAG, "Login FAILED: No login data in response")
                    LoginResult(success = false, error = "Invalid response")
                }
            } else {
                DebugLogger.log(TAG, "Login FAILED: Null response")
                LoginResult(success = false, error = "No response from server")
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Login EXCEPTION: ${e.message}")
            LoginResult(success = false, error = e.message)
        }
    }
    
    /**
     * Refresh auth token using refresh token
     */
    fun refreshAuth(): Boolean {
        if (refreshToken == null) {
            DebugLogger.log(TAG, "Cannot refresh: No refresh token")
            return false
        }
        
        val query = """
            query {
                loginRefresh(refreshToken: "$refreshToken") {
                    authToken
                    refreshToken
                    account {
                        username
                    }
                }
            }
        """.trimIndent()
        
        return try {
            val response = executeQuery(query, requireAuth = false)
            val refreshData = response?.get("loginRefresh") as? JsonObject
            
            if (refreshData != null) {
                authToken = refreshData["authToken"]?.jsonPrimitive?.contentOrNull
                refreshToken = refreshData["refreshToken"]?.jsonPrimitive?.contentOrNull
                DebugLogger.log(TAG, "Token refresh SUCCESS")
                true
            } else {
                DebugLogger.log(TAG, "Token refresh FAILED")
                false
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Token refresh EXCEPTION: ${e.message}")
            false
        }
    }
    
    /**
     * Search for shows by name
     */
    fun searchShows(query: String, limit: Int = 10): List<Pair<String, String>> {
        val gql = """
            query {
                searchShows(search: "${query.replace("\"", "\\\"")}", limit: $limit) {
                    id
                    name
                    originalName
                }
            }
        """.trimIndent()
        
        DebugLogger.log(TAG, "Searching shows: '$query'")
        
        return try {
            val response = executeQuery(gql, requireAuth = false)
            val shows = response?.get("searchShows") as? JsonArray ?: return emptyList()
            
            shows.mapNotNull { show ->
                val obj = show as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull 
                    ?: obj["originalName"]?.jsonPrimitive?.contentOrNull 
                    ?: return@mapNotNull null
                id to name
            }.also {
                DebugLogger.log(TAG, "Found ${it.size} shows")
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Search EXCEPTION: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Find episode and get timestamps
     */
    fun findEpisode(showId: String, episodeName: String): EpisodeResult? {
        val gql = """
            query {
                findEpisodeByName(showId: "$showId", name: "${episodeName.replace("\"", "\\\"")}") {
                    id
                    name
                    show {
                        id
                        name
                    }
                    timestamps {
                        at
                        typeId
                        type {
                            name
                        }
                    }
                }
            }
        """.trimIndent()
        
        DebugLogger.log(TAG, "Finding episode: showId=$showId, name='$episodeName'")
        
        return try {
            val response = executeQuery(gql, requireAuth = false)
            val episode = response?.get("findEpisodeByName") as? JsonObject
            
            if (episode != null) {
                val show = episode["show"] as? JsonObject
                val timestampsArray = episode["timestamps"] as? JsonArray ?: JsonArray(emptyList())
                
                val timestamps = timestampsArray.mapNotNull { ts ->
                    val obj = ts as? JsonObject ?: return@mapNotNull null
                    val at = obj["at"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val typeId = obj["typeId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val typeName = (obj["type"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                    Timestamp(at, typeId, typeName)
                }
                
                DebugLogger.log(TAG, "Found episode with ${timestamps.size} timestamps")
                timestamps.forEach { 
                    DebugLogger.log(TAG, "  - ${it.at}s: ${it.typeName ?: it.typeId}")
                }
                
                EpisodeResult(
                    showId = show?.get("id")?.jsonPrimitive?.contentOrNull,
                    showName = show?.get("name")?.jsonPrimitive?.contentOrNull,
                    episodeId = episode["id"]?.jsonPrimitive?.contentOrNull,
                    episodeName = episode["name"]?.jsonPrimitive?.contentOrNull,
                    timestamps = timestamps
                )
            } else {
                DebugLogger.log(TAG, "Episode not found")
                null
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Find episode EXCEPTION: ${e.message}")
            null
        }
    }
    
    /**
     * Get timestamps for a show and episode
     * This is the main entry point - searches for show then gets episode timestamps
     */
    fun getTimestamps(showName: String, episodeName: String): EpisodeResult? {
        DebugLogger.log(TAG, "╔═══════════════════════════════════════════════════════════════╗")
        DebugLogger.log(TAG, "║  ANIME SKIP TIMESTAMP FETCH                                   ║")
        DebugLogger.log(TAG, "╚═══════════════════════════════════════════════════════════════╝")
        DebugLogger.log(TAG, "  Show: '$showName'")
        DebugLogger.log(TAG, "  Episode: '$episodeName'")
        
        // Search for show
        val shows = searchShows(showName, limit = 5)
        if (shows.isEmpty()) {
            DebugLogger.log(TAG, "  ✗ No shows found")
            return null
        }
        
        // Try first match
        val (showId, matchedName) = shows.first()
        DebugLogger.log(TAG, "  ✓ Matched show: '$matchedName' (ID: $showId)")
        
        // Get episode timestamps
        return findEpisode(showId, episodeName)
    }
    
    /**
     * Filter timestamps by user preferences
     */
    fun filterByPreferences(
        timestamps: List<Timestamp>,
        skipBranding: Boolean = true,
        skipRecaps: Boolean = true,
        skipTitleCard: Boolean = true,
        skipIntros: Boolean = true,
        skipNewIntros: Boolean = false,
        skipMixedIntros: Boolean = false,
        skipCanon: Boolean = false,
        skipFiller: Boolean = true,
        skipTransitions: Boolean = true,
        skipCredits: Boolean = true,
        skipNewCredits: Boolean = false,
        skipMixedCredits: Boolean = false,
        skipPreview: Boolean = true
    ): List<Timestamp> {
        return timestamps.filter { ts ->
            when (ts.typeId) {
                TimestampTypes.BRANDING -> skipBranding
                TimestampTypes.RECAPS -> skipRecaps
                TimestampTypes.TITLE_CARD -> skipTitleCard
                TimestampTypes.INTROS -> skipIntros
                TimestampTypes.NEW_INTROS -> skipNewIntros
                TimestampTypes.MIXED_INTROS -> skipMixedIntros
                TimestampTypes.CANON -> skipCanon
                TimestampTypes.FILLER -> skipFiller
                TimestampTypes.TRANSITIONS -> skipTransitions
                TimestampTypes.CREDITS -> skipCredits
                TimestampTypes.NEW_CREDITS -> skipNewCredits
                TimestampTypes.MIXED_CREDITS -> skipMixedCredits
                TimestampTypes.PREVIEW -> skipPreview
                else -> false
            }
        }
    }
    
    /**
     * Check if a timestamp type is an Intro (for IntroDB auto-submit)
     */
    fun isIntroType(typeId: String): Boolean {
        return typeId == TimestampTypes.INTROS || 
               typeId == TimestampTypes.NEW_INTROS || 
               typeId == TimestampTypes.MIXED_INTROS
    }
    
    // === Private Helpers ===
    
    private fun executeQuery(query: String, requireAuth: Boolean = false): JsonObject? {
        val requestBody = JsonObject(mapOf(
            "query" to JsonPrimitive(query)
        )).toString()
        
        val requestBuilder = Request.Builder()
            .url(API_URL)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("X-Client-ID", CLIENT_ID)
        
        if (requireAuth && authToken != null) {
            requestBuilder.header("Authorization", "Bearer $authToken")
        }
        
        val request = requestBuilder.build()
        
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            
            if (body != null) {
                val jsonResponse = json.parseToJsonElement(body) as? JsonObject
                
                // Check for errors
                val errors = jsonResponse?.get("errors") as? JsonArray
                if (errors != null && errors.isNotEmpty()) {
                    val errorMsg = (errors[0] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                    DebugLogger.log(TAG, "GraphQL Error: $errorMsg")
                    
                    // Check for auth error
                    if (errorMsg == "Invalid Token" && requireAuth) {
                        if (refreshAuth()) {
                            return executeQuery(query, requireAuth) // Retry with new token
                        }
                    }
                    return null
                }
                
                return jsonResponse?.get("data") as? JsonObject
            }
            return null
        }
    }
    
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    // Token management for persistence
    fun setTokens(auth: String?, refresh: String?) {
        authToken = auth
        refreshToken = refresh
    }
    
    fun getAuthToken(): String? = authToken
    fun getRefreshToken(): String? = refreshToken
    fun isLoggedIn(): Boolean = authToken != null
    
    fun logout() {
        authToken = null
        refreshToken = null
    }
}

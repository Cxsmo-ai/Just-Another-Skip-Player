package com.brouken.player.stremio

import android.content.Context
import android.net.Uri
import com.brouken.player.utils.DebugLogger
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for communicating with Stremio subtitle addons
 */
class StremioAddonClient(private val context: Context) {
    
    companion object {
        private const val TAG = "StremioAddonClient"
        private const val CONNECT_TIMEOUT = 10L
        private const val READ_TIMEOUT = 15L
        
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "JASP/1.0 (Android; Stremio Subtitle Client)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()
    
    /**
     * Fetch and parse addon manifest
     */
    suspend fun fetchManifest(manifestUrl: String): Result<StremioManifest> = withContext(Dispatchers.IO) {
        try {
            DebugLogger.log(TAG, "Fetching manifest: $manifestUrl")
            
            val request = Request.Builder()
                .url(manifestUrl)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(
                IOException("Empty response body")
            )
            
            val manifest = json.decodeFromString<StremioManifest>(body)
            
            // Validate that addon supports subtitles
            if (!manifest.resources.contains("subtitles")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Addon does not support subtitles resource")
                )
            }
            
            DebugLogger.log(TAG, "Manifest parsed: ${manifest.name} v${manifest.version}")
            Result.success(manifest)
            
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to fetch manifest: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Query an addon for subtitles
     */
    suspend fun querySubtitles(
        addon: SubtitleAddon,
        params: SubtitleQueryParams
    ): Result<List<StremioSubtitle>> = withContext(Dispatchers.IO) {
        try {
            // Build the API URL
            // Format: {baseUrl}/subtitles/{type}/{id}/{extraArgs}.json
            val url = buildSubtitleUrl(addon.baseUrl, params)
            
            DebugLogger.log(TAG, "Querying subtitles from ${addon.displayName}: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "Query failed: HTTP ${response.code}")
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(
                IOException("Empty response body")
            )
            
            val subtitleResponse = json.decodeFromString<StremioSubtitleResponse>(body)
            
            DebugLogger.log(TAG, "Found ${subtitleResponse.subtitles.size} subtitles from ${addon.displayName}")
            Result.success(subtitleResponse.subtitles)
            
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Query failed for ${addon.displayName}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Query multiple addons in parallel
     */
    suspend fun queryAllAddons(
        addons: List<SubtitleAddon>,
        params: SubtitleQueryParams
    ): List<SubtitleTrack> = coroutineScope {
        val results = mutableListOf<SubtitleTrack>()
        
        // Query all enabled addons in parallel
        val deferreds = addons
            .filter { it.isEnabled }
            .sortedBy { it.priority }
            .map { addon ->
                async {
                    querySubtitles(addon, params).getOrNull()?.map { stremioSub ->
                        convertToSubtitleTrack(stremioSub, addon)
                    } ?: emptyList()
                }
            }
        
        // Collect all results
        deferreds.forEach { deferred ->
            results.addAll(deferred.await())
        }
        
        // Deduplicate by URL
        val deduplicated = results
            .distinctBy { it.url }
            .sortedByDescending { it.matchScore }
        
        DebugLogger.log(TAG, "Total subtitles from all addons: ${deduplicated.size}")
        deduplicated
    }
    
    /**
     * Test connection to an addon
     */
    suspend fun testConnection(manifestUrl: String): Result<StremioManifest> {
        return fetchManifest(manifestUrl)
    }
    
    /**
     * Build the subtitle query URL
     */
    private fun buildSubtitleUrl(baseUrl: String, params: SubtitleQueryParams): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val idPath = params.getIdPath()
        val extraArgs = params.toExtraArgs()
        
        return if (extraArgs.isNotEmpty()) {
            "$cleanBaseUrl/subtitles/${params.type}/$idPath/$extraArgs.json"
        } else {
            "$cleanBaseUrl/subtitles/${params.type}/$idPath.json"
        }
    }
    
    /**
     * Convert Stremio API response to our SubtitleTrack model
     */
    private fun convertToSubtitleTrack(
        stremioSub: StremioSubtitle,
        addon: SubtitleAddon
    ): SubtitleTrack {
        // Parse language code
        val langCode = stremioSub.lang.take(3).lowercase()
        val langName = getLanguageName(langCode)
        
        // Detect SDH/HI from various indicators
        val isSDH = stremioSub.SubHearingImpaired == true ||
                    stremioSub.id.contains("sdh", ignoreCase = true) ||
                    stremioSub.MovieReleaseName?.contains("SDH", ignoreCase = true) == true
        
        // Calculate match score
        val score = calculateMatchScore(stremioSub)
        
        // Detect mime type from URL
        val mimeType = getMimeTypeFromUrl(stremioSub.url)
        
        return SubtitleTrack(
            id = stremioSub.id,
            language = langName,
            languageCode = langCode,
            source = SubtitleSource.ONLINE,
            addonName = addon.displayName,
            addonId = addon.id,
            url = stremioSub.url,
            mimeType = mimeType,
            matchScore = score,
            isHashMatch = stremioSub.Score != null && stremioSub.Score > 0.9f,
            isSDH = isSDH,
            isHI = stremioSub.SubHearingImpaired == true,
            downloadCount = stremioSub.SubDownloadsCnt,
            rating = stremioSub.SubRating,
            encoding = stremioSub.SubEncoding ?: "UTF-8"
        )
    }
    
    /**
     * Calculate a match confidence score
     */
    private fun calculateMatchScore(sub: StremioSubtitle): Int {
        var score = 50 // Base score
        
        // Hash match is highest confidence
        if (sub.Score != null && sub.Score > 0.9f) {
            score = 99
        } else if (sub.Score != null) {
            score = (sub.Score * 100).toInt().coerceIn(0, 100)
        }
        
        // Boost for high download count
        sub.SubDownloadsCnt?.let { downloads ->
            when {
                downloads > 100000 -> score = minOf(score + 10, 99)
                downloads > 10000 -> score = minOf(score + 5, 99)
                downloads > 1000 -> score = minOf(score + 2, 99)
            }
        }
        
        // Boost for high rating
        sub.SubRating?.let { rating ->
            if (rating > 8.0f) score = minOf(score + 5, 99)
        }
        
        return score
    }
    
    /**
     * Get human-readable language name from ISO code
     */
    private fun getLanguageName(code: String): String {
        return when (code) {
            "eng", "en" -> "English"
            "spa", "es" -> "Spanish"
            "fra", "fr" -> "French"
            "deu", "de" -> "German"
            "ita", "it" -> "Italian"
            "por", "pt" -> "Portuguese"
            "rus", "ru" -> "Russian"
            "jpn", "ja" -> "Japanese"
            "kor", "ko" -> "Korean"
            "zho", "zh" -> "Chinese"
            "ara", "ar" -> "Arabic"
            "hin", "hi" -> "Hindi"
            "tur", "tr" -> "Turkish"
            "pol", "pl" -> "Polish"
            "nld", "nl" -> "Dutch"
            "swe", "sv" -> "Swedish"
            "nor", "no" -> "Norwegian"
            "dan", "da" -> "Danish"
            "fin", "fi" -> "Finnish"
            "heb", "he" -> "Hebrew"
            "tha", "th" -> "Thai"
            "vie", "vi" -> "Vietnamese"
            "ind", "id" -> "Indonesian"
            "msa", "ms" -> "Malay"
            "hun", "hu" -> "Hungarian"
            "ces", "cs" -> "Czech"
            "ron", "ro" -> "Romanian"
            "ell", "el" -> "Greek"
            "bul", "bg" -> "Bulgarian"
            "ukr", "uk" -> "Ukrainian"
            "hrv", "hr" -> "Croatian"
            "srp", "sr" -> "Serbian"
            "slv", "sl" -> "Slovenian"
            "cat", "ca" -> "Catalan"
            "eus", "eu" -> "Basque"
            else -> code.uppercase()
        }
    }
    
    /**
     * Detect MIME type from URL extension
     */
    private fun getMimeTypeFromUrl(url: String): String {
        val extension = Uri.parse(url).lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase() ?: ""
        
        return when (extension) {
            "srt" -> "application/x-subrip"
            "vtt", "webvtt" -> "text/vtt"
            "ass" -> "text/x-ssa"
            "ssa" -> "text/x-ssa"
            "ttml", "xml" -> "application/ttml+xml"
            "dfxp" -> "application/ttml+xml"
            "smi", "sami" -> "application/x-sami"
            "sub" -> "text/x-microdvd"
            else -> "application/x-subrip" // Default to SRT
        }
    }
}

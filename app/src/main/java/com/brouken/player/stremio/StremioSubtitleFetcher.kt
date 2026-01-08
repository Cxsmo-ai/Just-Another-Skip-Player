package com.brouken.player.stremio

import android.content.Context
import android.net.Uri
import com.brouken.player.utils.DebugLogger
import kotlinx.coroutines.*

/**
 * Main coordinator for fetching subtitles from Stremio addons
 * Handles the complete flow from video info to subtitle results
 */
class StremioSubtitleFetcher(private val context: Context) {
    
    companion object {
        private const val TAG = "StremioSubtitleFetcher"
    }
    
    private val addonManager = SubtitleAddonManager(context)
    private val client = StremioAddonClient(context)
    
    // Callback for progress updates
    var onProgressUpdate: ((String) -> Unit)? = null
    
    // Callback for results
    var onSubtitlesFound: ((List<SubtitleTrack>) -> Unit)? = null
    
    // Callback for errors
    var onError: ((String) -> Unit)? = null
    
    /**
     * Fetch subtitles for a video
     */
    suspend fun fetchSubtitles(
        videoUri: Uri,
        title: String?,
        imdbId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        
        val enabledAddons = addonManager.getEnabledAddons()
        
        if (enabledAddons.isEmpty()) {
            DebugLogger.log(TAG, "No subtitle addons configured")
            onError?.invoke("No subtitle addons configured. Add addons in settings.")
            return@withContext emptyList()
        }
        
        onProgressUpdate?.invoke("Preparing to fetch subtitles...")
        
        // Determine media type
        val type = if (seasonNumber != null && episodeNumber != null) "series" else "movie"
        
        // Try to extract IMDB ID from title if not provided
        val extractedImdbId = imdbId ?: extractImdbIdFromTitle(title)
        
        // Try to compute video hash for local files
        var videoHash: String? = null
        var videoSize: Long? = null
        
        if (!OpenSubtitlesHasher.isRemoteStream(videoUri)) {
            onProgressUpdate?.invoke("Computing video hash...")
            
            val hashResult = if (videoUri.scheme == "file") {
                val path = videoUri.path
                if (path != null) {
                    val file = java.io.File(path)
                    videoSize = file.length()
                    OpenSubtitlesHasher.computeHash(file)
                } else null
            } else {
                OpenSubtitlesHasher.computeHashFromUri(videoUri, context.contentResolver)
            }
            
            videoHash = hashResult?.getOrNull()
            
            if (videoHash != null) {
                DebugLogger.log(TAG, "Video hash: $videoHash")
            }
        } else {
            DebugLogger.log(TAG, "Remote stream - skipping hash calculation")
        }
        
        // Extract filename
        val filename = extractFilename(videoUri, title)
        
        // Build query params
        val params = SubtitleQueryParams(
            type = type,
            imdbId = extractedImdbId,
            videoHash = videoHash,
            videoSize = videoSize,
            filename = filename,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
        
        DebugLogger.log(TAG, "Query params: type=$type, imdb=$extractedImdbId, hash=$videoHash")
        
        // Check if we have enough info to query
        if (extractedImdbId == null && videoHash == null) {
            DebugLogger.log(TAG, "Insufficient info for subtitle search")
            onError?.invoke("Could not identify video. Try naming file with IMDB ID (e.g., tt1234567)")
            return@withContext emptyList()
        }
        
        // Query all addons in parallel
        onProgressUpdate?.invoke("Searching ${enabledAddons.size} addon(s)...")
        
        val results = mutableListOf<SubtitleTrack>()
        val errors = mutableListOf<String>()
        
        coroutineScope {
            val deferreds = enabledAddons.map { addon ->
                async {
                    onProgressUpdate?.invoke("Querying ${addon.displayName}...")
                    
                    val result = client.querySubtitles(addon, params)
                    
                    result.fold(
                        onSuccess = { subs ->
                            val tracks = subs.map { convertToTrack(it, addon) }
                            addonManager.recordUsage(addon.id)
                            tracks
                        },
                        onFailure = { error ->
                            addonManager.recordError(addon.id, error.message ?: "Unknown error")
                            errors.add("${addon.displayName}: ${error.message}")
                            emptyList()
                        }
                    )
                }
            }
            
            deferreds.forEach { deferred ->
                results.addAll(deferred.await())
            }
        }
        
        // Deduplicate and sort
        val deduplicated = results
            .distinctBy { it.url }
            .sortedWith(compareByDescending<SubtitleTrack> { it.isHashMatch }
                .thenByDescending { it.matchScore }
                .thenByDescending { it.downloadCount ?: 0 })
        
        DebugLogger.log(TAG, "Found ${deduplicated.size} unique subtitles")
        
        if (deduplicated.isEmpty() && errors.isNotEmpty()) {
            onError?.invoke("Search failed: ${errors.first()}")
        } else if (deduplicated.isEmpty()) {
            onError?.invoke("No subtitles found for this video")
        } else {
            onProgressUpdate?.invoke("Found ${deduplicated.size} subtitles!")
            onSubtitlesFound?.invoke(deduplicated)
        }
        
        deduplicated
    }
    
    /**
     * Try to extract IMDB ID from title/filename
     * Patterns: tt1234567, imdb-tt1234567, [tt1234567]
     */
    private fun extractImdbIdFromTitle(title: String?): String? {
        if (title == null) return null
        
        // Pattern: tt followed by 7-8 digits
        val regex = Regex("""tt\d{7,8}""", RegexOption.IGNORE_CASE)
        val match = regex.find(title)
        
        return match?.value?.lowercase()
    }
    
    /**
     * Extract filename from URI or title
     */
    private fun extractFilename(uri: Uri, title: String?): String {
        // Try to get from URI
        uri.lastPathSegment?.let { segment ->
            if (segment.contains('.')) {
                return segment
            }
        }
        
        // Fall back to title
        return title ?: "unknown"
    }
    
    /**
     * Convert Stremio response to SubtitleTrack
     */
    private fun convertToTrack(
        sub: StremioSubtitle,
        addon: SubtitleAddon
    ): SubtitleTrack {
        val langCode = sub.lang.take(3).lowercase()
        
        return SubtitleTrack(
            id = sub.id,
            language = getLanguageName(langCode),
            languageCode = langCode,
            source = SubtitleSource.ONLINE,
            addonName = addon.displayName,
            addonId = addon.id,
            url = sub.url,
            mimeType = getMimeTypeFromUrl(sub.url),
            matchScore = calculateScore(sub),
            isHashMatch = sub.Score != null && sub.Score > 0.9f,
            isSDH = sub.SubHearingImpaired == true,
            isHI = sub.SubHearingImpaired == true,
            downloadCount = sub.SubDownloadsCnt,
            rating = sub.SubRating,
            encoding = sub.SubEncoding ?: "UTF-8"
        )
    }
    
    private fun calculateScore(sub: StremioSubtitle): Int {
        var score = 50
        
        sub.Score?.let { 
            if (it > 0.9f) score = 99
            else score = (it * 100).toInt().coerceIn(0, 100)
        }
        
        sub.SubDownloadsCnt?.let { downloads ->
            when {
                downloads > 100000 -> score = minOf(score + 10, 99)
                downloads > 10000 -> score = minOf(score + 5, 99)
            }
        }
        
        return score
    }
    
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
            else -> code.uppercase()
        }
    }
    
    private fun getMimeTypeFromUrl(url: String): String {
        val ext = url.substringAfterLast('.', "").lowercase().take(5)
        return when {
            ext.startsWith("srt") -> "application/x-subrip"
            ext.startsWith("vtt") -> "text/vtt"
            ext.startsWith("ass") -> "text/x-ssa"
            ext.startsWith("ssa") -> "text/x-ssa"
            else -> "application/x-subrip"
        }
    }
}

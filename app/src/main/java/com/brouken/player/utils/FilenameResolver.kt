package com.brouken.player.utils

import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Utility object for resolving actual filenames from streaming URLs.
 * 
 * Handles:
 * - AIOStreams: Extract filename from apiTitle
 * - Syncler: Check video_list.filename intent extra
 * - TorBox/CDN: Fallback to Content-Disposition header
 * 
 * No API keys required!
 */
object FilenameResolver {
    private const val TAG = "FilenameResolver"
    
    // Cache resolved filenames to avoid repeated lookups
    private val filenameCache = ConcurrentHashMap<String, String>()
    private val pendingRequests = mutableSetOf<String>()
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // HTTP client for Content-Disposition header requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    // Pattern to detect video file extensions in text
    private val FILENAME_PATTERN = Pattern.compile(
        "([\\w\\.\\-\\[\\]\\(\\)\\s]+\\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|ts))",
        Pattern.CASE_INSENSITIVE
    )
    
    // Pattern for UUID/hash detection (these are NOT filenames)
    private val UUID_PATTERN = Pattern.compile(
        "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$",
        Pattern.CASE_INSENSITIVE
    )
    private val HASH_PATTERN = Pattern.compile(
        "^[a-f0-9]{32,64}$",
        Pattern.CASE_INSENSITIVE
    )
    
    // CDN domains known to use UUIDs in paths
    private val UUID_CDN_DOMAINS = listOf(
        "tb-cdn.io",      // TorBox
        "torbox.app",     // TorBox direct
        "wnam.tb-cdn.io", // TorBox regional
        "enam.tb-cdn.io",
        "weur.tb-cdn.io"
    )
    
    /**
     * Java-compatible callback interface for filename resolution.
     */
    fun interface FilenameCallback {
        fun onFilename(filename: String?)
    }
    
    /**
     * Main entry point: Resolve filename from all available sources.
     * 
     * Priority:
     * 1. Syncler intent extra (video_list.filename)
     * 2. Extract from apiTitle (AIOStreams format)
     * 3. HTTP Content-Disposition header (async)
     * 4. Fallback to apiTitle as-is
     * 
     * @param intent The launch intent (may contain Syncler extras)
     * @param apiTitle The title passed by Stremio/Syncler
     * @param mediaUri The stream URL
     * @param callback Receives the resolved filename
     */
    @JvmStatic
    fun resolveFilename(
        intent: Intent?,
        apiTitle: String?,
        mediaUri: Uri?,
        callback: FilenameCallback
    ) {
        // Priority 1: Check Syncler's video_list.filename intent extra
        intent?.extras?.let { bundle ->
            val synclerFilename = bundle.getString("video_list.filename")
                ?: bundle.getString("filename")
            if (!synclerFilename.isNullOrBlank()) {
                Log.d(TAG, "Found Syncler filename extra: $synclerFilename")
                callback.onFilename(cleanFilename(synclerFilename))
                return
            }
        }
        
        // Priority 2: Extract filename from apiTitle (AIOStreams format)
        apiTitle?.let {
            val extracted = extractFilenameFromTitle(it)
            if (extracted != null) {
                Log.d(TAG, "Extracted filename from apiTitle: $extracted")
                callback.onFilename(cleanFilename(extracted))
                return
            }
        }
        
        // Priority 3: Check if URL path contains a UUID (needs Content-Disposition)
        mediaUri?.let { uri ->
            val urlString = uri.toString()
            
            // Check cache first
            filenameCache[urlString]?.let {
                Log.d(TAG, "Using cached filename: $it")
                callback.onFilename(cleanFilename(it))
                return
            }
            
            val pathSegment = uri.lastPathSegment ?: ""
            if (isUuidOrHash(pathSegment) || isKnownUuidCdn(uri)) {
                Log.d(TAG, "UUID detected in path, fetching Content-Disposition")
                fetchFilenameAsync(urlString) { filename ->
                    if (filename != null) {
                        callback.onFilename(cleanFilename(filename))
                    } else {
                        // Fallback to apiTitle
                        callback.onFilename(apiTitle?.let { cleanFilename(it) })
                    }
                }
                return
            }
            
            // Path looks like a normal filename
            if (pathSegment.contains(".") && !isUuidOrHash(pathSegment)) {
                Log.d(TAG, "Using path segment as filename: $pathSegment")
                callback.onFilename(cleanFilename(pathSegment))
                return
            }
        }
        
        // Priority 4: Fallback to apiTitle
        callback.onFilename(apiTitle?.let { cleanFilename(it) })
    }
    
    /**
     * Extract filename pattern from AIOStreams-style title.
     * Example: "⚡ TorBox | 1080p WEB-DL | The.Big.Bang.Theory.S01E01.mkv"
     */
    fun extractFilenameFromTitle(title: String): String? {
        val matcher = FILENAME_PATTERN.matcher(title)
        if (matcher.find()) {
            return matcher.group(1)
        }
        
        // Try splitting by | and taking last segment
        if (title.contains("|")) {
            val parts = title.split("|").map { it.trim() }
            val lastPart = parts.lastOrNull() ?: return null
            
            // Check if last part looks like a filename
            val lastMatcher = FILENAME_PATTERN.matcher(lastPart)
            if (lastMatcher.find()) {
                return lastMatcher.group(1)
            }
        }
        
        return null
    }
    
    /**
     * Check if a path segment looks like a UUID or hash (not a filename).
     */
    fun isUuidOrHash(segment: String): Boolean {
        return UUID_PATTERN.matcher(segment).matches() || 
               HASH_PATTERN.matcher(segment).matches()
    }
    
    /**
     * Check if URL is from a known CDN that uses UUIDs.
     */
    private fun isKnownUuidCdn(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return UUID_CDN_DOMAINS.any { host.contains(it, ignoreCase = true) }
    }
    
    /**
     * Fetch filename from Content-Disposition header asynchronously.
     */
    fun fetchFilenameAsync(url: String, callback: (String?) -> Unit) {
        // Check cache first
        filenameCache[url]?.let {
            callback(it)
            return
        }
        
        // Avoid duplicate requests
        synchronized(pendingRequests) {
            if (url in pendingRequests) {
                Log.d(TAG, "Request already pending for: $url")
                return
            }
            pendingRequests.add(url)
        }
        
        scope.launch {
            try {
                val filename = fetchContentDisposition(url)
                filename?.let { filenameCache[url] = it }
                
                withContext(Dispatchers.Main) {
                    callback(filename)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Content-Disposition", e)
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            } finally {
                synchronized(pendingRequests) {
                    pendingRequests.remove(url)
                }
            }
        }
    }
    
    /**
     * Make HTTP HEAD request to get Content-Disposition header.
     */
    private suspend fun fetchContentDisposition(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    val header = response.header("Content-Disposition")
                    Log.d(TAG, "Content-Disposition header: $header")
                    parseContentDisposition(header)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch Content-Disposition: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Parse filename from Content-Disposition header.
     * Handles formats:
     * - attachment; filename="movie.mkv"
     * - attachment; filename*=UTF-8''movie.mkv
     * - inline; filename=movie.mkv
     */
    fun parseContentDisposition(header: String?): String? {
        if (header == null) return null
        
        // Try filename*= (RFC 5987 extended notation)
        val extPattern = Pattern.compile(
            "filename\\*=(?:UTF-8'')?([^;\\r\\n\"']+)",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = extPattern.matcher(header)
        if (matcher.find()) {
            return try {
                URLDecoder.decode(matcher.group(1), "UTF-8")
            } catch (e: Exception) {
                matcher.group(1)
            }
        }
        
        // Try filename= (standard)
        val stdPattern = Pattern.compile(
            "filename=[\"']?([^\"';\\r\\n]+)[\"']?",
            Pattern.CASE_INSENSITIVE
        )
        matcher = stdPattern.matcher(header)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        
        return null
    }
    
    /**
     * Clean up filename for display:
     * - Remove extension
     * - Replace dots/underscores with spaces
     * - Trim whitespace
     */
    fun cleanFilename(filename: String): String {
        var result = filename.trim()
        
        // Remove file extension
        val dotIndex = result.lastIndexOf('.')
        if (dotIndex > 0) {
            val ext = result.substring(dotIndex + 1).lowercase()
            if (ext in listOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts")) {
                result = result.substring(0, dotIndex)
            }
        }
        
        // Replace dots and underscores with spaces
        result = result.replace(".", " ").replace("_", " ")
        
        // Clean up multiple spaces
        result = result.replace(Regex("\\s+"), " ").trim()
        
        return result
    }
    
    /**
     * Clear the filename cache.
     */
    fun clearCache() {
        filenameCache.clear()
    }
}

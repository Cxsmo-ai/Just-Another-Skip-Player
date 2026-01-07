package com.brouken.player.tmdb

import android.content.Context
import com.brouken.player.utils.DebugLogger

/**
 * Memory-only cache for jump scare timestamps (current session only)
 * 
 * Data is fetched once when movie opens and cleared when movie closes.
 * No disk persistence - fresh fetch each time a movie is played.
 */
class JumpScareCache(private val context: Context) {
    
    companion object {
        private const val TAG = "JumpScareCache"
    }
    
    // In-memory cache only - cleared when app closes or reset() called
    private val memoryCache: MutableMap<String, List<Long>> = mutableMapOf()
    
    /**
     * Get cached jump scare timestamps for a media item
     * 
     * @param key IMDB ID or "title|year" fallback
     * @return List of timestamps in MS, or null if not cached
     */
    fun get(key: String): List<Long>? {
        val result = memoryCache[key]
        if (result != null) {
            DebugLogger.log(TAG, "[CACHE HIT] Found data for '$key' (${result.size} items)")
        } else {
            DebugLogger.log(TAG, "[CACHE MISS] No data for '$key'")
        }
        return result
    }
    
    /**
     * Store jump scare timestamps for a media item (memory only)
     * 
     * @param key IMDB ID or "title|year" fallback
     * @param scares List of timestamps in milliseconds
     */
    fun put(key: String, scares: List<Long>) {
        DebugLogger.log(TAG, "[CACHE PUT] Storing ${scares.size} items for '$key'")
        memoryCache[key] = scares
    }
    
    /**
     * Check if we have cached data for a key
     */
    fun has(key: String): Boolean {
        val exists = memoryCache.containsKey(key)
        DebugLogger.log(TAG, "has('$key') -> $exists")
        return exists
    }
    
    /**
     * Clear all cached data (called when movie closes)
     */
    fun clear() {
        DebugLogger.log(TAG, "Clearing cache (Memory: ${memoryCache.size} entries)")
        memoryCache.clear()
    }
}

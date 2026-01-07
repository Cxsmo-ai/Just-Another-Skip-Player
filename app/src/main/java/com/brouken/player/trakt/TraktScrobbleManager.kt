package com.brouken.player.trakt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.brouken.player.utils.DebugLogger
import kotlinx.coroutines.*

/**
 * Trakt Scrobble Manager
 * 
 * Manages the scrobbling state machine and displays toast notifications.
 * Tracks playback state and sends appropriate scrobble events to Trakt.
 */
class TraktScrobbleManager(
    private val context: Context,
    private val accessToken: String,
    private val clientId: String,
    private val showToasts: Boolean = true  // Toggle for toast notifications
) {
    companion object {
        private const val TAG = "TraktScrobble"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 15 * 60 * 1000L  // 15 minutes
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current media being tracked
    private var currentImdbId: String? = null
    private var currentTmdbId: Int? = null
    private var currentSeason: Int? = null
    private var currentEpisode: Int? = null
    private var currentMediaType: TraktMediaType = TraktMediaType.MOVIE
    private var currentTitle: String = ""
    
    // State tracking
    private var isTracking = false
    private var lastAction: String? = null
    
    // Progress timer
    private var progressRunnable: Runnable? = null

    /**
     * Set the current media to track
     * Call this before starting playback
     */
    fun setMedia(
        imdbId: String?,
        tmdbId: Int?,
        title: String,
        season: Int? = null,
        episode: Int? = null
    ) {
        currentImdbId = imdbId
        currentTmdbId = tmdbId
        currentTitle = title
        currentSeason = season
        currentEpisode = episode
        currentMediaType = if (season != null && episode != null) {
            TraktMediaType.EPISODE
        } else {
            TraktMediaType.MOVIE
        }
        DebugLogger.log(TAG, "Media set: $title (IMDB: $imdbId, TMDB: $tmdbId, S${season}E${episode})")
    }

    /**
     * Update IMDB ID after async resolution (e.g., from Cinemeta)
     * Call this when IMDB ID becomes available after initial playback start
     */
    fun updateImdbId(imdbId: String?, title: String? = null) {
        DebugLogger.log(TAG, "╔══════════════════════════════════════════════════════════════════╗")
        DebugLogger.log(TAG, "║  IMDB ID UPDATED (from Cinemeta)                                 ║")
        DebugLogger.log(TAG, "╚══════════════════════════════════════════════════════════════════╝")
        DebugLogger.log(TAG, "  Old IMDB: $currentImdbId → New IMDB: $imdbId")
        
        currentImdbId = imdbId
        if (title != null && title.isNotBlank()) {
            currentTitle = title
            DebugLogger.log(TAG, "  Title updated: $currentTitle")
        }
        
        DebugLogger.log(TAG, "  isTracking: $isTracking, canScrobble: ${canScrobble()}")
        
        // KEY FIX: If we're already tracking and NOW have valid IDs, 
        // immediately send scrobble start to Trakt!
        if (isTracking && canScrobble()) {
            DebugLogger.log(TAG, "  ★ Re-sending scrobble START with new IMDB ID...")
            scope.launch {
                val response = TraktClient.scrobbleStart(
                    accessToken, clientId, currentMediaType,
                    currentImdbId, currentTmdbId, currentSeason, currentEpisode, 0.1f
                )
                if (response != null) {
                    lastAction = response.action
                    DebugLogger.log(TAG, "  ★ Re-scrobble response: ${response.action}")
                    if (showToasts) {
                        mainHandler.post {
                            Toast.makeText(context, "✓ Now tracking on Trakt", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    DebugLogger.log(TAG, "  ✗ Re-scrobble failed - null response")
                }
            }
        } else {
            DebugLogger.log(TAG, "  Not re-scrobbling (isTracking=$isTracking, canScrobble=${canScrobble()})")
        }
    }

    /**
     * Called when playback starts or resumes
     */
    fun onPlaybackStarted(progress: Float) {
        DebugLogger.log(TAG, "onPlaybackStarted called, progress: $progress%")
        DebugLogger.log(TAG, "  currentImdbId: $currentImdbId, currentTmdbId: $currentTmdbId")
        DebugLogger.log(TAG, "  currentTitle: $currentTitle")
        DebugLogger.log(TAG, "  accessToken: ${if (accessToken.isNotBlank()) "SET" else "EMPTY"}")
        DebugLogger.log(TAG, "  clientId: ${if (clientId.isNotBlank()) "SET" else "EMPTY"}")
        
        isTracking = true
        
        // Always show toast when tracking starts
        showToast("📺 Tracking: $currentTitle")
        
        // Only make API call if we have valid IDs
        if (!canScrobble()) {
            DebugLogger.log(TAG, "Skipping API call - no valid media IDs")
            return
        }
        
        scope.launch {
            val response = TraktClient.scrobbleStart(
                accessToken, clientId, currentMediaType,
                currentImdbId, currentTmdbId, currentSeason, currentEpisode, progress
            )
            
            if (response != null) {
                lastAction = response.action
                DebugLogger.log(TAG, "Scrobble start response: ${response.action}")
            } else {
                DebugLogger.log(TAG, "Scrobble start failed - null response")
            }
        }
        
        // Start progress update timer (every 15 min)
        startProgressTimer(progress)
    }

    /**
     * Called when playback is paused
     */
    /**
     * Called when playback is paused
     */
    fun onPlaybackPaused(progress: Float) {
        DebugLogger.log(TAG, "onPlaybackPaused called, progress: $progress%, isTracking: $isTracking")
        
        if (!isTracking) {
             DebugLogger.log(TAG, "Skipping pause - not currently tracking")
             return
        }

        // Always show toast
        showToast("⏸️ Progress saved")

        if (!canScrobble()) {
             DebugLogger.log(TAG, "Skipping pause API call - cannot scrobble")
             return
        }
        
        DebugLogger.log(TAG, "Sending pause to Trakt...")
        stopProgressTimer()
        
        scope.launch {
            val response = TraktClient.scrobblePause(
                accessToken, clientId, currentMediaType,
                currentImdbId, currentTmdbId, currentSeason, currentEpisode, progress
            )
            
            if (response != null) {
                lastAction = response.action
                DebugLogger.log(TAG, "Pause response: ${response.action}")
            } else {
                 DebugLogger.log(TAG, "Pause failed - null response")
            }
            
            // Also sync to Trakt's playback progress (Continue Watching)
            TraktClient.syncPlaybackProgress(
                accessToken, clientId, currentMediaType,
                currentImdbId, currentTmdbId, currentSeason, currentEpisode, progress
            )
        }
    }

    /**
     * Called when playback stops (user exits or video ends)
     * 
     * If progress > 80%: Marked as WATCHED
     * If progress 1-79%: Saved as paused
     */
    /**
     * Called when playback stops (user exits or video ends)
     * 
     * If progress > 80%: Marked as WATCHED
     * If progress 1-79%: Saved as paused
     */
    fun onPlaybackStopped(progress: Float) {
        DebugLogger.log(TAG, "onPlaybackStopped called, progress: $progress%, isTracking: $isTracking")
        
        if (!isTracking) {
             DebugLogger.log(TAG, "Skipping stop - not currently tracking")
             return
        }

        stopProgressTimer()
        isTracking = false

        if (!canScrobble()) {
             DebugLogger.log(TAG, "Skipping stop API call - cannot scrobble")
             return
        }
        
        DebugLogger.log(TAG, "Sending stop to Trakt...")
        scope.launch {
            val response = TraktClient.scrobbleStop(
                accessToken, clientId, currentMediaType,
                currentImdbId, currentTmdbId, currentSeason, currentEpisode, progress
            )
            
            if (response != null) {
                lastAction = response.action
                DebugLogger.log(TAG, "Stop response: ${response.action}")
                
                // Show specific toast based on action
                if (showToasts) {
                    mainHandler.post {
                       when (response.action) {
                            "scrobble" -> Toast.makeText(context, "✓ Scrobbled to Trakt", Toast.LENGTH_SHORT).show()
                            "pause" -> Toast.makeText(context, "Progress saved", Toast.LENGTH_SHORT).show()
                            else -> DebugLogger.log(TAG, "Stop action: ${response.action}")
                        }
                    }
                }
                
                // If marked as watched (scrobbled), also add to history
                // If paused, sync playback progress
                if (response.action == "scrobble" || progress >= 80f) {
                    TraktClient.syncToHistory(
                        accessToken, clientId, currentMediaType,
                        currentImdbId, currentTmdbId, currentSeason, currentEpisode
                    )
                } else {
                    TraktClient.syncPlaybackProgress(
                        accessToken, clientId, currentMediaType,
                        currentImdbId, currentTmdbId, currentSeason, currentEpisode, progress
                    )
                }
            } else {
                 DebugLogger.log(TAG, "Stop failed - null response")
                 // Still try to sync progress even if scrobble failed
                 TraktClient.syncPlaybackProgress(
                     accessToken, clientId, currentMediaType,
                     currentImdbId, currentTmdbId, currentSeason, currentEpisode, progress
                 )
            }
        }
    }

    /**
     * Called periodically to update progress (every 15 min)
     * Sends a silent scrobble/start to update Trakt's "Now Watching"
     */
    fun onProgressUpdate(progress: Float) {
        if (!canScrobble() || !isTracking) return
        
        DebugLogger.log(TAG, "Progress update: $progress%")
        
        scope.launch {
            // Silent update - no toast
            TraktClient.scrobbleStart(
                accessToken, clientId, currentMediaType,
                currentImdbId, currentTmdbId, currentSeason, currentEpisode, progress
            )
        }
    }

    private fun startProgressTimer(initialProgress: Float) {
        stopProgressTimer()
        
        progressRunnable = object : Runnable {
            override fun run() {
                // This will be called to trigger progress updates
                // The actual progress is calculated by PlayerActivity
                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
        mainHandler.postDelayed(progressRunnable!!, PROGRESS_UPDATE_INTERVAL_MS)
    }

    private fun stopProgressTimer() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun canScrobble(): Boolean {
        DebugLogger.log(TAG, "Checking canScrobble()...")
        if (accessToken.isBlank()) {
             DebugLogger.log(TAG, "  NO: Access token is blank")
             return false
        }
        if (clientId.isBlank()) {
             DebugLogger.log(TAG, "  NO: Client ID is blank")
             return false
        }
        if (currentImdbId.isNullOrBlank() && currentTmdbId == null) {
            DebugLogger.log(TAG, "  NO: No IMDB or TMDB ID (Imdb: $currentImdbId, Tmdb: $currentTmdbId)")
            return false
        }
        DebugLogger.log(TAG, "  YES: Ready to scrobble")
        return true
    }

    private fun showToast(message: String) {
        if (!showToasts) return  // Check if toasts are enabled
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Clean up resources
     */
    fun release() {
        stopProgressTimer()
        scope.cancel()
        isTracking = false
    }
}

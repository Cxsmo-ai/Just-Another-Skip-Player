package com.brouken.player.tmdb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.Player
import com.brouken.player.utils.DebugLogger
import com.brouken.player.utils.LogTags
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages fetching, monitoring, and triggering skips for jump scares.
 */
class JumpScareManager(private val context: Context) {

    companion object {
        private const val TAG = "JumpScareManager"
        private const val POLL_INTERVAL_MS = 250L
        private const val WARNING_THRESHOLD_MS = 5000L // Show warning 5s before
    }

    private var jumpScares: List<JumpScareClient.JumpScare> = emptyList()
    private var isEnabled = false
    private var isMonitoring = false
    
    // Metadata
    private var currentShowName: String? = null
    private var currentYear: Int? = null
    private var currentImdbId: String? = null
    private var currentSeason: Int? = null
    private var currentEpisode: Int? = null

    // Callbacks
    private var onSkipCallback: ((String) -> Unit)? = null
    private var onScaresFoundCallback: (() -> Unit)? = null

    // Threading
    private val bgExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val monitorHandler = Handler(Looper.getMainLooper())

    // Player reference
    private var player: Player? = null

    fun setOnSkipCallback(callback: (String) -> Unit) {
        this.onSkipCallback = callback
    }

    fun setOnScaresFoundCallback(callback: () -> Unit) {
        this.onScaresFoundCallback = callback
    }

    @JvmOverloads
    fun initialize(showName: String, year: Int?, imdbId: String?, enabled: Boolean, season: Int? = null, episode: Int? = null) {
        DebugLogger.t(TAG, "=== initialize() START ===", mapOf(
            "showName" to showName,
            "year" to (year ?: "null"),
            "imdbId" to (imdbId ?: "null"),
            "enabled" to enabled,
            "season" to (season ?: "null"),
            "episode" to (episode ?: "null")
        ))
        
        this.currentShowName = showName
        this.currentYear = year
        this.currentImdbId = imdbId
        this.isEnabled = enabled
        this.currentSeason = season
        this.currentEpisode = episode
        this.jumpScares = emptyList() // Reset

        DebugLogger.i(TAG, "State Updated", mapOf(
            "currentShowName" to currentShowName,
            "currentYear" to currentYear,
            "currentImdbId" to currentImdbId,
            "isEnabled" to isEnabled,
            "currentSeason" to currentSeason,
            "currentEpisode" to currentEpisode,
            "jumpScares_count" to jumpScares.size
        ))

        if (enabled) {
            DebugLogger.i(TAG, "Enabled=true - Triggering fetchJumpScares()")
            fetchJumpScares()
        } else {
            DebugLogger.w(TAG, "Enabled=false - Skipping fetchJumpScares()")
        }
        
        DebugLogger.t(TAG, "=== initialize() END ===")
    }

    fun startMonitoring(player: Player) {
        DebugLogger.t(TAG, "startMonitoring() called", mapOf(
            "player" to (if (player != null) "PLAYER" else "NULL"),
            "isMonitoring" to isMonitoring,
            "isEnabled" to isEnabled,
            "jumpScares_count" to jumpScares.size
        ))
        
        this.player = player
        
        if (!isMonitoring && isEnabled) {
            isMonitoring = true
            DebugLogger.i(TAG, "Starting monitoring loop", mapOf(
                "poll_interval_ms" to POLL_INTERVAL_MS,
                "warning_threshold_ms" to WARNING_THRESHOLD_MS,
                "jump_scares_to_monitor" to jumpScares.size
            ))
            monitorRunnable.run()
        } else {
            DebugLogger.w(TAG, "Monitoring NOT started", mapOf(
                "reason" to when {
                    isMonitoring -> "Already monitoring"
                    !isEnabled -> "Disabled in preferences"
                    else -> "Unknown reason"
                }
            ))
        }
    }

    fun stopMonitoring() {
        DebugLogger.t(TAG, "stopMonitoring() called", mapOf(
            "isMonitoring" to isMonitoring,
            "callbacks_pending" to monitorHandler.hasCallbacks(monitorRunnable)
        ))
        
        isMonitoring = false
        monitorHandler.removeCallbacks(monitorRunnable)
        player = null
        
        DebugLogger.i(TAG, "Monitoring stopped", mapOf(
            "callbacks_removed" to true,
            "player_cleared" to true
        ))
    }
    
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun fetchJumpScares() {
        DebugLogger.section(LogTags.JUMPSCARE_FETCH, "JUMP SCARE FETCH START")
        
        if (currentShowName == null) {
            DebugLogger.e(TAG, "Cannot fetch - currentShowName is NULL")
            return
        }

        DebugLogger.i(TAG, "Starting fetch operation", mapOf(
            "showName" to currentShowName,
            "year" to (currentYear ?: "null"),
            "season" to (currentSeason ?: "null"),
            "episode" to (currentEpisode ?: "null"),
            "imdbId" to (currentImdbId ?: "null")
        ))

        bgExecutor.execute {
            val opId = "fetch-jump-scares-${System.currentTimeMillis()}"
            DebugLogger.startTimer(opId)
            
            try {
                DebugLogger.step(TAG, 1, 5, "Creating JumpScareClient instance")
                val client = JumpScareClient()
                
                DebugLogger.step(TAG, 2, 5, "Calling fetchJumpScares()", mapOf(
                    "title" to currentShowName,
                    "year" to currentYear,
                    "season" to currentSeason
                ))
                
                // Pass season AND episode to enable episode-scoped parsing (the KEY fix)
                val rawScares = client.fetchJumpScares(currentShowName!!, currentYear, currentSeason, currentEpisode)
                
                DebugLogger.step(TAG, 3, 5, "Received raw scares", mapOf(
                    "count" to rawScares.size,
                    "has_episode_filter" to (currentEpisode != null)
                ))
                
                // Filter scares based on episode if applicable
                val filteredScares = filterScaresForEpisode(rawScares)
                
                DebugLogger.step(TAG, 4, 5, "Filtered scares", mapOf(
                    "before" to rawScares.size,
                    "after" to filteredScares.size,
                    "removed" to (rawScares.size - filteredScares.size)
                ))
                
                mainHandler.post {
                    DebugLogger.step(TAG, 5, 5, "Processing on UI thread")
                    
                    if (filteredScares.isNotEmpty()) {
                        jumpScares = filteredScares
                        
                        // Build detailed log of all scares
                        DebugLogger.section(LogTags.JUMPSCARE_RESULT, "JUMP SCARES FOUND")
                        DebugLogger.i(TAG, "Total scares: ${jumpScares.size}", mapOf(
                            "show" to currentShowName,
                            "season" to (currentSeason ?: "N/A"),
                            "episode" to (currentEpisode ?: "N/A")
                        ))
                        
                        val seasonText = if (currentSeason != null) "S$currentSeason" else ""
                        val episodeText = if (currentEpisode != null) "E$currentEpisode" else ""
                        val episodeInfo = if (seasonText.isNotEmpty() || episodeText.isNotEmpty()) 
                            " [$seasonText $episodeText]" else ""
                        
                        DebugLogger.i(TAG, "Scare Details:")
                        for ((index, scare) in jumpScares.withIndex()) {
                            val timeStr = formatTime(scare.timeMs)
                            val desc = scare.description.replace(Regex("\\[Ep \\d+\\]"), "").trim()
                            DebugLogger.d(TAG, "  Scare #${index + 1}", mapOf(
                                "time" to timeStr,
                                "time_ms" to scare.timeMs,
                                "description" to desc,
                                "intensity" to scare.intensity
                            ))
                        }
                        
                        DebugLogger.success(TAG, "Ready to monitor for ${jumpScares.size} jump scares")
                        
                        // Show toast notification
                        val toastMsg = "Found ${jumpScares.size} jump scare${if (jumpScares.size > 1) "s" else ""}$episodeInfo"
                        Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                        DebugLogger.i(TAG, "User notification shown", mapOf("message" to toastMsg))
                        
                        onScaresFoundCallback?.invoke()
                        DebugLogger.d(TAG, "onScaresFoundCallback invoked")
                    } else {
                        val seasonText = if (currentSeason != null) "S$currentSeason" else ""
                        val episodeText = if (currentEpisode != null) "E$currentEpisode" else ""
                        val episodeInfo = if (seasonText.isNotEmpty() || episodeText.isNotEmpty()) 
                            " [$seasonText $episodeText]" else ""
                        
                        DebugLogger.w(TAG, "No jumpscares found", mapOf(
                            "show" to currentShowName,
                            "episode_info" to episodeInfo,
                            "raw_scares" to rawScares.size
                        ))
                        
                        Toast.makeText(context, "No jump scares found for this episode", Toast.LENGTH_SHORT).show()
                    }
                    
                    DebugLogger.timed(TAG, opId, "Fetch operation complete")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Error fetching scares", e, mapOf(
                    "showName" to currentShowName,
                    "season" to currentSeason,
                    "episode" to currentEpisode
                ))
                e.printStackTrace()
            }
        }
    }

    private fun filterScaresForEpisode(scares: List<JumpScareClient.JumpScare>): List<JumpScareClient.JumpScare> {
        if (scares.isEmpty()) return emptyList()
        
        // If we don't have episode info, return all (movie or unknown context)
        if (currentEpisode == null) {
            DebugLogger.log(TAG, "No episode context, returning all ${scares.size} scares")
            return scares
        }

        DebugLogger.log(TAG, "Filtering ${scares.size} scares for Episode $currentEpisode")
        
        // First, check if any scare has an [Ep X] tag
        val listHasEpisodeTags = scares.any { it.description.contains("[Ep ") }
        DebugLogger.log(TAG, "List has episode tags: $listHasEpisodeTags")
        
        if (!listHasEpisodeTags) {
            // No episode structure - this might be a movie page or the parser didn't find tags
            // Return all scares since we can't filter by episode
            DebugLogger.log(TAG, "No episode tags found in list, returning all scares")
            return scares
        }
        
        // Filter to only scares matching our episode
        val filtered = scares.filter { scare ->
            val match = Regex("""\[Ep\s*(\d+)\]""").find(scare.description)
            if (match != null) {
                val epNum = match.groupValues[1].toIntOrNull()
                val matches = epNum == currentEpisode
                if (matches) {
                    DebugLogger.log(TAG, "  ✓ Ep $epNum matches current episode $currentEpisode")
                }
                matches
            } else {
                // Scare has no episode tag but list has tags - EXCLUDE it
                // (likely a parser issue or series-wide warning)
                DebugLogger.log(TAG, "  ✗ Excluding untagged scare: ${scare.description.take(50)}")
                false
            }
        }
        
        DebugLogger.log(TAG, "Filtered down to ${filtered.size} scares for Episode $currentEpisode")
        return filtered
    }

    private val monitorRunnable = object : Runnable {
        private var lastLoggedSecond: Long = -1
        
        override fun run() {
            if (!isMonitoring || player == null || jumpScares.isEmpty()) {
                if (jumpScares.isEmpty() && isMonitoring) {
                    DebugLogger.t(LogTags.JUMPSCARE_MONITOR, "Monitor tick - no scares to monitor")
                }
                return
            }

            try {
                val currentPos = player!!.currentPosition // ms
                val currentSecond = currentPos / 1000
                
                // Find closest upcoming scare
                val nextScare = jumpScares.minByOrNull { 
                    val diff = it.timeMs - currentPos
                    if (diff < 0) Long.MAX_VALUE else diff 
                }
                
                // Log position every 5 seconds (not every tick)
                if (nextScare != null && currentSecond != lastLoggedSecond && currentSecond % 5 == 0L) {
                    val dist = nextScare.timeMs - currentPos
                    DebugLogger.t(LogTags.JUMPSCARE_MONITOR, "Position check", mapOf(
                        "current_pos" to "${formatTime(currentPos)} (${currentPos}ms)",
                        "next_scare" to "${formatTime(nextScare.timeMs)} (${nextScare.timeMs}ms)",
                        "distance_ms" to dist
                    ))
                    lastLoggedSecond = currentSecond
                }
                
                for (scare in jumpScares) {
                    val dist = scare.timeMs - currentPos
                    
                    // Warning at 5 seconds
                    if (dist in 4500..5000) {
                        DebugLogger.w(LogTags.JUMPSCARE_WARN, "⚠️ Approaching jump scare!", mapOf(
                            "scare_at" to formatTime(scare.timeMs),
                            "distance_ms" to dist,
                            "description" to scare.description.take(40)
                        ))
                    }
                    
                    // Skip at 300ms before
                    if (dist in 0..300) {
                        val seekTarget = scare.timeMs + 1000 // Skip to 1s after
                        
                        DebugLogger.section(LogTags.JUMPSCARE_SKIP, "SKIP TRIGGERED")
                        DebugLogger.i(LogTags.JUMPSCARE_SKIP, "🎬 Skipping jump scare", mapOf(
                            "from_ms" to currentPos,
                            "from_time" to formatTime(currentPos),
                            "to_ms" to seekTarget,
                            "to_time" to formatTime(seekTarget),
                            "skipped_seconds" to ((seekTarget - currentPos) / 1000.0),
                            "description" to scare.description.replace(Regex("\\[Ep \\d+\\]"), "").trim()
                        ))
                        
                        player?.seekTo(seekTarget)
                        
                        val msg = "Skipped: ${scare.description.replace(Regex("\\[Ep \\d+\\]"), "").trim()}"
                        onSkipCallback?.invoke(msg)
                        
                        DebugLogger.success(LogTags.JUMPSCARE_SKIP, "Skip complete, callback invoked")
                        
                        // Break to avoid double seeks
                        break
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e(LogTags.JUMPSCARE_MONITOR, "Monitor error", e)
                e.printStackTrace()
            }

            monitorHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }
}
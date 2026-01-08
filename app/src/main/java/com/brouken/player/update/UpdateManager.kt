package com.brouken.player.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Manages in-app updates from GitHub releases.
 * Checks for updates on app launch and provides manual check functionality.
 */
class UpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
        private const val CHECK_INTERVAL_HOURS = 24L
        
        @Volatile
        private var instance: UpdateManager? = null
        
        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val githubClient = GitHubReleasesClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Check for updates on app launch (rate-limited to once per 24 hours)
     */
    fun checkOnLaunch(callback: (UpdateInfo?) -> Unit) {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        val now = System.currentTimeMillis()
        val hoursSinceLastCheck = TimeUnit.MILLISECONDS.toHours(now - lastCheck)
        
        if (hoursSinceLastCheck < CHECK_INTERVAL_HOURS) {
            Log.d(TAG, "Skipping check, last checked ${hoursSinceLastCheck}h ago")
            callback(null)
            return
        }
        
        checkForUpdates(callback)
    }
    
    /**
     * Force check for updates (bypasses rate limiting)
     */
    fun forceCheck(callback: (UpdateInfo?) -> Unit) {
        checkForUpdates(callback)
    }
    
    private fun checkForUpdates(callback: (UpdateInfo?) -> Unit) {
        scope.launch {
            try {
                Log.d(TAG, "Checking for updates...")
                val updateInfo = githubClient.getLatestRelease()
                
                if (updateInfo != null) {
                    prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                    
                    val currentVersion = getCurrentVersionCode()
                    val remoteVersion = parseVersionCode(updateInfo.tagName)
                    
                    Log.d(TAG, "Current: $currentVersion, Remote: $remoteVersion")
                    
                    if (remoteVersion > currentVersion) {
                        val skippedVersion = prefs.getString(KEY_SKIPPED_VERSION, null)
                        if (skippedVersion == updateInfo.tagName) {
                            Log.d(TAG, "User skipped this version")
                            withContext(Dispatchers.Main) { callback(null) }
                            return@launch
                        }
                        
                        Log.d(TAG, "Update available: ${updateInfo.tagName}")
                        withContext(Dispatchers.Main) { callback(updateInfo) }
                    } else {
                        Log.d(TAG, "App is up to date")
                        withContext(Dispatchers.Main) { callback(null) }
                    }
                } else {
                    withContext(Dispatchers.Main) { callback(null) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }
    
    /**
     * Mark a version as skipped (user clicked "Later")
     */
    fun skipVersion(tagName: String) {
        prefs.edit().putString(KEY_SKIPPED_VERSION, tagName).apply()
    }
    
    /**
     * Get current app version code
     */
    private fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Parse version tag to version code
     * v1.0.2 -> 10002
     */
    private fun parseVersionCode(tag: String): Int {
        return try {
            val version = tag.removePrefix("v").split("-")[0]  // Remove "v" prefix and build suffix
            val parts = version.split(".")
            when (parts.size) {
                3 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                2 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100
                1 -> parts[0].toInt() * 10000
                else -> 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse version: $tag", e)
            0
        }
    }
    
    fun destroy() {
        scope.cancel()
    }
}

package com.brouken.player.update

import android.util.Log
import com.brouken.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for fetching release information from GitHub API
 */
class GitHubReleasesClient {
    
    companion object {
        private const val TAG = "GitHubReleasesClient"
        private const val GITHUB_API_URL = "https://api.github.com/repos/Cxsmo-ai/Just-Another-Skip-Player/releases/latest"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    /**
     * Fetch the latest release from GitHub
     */
    fun getLatestRelease(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "GitHub API error: ${response.code}")
                return null
            }
            
            val body = response.body?.string() ?: return null
            parseRelease(JSONObject(body))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching release", e)
            null
        }
    }
    
    private fun parseRelease(json: JSONObject): UpdateInfo {
        val tagName = json.getString("tag_name")
        val name = json.optString("name", tagName)
        val changelog = json.optString("body", "")
        val assets = json.getJSONArray("assets")
        
        // Find APK matching current variant
        val apkAsset = findMatchingApk(assets)
        
        return UpdateInfo(
            tagName = tagName,
            name = name,
            changelog = changelog,
            apkUrl = apkAsset?.optString("browser_download_url"),
            apkSize = apkAsset?.optLong("size") ?: 0L,
            apkName = apkAsset?.optString("name") ?: ""
        )
    }
    
    /**
     * Find APK asset matching current build variant (JASP or Original)
     */
    private fun findMatchingApk(assets: JSONArray): JSONObject? {
        val isJasp = BuildConfig.FLAVOR.contains("jasp", ignoreCase = true)
        val pattern = if (isJasp) {
            Regex("JASP.*\\.apk", RegexOption.IGNORE_CASE)
        } else {
            Regex("Just-Player.*\\.apk", RegexOption.IGNORE_CASE)
        }
        
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (pattern.matches(name)) {
                Log.d(TAG, "Found matching APK: $name")
                return asset
            }
        }
        
        // Fallback: try to find any APK
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                Log.d(TAG, "Fallback APK: $name")
                return asset
            }
        }
        
        return null
    }
}

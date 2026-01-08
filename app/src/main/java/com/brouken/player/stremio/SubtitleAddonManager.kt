package com.brouken.player.stremio

import android.content.Context
import android.content.SharedPreferences
import com.brouken.player.utils.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Manages the list of configured subtitle addons
 * Handles persistence, ordering, and validation
 */
class SubtitleAddonManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SubtitleAddonManager"
        private const val PREFS_NAME = "jasp_subtitle_addons"
        private const val KEY_ADDONS = "addons_list"
        
        // Pre-configured popular addon manifests (scraped from stremio-addons.net)
        val POPULAR_ADDONS = listOf(
            PopularAddon(
                name = "OpenSubtitles PRO",
                manifestUrl = "https://opensubtitlesv3-pro.dexter21767.com/manifest.json",
                description = "Ad-free and spam-free subtitles addon",
                logoUrl = "https://i.imgur.com/cGc1DXB.png"
            ),
            PopularAddon(
                name = "SubSource Subtitles",
                manifestUrl = "https://subsource.strem.top/manifest.json",
                description = "Get subtitles from SubSource.net",
                logoUrl = "https://raw.githubusercontent.com/nexusdiscord/tv-logo/master/ss.png"
            ),
            PopularAddon(
                name = "SubDL Subtitles",
                manifestUrl = "https://subdl.strem.top/manifest.json",
                description = "Get subtitles from SubDL.com",
                logoUrl = "https://raw.githubusercontent.com/nexusdiscord/tv-logo/master/download.jpg"
            ),
            PopularAddon(
                name = "GTSubs",
                manifestUrl = "https://gtsubs.strem.top/manifest.json",
                description = "Auto-translate subtitles via Google Translate",
                logoUrl = "https://raw.githubusercontent.com/nexusdiscord/tv-logo/master/gt.png"
            ),
            PopularAddon(
                name = "MSubtitles",
                manifestUrl = "https://msubtitles.lowlevel1989.click/conf/api/v1/manifest.json",
                description = "Manage your subtitles or follow other users",
                logoUrl = "https://lowlevel-1989.github.io/manage-subtitles-static/seo/images/logo-256x.png"
            ),
            PopularAddon(
                name = "Community Subtitles",
                manifestUrl = "https://stremio-community-subtitles.top/manifest.json",
                description = "Community-driven subtitles with user accounts",
                logoUrl = "https://stremio-community-subtitles.top/static/logo.png"
            ),
            PopularAddon(
                name = "AIO Subtitle PRO",
                manifestUrl = "https://api.aiosubtitle.org/stremio/manifest.json",
                description = "OpenSubtitles PRO + OpenAI translations",
                logoUrl = "https://api.aiosubtitle.org/assets/logo.webp"
            ),
            PopularAddon(
                name = "Subtito AI",
                manifestUrl = "https://subtito.com/manifest.json",
                description = "AI translated subtitles in your language",
                logoUrl = "https://subtito.com/static/images/logo_website.png"
            ),
            PopularAddon(
                name = "MultiSub AI",
                manifestUrl = "https://www.multisub.org/manifest.json",
                description = "AI-powered bilingual subtitles",
                logoUrl = "https://www.multisub.org/logo.png"
            ),
            PopularAddon(
                name = "Strelingo Dual Subs",
                manifestUrl = "https://strelingo-addon.vercel.app/manifest.json",
                description = "Dual subtitles for language learning",
                logoUrl = "https://raw.githubusercontent.com/Serkali-sudo/strelingo-addon/refs/heads/main/assets/strelingo_icon.jpg"
            ),
            PopularAddon(
                name = "YIFYSubtitles",
                manifestUrl = "https://2ecbbd610840-yifysubtitles.baby-beamup.club/manifest.json",
                description = "Subtitles from yifysubtitles.org",
                logoUrl = "https://yifysubtitles.org/images/misc/yifysubtitles-logo-small.png"
            ),
            PopularAddon(
                name = "Podnapisi",
                manifestUrl = "https://2ecbbd610840-podnapisi.baby-beamup.club/manifest.json",
                description = "European/Slovenian subtitles",
                logoUrl = "https://www.podnapisi.net/static/favicon.ico"
            ),
            PopularAddon(
                name = "DeepL Translate",
                manifestUrl = "https://deeplsubtitle.sonsuzanime.com/manifest.json",
                description = "Translate subtitles using DeepL",
                logoUrl = "https://deeplsubtitle.sonsuzanime.com/subtitles/logo.png"
            ),
            PopularAddon(
                name = "AIO Subtitle",
                manifestUrl = "https://3b4bbf5252c4-aio-streaming.baby-beamup.club/manifest.json",
                description = "Subscene, OpenSubtitles & Kitsunekko",
                logoUrl = "https://3b4bbf5252c4-aio-streaming.baby-beamup.club/assets/AI-sub.png"
            ),
            PopularAddon(
                name = "Heb Subs Premium",
                manifestUrl = "https://stremiohebsubs.onrender.com/manifest.json",
                description = "Hebrew subtitles from top websites",
                logoUrl = "https://i.ibb.co/p1PRCyW/icon.png"
            )
        )
        
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
    
    data class PopularAddon(
        val name: String,
        val manifestUrl: String,
        val description: String,
        val logoUrl: String? = null
    )
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val client = StremioAddonClient(context)
    
    private var cachedAddons: MutableList<SubtitleAddon>? = null
    
    /**
     * Get all configured addons
     */
    fun getAddons(): List<SubtitleAddon> {
        if (cachedAddons == null) {
            loadAddons()
        }
        return cachedAddons ?: emptyList()
    }
    
    /**
     * Get only enabled addons, sorted by priority
     */
    fun getEnabledAddons(): List<SubtitleAddon> {
        return getAddons()
            .filter { it.isEnabled }
            .sortedBy { it.priority }
    }
    
    /**
     * Add a new addon by manifest URL
     */
    suspend fun addAddon(
        manifestUrl: String,
        displayName: String? = null,
        logoUrl: String? = null
    ): Result<SubtitleAddon> = withContext(Dispatchers.IO) {
        try {
            DebugLogger.log(TAG, "Adding addon: $manifestUrl")
            
            // Normalize URL
            val normalizedUrl = normalizeManifestUrl(manifestUrl)
            
            // Check if already exists
            if (getAddons().any { it.manifestUrl == normalizedUrl }) {
                return@withContext Result.failure(
                    IllegalArgumentException("Addon already exists")
                )
            }
            
            // Fetch manifest to validate and get name
            val manifestResult = client.fetchManifest(normalizedUrl)
            val manifest = manifestResult.getOrElse { 
                return@withContext Result.failure(it) 
            }
            
            // Extract base URL from manifest URL
            val baseUrl = normalizedUrl.substringBeforeLast("/manifest.json")
                .trimEnd('/')
            
            // Create addon entry
            val addon = SubtitleAddon(
                id = UUID.randomUUID().toString(),
                manifestUrl = normalizedUrl,
                baseUrl = baseUrl,
                displayName = displayName ?: manifest.name,
                description = manifest.description,
                version = manifest.version,
                logoUrl = logoUrl,
                isEnabled = true,
                priority = getAddons().size, // Add at end
                supportsHash = true, // Assume true unless proven otherwise
                supportsImdb = true,
                supportedTypes = manifest.types
            )
            
            // Add to list and save
            val addons = getAddons().toMutableList()
            addons.add(addon)
            saveAddons(addons)
            
            DebugLogger.log(TAG, "Addon added: ${addon.displayName}")
            Result.success(addon)
            
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to add addon: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Add a popular addon by preset
     */
    suspend fun addPopularAddon(preset: PopularAddon): Result<SubtitleAddon> {
        return addAddon(preset.manifestUrl, preset.name, preset.logoUrl)
    }
    
    /**
     * Remove an addon by ID
     */
    fun removeAddon(addonId: String): Boolean {
        val addons = getAddons().toMutableList()
        val removed = addons.removeAll { it.id == addonId }
        
        if (removed) {
            // Recalculate priorities
            addons.forEachIndexed { index, addon ->
                addons[index] = addon.copy(priority = index)
            }
            saveAddons(addons)
            DebugLogger.log(TAG, "Addon removed: $addonId")
        }
        
        return removed
    }
    
    /**
     * Update addon enabled state
     */
    fun setEnabled(addonId: String, enabled: Boolean) {
        val addons = getAddons().toMutableList()
        val index = addons.indexOfFirst { it.id == addonId }
        
        if (index >= 0) {
            addons[index] = addons[index].copy(isEnabled = enabled)
            saveAddons(addons)
        }
    }
    
    /**
     * Update addon display name
     */
    fun updateDisplayName(addonId: String, newName: String) {
        val addons = getAddons().toMutableList()
        val index = addons.indexOfFirst { it.id == addonId }
        
        if (index >= 0) {
            addons[index] = addons[index].copy(displayName = newName)
            saveAddons(addons)
        }
    }
    
    /**
     * Reorder addons (for drag-and-drop)
     */
    fun reorderAddons(orderedIds: List<String>) {
        val currentAddons = getAddons().associateBy { it.id }
        val reordered = orderedIds.mapIndexedNotNull { index, id ->
            currentAddons[id]?.copy(priority = index)
        }
        
        // Add any addons not in the ordered list at the end
        val remaining = getAddons()
            .filter { it.id !in orderedIds }
            .mapIndexed { index, addon ->
                addon.copy(priority = reordered.size + index)
            }
        
        saveAddons(reordered + remaining)
    }
    
    /**
     * Move addon up in priority
     */
    fun moveUp(addonId: String) {
        val addons = getAddons().sortedBy { it.priority }.toMutableList()
        val index = addons.indexOfFirst { it.id == addonId }
        
        if (index > 0) {
            // Swap with previous
            val temp = addons[index - 1]
            addons[index - 1] = addons[index].copy(priority = index - 1)
            addons[index] = temp.copy(priority = index)
            saveAddons(addons)
        }
    }
    
    /**
     * Move addon down in priority
     */
    fun moveDown(addonId: String) {
        val addons = getAddons().sortedBy { it.priority }.toMutableList()
        val index = addons.indexOfFirst { it.id == addonId }
        
        if (index >= 0 && index < addons.size - 1) {
            // Swap with next
            val temp = addons[index + 1]
            addons[index + 1] = addons[index].copy(priority = index + 1)
            addons[index] = temp.copy(priority = index)
            saveAddons(addons)
        }
    }
    
    /**
     * Record last usage time
     */
    fun recordUsage(addonId: String) {
        val addons = getAddons().toMutableList()
        val index = addons.indexOfFirst { it.id == addonId }
        
        if (index >= 0) {
            addons[index] = addons[index].copy(
                lastUsed = System.currentTimeMillis(),
                lastError = null,
                errorCount = 0
            )
            saveAddons(addons)
        }
    }
    
    /**
     * Record an error
     */
    fun recordError(addonId: String, error: String) {
        val addons = getAddons().toMutableList()
        val index = addons.indexOfFirst { it.id == addonId }
        
        if (index >= 0) {
            val current = addons[index]
            addons[index] = current.copy(
                lastError = error,
                errorCount = current.errorCount + 1
            )
            saveAddons(addons)
        }
    }
    
    /**
     * Test connection to an addon
     */
    suspend fun testConnection(addonId: String): Result<StremioManifest> {
        val addon = getAddons().find { it.id == addonId }
            ?: return Result.failure(IllegalArgumentException("Addon not found"))
        
        return client.testConnection(addon.manifestUrl)
    }
    
    /**
     * Clear all addons
     */
    fun clearAll() {
        saveAddons(emptyList())
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private fun loadAddons() {
        try {
            val jsonString = prefs.getString(KEY_ADDONS, "[]") ?: "[]"
            cachedAddons = json.decodeFromString<List<SubtitleAddon>>(jsonString).toMutableList()
            DebugLogger.log(TAG, "Loaded ${cachedAddons?.size ?: 0} addons")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to load addons: ${e.message}", e)
            cachedAddons = mutableListOf()
        }
    }
    
    private fun saveAddons(addons: List<SubtitleAddon>) {
        try {
            val jsonString = json.encodeToString(addons)
            prefs.edit().putString(KEY_ADDONS, jsonString).apply()
            cachedAddons = addons.toMutableList()
            DebugLogger.log(TAG, "Saved ${addons.size} addons")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to save addons: ${e.message}", e)
        }
    }
    
    private fun normalizeManifestUrl(url: String): String {
        var normalized = url.trim()
        
        // Add https if no protocol
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        
        // Add manifest.json if not present
        if (!normalized.endsWith("manifest.json")) {
            normalized = normalized.trimEnd('/') + "/manifest.json"
        }
        
        return normalized
    }
}

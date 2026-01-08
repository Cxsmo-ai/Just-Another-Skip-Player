package com.brouken.player.stremio

import com.brouken.player.utils.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client for fetching the live addon catalog from stremio-addons.net
 * Filters for subtitle addons and supports text search
 */
class StremioAddonsCatalogClient {
    
    companion object {
        private const val TAG = "StremioAddonsCatalog"
        private const val CATALOG_URL = "https://stremio-addons.net/api/addon_catalog/all/stremio-addons.net.json"
        
        private val json = Json { 
            ignoreUnknownKeys = true 
            coerceInputValues = true
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Cache for the full catalog
    private var cachedCatalog: List<CatalogAddon>? = null
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    
    /**
     * Fetch subtitle addons from the catalog, with optional search filter
     */
    suspend fun searchSubtitleAddons(
        query: String = ""
    ): Result<List<CatalogAddon>> = withContext(Dispatchers.IO) {
        try {
            // Use cache if valid
            val now = System.currentTimeMillis()
            val catalog = if (cachedCatalog != null && now - lastFetchTime < CACHE_DURATION_MS) {
                DebugLogger.log(TAG, "Using cached catalog")
                cachedCatalog!!
            } else {
                DebugLogger.log(TAG, "Fetching catalog from $CATALOG_URL")
                val freshCatalog = fetchCatalog()
                cachedCatalog = freshCatalog
                lastFetchTime = now
                freshCatalog
            }
            
            // Filter for subtitle addons
            val subtitleAddons = catalog.filter { isSubtitleAddon(it) }
            
            // Apply search filter if provided
            val filtered = if (query.isBlank()) {
                subtitleAddons
            } else {
                val lowerQuery = query.lowercase()
                subtitleAddons.filter { addon ->
                    addon.name.lowercase().contains(lowerQuery) ||
                    addon.description?.lowercase()?.contains(lowerQuery) == true
                }
            }
            
            DebugLogger.log(TAG, "Found ${filtered.size} subtitle addons matching '$query'")
            Result.success(filtered)
            
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to fetch catalog: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clear the cache to force a fresh fetch
     */
    fun clearCache() {
        cachedCatalog = null
        lastFetchTime = 0
    }
    
    private fun fetchCatalog(): List<CatalogAddon> {
        val request = Request.Builder()
            .url(CATALOG_URL)
            .header("Accept", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        
        val body = response.body?.string() 
            ?: throw Exception("Empty response")
        
        val catalogResponse = json.decodeFromString<CatalogResponse>(body)
        
        // Map to our simpler CatalogAddon model
        return catalogResponse.addons.mapNotNull { entry ->
            val manifest = entry.manifest ?: return@mapNotNull null
            CatalogAddon(
                name = manifest.name ?: return@mapNotNull null,
                description = manifest.description,
                logoUrl = manifest.logo ?: manifest.icon,
                manifestUrl = entry.transportUrl,
                types = manifest.types ?: emptyList(),
                resources = extractResources(manifest.resources)
            )
        }
    }
    
    private fun isSubtitleAddon(addon: CatalogAddon): Boolean {
        return addon.resources.contains("subtitles")
    }
    
    private fun extractResources(resources: List<ResourceEntry>?): List<String> {
        return resources?.map { it.name ?: it.stringValue ?: "" }?.filter { it.isNotEmpty() } 
            ?: emptyList()
    }
}

/**
 * Simplified addon model for UI display
 */
data class CatalogAddon(
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val manifestUrl: String,
    val types: List<String>,
    val resources: List<String>
)

// API Response Models
@Serializable
private data class CatalogResponse(
    val addons: List<CatalogEntry> = emptyList()
)

@Serializable
private data class CatalogEntry(
    val transportUrl: String = "",
    val transportName: String? = null,
    val manifest: ManifestData? = null
)

@Serializable
private data class ManifestData(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val version: String? = null,
    val logo: String? = null,
    val icon: String? = null,
    val types: List<String>? = null,
    val resources: List<ResourceEntry>? = null
)

@Serializable(with = ResourceEntrySerializer::class)
private data class ResourceEntry(
    val name: String? = null,
    val stringValue: String? = null
)

// Custom serializer to handle both string and object resources
private object ResourceEntrySerializer : kotlinx.serialization.KSerializer<ResourceEntry> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("ResourceEntry")
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ResourceEntry {
        val jsonDecoder = decoder as kotlinx.serialization.json.JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                ResourceEntry(stringValue = element.content)
            }
            is kotlinx.serialization.json.JsonObject -> {
                val name = element["name"]?.let { 
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content 
                }
                ResourceEntry(name = name)
            }
            else -> ResourceEntry()
        }
    }
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ResourceEntry) {
        encoder.encodeString(value.name ?: value.stringValue ?: "")
    }
}

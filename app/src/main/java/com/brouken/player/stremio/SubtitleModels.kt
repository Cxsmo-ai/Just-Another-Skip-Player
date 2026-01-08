package com.brouken.player.stremio

import kotlinx.serialization.Serializable

/**
 * Data models for Stremio subtitle addon integration and subtitle management
 */

// ============================================================================
// SUBTITLE TRACK MODEL
// ============================================================================

/**
 * Represents a single subtitle track from any source
 */
@Serializable
data class SubtitleTrack(
    val id: String,
    val language: String,           // "English", "Spanish"
    val languageCode: String,       // "eng", "spa" (ISO 639-2)
    val countryCode: String? = null, // "US", "GB", "MX"
    val label: String? = null,      // "SDH", "Forced", "HI", "CC"
    val source: SubtitleSource,
    val addonName: String? = null,  // "OpenSubtitles", "Community Subs"
    val addonId: String? = null,    // ID of the addon that provided this
    val url: String? = null,        // Direct URL to subtitle file
    val mimeType: String? = null,   // "application/x-subrip", "text/vtt"
    val matchScore: Int = 0,        // 0-100 confidence score
    val isHashMatch: Boolean = false,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isSDH: Boolean = false,     // Subtitles for Deaf/Hard of Hearing
    val isHI: Boolean = false,      // Hearing Impaired
    val isCC: Boolean = false,      // Closed Captions
    val downloadCount: Int? = null, // Popularity indicator
    val rating: Float? = null,      // User rating if available
    val encoding: String? = "UTF-8",
    val embeddedTrackIndex: Int? = null // For embedded tracks
) {
    companion object {
        /**
         * Create an embedded subtitle track (for Java interop)
         */
        @JvmStatic
        fun createEmbedded(
            id: String,
            language: String,
            languageCode: String,
            label: String?,
            mimeType: String?,
            embeddedIndex: Int,
            isSDH: Boolean = false,
            isForced: Boolean = false
        ): SubtitleTrack = SubtitleTrack(
            id = id,
            language = language,
            languageCode = languageCode,
            label = label,
            source = SubtitleSource.EMBEDDED,
            mimeType = mimeType,
            isSDH = isSDH,
            isForced = isForced,
            embeddedTrackIndex = embeddedIndex
        )
        
        /**
         * Create an online subtitle track (for Java interop)
         */
        @JvmStatic
        fun createOnline(
            id: String,
            language: String,
            languageCode: String,
            url: String,
            addonName: String?,
            mimeType: String? = null,
            matchScore: Int = 0,
            isHashMatch: Boolean = false,
            downloadCount: Int? = null
        ): SubtitleTrack = SubtitleTrack(
            id = id,
            language = language,
            languageCode = languageCode,
            source = SubtitleSource.ONLINE,
            url = url,
            addonName = addonName,
            mimeType = mimeType,
            matchScore = matchScore,
            isHashMatch = isHashMatch,
            downloadCount = downloadCount
        )
        
        /**
         * Create "Off" track
         */
        @JvmStatic
        fun createOff(): SubtitleTrack = SubtitleTrack(
            id = "off",
            language = "Off",
            languageCode = "",
            source = SubtitleSource.MANUAL
        )
    }
}

@Serializable
enum class SubtitleSource {
    EMBEDDED,   // In video file (MKV, MP4)
    ONLINE,     // From Stremio addon
    LOCAL,      // Sidecar .srt/.ass file
    MANUAL      // User uploaded/selected
}

// ============================================================================
// STREMIO ADDON MODEL
// ============================================================================

/**
 * Represents a configured Stremio subtitle addon
 */
@Serializable
data class SubtitleAddon(
    val id: String,
    val manifestUrl: String,          // Full URL to manifest.json
    val baseUrl: String,              // Extracted base URL for API calls
    val displayName: String,          // User-friendly name
    val description: String? = null,
    val version: String? = null,
    val logoUrl: String? = null,      // URL to addon logo/icon
    val isEnabled: Boolean = true,
    val priority: Int = 0,            // Query order (lower = first)
    val lastUsed: Long? = null,
    val lastError: String? = null,
    val errorCount: Int = 0,
    val supportsHash: Boolean = true,
    val supportsImdb: Boolean = true,
    val supportedTypes: List<String> = listOf("movie", "series")
)

// ============================================================================
// STREMIO API RESPONSE MODELS
// ============================================================================

/**
 * Stremio addon manifest.json structure
 */
@Serializable
data class StremioManifest(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: String,
    val resources: List<String>,      // ["subtitles", "catalog", etc.]
    val types: List<String>,          // ["movie", "series"]
    val idPrefixes: List<String>? = null,
    val catalogs: List<StremioCatalog>? = null
)

@Serializable
data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String? = null
)

/**
 * Response from /subtitles/{type}/{id}/{extraArgs}.json
 */
@Serializable
data class StremioSubtitleResponse(
    val subtitles: List<StremioSubtitle>
)

@Serializable
data class StremioSubtitle(
    val id: String,
    val url: String,
    val lang: String,
    val SubEncoding: String? = null,
    val SubFormat: String? = null,
    val SubHearingImpaired: Boolean? = null,
    val SubDownloadsCnt: Int? = null,
    val SubRating: Float? = null,
    val MovieReleaseName: String? = null,
    val Score: Float? = null
)

// ============================================================================
// SUBTITLE POSITION MODEL
// ============================================================================

/**
 * Dynamic positioning for subtitles (ASS/SSA, VTT, PGS)
 */
@Serializable
data class SubtitlePosition(
    val alignment: Int = 2,           // 1-9 numpad style (2 = bottom center)
    val x: Float? = null,             // Absolute X (0-1 normalized)
    val y: Float? = null,             // Absolute Y (0-1 normalized)
    val marginLeft: Int = 0,          // Left margin in pixels
    val marginRight: Int = 0,         // Right margin in pixels
    val marginVertical: Int = 0,      // Vertical margin in pixels
    val layer: Int = 0,               // Z-order for overlapping
    val rotation: Float = 0f,         // Rotation in degrees
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
) {
    companion object {
        // Numpad-style alignment constants
        const val ALIGN_BOTTOM_LEFT = 1
        const val ALIGN_BOTTOM_CENTER = 2
        const val ALIGN_BOTTOM_RIGHT = 3
        const val ALIGN_MIDDLE_LEFT = 4
        const val ALIGN_MIDDLE_CENTER = 5
        const val ALIGN_MIDDLE_RIGHT = 6
        const val ALIGN_TOP_LEFT = 7
        const val ALIGN_TOP_CENTER = 8
        const val ALIGN_TOP_RIGHT = 9
        
        val DEFAULT = SubtitlePosition()
        val TOP = SubtitlePosition(alignment = ALIGN_TOP_CENTER)
        val MIDDLE = SubtitlePosition(alignment = ALIGN_MIDDLE_CENTER)
        val BOTTOM = SubtitlePosition(alignment = ALIGN_BOTTOM_CENTER)
    }
}

@Serializable
enum class SafeZone {
    NONE,           // Allow anywhere
    ACTION_SAFE,    // 5% margin
    TITLE_SAFE,     // 10% margin
    CUSTOM          // User-defined
}

// ============================================================================
// SUBTITLE STYLING MODEL
// ============================================================================

/**
 * Styling options for text subtitles
 */
@Serializable
data class SubtitleStyle(
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 22f,        // in sp
    val fontColor: Int = 0xFFFFFFFF.toInt(),       // White
    val fontOpacity: Float = 1f,
    val backgroundColor: Int = 0x80000000.toInt(), // 50% black
    val backgroundOpacity: Float = 0.5f,
    val outlineColor: Int = 0xFF000000.toInt(),    // Black
    val outlineWidth: Float = 2f,
    val shadowColor: Int = 0x80000000.toInt(),
    val shadowOffset: Float = 2f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val edgeType: EdgeType = EdgeType.DROP_SHADOW
)

@Serializable
enum class EdgeType {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED,
    DEPRESSED
}

// ============================================================================
// QUERY PARAMETERS MODEL
// ============================================================================

/**
 * Parameters for querying subtitle addons
 */
data class SubtitleQueryParams(
    val type: String,                 // "movie" or "series"
    val imdbId: String?,              // tt1234567 or tt1234567:1:5 for episodes
    val videoHash: String?,           // OpenSubtitles 64-bit hash
    val videoSize: Long?,             // File size in bytes
    val filename: String?,            // Original filename
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
) {
    /**
     * Build the extraArgs query string for Stremio API
     */
    fun toExtraArgs(): String {
        val params = mutableListOf<String>()
        
        imdbId?.let { params.add("videoId=$it") }
        videoHash?.let { params.add("videoHash=$it") }
        videoSize?.let { params.add("videoSize=$it") }
        filename?.let { params.add("filename=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        
        return params.joinToString("&")
    }
    
    /**
     * Get the ID portion for the API path
     */
    fun getIdPath(): String {
        return when {
            seasonNumber != null && episodeNumber != null && imdbId != null -> 
                "$imdbId:$seasonNumber:$episodeNumber"
            imdbId != null -> imdbId
            videoHash != null -> videoHash
            else -> "unknown"
        }
    }
}

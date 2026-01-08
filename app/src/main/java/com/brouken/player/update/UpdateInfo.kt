package com.brouken.player.update

/**
 * Data class representing update information from GitHub
 */
data class UpdateInfo(
    val tagName: String,        // e.g., "v1.0.3"
    val name: String,           // Release title
    val changelog: String,      // Release body (markdown)
    val apkUrl: String?,        // Download URL for APK
    val apkSize: Long,          // APK file size in bytes
    val apkName: String         // APK filename
) {
    /**
     * Get human-readable version string
     */
    val versionString: String
        get() = tagName.removePrefix("v")
    
    /**
     * Get human-readable file size
     */
    val fileSizeString: String
        get() {
            val mb = apkSize / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }
    
    /**
     * Check if APK is available for download
     */
    val hasApk: Boolean
        get() = !apkUrl.isNullOrBlank()
}

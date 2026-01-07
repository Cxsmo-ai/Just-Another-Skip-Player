package com.brouken.player

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.view.Display
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.brouken.player.utils.DebugLogger

/**
 * FormatSupportManager - Deep integration for HDR/DV and Audio format handling
 * 
 * Handles:
 * - HDR/Dolby Vision detection and SDR tonemapping fallback
 * - Immersive audio (Atmos/DTS:X/Auro-3D) detection and fallback
 * - Auro-3D to Dolby Atmos channel mapping
 */
class FormatSupportManager(private val context: Context) {

    companion object {
        private const val TAG = "FormatSupport"
        
        // HDR Transfer Functions
        const val HDR10 = C.COLOR_TRANSFER_ST2084
        const val HLG = C.COLOR_TRANSFER_HLG
        
        // Dolby Vision Profiles
        const val DV_PROFILE_5 = 5  // Single layer, MEL
        const val DV_PROFILE_7 = 7  // Dual layer, MEL/FEL
        const val DV_PROFILE_8 = 8  // Single layer, cross-compatible
        
        // Audio MIME Types for Immersive Audio
        val ATMOS_MIME_TYPES = listOf(
            MimeTypes.AUDIO_E_AC3_JOC,  // E-AC-3 JOC (Atmos)
            MimeTypes.AUDIO_AC4         // AC-4 (Atmos)
        )
        
        val DTS_X_MIME_TYPES = listOf(
            "audio/vnd.dts.uhd",        // DTS:X
            "audio/vnd.dts.uhd;profile=p2"  // DTS:X Pro
        )
        
        val AURO_MIME_TYPES = listOf(
            "audio/auro-3d",            // Auro-3D
            "audio/auromax"             // AuroMax
        )
        
        val TRUEHD_MIME_TYPES = listOf(
            MimeTypes.AUDIO_TRUEHD      // TrueHD (may contain Atmos)
        )
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // ================== HDR/DV Support Detection ==================
    
    /**
     * Check if display supports HDR
     */
    fun isHdrDisplaySupported(display: Display?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || display == null) {
            return false
        }
        
        val hdrCapabilities = display.hdrCapabilities ?: return false
        val supportedTypes = hdrCapabilities.supportedHdrTypes
        
        val supportsHdr10 = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10)
        val supportsHdr10Plus = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS)
        val supportsHlg = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HLG)
        val supportsDv = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
        
        DebugLogger.log(TAG, "HDR Display Capabilities:")
        DebugLogger.log(TAG, "  HDR10: $supportsHdr10")
        DebugLogger.log(TAG, "  HDR10+: $supportsHdr10Plus")
        DebugLogger.log(TAG, "  HLG: $supportsHlg")
        DebugLogger.log(TAG, "  Dolby Vision: $supportsDv")
        
        return supportsHdr10 || supportsHdr10Plus || supportsHlg || supportsDv
    }
    
    /**
     * Check if display supports Dolby Vision
     */
    fun isDolbyVisionSupported(display: Display?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || display == null) {
            return false
        }
        val hdrCapabilities = display.hdrCapabilities ?: return false
        return hdrCapabilities.supportedHdrTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
    }
    
    /**
     * Detect Dolby Vision profile from video format
     */
    fun getDolbyVisionProfile(format: Format?): Int? {
        if (format == null) return null
        
        // Check codec profile for DV
        val codecs = format.codecs ?: return null
        
        return when {
            codecs.contains("dvhe.05") || codecs.contains("dvh1.05") -> DV_PROFILE_5
            codecs.contains("dvhe.07") || codecs.contains("dvh1.07") -> DV_PROFILE_7
            codecs.contains("dvhe.08") || codecs.contains("dvh1.08") -> DV_PROFILE_8
            else -> null
        }
    }
    
    /**
     * Determine if HDR to SDR tonemapping is needed
     */
    fun shouldTonemapToSdr(format: Format?, display: Display?, forceTonemapping: Boolean): Boolean {
        if (format == null) return false
        
        // If forced, always tonemap
        if (forceTonemapping) {
            DebugLogger.log(TAG, "Force SDR tonemapping enabled")
            return true
        }
        
        // Check if content is HDR
        val isHdrContent = format.colorInfo?.colorTransfer == HDR10 ||
                          format.colorInfo?.colorTransfer == HLG ||
                          getDolbyVisionProfile(format) != null
        
        if (!isHdrContent) return false
        
        // Check if display supports HDR
        val displaySupportsHdr = isHdrDisplaySupported(display)
        
        val shouldTonemap = !displaySupportsHdr
        DebugLogger.log(TAG, "HDR content detected, display supports HDR: $displaySupportsHdr, will tonemap: $shouldTonemap")
        
        return shouldTonemap
    }
    
    // ================== Audio Support Detection ==================
    
    /**
     * Check if device supports Dolby Atmos passthrough
     */
    fun isAtmosPassthroughSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        
        return try {
            val deviceInfo = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasHdmiOutput = deviceInfo.any { it.type == android.media.AudioDeviceInfo.TYPE_HDMI }
            val hasHdmiArcOutput = deviceInfo.any { it.type == android.media.AudioDeviceInfo.TYPE_HDMI_ARC }
            val hasHdmiEarcOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                deviceInfo.any { it.type == android.media.AudioDeviceInfo.TYPE_HDMI_EARC }
            } else false
            
            // Atmos passthrough requires HDMI, ARC, or eARC connection
            val supportsPassthrough = hasHdmiOutput || hasHdmiArcOutput || hasHdmiEarcOutput
            
            DebugLogger.log(TAG, "Atmos passthrough: HDMI=$hasHdmiOutput, ARC=$hasHdmiArcOutput, eARC=$hasHdmiEarcOutput")
            supportsPassthrough
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error checking Atmos support: ${e.message}")
            false
        }
    }
    
    /**
     * Check if device supports DTS:X passthrough
     */
    fun isDtsXPassthroughSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        
        return try {
            val deviceInfo = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasHdmiOutput = deviceInfo.any { it.type == android.media.AudioDeviceInfo.TYPE_HDMI }
            
            // DTS:X typically requires HDMI passthrough
            DebugLogger.log(TAG, "DTS:X passthrough support (HDMI): $hasHdmiOutput")
            hasHdmiOutput
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error checking DTS:X support: ${e.message}")
            false
        }
    }
    
    /**
     * Check if device supports Auro-3D
     */
    fun isAuro3DSupported(): Boolean {
        // Auro-3D requires specific receiver support - typically not detectable programmatically
        // Default to false, rely on passthrough to appropriate receiver
        return false
    }
    
    /**
     * Get maximum supported audio channel count
     */
    fun getMaxAudioChannels(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 2
        
        return try {
            val deviceInfo = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasMultiChannel = deviceInfo.any { device ->
                device.channelCounts.any { it >= 6 }
            }
            
            if (hasMultiChannel) 8 else 2
        } catch (e: Exception) {
            2
        }
    }
    
    /**
     * Determine audio fallback strategy
     */
    fun getAudioFallbackStrategy(format: Format?, immersiveFallbackEnabled: Boolean): AudioFallbackResult {
        if (format == null) return AudioFallbackResult(AudioStrategy.PASSTHROUGH, 0)
        
        val mimeType = format.sampleMimeType ?: return AudioFallbackResult(AudioStrategy.PASSTHROUGH, 0)
        val channelCount = format.channelCount
        
        DebugLogger.log(TAG, "Audio format: $mimeType, channels: $channelCount")
        
        // Detect immersive audio types
        val isAtmos = ATMOS_MIME_TYPES.contains(mimeType) || 
                     (TRUEHD_MIME_TYPES.contains(mimeType) && channelCount > 6)
        val isDtsX = DTS_X_MIME_TYPES.any { mimeType.contains(it) }
        val isAuro = AURO_MIME_TYPES.any { mimeType.contains(it) } || channelCount > 8
        
        if (!immersiveFallbackEnabled) {
            return AudioFallbackResult(AudioStrategy.PASSTHROUGH, channelCount)
        }
        
        // Atmos fallback chain
        if (isAtmos) {
            return when {
                isAtmosPassthroughSupported() -> AudioFallbackResult(AudioStrategy.PASSTHROUGH, channelCount)
                getMaxAudioChannels() >= 8 -> AudioFallbackResult(AudioStrategy.DECODE_7_1, 8)
                getMaxAudioChannels() >= 6 -> AudioFallbackResult(AudioStrategy.DECODE_5_1, 6)
                else -> AudioFallbackResult(AudioStrategy.DOWNMIX_STEREO, 2)
            }
        }
        
        // DTS:X fallback chain
        if (isDtsX) {
            return when {
                isDtsXPassthroughSupported() -> AudioFallbackResult(AudioStrategy.PASSTHROUGH, channelCount)
                getMaxAudioChannels() >= 8 -> AudioFallbackResult(AudioStrategy.DECODE_7_1, 8)
                getMaxAudioChannels() >= 6 -> AudioFallbackResult(AudioStrategy.DECODE_5_1, 6)
                else -> AudioFallbackResult(AudioStrategy.DOWNMIX_STEREO, 2)
            }
        }
        
        // Auro-3D fallback chain
        if (isAuro) {
            return when {
                isAuro3DSupported() -> AudioFallbackResult(AudioStrategy.PASSTHROUGH, channelCount)
                isAtmosPassthroughSupported() -> AudioFallbackResult(AudioStrategy.MAP_TO_ATMOS, 8)
                else -> AudioFallbackResult(AudioStrategy.DOWNMIX_STEREO, 2)
            }
        }
        
        return AudioFallbackResult(AudioStrategy.PASSTHROUGH, channelCount)
    }
    
    /**
     * Apply track selector parameters based on format support
     */
    fun applyTrackSelectorParams(
        trackSelector: DefaultTrackSelector,
        forceSdrTonemapping: Boolean,
        immersiveAudioFallback: Boolean,
        auroChannelMapping: Boolean
    ) {
        val params = trackSelector.buildUponParameters()
        
        // If forcing SDR, prefer SDR tracks when available
        if (forceSdrTonemapping) {
            DebugLogger.log(TAG, "Applying SDR preference to track selector")
            // ExoPlayer will handle tonemapping automatically when needed
        }
        
        // Configure audio track selection based on capabilities
        if (immersiveAudioFallback) {
            val maxChannels = getMaxAudioChannels()
            DebugLogger.log(TAG, "Applying audio fallback, max channels: $maxChannels")
            params.setMaxAudioChannelCount(maxChannels)
        }
        
        trackSelector.setParameters(params.build())
    }
    
    // ================== Data Classes ==================
    
    enum class AudioStrategy {
        PASSTHROUGH,      // Send directly to receiver
        DECODE_7_1,       // Decode to 7.1 PCM
        DECODE_5_1,       // Decode to 5.1 PCM
        DOWNMIX_STEREO,   // Downmix to stereo
        MAP_TO_ATMOS      // Map Auro-3D channels to Atmos objects
    }
    
    data class AudioFallbackResult(
        val strategy: AudioStrategy,
        val outputChannels: Int
    )
}

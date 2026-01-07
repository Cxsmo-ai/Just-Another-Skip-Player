package com.brouken.player.tonemap

import android.content.Context
import android.os.Build
import android.view.Display
import androidx.media3.common.C
import androidx.media3.common.Format
import com.brouken.player.utils.DebugLogger

/**
 * TonemapProcessor - Handles HDR/DV detection and tonemapping decisions
 * 
 * Super performant:
 * - Lazy initialization
 * - Caches capability checks
 * - Minimal per-frame overhead
 */
class TonemapProcessor(private val context: Context) {

    companion object {
        private const val TAG = "TonemapProcessor"
        
        // Transfer function constants
        const val TRANSFER_PQ = 0  // HDR10, DV
        const val TRANSFER_HLG = 1
        const val TRANSFER_SDR = 2
        
        // Color space constants
        const val COLORSPACE_BT2020 = 0
        const val COLORSPACE_BT709 = 1
    }

    // Cached display capabilities
    private var cachedHdrSupport: Boolean? = null
    private var cachedDvSupport: Boolean? = null
    
    // Current tonemapping parameters
    private var currentMaxLuminance = 1000f
    private var currentMinLuminance = 0.005f
    private var currentAvgLuminance = 200f
    private var currentTransfer = TRANSFER_SDR
    private var currentColorSpace = COLORSPACE_BT709

    /**
     * Check if tonemapping is needed for current format
     * Cached for performance
     */
    fun needsTonemapping(format: Format?, display: Display?, forceSdr: Boolean): Boolean {
        if (format == null) return false
        if (forceSdr) return isHdrContent(format)
        
        val isHdr = isHdrContent(format)
        if (!isHdr) return false
        
        // Check display HDR support (cached)
        val displaySupportsHdr = getDisplayHdrSupport(display)
        return !displaySupportsHdr
    }

    /**
     * Check if format is HDR content
     */
    fun isHdrContent(format: Format?): Boolean {
        if (format == null) return false
        
        val colorTransfer = format.colorInfo?.colorTransfer ?: return false
        
        return colorTransfer == C.COLOR_TRANSFER_ST2084 ||  // PQ (HDR10, DV)
               colorTransfer == C.COLOR_TRANSFER_HLG       // HLG
    }

    /**
     * Get display HDR support (cached for performance)
     */
    private fun getDisplayHdrSupport(display: Display?): Boolean {
        cachedHdrSupport?.let { return it }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || display == null) {
            cachedHdrSupport = false
            return false
        }
        
        val hdrCapabilities = display.hdrCapabilities
        val supports = hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        cachedHdrSupport = supports
        
        DebugLogger.log(TAG, "Display HDR support: $supports")
        return supports
    }

    /**
     * Update tonemapping parameters from format metadata
     */
    fun updateFromFormat(format: Format?) {
        if (format == null) return
        
        val colorInfo = format.colorInfo ?: return
        
        // Determine transfer function
        currentTransfer = when (colorInfo.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> TRANSFER_PQ
            C.COLOR_TRANSFER_HLG -> TRANSFER_HLG
            else -> TRANSFER_SDR
        }
        
        // Determine color space
        currentColorSpace = when (colorInfo.colorSpace) {
            C.COLOR_SPACE_BT2020 -> COLORSPACE_BT2020
            else -> COLORSPACE_BT709
        }
        
        // Extract HDR metadata if available
        colorInfo.hdrStaticInfo?.let { staticInfo ->
            // HDR10 static metadata
            // luminance values are in cd/m² (nits)
            // For now, use defaults - could parse SEI/MDCV
        }
        
        DebugLogger.log(TAG, "Format: transfer=$currentTransfer, colorSpace=$currentColorSpace")
    }

    /**
     * Update from Dolby Vision L1 metadata
     */
    fun updateFromDvMetadata(l1MinLuma: Float, l1MaxLuma: Float, l1AvgLuma: Float) {
        currentMinLuminance = l1MinLuma
        currentMaxLuminance = l1MaxLuma
        currentAvgLuminance = l1AvgLuma
        
        DebugLogger.log(TAG, "DV L1: min=$l1MinLuma, max=$l1MaxLuma, avg=$l1AvgLuma")
    }

    /**
     * Apply current parameters to shader
     */
    fun applyToShader() {
        HdrTonemapShader.setParameters(
            maxLuminance = currentMaxLuminance,
            minLuminance = currentMinLuminance,
            avgLuminance = currentAvgLuminance,
            transferFunction = currentTransfer,
            colorSpace = currentColorSpace
        )
    }

    /**
     * Get info string for debug overlay
     */
    fun getDebugInfo(): String {
        val transfer = when (currentTransfer) {
            TRANSFER_PQ -> "PQ"
            TRANSFER_HLG -> "HLG"
            else -> "SDR"
        }
        val colorspace = if (currentColorSpace == COLORSPACE_BT2020) "BT.2020" else "BT.709"
        return "HDR→SDR: $transfer/$colorspace, ${currentMaxLuminance.toInt()}nits"
    }

    /**
     * Clear cached values (call on display change)
     */
    fun invalidateCache() {
        cachedHdrSupport = null
        cachedDvSupport = null
    }

    /**
     * Release resources
     */
    fun release() {
        HdrTonemapShader.release()
        invalidateCache()
    }
}

package com.brouken.player.trakt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * Trakt Authentication Manager
 * 
 * Handles OAuth Device Code flow:
 * 1. Generate device code (shows user_code to user)
 * 2. Poll for access token while user authorizes
 * 3. Store tokens when successful
 * 4. Refresh expired tokens automatically
 */
class TraktAuthManager(
    private val context: Context,
    private val clientId: String,
    private val clientSecret: String,
    private val onTokenReceived: TokenReceivedCallback,
    private val onAuthFailed: AuthFailedCallback
) {
    companion object {
        private const val TAG = "TraktAuth"
    }

    // Java-compatible callback interfaces
    fun interface TokenReceivedCallback {
        fun onTokenReceived(tokens: TokenResponse)
    }
    
    fun interface AuthFailedCallback {
        fun onAuthFailed(error: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var pollingJob: Job? = null
    private var currentDeviceCode: String? = null
    private var pollInterval: Int = 5

    /**
     * Callback interface for auth flow UI updates
     */
    interface AuthCallback {
        fun onDeviceCodeReceived(userCode: String, verificationUrl: String)
        fun onSuccess()
        fun onExpired()
        fun onDenied()
        fun onError(message: String)
    }

    /**
     * Start the device code authentication flow
     * Returns the user code and URL to display to user
     */
    fun startDeviceFlow(callback: AuthCallback) {
        Log.d(TAG, "Starting device flow...")
        
        scope.launch {
            val codeResponse = TraktClient.generateDeviceCode(clientId)
            
            if (codeResponse != null) {
                currentDeviceCode = codeResponse.deviceCode
                pollInterval = codeResponse.interval
                
                mainHandler.post {
                    callback.onDeviceCodeReceived(
                        codeResponse.userCode,
                        codeResponse.verificationUrl
                    )
                }
                
                // Start polling
                startPolling(
                    codeResponse.deviceCode,
                    codeResponse.expiresIn,
                    codeResponse.interval,
                    callback
                )
            } else {
                mainHandler.post {
                    callback.onError("Failed to connect to Trakt")
                    showToast("Failed to connect to Trakt")
                }
            }
        }
    }

    /**
     * Poll for access token
     * Runs in background until success, expired, or denied
     */
    private fun startPolling(
        deviceCode: String,
        expiresIn: Int,
        interval: Int,
        callback: AuthCallback
    ) {
        pollingJob = scope.launch {
            val endTime = System.currentTimeMillis() + (expiresIn * 1000L)
            var currentInterval = interval * 1000L
            
            while (isActive && System.currentTimeMillis() < endTime) {
                delay(currentInterval)
                
                val result = TraktClient.pollForToken(deviceCode, clientId, clientSecret)
                
                when (result) {
                    is TokenPollResult.Success -> {
                        Log.d(TAG, "Auth successful!")
                        mainHandler.post {
                            onTokenReceived.onTokenReceived(result.tokens)
                            callback.onSuccess()
                            showToast("✓ Connected to Trakt!")
                        }
                        return@launch
                    }
                    is TokenPollResult.Pending -> {
                        // Keep polling
                        Log.d(TAG, "Still waiting for user authorization...")
                    }
                    is TokenPollResult.SlowDown -> {
                        // Increase interval by 5 seconds
                        currentInterval += 5000
                        Log.d(TAG, "Slowing down, new interval: ${currentInterval}ms")
                    }
                    is TokenPollResult.Expired -> {
                        Log.d(TAG, "Device code expired")
                        mainHandler.post {
                            callback.onExpired()
                            onAuthFailed.onAuthFailed("Code expired")
                            showToast("Code expired. Try again.")
                        }
                        return@launch
                    }
                    is TokenPollResult.Denied -> {
                        Log.d(TAG, "User denied access")
                        mainHandler.post {
                            callback.onDenied()
                            onAuthFailed.onAuthFailed("Access denied")
                            showToast("Access denied by user.")
                        }
                        return@launch
                    }
                    is TokenPollResult.InvalidCode, 
                    is TokenPollResult.AlreadyApproved -> {
                        Log.e(TAG, "Invalid or already used code")
                        mainHandler.post {
                            callback.onError("Invalid code")
                            onAuthFailed.onAuthFailed("Invalid code")
                        }
                        return@launch
                    }
                    is TokenPollResult.Error -> {
                        Log.e(TAG, "Polling error, will retry...")
                        // Continue polling on network errors
                    }
                }
            }
            
            // Timeout
            if (System.currentTimeMillis() >= endTime) {
                mainHandler.post {
                    callback.onExpired()
                    onAuthFailed.onAuthFailed("Timeout")
                    showToast("Code expired. Try again.")
                }
            }
        }
    }

    /**
     * Cancel ongoing polling
     */
    fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
        currentDeviceCode = null
    }

    /**
     * Refresh expired access token
     * Returns new tokens or null if refresh failed
     */
    suspend fun refreshTokenIfNeeded(
        currentAccessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    ): TokenResponse? {
        // Check if token is expired or will expire soon (within 1 day)
        val now = System.currentTimeMillis() / 1000
        if (now < tokenExpiry - 86400) {
            // Token still valid, no refresh needed
            return null
        }
        
        Log.d(TAG, "Token expired or expiring soon, refreshing...")
        return TraktClient.refreshToken(refreshToken, clientId, clientSecret)
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Clean up resources
     */
    fun release() {
        cancelPolling()
        scope.cancel()
    }
}

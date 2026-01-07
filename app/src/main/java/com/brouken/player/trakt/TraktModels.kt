package com.brouken.player.trakt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Device Code Response from POST /oauth/device/code
 * User displays user_code and verification_url
 */
@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int
)

/**
 * Token Response from POST /oauth/device/token or /oauth/token
 * access_token valid for ~90 days, use refresh_token to renew
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String,
    val scope: String,
    @SerialName("created_at") val createdAt: Long
)

/**
 * Scrobble Response from POST /scrobble/start, /pause, /stop
 * action = "start" | "pause" | "scrobble" (watched >80%)
 */
@Serializable
data class ScrobbleResponse(
    val id: Long = 0,
    val action: String,
    val progress: Float
)

/**
 * Playback Item from GET /sync/playback
 * Used to resume watching on different devices
 */
@Serializable
data class PlaybackItem(
    val progress: Float,
    @SerialName("paused_at") val pausedAt: String,
    val id: Int,
    val type: String
)

/**
 * Result of polling for token
 */
sealed class TokenPollResult {
    data class Success(val tokens: TokenResponse) : TokenPollResult()
    object Pending : TokenPollResult()           // 400 - Still waiting
    object InvalidCode : TokenPollResult()       // 404 - Bad device_code
    object AlreadyApproved : TokenPollResult()   // 409 - Code was already used
    object Expired : TokenPollResult()           // 410 - Code expired
    object Denied : TokenPollResult()            // 418 - User denied access
    object SlowDown : TokenPollResult()          // 429 - Polling too fast
    object Error : TokenPollResult()             // Network/unknown error
}

/**
 * Media type for scrobbling
 */
enum class TraktMediaType {
    MOVIE,
    EPISODE
}

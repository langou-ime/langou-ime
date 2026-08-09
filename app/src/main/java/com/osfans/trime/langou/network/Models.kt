package com.osfans.trime.langou.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("subject_type") val subjectType: String,
)

@Serializable
data class GuestSessionRequest(
    @SerialName("device_id") val deviceId: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class SmsSendRequest(
    val phone: String,
)

@Serializable
data class SmsSendResponse(
    val status: String,
    @SerialName("retry_after") val retryAfter: Int,
)

@Serializable
data class SmsVerifyRequest(
    val phone: String,
    val code: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class GuestMergeRequest(
    @SerialName("guest_refresh_token") val guestRefreshToken: String,
)

@Serializable
data class ClientSettings(
    val theme: String = "cream",
    @SerialName("auto_suggest") val autoSuggest: Boolean = true,
    @SerialName("save_history") val saveHistory: Boolean = true,
    val diagnostics: Boolean = false,
)

@Serializable
data class ReleaseManifest(
    val platform: String,
    val version: String,
    @SerialName("minimum_supported_version") val minimumSupportedVersion: String,
    val mandatory: Boolean,
    val url: String,
    val size: Long,
    val sha256: String,
    val signature: String,
    @SerialName("published_at") val publishedAt: String,
)

@Serializable
data class ConversationTurn(
    val role: String,
    val text: String,
)

@Serializable
data class SuggestionRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("device_id") val deviceId: String,
    val application: String,
    val locale: String,
    val turns: List<ConversationTurn>,
    val draft: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("memory_summary") val memorySummary: String? = null,
    val trigger: String = "context_change",
    @SerialName("save_history") val saveHistory: Boolean = true,
)

@Serializable
data class Suggestion(
    val index: Int,
    val style: String,
    val text: String,
)

@Serializable
data class ApiError(
    val code: String = "unknown_error",
    val message: String = "请求失败",
)

data class SseEvent(
    val event: String,
    val data: String,
)

interface LangouTransport {
    suspend fun requestJson(
        method: String,
        path: String,
        body: String? = null,
        bearerToken: String? = null,
    ): String = throw UnsupportedOperationException("$method is not supported by this transport")

    suspend fun postJson(
        path: String,
        body: String,
        bearerToken: String? = null,
    ): String = requestJson("POST", path, body, bearerToken)

    suspend fun postSse(
        path: String,
        body: String,
        bearerToken: String,
        onEvent: (SseEvent) -> Unit,
    )
}

package com.osfans.trime.langou.network

import com.osfans.trime.langou.auth.SessionApi
import com.osfans.trime.langou.privacy.ClientRedactor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LangouApiException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

class LangouApiClient(
    private val transport: LangouTransport,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
) : SessionApi {
    override suspend fun createGuestSession(
        deviceId: String,
        platform: String,
        appVersion: String,
    ): TokenPair {
        val response =
            transport.postJson(
                path = "/v1/devices/guest-session",
                body =
                    json.encodeToString(
                        GuestSessionRequest(
                            deviceId = deviceId,
                            platform = platform,
                            appVersion = appVersion,
                        ),
                    ),
            )
        return json.decodeFromString(response)
    }

    override suspend fun refreshSession(refreshToken: String): TokenPair {
        val response =
            transport.postJson(
                path = "/v1/auth/token/refresh",
                body = json.encodeToString(RefreshTokenRequest(refreshToken)),
            )
        return json.decodeFromString(response)
    }

    suspend fun sendSms(phone: String): SmsSendResponse =
        request(
            method = "POST",
            path = "/v1/auth/sms/send",
            body = json.encodeToString(SmsSendRequest(phone)),
        )

    suspend fun verifySms(
        phone: String,
        code: String,
        deviceId: String,
    ): TokenPair =
        request(
            method = "POST",
            path = "/v1/auth/sms/verify",
            body = json.encodeToString(SmsVerifyRequest(phone, code, deviceId)),
        )

    suspend fun mergeGuest(
        bearerToken: String,
        guestRefreshToken: String,
    ) {
        transport.requestJson(
            method = "POST",
            path = "/v1/auth/sms/merge",
            body = json.encodeToString(GuestMergeRequest(guestRefreshToken)),
            bearerToken = bearerToken,
        )
    }

    suspend fun getSettings(bearerToken: String): ClientSettings =
        request(
            method = "GET",
            path = "/v1/settings",
            bearerToken = bearerToken,
        )

    suspend fun putSettings(
        bearerToken: String,
        settings: ClientSettings,
    ): ClientSettings =
        request(
            method = "PUT",
            path = "/v1/settings",
            body = json.encodeToString(settings),
            bearerToken = bearerToken,
        )

    suspend fun deleteAllHistory(bearerToken: String) {
        transport.requestJson(
            method = "DELETE",
            path = "/v1/history",
            bearerToken = bearerToken,
        )
    }

    suspend fun latestRelease(platform: String): ReleaseManifest {
        require(platform == "android" || platform == "windows")
        return request(
            method = "GET",
            path = "/v1/releases/$platform/latest",
        )
    }

    suspend fun suggestions(
        bearerToken: String,
        request: SuggestionRequest,
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        streamSuggestions(bearerToken, request, suggestions::add)
        return suggestions
    }

    suspend fun streamSuggestions(
        bearerToken: String,
        request: SuggestionRequest,
        onSuggestion: (Suggestion) -> Unit,
    ) {
        val safeRequest =
            request.copy(
                turns =
                    request.turns.map { turn ->
                        turn.copy(text = ClientRedactor.redact(turn.text))
                    },
                draft = request.draft?.let(ClientRedactor::redact),
                memorySummary = request.memorySummary?.let(ClientRedactor::redact),
            )
        var suggestionCount = 0
        transport.postSse(
            path = "/v1/ai/suggestions:stream",
            body = json.encodeToString(safeRequest),
            bearerToken = bearerToken,
        ) { event ->
            when (event.event) {
                "suggestion" -> {
                    if (suggestionCount < MAX_SUGGESTIONS) {
                        suggestionCount += 1
                        onSuggestion(json.decodeFromString<Suggestion>(event.data))
                    }
                }
                "error" -> {
                    val error = json.decodeFromString<ApiError>(event.data)
                    throw LangouApiException(error.code, error.message)
                }
            }
        }
    }

    private suspend inline fun <reified T> request(
        method: String,
        path: String,
        body: String? = null,
        bearerToken: String? = null,
    ): T =
        json.decodeFromString(
            transport.requestJson(
                method = method,
                path = path,
                body = body,
                bearerToken = bearerToken,
            ),
        )

    private companion object {
        const val MAX_SUGGESTIONS = 3
    }
}

/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.network

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class LangouAccountApiClientTest :
    StringSpec({
        "uses the production SMS and guest merge contracts" {
            val transport =
                AccountRecordingTransport(
                    responses =
                        mutableListOf(
                            """{"status":"sent","retry_after":60}""",
                            """
                            {
                              "access_token":"user-access",
                              "refresh_token":"user-refresh",
                              "token_type":"bearer",
                              "expires_in":900,
                              "subject_type":"user"
                            }
                            """.trimIndent(),
                            """{"status":"merged"}""",
                        ),
                )
            val client = LangouApiClient(transport)

            client.sendSms("+8613800138000").retryAfter shouldBe 60
            val user =
                client.verifySms(
                    phone = "+8613800138000",
                    code = "123456",
                    deviceId = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
                )
            client.mergeGuest(
                bearerToken = user.accessToken,
                guestRefreshToken = "guest-refresh-token-value-that-is-long-enough",
            )

            transport.requests[0].path shouldBe "/v1/auth/sms/send"
            transport.requests[0].body shouldBe """{"phone":"+8613800138000"}"""
            transport.requests[1].path shouldBe "/v1/auth/sms/verify"
            transport.requests[1].body shouldContain """"code":"123456""""
            transport.requests[2].path shouldBe "/v1/auth/sms/merge"
            transport.requests[2].bearerToken shouldBe "user-access"
            user.subjectType shouldBe "user"
        }

        "synchronizes settings clears history and checks Android releases" {
            val transport =
                AccountRecordingTransport(
                    responses =
                        mutableListOf(
                            """
                            {
                              "theme":"moon",
                              "auto_suggest":true,
                              "save_history":false,
                              "diagnostics":false
                            }
                            """.trimIndent(),
                            """
                            {
                              "theme":"soda",
                              "auto_suggest":false,
                              "save_history":true,
                              "diagnostics":false
                            }
                            """.trimIndent(),
                            "",
                            """
                            {
                              "platform":"android",
                              "version":"1.0.1",
                              "minimum_supported_version":"1.0.0",
                              "mandatory":false,
                              "url":"https://download.langou.tech/langou-1.0.1.apk",
                              "size":42000000,
                              "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                              "signature":"signed-manifest",
                              "published_at":"2026-07-26T12:00:00Z"
                            }
                            """.trimIndent(),
                        ),
                )
            val client = LangouApiClient(transport)

            client.getSettings("access").theme shouldBe "moon"
            client
                .putSettings(
                    bearerToken = "access",
                    settings =
                        ClientSettings(
                            theme = "soda",
                            autoSuggest = false,
                            saveHistory = true,
                            diagnostics = false,
                        ),
                ).theme shouldBe "soda"
            client.deleteAllHistory("access")
            client.latestRelease("android").version shouldBe "1.0.1"

            transport.requests.map { it.method to it.path } shouldBe
                listOf(
                    "GET" to "/v1/settings",
                    "PUT" to "/v1/settings",
                    "DELETE" to "/v1/history",
                    "GET" to "/v1/releases/android/latest",
                )
            transport.requests.take(3).forEach {
                it.bearerToken shouldBe "access"
            }
        }
    })

private data class RecordedRequest(
    val method: String,
    val path: String,
    val body: String,
    val bearerToken: String?,
)

private class AccountRecordingTransport(
    private val responses: MutableList<String>,
) : LangouTransport {
    val requests = mutableListOf<RecordedRequest>()

    override suspend fun requestJson(
        method: String,
        path: String,
        body: String?,
        bearerToken: String?,
    ): String {
        requests += RecordedRequest(method, path, body.orEmpty(), bearerToken)
        return responses.removeFirst()
    }

    override suspend fun postSse(
        path: String,
        body: String,
        bearerToken: String,
        onEvent: (SseEvent) -> Unit,
    ) = Unit
}

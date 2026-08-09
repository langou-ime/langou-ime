/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.network

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class LangouApiClientTest :
    StringSpec({
        "creates guest session and delivers each SSE suggestion immediately" {
            val delivered = mutableListOf<String>()
            val deliveredCountsAfterEvents = mutableListOf<Int>()
            val transport =
                RecordingTransport(
                    jsonResponse =
                        """
                        {
                          "access_token":"access-token-value",
                          "refresh_token":"refresh-token-value",
                          "token_type":"bearer",
                          "expires_in":900,
                          "subject_type":"guest"
                        }
                        """.trimIndent(),
                    events =
                        listOf(
                            SseEvent("meta", """{"request_id":"req_12345678"}"""),
                            SseEvent("suggestion", """{"index":0,"style":"natural","text":"好呀～"}"""),
                            SseEvent("suggestion", """{"index":1,"style":"gentle","text":"当然可以呀"}"""),
                            SseEvent("suggestion", """{"index":2,"style":"boundary","text":"今天不方便哦"}"""),
                            SseEvent("memory", """{"summary":"朋友；喜欢简短回复"}"""),
                            SseEvent("done", """{"count":3}"""),
                        ),
                    afterEvent = { event ->
                        if (event.event == "suggestion") {
                            deliveredCountsAfterEvents += delivered.size
                        }
                    },
                )
            val client = LangouApiClient(transport)

            val session =
                client.createGuestSession(
                    deviceId = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
                    platform = "android",
                    appVersion = "1.0.0",
                )
            client.streamSuggestions(
                    session.accessToken,
                    SuggestionRequest(
                        requestId = "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
                        deviceId = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
                        application = "wechat",
                        locale = "zh-CN",
                        conversationId = "conv_0123456789abcdef",
                        memorySummary = "朋友；喜欢简短回复",
                        turns =
                            listOf(
                                ConversationTurn(
                                    role = "other",
                                    text = "打我电话13800138000",
                                ),
                            ),
                    ),
                ) { suggestion -> delivered += suggestion.text }

            session.subjectType shouldBe "guest"
            delivered.shouldContainExactly(
                "好呀～",
                "当然可以呀",
                "今天不方便哦",
            )
            deliveredCountsAfterEvents.shouldContainExactly(1, 2, 3)
            transport.lastBody shouldNotContain "13800138000"
            transport.lastBody shouldNotContain "screenshot"
            transport.lastBody shouldContain "朋友；喜欢简短回复"
        }
    })

private class RecordingTransport(
    private val jsonResponse: String,
    private val events: List<SseEvent>,
    private val afterEvent: (SseEvent) -> Unit = {},
) : LangouTransport {
    var lastBody: String = ""

    override suspend fun postJson(
        path: String,
        body: String,
        bearerToken: String?,
    ): String {
        del(path, bearerToken)
        lastBody = body
        return jsonResponse
    }

    override suspend fun postSse(
        path: String,
        body: String,
        bearerToken: String,
        onEvent: (SseEvent) -> Unit,
    ) {
        del(path, bearerToken)
        lastBody = body
        events.forEach { event ->
            onEvent(event)
            afterEvent(event)
        }
    }

    private fun del(vararg values: Any?) {
        values.size
    }
}

/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.osfans.trime.langou.auth.LangouSessionManager
import com.osfans.trime.langou.auth.SessionStore
import com.osfans.trime.langou.auth.StoredSession
import com.osfans.trime.langou.context.ChatContextSnapshot
import com.osfans.trime.langou.context.ChatTurn
import com.osfans.trime.langou.context.ContextSnapshotStore
import com.osfans.trime.langou.memory.IdentityConfidence
import com.osfans.trime.langou.network.LangouApiClient
import com.osfans.trime.langou.network.LangouTransport
import com.osfans.trime.langou.network.SseEvent
import com.osfans.trime.langou.network.TokenPair

/** Debug-only bridge used by on-device tests to configure the IME's own process. */
class LangouDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getStringExtra(EXTRA_COMMAND)) {
            COMMAND_INSTALL_FAKE_AI -> installFakeAi()
            COMMAND_INJECT_CONTEXT -> injectContext(context.packageName)
            COMMAND_CLEAR -> {
                ContextSnapshotStore.clear()
                LangouDebugOverrides.clear()
            }
        }
    }

    private fun installFakeAi() {
        LangouDebugOverrides.apiFactoryOverride = {
            LangouApiClient(FakeSuggestionTransport())
        }
        LangouDebugOverrides.sessionManagerOverride = { _, api ->
            LangouSessionManager(
                api = api,
                store = MemorySessionStore(StoredSession(DEVICE_ID, TOKENS, Long.MAX_VALUE)),
                deviceIdFactory = { DEVICE_ID },
                epochSeconds = { 1_000L },
                appVersion = "1.0.0",
            )
        }
    }

    private fun injectContext(packageName: String) {
        ContextSnapshotStore.update(
            ChatContextSnapshot(
                packageName = packageName,
                application = "wechat",
                conversationHint = "小夏",
                identityConfidence = IdentityConfidence.High,
                turns =
                    listOf(
                        ChatTurn("other", "周六晚上一起吃饭吗？"),
                        ChatTurn("self", "可以呀，你想吃什么？"),
                        ChatTurn("other", "火锅怎么样，我想早点订位"),
                    ),
                capturedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private class MemorySessionStore(
        private var session: StoredSession?,
    ) : SessionStore {
        override fun save(session: StoredSession) {
            this.session = session
        }

        override fun load(): StoredSession? = session

        override fun clear() {
            session = null
        }
    }

    private class FakeSuggestionTransport : LangouTransport {
        override suspend fun postSse(
            path: String,
            body: String,
            bearerToken: String,
            onEvent: (SseEvent) -> Unit,
        ) {
            require(path == "/v1/ai/suggestions:stream")
            require(body.contains("火锅怎么样"))
            require(bearerToken == ACCESS_TOKEN)
            onEvent(SseEvent("suggestion", """{"index":0,"style":"natural","text":"好呀，火锅可以呀，我早点出发～"}"""))
            onEvent(SseEvent("suggestion", """{"index":1,"style":"gentle","text":"当然可以呀，你定好位告诉我～"}"""))
            onEvent(SseEvent("suggestion", """{"index":2,"style":"boundary","text":"可以，不过我想早点确认时间。"}"""))
            onEvent(SseEvent("done", """{"count":3}"""))
        }

        override suspend fun requestJson(
            method: String,
            path: String,
            body: String?,
            bearerToken: String?,
        ): String = error("Unexpected request: $method $path")
    }

    private companion object {
        const val EXTRA_COMMAND = "command"
        const val COMMAND_INSTALL_FAKE_AI = "install_fake_ai"
        const val COMMAND_INJECT_CONTEXT = "inject_context"
        const val COMMAND_CLEAR = "clear"
        const val DEVICE_ID = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP"
        const val ACCESS_TOKEN = "guest-access-token"
        val TOKENS =
            TokenPair(
                accessToken = ACCESS_TOKEN,
                refreshToken = "guest-refresh-token",
                tokenType = "bearer",
                expiresIn = 3600,
                subjectType = "guest",
            )
    }
}

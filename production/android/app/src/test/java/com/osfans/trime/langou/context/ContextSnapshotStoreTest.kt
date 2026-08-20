/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import com.osfans.trime.langou.memory.IdentityConfidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ContextSnapshotStoreTest :
    StringSpec({
        afterTest {
            ContextCaptureState.deactivate()
            ContextSnapshotStore.clear()
        }

        "publishes accessibility and OCR snapshot changes to the active IME" {
            val snapshot =
                ChatContextSnapshot(
                    packageName = "com.tencent.mm",
                    application = "wechat",
                    conversationHint = "小夏",
                    identityConfidence = IdentityConfidence.High,
                    turns = listOf(ChatTurn("other", "晚上吃什么？")),
                    capturedAtEpochMillis = 100L,
                )

            ContextSnapshotStore.snapshots.replayCache.single() shouldBe null
            ContextSnapshotStore.update(snapshot)
            ContextSnapshotStore.snapshots.replayCache.single() shouldBe snapshot
            ContextSnapshotStore.clear()
            ContextSnapshotStore.snapshots.replayCache.single() shouldBe null
        }

        "switching chat apps clears the previous conversation immediately" {
            val snapshot =
                ChatContextSnapshot(
                    packageName = "com.tencent.mm",
                    application = "wechat",
                    turns = listOf(ChatTurn("other", "晚上吃什么？")),
                    capturedAtEpochMillis = System.currentTimeMillis(),
                )

            ContextCaptureState.activate("com.tencent.mm")
            ContextSnapshotStore.update(snapshot)
            ContextCaptureState.activate("com.tencent.mobileqq")

            ContextSnapshotStore.snapshots.replayCache.single() shouldBe null
            ContextCaptureState.isActive("com.tencent.mobileqq") shouldBe true
        }

        "requests a fresh capture when the same chat app remains active" {
            ContextCaptureState.activate("com.tencent.mm")

            runBlocking {
                val requestedPackage =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(1_000) { ContextCaptureState.captureRequests.first() }
                    }

                ContextCaptureState.requestCapture("com.tencent.mm") shouldBe true
                requestedPackage.await() shouldBe "com.tencent.mm"
                ContextCaptureState.requestCapture("com.tencent.mobileqq") shouldBe false
            }
        }
    })

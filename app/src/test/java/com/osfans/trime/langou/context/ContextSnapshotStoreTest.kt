/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import com.osfans.trime.langou.memory.IdentityConfidence
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ContextSnapshotStoreTest :
    StringSpec({
        afterTest {
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
    })

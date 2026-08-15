/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.release

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class RimeNativeLoggingContractTest :
    StringSpec({
        "first-run dictionary compilation suppresses per-entry info logging" {
            val bridge = File("src/main/jni/librime_jni/rime_jni.cc").readText()

            bridge shouldContain "trime_traits.min_log_level = 1;"
        }

        "rime is not marked ready before first-run maintenance completes" {
            val bridge = File("src/main/jni/librime_jni/rime_jni.cc").readText()
            val startup =
                bridge
                    .substringAfter("void startup(bool fullCheck")
                    .substringBefore("bool deploySchema")

            startup shouldContain "rime->start_maintenance(fullCheck);"
            startup shouldContain "rime->join_maintenance_thread();"
        }
    })

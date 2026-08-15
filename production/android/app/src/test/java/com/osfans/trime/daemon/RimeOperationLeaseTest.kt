/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.daemon

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class RimeOperationLeaseTest :
    StringSpec({
        "the final session waits for an in-flight RIME operation before stopping" {
            val leases = RimeOperationLease()

            leases.acquire()

            leases.canFinalize(hasSessions = false).shouldBeFalse()
            leases.releaseAndCanFinalize(hasSessions = false).shouldBeTrue()
        }

        "releasing an operation never stops RIME while another session remains" {
            val leases = RimeOperationLease()

            leases.acquire()

            leases.releaseAndCanFinalize(hasSessions = true).shouldBeFalse()
        }

        "all overlapping operations must finish before the dispatcher can stop" {
            val leases = RimeOperationLease()

            leases.acquire()
            leases.acquire()

            leases.releaseAndCanFinalize(hasSessions = false).shouldBeFalse()
            leases.releaseAndCanFinalize(hasSessions = false).shouldBeTrue()
        }
    })

// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

fun RimeSession.launchOnReady(block: suspend CoroutineScope.(RimeApi) -> Unit) {
    // Acquire the daemon's operation lease before returning to the caller. This closes the window
    // where a component could destroy its last session before this coroutine starts.
    lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        runOnReady { block(this) }
    }
}

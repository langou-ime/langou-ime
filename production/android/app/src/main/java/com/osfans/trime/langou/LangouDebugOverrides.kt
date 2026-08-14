/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.content.Context
import com.osfans.trime.langou.auth.LangouSessionManager
import com.osfans.trime.langou.network.LangouApiClient

object LangouDebugOverrides {
    @Volatile
    var apiFactoryOverride: (() -> LangouApiClient)? = null

    @Volatile
    var sessionManagerOverride: ((Context, LangouApiClient) -> LangouSessionManager)? = null

    fun clear() {
        apiFactoryOverride = null
        sessionManagerOverride = null
    }
}

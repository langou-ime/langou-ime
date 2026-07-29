/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou

import android.content.Context
import com.osfans.trime.BuildConfig
import com.osfans.trime.langou.auth.AndroidKeystoreCipher
import com.osfans.trime.langou.auth.LangouSessionManager
import com.osfans.trime.langou.auth.SecureSessionStore
import com.osfans.trime.langou.auth.SharedPreferencesKeyValueStore
import com.osfans.trime.langou.network.LangouApiClient
import com.osfans.trime.langou.network.UrlConnectionTransport
import java.util.UUID

object LangouClientFactory {
    fun api(): LangouApiClient =
        LangouApiClient(UrlConnectionTransport(BuildConfig.LANGOU_API_BASE_URL))

    fun sessionManager(
        context: Context,
        api: LangouApiClient,
    ): LangouSessionManager =
        LangouSessionManager(
            api = api,
            store =
                SecureSessionStore(
                    SharedPreferencesKeyValueStore(
                        context.getSharedPreferences(
                            LangouPreferences.SESSION_FILE,
                            Context.MODE_PRIVATE,
                        ),
                    ),
                    AndroidKeystoreCipher(),
                ),
            deviceIdFactory = {
                "dev_${UUID.randomUUID().toString().replace("-", "")}"
            },
            epochSeconds = { System.currentTimeMillis() / 1_000L },
            appVersion = BuildConfig.VERSION_NAME,
        )
}

/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.memory

import android.content.Context
import java.io.File

object LangouMemoryFactory {
    fun store(context: Context): EncryptedConversationStore =
        EncryptedConversationStore(
            directory = File(context.noBackupFilesDir, "langou-conversation-memory"),
            cipher = AndroidKeystoreMemoryCipher(),
        )

    fun identityResolver(): ConversationIdentityResolver =
        ConversationIdentityResolver(AndroidKeystoreConversationIdHasher())
}

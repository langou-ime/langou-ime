// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SetupViewModel : ViewModel() {
    val permissionsDone = MutableLiveData(false)
    val keyboardReady = MutableLiveData(false)

    fun canFinish(): Boolean =
        SetupCompletionGate.canFinish(
            permissionsDone = permissionsDone.value == true,
            keyboardReady = keyboardReady.value == true,
        )
}

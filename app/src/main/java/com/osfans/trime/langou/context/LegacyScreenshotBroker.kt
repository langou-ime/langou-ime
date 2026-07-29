package com.osfans.trime.langou.context

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference

interface LegacyScreenshotProvider {
    fun request(callback: (Bitmap) -> Unit)
}

object LegacyScreenshotBroker {
    private val provider = AtomicReference<LegacyScreenshotProvider?>(null)

    fun install(value: LegacyScreenshotProvider) {
        provider.set(value)
    }

    fun uninstall(value: LegacyScreenshotProvider) {
        provider.compareAndSet(value, null)
    }

    fun isAvailable(): Boolean = provider.get() != null

    fun request(callback: (Bitmap) -> Unit): Boolean {
        val active = provider.get() ?: return false
        active.request(callback)
        return true
    }
}

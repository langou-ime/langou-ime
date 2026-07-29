/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.text.InputType
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.osfans.trime.langou.ai.ChatApplicationMapper
import com.osfans.trime.langou.privacy.ContextSignals
import com.osfans.trime.langou.privacy.SensitiveContextPolicy
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class LangouAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val localOcr by lazy { LocalPaddleOcr(applicationContext) }
    private var ocrJob: Job? = null
    private var lastOcrAttemptEpochMillis = 0L
    private var destroyed = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: event?.source ?: return
        val packageName =
            root.packageName
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?: event?.packageName?.toString().orEmpty()
        val application = ChatApplicationMapper.map(packageName)
        if (application == null) {
            ContextSnapshotStore.clear()
            return
        }

        val nodes = collectVisibleText(root)
        val labels = nodes.joinToString(" ") { it.text }.take(MAX_SCREEN_LABEL_CHARACTERS)
        val signals =
            ContextSignals(
                inputType =
                    if (nodes.any(VisibleText::password)) {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    } else {
                        InputType.TYPE_CLASS_TEXT
                    },
                packageName = packageName,
                screenLabels = labels,
            )
        if (!SensitiveContextPolicy.canCollect(signals)) {
            ContextSnapshotStore.clear()
            return
        }

        val turns = ChatTextSegmenter.segment(nodes)
        if (turns.isEmpty()) {
            ContextSnapshotStore.clear()
        } else {
            ContextSnapshotStore.update(
                ChatContextSnapshot(
                    packageName = packageName,
                    application = application,
                    turns = turns,
                    capturedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
        if (turns.size < MIN_ACCESSIBILITY_TURNS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captureAndRecognize(packageName, application)
            } else {
                captureLegacyAndRecognize(packageName, application)
            }
        }
    }

    override fun onInterrupt() {
        ContextSnapshotStore.clear()
    }

    override fun onDestroy() {
        destroyed = true
        ContextSnapshotStore.clear()
        val releaseJob =
            serviceScope.launch {
                ocrJob?.cancel()
                localOcr.release()
            }
        releaseJob.invokeOnCompletion { serviceScope.cancel() }
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureAndRecognize(
        packageName: String,
        application: String,
    ) {
        if (!beginOcrAttempt()) return
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val hardwareBitmap =
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                    val bitmap =
                        hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap?.recycle()
                    buffer.close()
                    if (bitmap == null || destroyed) {
                        bitmap?.recycle()
                        return
                    }
                    recognizeBitmap(bitmap, packageName, application)
                }

                override fun onFailure(errorCode: Int) {
                    Timber.w("Accessibility screenshot unavailable: code=$errorCode")
                }
            },
        )
    }

    private fun captureLegacyAndRecognize(
        packageName: String,
        application: String,
    ) {
        if (!beginOcrAttempt()) return
        if (
            !LegacyScreenshotBroker.request { bitmap ->
                if (destroyed) {
                    bitmap.recycle()
                } else {
                    recognizeBitmap(bitmap, packageName, application)
                }
            }
        ) {
            lastOcrAttemptEpochMillis = 0L
        }
    }

    private fun beginOcrAttempt(): Boolean {
        val now = System.currentTimeMillis()
        if (
            destroyed ||
            ocrJob?.isActive == true ||
            now - lastOcrAttemptEpochMillis < OCR_THROTTLE_MILLIS
        ) {
            return false
        }
        lastOcrAttemptEpochMillis = now
        return true
    }

    private fun recognizeBitmap(
        bitmap: Bitmap,
        packageName: String,
        application: String,
    ) {
        ocrJob =
            serviceScope.launch {
                try {
                    val activePackage =
                        rootInActiveWindow?.packageName?.toString().orEmpty()
                    if (activePackage != packageName) return@launch
                    val visible =
                        OcrTextAdapter.toVisibleText(
                            localOcr.recognize(bitmap),
                            bitmap.width,
                        )
                    val turns = ChatTextSegmenter.segment(visible)
                    if (turns.isNotEmpty()) {
                        ContextSnapshotStore.update(
                            ChatContextSnapshot(
                                packageName = packageName,
                                application = application,
                                turns = turns,
                                capturedAtEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    Timber.w(
                        "Local OCR unavailable: ${failure.javaClass.simpleName}",
                    )
                } finally {
                    bitmap.recycle()
                }
            }
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): List<VisibleText> {
        val result = mutableListOf<VisibleText>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val screenWidth = resources.displayMetrics.widthPixels
        while (queue.isNotEmpty() && visited < MAX_VISITED_NODES) {
            val node = queue.removeFirst()
            visited += 1
            if (node.isVisibleToUser) {
                val value =
                    node.text
                        ?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?: node.contentDescription?.toString().orEmpty()
                if (value.isNotBlank()) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    result +=
                        VisibleText(
                            text = value,
                            centerX = bounds.centerX(),
                            screenWidth = screenWidth,
                            editable = node.isEditable || node.isFocused,
                            password = node.isPassword,
                        )
                }
                repeat(node.childCount) { index ->
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }
        return result
    }

    private companion object {
        const val MAX_VISITED_NODES = 400
        const val MAX_SCREEN_LABEL_CHARACTERS = 4_000
        const val MIN_ACCESSIBILITY_TURNS = 2
        const val OCR_THROTTLE_MILLIS = 2_000L
    }
}

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class LangouAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val localOcr by lazy { LocalPaddleOcr(applicationContext) }
    private var ocrJob: Job? = null
    private var lastOcrAttemptEpochMillis = 0L
    private var lastAccessibilityCaptureEpochMillis = 0L
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch(Dispatchers.Default) {
            runCatching { localOcr.prepare() }
                .onFailure { failure ->
                    Timber.w("Local OCR warm-up unavailable: ${failure.javaClass.simpleName}")
                }
        }
        serviceScope.launch {
            ContextCaptureState.activePackages.collect { packageName ->
                if (packageName == null) {
                    ContextSnapshotStore.clear()
                } else {
                    captureCurrentContext(packageName)
                }
            }
        }
        serviceScope.launch {
            ContextCaptureState.captureRequests.collect(::captureCurrentContext)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shouldHandleEvent(event)) return
        val packageName = ContextCaptureState.activePackages.value ?: return
        // The IME creates its own accessibility window. Ignore those events and keep the chat
        // snapshot instead of clearing it as soon as the keyboard becomes visible.
        if (!eventBelongsToActiveApp(event.packageName, packageName)) return
        val root = findChatRoot(packageName, event.source) ?: return
        if (!beginAccessibilityCapture()) return
        captureVisibleContext(root, packageName)
    }

    private fun shouldHandleEvent(event: AccessibilityEvent): Boolean =
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            -> true
            else -> false
        }

    private fun beginAccessibilityCapture(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAccessibilityCaptureEpochMillis < ACCESSIBILITY_CAPTURE_THROTTLE_MILLIS) {
            return false
        }
        lastAccessibilityCaptureEpochMillis = now
        return true
    }

    private fun captureVisibleContext(
        root: AccessibilityNodeInfo,
        targetPackageName: String,
    ) {
        val packageName =
            root.packageName
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?: targetPackageName
        if (packageName != targetPackageName) return
        val application = ChatApplicationMapper.map(packageName)
        if (application == null) {
            Timber.i("Langou context cleared reason=unsupported_app package=%s", packageName)
            ContextSnapshotStore.clear()
            return
        }
        if (!ContextCaptureState.isActive(packageName)) {
            Timber.i("Langou context cleared reason=inactive package=%s", packageName)
            ContextSnapshotStore.clear()
            return
        }

        val nodes = collectVisibleText(root)
        val screenHeight = resources.displayMetrics.heightPixels
        val conversationHint =
            ConversationHintResolver.resolve(
                items = nodes,
                screenHeight = screenHeight,
            )
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
            Timber.i("Langou context cleared reason=sensitive package=%s", packageName)
            ContextSnapshotStore.clear()
            return
        }

        val turns =
            ChatTextSegmenter.segment(
                nodes.filter { item ->
                    item.centerY >= screenHeight * CHAT_CONTENT_START_RATIO
                },
            )
        if (turns.isEmpty()) {
            val previous = ContextSnapshotStore.get(packageName)
            if (previous == null) {
                Timber.i("Langou context cleared reason=no_turns package=%s", packageName)
                ContextSnapshotStore.clear()
            } else {
                Timber.i(
                    "Langou context retained reason=no_turns_keep_previous package=%s previous_turns=%s",
                    packageName,
                    previous.turns.size,
                )
            }
        } else {
            Timber.i(
                "Langou context snapshot source=accessibility package=%s turns=%s hint=%s",
                packageName,
                turns.size,
                conversationHint.text != null,
            )
            ContextSnapshotStore.update(
                ChatContextSnapshot(
                    packageName = packageName,
                    application = application,
                    conversationHint = conversationHint.text,
                    identityConfidence = conversationHint.confidence,
                    turns = turns,
                    capturedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
        if (turns.size < MIN_ACCESSIBILITY_TURNS) {
            val contentBounds = Rect().also(root::getBoundsInScreen)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captureAndRecognize(
                    packageName,
                    application,
                    conversationHint,
                    contentBounds,
                )
            } else {
                captureLegacyAndRecognize(
                    packageName,
                    application,
                    conversationHint,
                    contentBounds,
                )
            }
        }
    }

    private fun findChatRoot(
        packageName: String,
        fallback: AccessibilityNodeInfo? = null,
    ): AccessibilityNodeInfo? {
        fun AccessibilityNodeInfo.belongsToTarget() =
            this.packageName?.toString() == packageName

        rootInActiveWindow?.takeIf { it.belongsToTarget() }?.let { return it }
        windows
            .asSequence()
            .mapNotNull { it.root }
            .firstOrNull { it.belongsToTarget() }
            ?.let { return it }
        return fallback?.takeIf { it.belongsToTarget() }
    }

    private fun captureCurrentContext(packageName: String) {
        if (!ContextCaptureState.isActive(packageName)) return
        findChatRoot(packageName)?.let { root ->
            captureVisibleContext(root, packageName)
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
        conversationHint: ConversationHint,
        contentBounds: Rect,
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
                    recognizeBitmap(
                        cropToContentWindow(bitmap, contentBounds),
                        packageName,
                        application,
                        conversationHint,
                    )
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
        conversationHint: ConversationHint,
        contentBounds: Rect,
    ) {
        if (!beginOcrAttempt()) return
        if (
            !LegacyScreenshotBroker.request { bitmap ->
                if (destroyed) {
                    bitmap.recycle()
                } else {
                    recognizeBitmap(
                        cropToContentWindow(bitmap, contentBounds),
                        packageName,
                        application,
                        conversationHint,
                    )
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

    private fun cropToContentWindow(
        bitmap: Bitmap,
        bounds: Rect,
    ): Bitmap {
        val left = bounds.left.coerceIn(0, bitmap.width)
        val top = bounds.top.coerceIn(0, bitmap.height)
        val right = bounds.right.coerceIn(left, bitmap.width)
        val bottom = bounds.bottom.coerceIn(top, bitmap.height)
        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) {
            return bitmap
        }
        if (right <= left || bottom <= top) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    private fun recognizeBitmap(
        bitmap: Bitmap,
        packageName: String,
        application: String,
        conversationHint: ConversationHint,
    ) {
        ocrJob =
            serviceScope.launch {
                try {
                    if (
                        !ContextCaptureState.isActive(packageName) ||
                        findChatRoot(packageName) == null
                    ) {
                        return@launch
                    }
                    val visible =
                        OcrTextAdapter.toVisibleText(
                            withContext(Dispatchers.Default) {
                                localOcr.recognize(bitmap)
                            },
                            bitmap.width,
                        )
                    if (!ContextCaptureState.isActive(packageName)) return@launch
                    val turns = ChatTextSegmenter.segment(visible)
                    if (turns.isNotEmpty()) {
                        Timber.i(
                            "Langou context snapshot source=ocr package=%s turns=%s hint=%s",
                            packageName,
                            turns.size,
                            conversationHint.text != null,
                        )
                        ContextSnapshotStore.update(
                            ChatContextSnapshot(
                                packageName = packageName,
                                application = application,
                                conversationHint = conversationHint.text,
                                identityConfidence = conversationHint.confidence,
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
                            centerY = bounds.centerY(),
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
        const val ACCESSIBILITY_CAPTURE_THROTTLE_MILLIS = 120L
        const val OCR_THROTTLE_MILLIS = 8_000L
        const val CHAT_CONTENT_START_RATIO = 0.18
    }
}

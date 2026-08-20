/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.context

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPaddleOcrSmokeTest {
    @Test
    fun loadsNativeRuntimeAndRunsOneRecognitionOnDevice() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bitmap = Bitmap.createBitmap(640, 160, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawText(
                    "Langou 123",
                    24f,
                    104f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 72f
                    },
                )
            }
            val ocr = LocalPaddleOcr(context)
            try {
                ocr.prepare()
                assertNotNull(ocr.recognize(bitmap))
            } finally {
                ocr.release()
                bitmap.recycle()
            }
        }
}

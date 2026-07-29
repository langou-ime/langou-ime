package com.osfans.trime.langou.context

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalPaddleOcr(
    private val context: Context,
) {
    private val mutex = Mutex()
    private var engine: PaddleOCR? = null

    suspend fun recognize(bitmap: Bitmap): List<OcrLine> =
        mutex.withLock {
            val ocr =
                engine ?: run {
                    check(OpenCVUtils.init(context)) {
                        "OpenCV initialization failed"
                    }
                    PaddleOCR.create(context).also { engine = it }
                }
            ocr.recognize(bitmap).results.map { result ->
                OcrLine(
                    text = result.text,
                    confidence = result.confidence,
                    centerX =
                        result.box.points
                            .map { it.x }
                            .average()
                            .toInt(),
                )
            }
        }

    suspend fun release() {
        mutex.withLock {
            engine?.release()
            engine = null
        }
    }
}

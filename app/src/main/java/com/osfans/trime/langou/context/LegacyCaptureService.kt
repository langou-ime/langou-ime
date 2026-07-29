package com.osfans.trime.langou.context

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.osfans.trime.R
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.util.createNotificationChannel
import java.util.concurrent.atomic.AtomicReference

class LegacyCaptureService :
    Service(),
    LegacyScreenshotProvider {
    private val captureThread = HandlerThread("LangouLegacyCapture")
    private lateinit var captureHandler: Handler
    private val pendingCallback = AtomicReference<((Bitmap) -> Unit)?>(null)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }

    override fun onCreate() {
        super.onCreate()
        captureThread.start()
        captureHandler = Handler(captureThread.looper)
        createNotificationChannel(
            CHANNEL_ID,
            getString(R.string.langou_capture_channel),
        )
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (projection != null) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val resultData =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_RESULT_DATA)
            }
        if (resultCode == null || resultCode == Int.MIN_VALUE || resultData == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val createdProjection = manager.getMediaProjection(resultCode, resultData)
        if (createdProjection == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        createdProjection.registerCallback(projectionCallback, captureHandler)
        projection = createdProjection
        createVirtualDisplay()
        LegacyScreenshotBroker.install(this)
        return START_NOT_STICKY
    }

    override fun request(callback: (Bitmap) -> Unit) {
        pendingCallback.set(callback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LegacyScreenshotBroker.uninstall(this)
        pendingCallback.set(null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
        captureThread.quitSafely()
        super.onDestroy()
    }

    private fun createVirtualDisplay() {
        val metrics = resources.displayMetrics
        imageReader =
            ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2,
            ).apply {
                setOnImageAvailableListener(
                    { reader ->
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            val callback = pendingCallback.getAndSet(null)
                                ?: return@setOnImageAvailableListener
                            val plane = image.planes.first()
                            val rowPadding =
                                plane.rowStride - plane.pixelStride * image.width
                            val padded =
                                Bitmap.createBitmap(
                                    image.width + rowPadding / plane.pixelStride,
                                    image.height,
                                    Bitmap.Config.ARGB_8888,
                                )
                            padded.copyPixelsFromBuffer(plane.buffer)
                            val screenshot =
                                Bitmap.createBitmap(
                                    padded,
                                    0,
                                    0,
                                    image.width,
                                    image.height,
                                )
                            if (screenshot !== padded) padded.recycle()
                            runCatching { callback(screenshot) }
                                .onFailure { screenshot.recycle() }
                        } finally {
                            image.close()
                        }
                    },
                    captureHandler,
                )
            }
        virtualDisplay =
            projection?.createVirtualDisplay(
                "LangouLegacyCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                captureHandler,
            )
    }

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trime_status)
            .setContentTitle(getString(R.string.langou_capture_notification_title))
            .setContentText(getString(R.string.langou_capture_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()

    companion object {
        const val EXTRA_RESULT_CODE = "tech.langou.ime.capture.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "tech.langou.ime.capture.RESULT_DATA"
        private const val CHANNEL_ID = "langou_legacy_capture"
        private const val NOTIFICATION_ID = 20_260_726
    }
}

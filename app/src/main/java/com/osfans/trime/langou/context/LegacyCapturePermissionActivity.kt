package com.osfans.trime.langou.context

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat

class LegacyCapturePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            finish()
            return
        }
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            CAPTURE_REQUEST_CODE,
        )
    }

    @Deprecated("MediaProjection is only used on Android 8–10")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAPTURE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, LegacyCaptureService::class.java).apply {
                    putExtra(LegacyCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(LegacyCaptureService.EXTRA_RESULT_DATA, data)
                },
            )
        }
        finish()
    }

    private companion object {
        const val CAPTURE_REQUEST_CODE = 20_260_726
    }
}

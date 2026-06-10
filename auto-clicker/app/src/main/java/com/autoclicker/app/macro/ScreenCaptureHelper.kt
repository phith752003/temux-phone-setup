package com.autoclicker.app.macro

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Helper class to manage Android MediaProjection screen capture.
 * Provides real-time screen Bitmaps for image recognition.
 */
object ScreenCaptureHelper {
    private const val TAG = "ScreenCaptureHelper"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var width = 0
    private var height = 0
    private var dpi = 0

    // Store the projection intent data globally so we can start projection in the Foreground Service
    var projectionResultCode: Int = 0
    var projectionIntentData: Intent? = null

    val isAuthorized: Boolean
        get() = projectionIntentData != null

    /**
     * Start the screen projection. Must be called after Foreground Service is started.
     */
    @SuppressLint("WrongConstant")
    fun startProjection(context: Context): Boolean {
        val data = projectionIntentData ?: return false
        val resultCode = projectionResultCode

        if (mediaProjection != null) return true // already running

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val projection = projectionManager.getMediaProjection(resultCode, data) ?: return false
            mediaProjection = projection

            val metrics = context.resources.displayMetrics
            width = metrics.widthPixels
            height = metrics.heightPixels
            dpi = metrics.densityDpi

            // Create ImageReader
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            // Create VirtualDisplay
            virtualDisplay = projection.createVirtualDisplay(
                "AutoClickerCapture",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null, null
            )

            Log.i(TAG, "Screen projection started: ${width}x${height} @ ${dpi}dpi")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media projection: ${e.message}", e)
            stopProjection()
            return false
        }
    }

    /**
     * Stop the projection and release resources.
     */
    fun stopProjection() {
        Log.i(TAG, "Stopping screen projection")
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    /**
     * Captures a screenshot of the current screen frame as a Bitmap.
     * Running on Dispatchers.Default.
     */
    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.Default) {
        val reader = imageReader ?: return@withContext null
        var image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        }

        // Retry if image is not ready yet (e.g. at startup)
        var retries = 5
        while (image == null && retries > 0) {
            delay(40)
            image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            retries--
        }

        if (image == null) {
            Log.w(TAG, "Failed to acquire image from reader")
            return@withContext null
        }

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            // Create temporary bitmap
            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop out padding if it exists
            val finalBitmap = if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
                    if (it != bitmap) {
                        bitmap.recycle()
                    }
                }
            } else {
                bitmap
            }
            finalBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}", e)
            null
        } finally {
            image.close()
        }
    }
}

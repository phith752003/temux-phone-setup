package com.autoclicker.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Accessibility Service that performs gestures (tap/swipe) via dispatchGesture.
 * 
 * Security notes:
 * - canRetrieveWindowContent = false (does not read screen content)
 * - Does not log any sensitive UI data
 * - Only performs gestures, never reads content
 */
class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "GestureService"

        /**
         * Singleton reference to the running service instance.
         * Null when service is not active.
         */
        @Volatile
        var instance: AutoClickerService? = null
            private set

        /**
         * Check if the accessibility service is currently enabled.
         */
        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't process accessibility events - we only use gesture dispatch
        // canRetrieveWindowContent is false in config
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility Service destroyed")
    }

    /**
     * Perform a tap gesture at the given coordinates.
     * Suspends until the gesture completes or is cancelled.
     * 
     * @param x X coordinate on screen
     * @param y Y coordinate on screen
     * @param durationMs Duration of the tap gesture in ms (default 100)
     * @return true if gesture completed, false if cancelled
     */
    suspend fun performTap(x: Float, y: Float, durationMs: Long = 100): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0,  // startTime
            durationMs.coerceAtLeast(1)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGestureAndWait(gesture)
    }

    /**
     * Perform a swipe gesture from (x1,y1) to (x2,y2).
     * Suspends until the gesture completes or is cancelled.
     * 
     * @param x1 Start X
     * @param y1 Start Y
     * @param x2 End X
     * @param y2 End Y
     * @param durationMs Duration of the swipe in ms
     * @return true if gesture completed, false if cancelled
     */
    suspend fun performSwipe(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        durationMs: Long = 300
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            durationMs.coerceAtLeast(1)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGestureAndWait(gesture)
    }

    /**
     * Dispatches a gesture and waits for completion callback.
     * This ensures we wait for onCompleted or onCancelled before proceeding.
     */
    private suspend fun dispatchGestureAndWait(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { cont ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) {
                        cont.resume(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Gesture cancelled")
                    if (cont.isActive) {
                        cont.resume(false)
                    }
                }
            }

            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                Log.e(TAG, "Failed to dispatch gesture")
                if (cont.isActive) {
                    cont.resume(false)
                }
            }
        }
    }
}

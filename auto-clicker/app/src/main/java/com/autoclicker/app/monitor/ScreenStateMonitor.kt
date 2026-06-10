package com.autoclicker.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors screen on/off state.
 * When screen turns off, requests macro pause.
 * When screen turns on, does NOT auto-resume (user must manually resume).
 */
class ScreenStateMonitor(private val context: Context) {

    companion object {
        private const val TAG = "ScreenMonitor"
    }

    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    private val _pausedByScreenOff = MutableStateFlow(false)
    val pausedByScreenOff: StateFlow<Boolean> = _pausedByScreenOff.asStateFlow()

    var onScreenOff: (() -> Unit)? = null

    private var receiver: BroadcastReceiver? = null

    /**
     * Start monitoring screen state.
     */
    fun start() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.i(TAG, "Screen turned OFF")
                        _isScreenOn.value = false
                        _pausedByScreenOff.value = true
                        onScreenOff?.invoke()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.i(TAG, "Screen turned ON (user must manually resume)")
                        _isScreenOn.value = true
                        // Do NOT auto-resume - user must press Resume button
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(receiver, filter)
        Log.i(TAG, "Screen state monitor started")
    }

    /**
     * Stop monitoring.
     */
    fun stop() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister screen receiver: ${e.message}")
            }
        }
        receiver = null
        Log.i(TAG, "Screen state monitor stopped")
    }

    /**
     * Reset the paused-by-screen-off flag (called when user manually resumes).
     */
    fun clearScreenOffPause() {
        _pausedByScreenOff.value = false
    }
}

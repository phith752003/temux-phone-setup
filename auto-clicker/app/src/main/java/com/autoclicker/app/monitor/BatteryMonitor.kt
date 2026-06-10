package com.autoclicker.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors battery level and charging status.
 * Triggers pause when battery is below 20% and not charging.
 */
class BatteryMonitor(private val context: Context) {

    companion object {
        private const val TAG = "BatteryMonitor"
        private const val LOW_BATTERY_THRESHOLD = 20
    }

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _shouldPause = MutableStateFlow(false)
    val shouldPause: StateFlow<Boolean> = _shouldPause.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    /**
     * Start monitoring battery status.
     */
    fun start() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                if (level >= 0 && scale > 0) {
                    _batteryLevel.value = (level * 100) / scale
                }

                _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val shouldPauseNow = _batteryLevel.value < LOW_BATTERY_THRESHOLD && !_isCharging.value
                if (shouldPauseNow != _shouldPause.value) {
                    _shouldPause.value = shouldPauseNow
                    if (shouldPauseNow) {
                        Log.w(TAG, "Low battery: ${_batteryLevel.value}%, not charging. Requesting pause.")
                    }
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.i(TAG, "Battery monitor started")
    }

    /**
     * Stop monitoring.
     */
    fun stop() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister battery receiver: ${e.message}")
            }
        }
        receiver = null
        Log.i(TAG, "Battery monitor stopped")
    }

    /**
     * Get a display string for battery status.
     */
    fun getStatusString(): String {
        val charging = if (_isCharging.value) " ⚡" else ""
        return "${_batteryLevel.value}%$charging"
    }
}

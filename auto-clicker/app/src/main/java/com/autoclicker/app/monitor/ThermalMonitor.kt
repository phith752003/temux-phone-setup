package com.autoclicker.app.monitor

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device thermal status.
 * Auto-pauses macro when thermal status is SEVERE or CRITICAL.
 * 
 * Note: ThermalStatusListener requires SDK 29+ (Android 10).
 * On older devices, thermal monitoring is safely skipped.
 */
class ThermalMonitor(private val context: Context) {

    companion object {
        private const val TAG = "ThermalMonitor"
    }

    private val _thermalStatus = MutableStateFlow(0) // THERMAL_STATUS_NONE
    val thermalStatus: StateFlow<Int> = _thermalStatus.asStateFlow()

    private val _shouldPause = MutableStateFlow(false)
    val shouldPause: StateFlow<Boolean> = _shouldPause.asStateFlow()

    private var isRegistered = false

    /**
     * Start monitoring thermal status.
     * Safely handles devices that don't support thermal monitoring.
     */
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.i(TAG, "Thermal monitoring not available on SDK ${Build.VERSION.SDK_INT} (requires 29+)")
            return
        }

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager == null) {
                Log.w(TAG, "PowerManager not available")
                return
            }

            powerManager.addThermalStatusListener { status ->
                _thermalStatus.value = status
                val isSevere = status >= PowerManager.THERMAL_STATUS_SEVERE
                if (isSevere != _shouldPause.value) {
                    _shouldPause.value = isSevere
                    if (isSevere) {
                        Log.w(TAG, "Thermal status SEVERE/CRITICAL ($status). Requesting pause.")
                    } else {
                        Log.i(TAG, "Thermal status normalized ($status)")
                    }
                }
            }
            isRegistered = true
            Log.i(TAG, "Thermal monitor started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register thermal listener: ${e.message}. Thermal monitoring disabled.")
        }
    }

    /**
     * Stop monitoring.
     */
    fun stop() {
        // ThermalStatusListener is automatically cleaned up when the context is destroyed
        isRegistered = false
        Log.i(TAG, "Thermal monitor stopped")
    }

    /**
     * Get a display string for thermal status.
     */
    fun getStatusString(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "N/A (SDK < 29)"
        }
        return when (_thermalStatus.value) {
            0 -> "None"
            1 -> "Light"
            2 -> "Moderate"
            3 -> "Severe ⚠"
            4 -> "Critical 🔥"
            5 -> "Emergency 🔥🔥"
            6 -> "Shutdown 💀"
            else -> "Unknown"
        }
    }
}

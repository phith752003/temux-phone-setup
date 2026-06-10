package com.autoclicker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives stop action from the foreground notification.
 */
class StopActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StopReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == MacroForegroundService.ACTION_STOP) {
            Log.i(TAG, "Stop action received from notification")
            context?.let {
                OverlayService.stop(it)
                MacroForegroundService.stop(it)
            }
        }
    }
}

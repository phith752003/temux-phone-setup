package com.autoclicker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autoclicker.app.MainActivity
import com.autoclicker.app.R
import com.autoclicker.app.macro.ScreenCaptureHelper

/**
 * Foreground service that runs while macro is executing.
 * Shows notification with current status and a Stop button.
 * 
 * Design principles:
 * - Only runs when macro is RUNNING
 * - stopForeground + stopSelf when stopped
 * - No wake lock
 * - Notification updates max 1/second
 */
class MacroForegroundService : Service() {

    companion object {
        private const val TAG = "ForegroundSvc"
        private const val CHANNEL_ID = "macro_runner_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.autoclicker.app.ACTION_STOP_MACRO"

        private const val EXTRA_PROFILE_NAME = "profile_name"
        private const val EXTRA_LOOP = "loop"
        private const val EXTRA_ACTION = "action"

        fun start(context: Context, profileName: String) {
            val intent = Intent(context, MacroForegroundService::class.java).apply {
                putExtra(EXTRA_PROFILE_NAME, profileName)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MacroForegroundService::class.java))
        }

        fun updateNotification(context: Context, profileName: String, loop: Int, actionIndex: Int) {
            val intent = Intent(context, MacroForegroundService::class.java).apply {
                putExtra(EXTRA_PROFILE_NAME, profileName)
                putExtra(EXTRA_LOOP, loop)
                putExtra(EXTRA_ACTION, actionIndex)
            }
            context.startForegroundService(intent)
        }
    }

    private var lastUpdateTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME) ?: "Macro"
        val loop = intent?.getIntExtra(EXTRA_LOOP, 0) ?: 0
        val actionIndex = intent?.getIntExtra(EXTRA_ACTION, 0) ?: 0

        // Throttle notification updates to max 1/second
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 1000 && lastUpdateTime > 0) {
            return START_STICKY
        }
        lastUpdateTime = now

        // Start screen capture if authorized
        if (ScreenCaptureHelper.isAuthorized) {
            ScreenCaptureHelper.startProjection(this)
        }

        val notification = buildNotification(profileName, loop, actionIndex)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenCaptureHelper.stopProjection()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Foreground service destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW  // Low importance = no sound/vibrate
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(profileName: String, loop: Int, actionIndex: Int): Notification {
        // Open app intent
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop action intent
        val stopIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("▶ $profileName")
            .setContentText("Loop $loop • Action ${actionIndex + 1}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_stop),
                stopIntent
            )
            .build()
    }
}

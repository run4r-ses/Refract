package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DecodingForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "refract_decoding_channel"
        const val ACTION_CANCEL_DECODING = "com.example.refract.CANCEL_DECODING"
        const val ACTION_CANCEL_EVENT_TO_VM = "com.example.refract.CANCEL_DECODING_EVENT"

        fun updateProgress(context: Context, progress: Int, status: String) {
            val intent = Intent(context, DecodingForegroundService::class.java).apply {
                action = "ACTION_UPDATE_PROGRESS"
                putExtra("extra_progress", progress)
                putExtra("extra_status", status)
            }
            context.startService(intent)
        }

        fun completeNotification(context: Context, fileName: String, success: Boolean) {
            val intent = Intent(context, DecodingForegroundService::class.java).apply {
                action = "ACTION_COMPLETE"
                putExtra("extra_file_name", fileName)
                putExtra("extra_success", success)
            }
            context.startService(intent)
        }

        fun cancelNotification(context: Context) {
            val intent = Intent(context, DecodingForegroundService::class.java).apply {
                action = "ACTION_CANCEL"
            }
            context.startService(intent)
        }
    }

    private var currentFileName: String = ""
    private var isCompleted = false

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CANCEL_DECODING) {
                // Broadcast back to VM
                val toVmIntent = Intent(ACTION_CANCEL_EVENT_TO_VM).setPackage(packageName)
                sendBroadcast(toVmIntent)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, IntentFilter(ACTION_CANCEL_DECODING), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(cancelReceiver, IntentFilter(ACTION_CANCEL_DECODING))
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(cancelReceiver)
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            "ACTION_UPDATE_PROGRESS" -> {
                val progress = intent.getIntExtra("extra_progress", 0)
                val status = intent.getStringExtra("extra_status") ?: ""
                handleProgressUpdate(progress, status)
            }
            "ACTION_COMPLETE" -> {
                val fileName = intent.getStringExtra("extra_file_name") ?: currentFileName
                val success = intent.getBooleanExtra("extra_success", true)
                handleComplete(fileName, success)
            }
            "ACTION_CANCEL" -> {
                handleCancel()
            }
            else -> {
                val fileName = intent.getStringExtra("extra_file_name") ?: "audio"
                currentFileName = fileName
                isCompleted = false
                handleStart(fileName)
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Decoding Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time audio decoding and extraction progress"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getMainActivityPendingIntent(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(ACTION_CANCEL_DECODING).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun handleStart(fileName: String) {
        val truncatedName = fileName.take(24) + if (fileName.length > 24) "..." else ""
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(fileName)
            .setContentText("$truncatedName · Preparing decoder...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setProgress(100, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(getMainActivityPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                getCancelPendingIntent()
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun handleProgressUpdate(progress: Int, status: String) {
        if (isCompleted) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentFileName)
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setProgress(100, progress.coerceIn(0, 99), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(getMainActivityPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                getCancelPendingIntent()
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun handleComplete(fileName: String, success: Boolean) {
        isCompleted = true
        val truncatedName = fileName.take(24) + if (fileName.length > 24) "..." else ""
        val text = if (success) "$truncatedName · Export complete" else "$truncatedName · Export failed"
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(fileName)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(getMainActivityPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        stopSelf()
    }

    private fun handleCancel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}

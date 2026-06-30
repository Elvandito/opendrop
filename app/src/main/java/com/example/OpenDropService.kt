package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class OpenDropService : Service() {
    private lateinit var transfer: NetworkTransfer

    override fun onCreate() {
        super.onCreate()
        transfer = NetworkTransfer.getInstance(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        System.gc()
        java.lang.Runtime.getRuntime().gc()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createForegroundNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            var serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                serviceType = serviceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(1003, notification, serviceType)
            } catch (e: Exception) {
                startForeground(1003, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
        } else {
            startForeground(1003, notification)
        }

        // Make sure NetworkTransfer is running
        transfer.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val prefs = getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val discoverable = prefs.getBoolean("discoverable", true)
        if (discoverable) {
            val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
                setPackage(packageName)
            }
            val restartServicePendingIntent = android.app.PendingIntent.getService(
                this, 1, restartServiceIntent, android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmService = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmService.set(
                android.app.AlarmManager.RTC,
                System.currentTimeMillis() + 1000,
                restartServicePendingIntent
            )
        }
    }

    override fun onDestroy() {
        transfer.stop()
        super.onDestroy()
        val prefs = getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val discoverable = prefs.getBoolean("discoverable", true)
        if (discoverable) {
            val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
                setPackage(packageName)
            }
            val restartServicePendingIntent = android.app.PendingIntent.getService(
                this, 1, restartServiceIntent, android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmService = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmService.set(
                android.app.AlarmManager.RTC,
                System.currentTimeMillis() + 1000,
                restartServicePendingIntent
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "OpenDrop Service"
            val descriptionText = "Keeps OpenDrop running in the background for peer-to-peer file sharing."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("opendrop_service_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "opendrop_service_channel")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("OpenDrop is Active")
            .setContentText("Ready to share files nearby")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}

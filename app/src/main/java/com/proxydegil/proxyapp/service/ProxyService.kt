package com.proxydegil.proxyapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.proxydegil.proxyapp.MainActivity
import com.proxydegil.proxyapp.data.ProxyProfile
import com.proxydegil.proxyapp.utils.ProxyManager

class ProxyService : Service() {

    companion object {
        const val CHANNEL_ID = "ProxyServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val EXTRA_ID = "EXTRA_ID"
        private const val EXTRA_NAME = "EXTRA_NAME"
        private const val EXTRA_HOST = "EXTRA_HOST"
        private const val EXTRA_PORT = "EXTRA_PORT"
        private const val EXTRA_IS_HTTP = "EXTRA_IS_HTTP"
        private const val EXTRA_PACKAGES = "EXTRA_PACKAGES"

        fun startService(context: Context, profile: ProxyProfile) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, profile.id)
                putExtra(EXTRA_NAME, profile.name)
                putExtra(EXTRA_HOST, profile.host)
                putExtra(EXTRA_PORT, profile.port)
                putExtra(EXTRA_IS_HTTP, profile.isHttp)
                putExtra(EXTRA_PACKAGES, profile.targetPackages)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val profile = ProxyProfile(
                    id = intent.getIntExtra(EXTRA_ID, 0),
                    name = intent.getStringExtra(EXTRA_NAME) ?: "Proxy",
                    host = intent.getStringExtra(EXTRA_HOST) ?: "",
                    port = intent.getIntExtra(EXTRA_PORT, 8080),
                    isHttp = intent.getBooleanExtra(EXTRA_IS_HTTP, true),
                    targetPackages = intent.getStringExtra(EXTRA_PACKAGES),
                    isActive = true
                )
                startProxy(profile)
            }
            ACTION_STOP -> {
                stopProxy()
            }
        }
        return START_STICKY
    }

    private fun startProxy(profile: ProxyProfile) {
        createNotificationChannel()
        
        // Intent to open App when clicking notification
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop proxy from notification
        val stopIntent = Intent(this, ProxyService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val desc = if (!profile.targetPackages.isNullOrBlank()) {
            val apps = profile.targetPackages.split(",").size
            "Targeting $apps specific apps"
        } else {
            "Global ${if (profile.isHttp) "HTTP" else "TCP"} Redirection"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JustProxy: ${profile.name}")
            .setContentText("${profile.host}:${profile.port} • $desc")
            .setSmallIcon(android.R.drawable.ic_lock_lock) // Using a system icon for reliability
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Proxy", stopPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Proxy is active and routing traffic to ${profile.host}:${profile.port}.\n" +
                "Profile: ${profile.name}\n" +
                "Mode: ${if (profile.targetPackages.isNullOrBlank()) "Global" else "App-Specific"}\n" +
                "Configuration: ${if (profile.isHttp) "System HTTP" else "Transparent TCP"}"
            ))
            .build()

        startForeground(1, notification)
        
        ProxyManager.startProxy(this, profile)
    }

    private fun stopProxy() {
        ProxyManager.stopProxy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        
        // Update any UI if necessary via broadcast or shared state
        // Here we just stop the service
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "JustProxy Active Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows information about the active proxy profile"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

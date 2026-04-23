package com.whoismept.justproxy.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.whoismept.justproxy.MainActivity
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.data.ProxyType
import com.whoismept.justproxy.utils.ProxyManager

class ProxyService : Service() {

    private var relay: LocalProxyRelay? = null

    companion object {
        const val CHANNEL_ID    = "ProxyServiceChannel"
        const val ACTION_START  = "ACTION_START"
        const val ACTION_STOP   = "ACTION_STOP"
        private const val EXTRA_ID          = "EXTRA_ID"
        private const val EXTRA_NAME        = "EXTRA_NAME"
        private const val EXTRA_HOST        = "EXTRA_HOST"
        private const val EXTRA_PORT        = "EXTRA_PORT"
        private const val EXTRA_TYPE        = "EXTRA_TYPE"
        private const val EXTRA_IS_HTTP     = "EXTRA_IS_HTTP"
        private const val EXTRA_PACKAGES    = "EXTRA_PACKAGES"
        private const val EXTRA_PERSISTENT  = "EXTRA_PERSISTENT"
        private const val PREFS_STATE       = "proxy_state"

        fun startService(context: Context, profile: ProxyProfile, persistentNotification: Boolean = true) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID,         profile.id)
                putExtra(EXTRA_NAME,       profile.name)
                putExtra(EXTRA_HOST,       profile.host)
                putExtra(EXTRA_PORT,       profile.port)
                putExtra(EXTRA_TYPE,       profile.type.name)
                putExtra(EXTRA_IS_HTTP,    profile.isHttp)
                putExtra(EXTRA_PACKAGES,   profile.targetPackages)
                putExtra(EXTRA_PERSISTENT, persistentNotification)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.startService(Intent(context, ProxyService::class.java).apply { action = ACTION_STOP })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val profile = ProxyProfile(
                    id             = intent.getIntExtra(EXTRA_ID, 0),
                    name           = intent.getStringExtra(EXTRA_NAME) ?: "Proxy",
                    host           = intent.getStringExtra(EXTRA_HOST) ?: "",
                    port           = intent.getIntExtra(EXTRA_PORT, 8080),
                    type           = ProxyType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: ProxyType.HTTP.name),
                    isHttp         = intent.getBooleanExtra(EXTRA_IS_HTTP, true),
                    targetPackages = intent.getStringExtra(EXTRA_PACKAGES),
                    isActive       = true
                )
                val persistent = intent.getBooleanExtra(EXTRA_PERSISTENT, true)
                startProxy(profile, persistent)
            }
            ACTION_STOP -> stopProxy()
            else -> {
                // null intent = Android restarted us via START_STICKY after being killed
                restoreProxyState()
            }
        }
        return START_STICKY
    }

    // ── Proxy lifecycle ───────────────────────────────────────────────────────

    private fun startProxy(profile: ProxyProfile, persistent: Boolean = true) {
        saveProxyState(profile, persistent)
        createNotificationChannel()

        val contentPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, ProxyService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val modeTag = when {
            !profile.targetPackages.isNullOrBlank() -> {
                val n = profile.targetPackages.split(",").filter { it.isNotBlank() }.size
                "$n app${if (n != 1) "s" else ""}"
            }
            profile.isHttp -> "System Proxy"
            else           -> "Transparent"
        }
        val typeTag = profile.type.name  // HTTP / SOCKS4 / SOCKS5

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(profile.name)
            .setContentText("${profile.host}:${profile.port}  ·  $typeTag  ·  $modeTag")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(if (persistent) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        // Start relay for ALL HTTP modes — transparent, system proxy, and per-app.
        // Relay intercepts connections so we can log traffic and inject synthetic TLS alerts.
        if (profile.type == ProxyType.HTTP) {
            relay = LocalProxyRelay(profile.host, profile.port)
            relay!!.start()
        }

        ProxyManager.startProxy(this, profile)
    }

    private fun stopProxy() {
        clearProxyState()
        relay?.stop()
        relay = null
        ProxyManager.stopProxy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── State persistence — survives START_STICKY restarts ────────────────────

    private fun saveProxyState(profile: ProxyProfile, persistent: Boolean) {
        getSharedPreferences(PREFS_STATE, MODE_PRIVATE).edit {
            putInt("profile_id",          profile.id)
            putString("profile_name",     profile.name)
            putString("profile_host",     profile.host)
            putInt("profile_port",        profile.port)
            putString("profile_type",     profile.type.name)
            putBoolean("profile_is_http", profile.isHttp)
            putString("profile_packages", profile.targetPackages)
            putBoolean("persistent",      persistent)
        }
    }

    private fun clearProxyState() {
        getSharedPreferences(PREFS_STATE, MODE_PRIVATE).edit { clear() }
    }

    private fun restoreProxyState() {
        val prefs = getSharedPreferences(PREFS_STATE, MODE_PRIVATE)
        val host  = prefs.getString("profile_host", null) ?: return
        val profile = ProxyProfile(
            id             = prefs.getInt("profile_id", 0),
            name           = prefs.getString("profile_name", "Proxy") ?: "Proxy",
            host           = host,
            port           = prefs.getInt("profile_port", 8080),
            type           = ProxyType.valueOf(prefs.getString("profile_type", ProxyType.HTTP.name) ?: ProxyType.HTTP.name),
            isHttp         = prefs.getBoolean("profile_is_http", true),
            targetPackages = prefs.getString("profile_packages", null),
            isActive       = true
        )
        val persistent = prefs.getBoolean("persistent", true)
        startProxy(profile, persistent)
    }

    // ── Keep alive after task removal ─────────────────────────────────────────
    // Some OEM launchers (Samsung, Xiaomi…) kill services on swipe.
    // Schedule a 1-second restart via AlarmManager so proxy recovers.

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val prefs = getSharedPreferences(PREFS_STATE, MODE_PRIVATE)
        if (!prefs.contains("profile_host")) return  // proxy not active, nothing to do

        val restartPi = PendingIntent.getService(
            applicationContext, 2,
            Intent(applicationContext, ProxyService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1_000, restartPi)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onDestroy() {
        relay?.stop()
        relay = null
        ProxyManager.stopProxy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification channel ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JustProxy Active Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Shows the active proxy profile and allows stopping it" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

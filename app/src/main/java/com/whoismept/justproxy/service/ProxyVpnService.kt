package com.whoismept.justproxy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.whoismept.justproxy.MainActivity
import com.whoismept.justproxy.data.ProxyType
import com.whoismept.justproxy.service.vpn.TcpConnectionManager
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "ProxyVpnService"

class ProxyVpnService : VpnService(), Runnable {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var connectionManager: TcpConnectionManager? = null

    private var host: String = ""
    private var port: Int = 8080
    private var proxyType: ProxyType = ProxyType.HTTP
    private var targetPackages: Array<String>? = null

    companion object {
        const val ACTION_STOP = "STOP"
        const val EXTRA_PROXY_TYPE = "proxy_type"
        private const val CHANNEL_ID = "VpnServiceChannel"
        private const val NOTIFICATION_ID = 2
        private const val MTU = 1500
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        host           = intent?.getStringExtra("host") ?: ""
        port           = intent?.getIntExtra("port", 8080) ?: 8080
        proxyType      = ProxyType.valueOf(intent?.getStringExtra(EXTRA_PROXY_TYPE) ?: ProxyType.HTTP.name)
        targetPackages = intent?.getStringArrayExtra("packages")

        startForegroundNotification()

        vpnThread?.interrupt()
        vpnThread = Thread(this, "ProxyVpnThread").apply { start() }

        return START_STICKY
    }

    override fun run() {
        try {
            val builder = Builder()
                .setSession("JustProxy")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .setMtu(MTU)
                .setBlocking(true)

            targetPackages?.forEach { pkg ->
                if (pkg.isNotBlank()) {
                    runCatching { builder.addAllowedApplication(pkg) }
                        .onFailure { Log.w(TAG, "Package not found: $pkg") }
                }
            }
            // Prevent routing loop: our own traffic must bypass VPN
            builder.addDisallowedApplication(packageName)

            val fd = builder.establish() ?: run {
                Log.e(TAG, "VPN establish() returned null — permission not granted?")
                stopSelf()
                return
            }
            vpnInterface = fd
            Log.i(TAG, "VPN interface established ($host:$port via $proxyType)")

            val tunIn  = FileInputStream(fd.fileDescriptor)
            val tunOut = FileOutputStream(fd.fileDescriptor)

            connectionManager = TcpConnectionManager(this, tunOut, host, port, proxyType)

            val buffer = ByteArray(MTU + 40) // extra headroom for IP+TCP headers
            while (!Thread.interrupted()) {
                val n = tunIn.read(buffer)
                if (n > 0) connectionManager?.handlePacket(buffer, n)
            }

        } catch (e: InterruptedException) {
            Log.i(TAG, "VPN thread interrupted")
        } catch (e: Exception) {
            Log.e(TAG, "VPN loop error", e)
        } finally {
            stopVpn()
        }
    }

    private fun stopVpn() {
        connectionManager?.shutdown()
        connectionManager = null
        vpnThread?.interrupt()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        vpnThread = null
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ProxyVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val persistent = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("persistent_notification", true)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JustProxy VPN")
            .setContentText("${proxyType.name} → $host:$port")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(persistent)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}

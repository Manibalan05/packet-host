package com.alan.app.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alan.app.AlanApp
import com.alan.app.MainActivity

/**
 * Foreground service hosting the LAN-facing reverse proxy entrypoint on
 * 0.0.0.0:[proxyPort]. Routing (Host header / path prefix) comes from the
 * site database via [ProxyManager].
 */
class ProxyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? AlanApp
        when (intent?.action) {
            ACTION_STOP -> {
                try {
                    app?.proxyManager?.stop()
                } catch (e: Exception) {
                    Log.e("ProxyService", "Proxy stop failed", e)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val port = intent?.getIntExtra(EXTRA_PORT, -1)?.takeIf { it > 0 }
            ?: com.alan.app.util.Prefs.getProxyPort(this)
        try {
            if (app != null) {
                app.proxyManager.start(port, app.repository)
                app.healthChecker.start()
            }
            startForeground(
                AlanApp.NOTIF_ID_PROXY,
                buildNotif("Reverse proxy live", "http://<phone-ip>:$port")
            )
        } catch (e: Exception) {
            Log.e("ProxyService", "Proxy start failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            (application as? AlanApp)?.proxyManager?.stop()
            (application as? AlanApp)?.healthChecker?.stop()
        } catch (_: Exception) {
        }
    }

    private fun buildNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, ProxyService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AlanApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "Stop proxy", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.alan.app.PROXY_START"
        const val ACTION_STOP = "com.alan.app.PROXY_STOP"
        const val EXTRA_PORT = "proxyPort"
    }
}

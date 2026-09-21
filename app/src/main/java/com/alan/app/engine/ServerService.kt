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
import com.alan.app.data.SiteStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that keeps per-site servers alive and performs
 * start/stop work off the main thread. START_STICKY like a PaaS agent.
 */
class ServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = application as? AlanApp
                        StaticServerManager.stopAll()
                        try {
                            app?.runtimeManager?.stopAll()
                        } catch (_: Exception) {
                        }
                        try {
                            val dao = app?.db?.siteDao()
                            dao?.getAll()?.forEach { site ->
                                dao.upsert(
                                    site.copy(
                                        status = SiteStatus.STOPPED,
                                        uptimeStartMs = null
                                    )
                                )
                            }
                        } catch (_: Exception) {
                        }
                        app?.proxyManager?.let { proxy ->
                            app.let { proxy.refresh(app.repository) }
                        }
                    } catch (e: Exception) {
                        Log.e("ServerService", "Stop-all failed", e)
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                return START_STICKY
            }
            ACTION_START_STATIC -> {
                val siteId = intent.getStringExtra(EXTRA_SITE_ID) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                val dirPath = intent.getStringExtra(EXTRA_DIR) ?: return START_NOT_STICKY
                val app = application as? AlanApp
                val logger = app?.runtimeManager?.loggerFor(siteId) ?: ProcessLogger()
                try {
                    val server = StaticServer(File(dirPath), port, logger) {
                        app?.let { alan ->
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching { alan.repository.incrementRequestCount(siteId) }
                            }
                        }
                    }
                    server.start()
                    StaticServerManager.put(siteId, server)
                    app?.let { alan ->
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                val dao = alan.db.siteDao()
                                val site = dao.getById(siteId)
                                if (site != null) {
                                    dao.upsert(
                                        site.copy(
                                            status = SiteStatus.RUNNING,
                                            port = port,
                                            uptimeStartMs = System.currentTimeMillis()
                                        )
                                    )
                                }
                                alan.proxyManager.refresh(alan.repository)
                            }
                        }
                    }
                    startForeground(
                        AlanApp.NOTIF_ID_SERVER,
                        buildNotif(
                            intent.getStringExtra(EXTRA_TITLE) ?: "Serving $siteId",
                            intent.getStringExtra(EXTRA_TEXT) ?: "http://127.0.0.1:$port"
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ServerService", "Failed to start static $siteId", e)
                }
                return START_STICKY
            }
            ACTION_STOP_SITE -> {
                val siteId = intent.getStringExtra(EXTRA_SITE_ID) ?: return START_STICKY
                CoroutineScope(Dispatchers.IO).launch {
                    StaticServerManager.stop(siteId)
                    val app = application as? AlanApp
                    runCatching { app?.runtimeManager?.stop(siteId) }
                    runCatching {
                        val dao = app?.db?.siteDao()
                        val site = dao?.getById(siteId)
                        if (site != null) {
                            dao.upsert(site.copy(status = SiteStatus.STOPPED, uptimeStartMs = null))
                        }
                        app?.let { app.proxyManager.refresh(app.repository) }
                    }
                }
                return START_STICKY
            }
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(com.alan.app.R.string.notification_title)
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: getString(com.alan.app.R.string.notification_text_idle)
        startForeground(AlanApp.NOTIF_ID_SERVER, buildNotif(title, text))
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the service alive when the user swipes the app away.
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).apply { action = ACTION_STOP_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AlanApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "Stop all", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_STOP_ALL = "com.alan.app.STOP_ALL"
        const val ACTION_START_STATIC = "com.alan.app.START_STATIC"
        const val ACTION_STOP_SITE = "com.alan.app.STOP_SITE"
        const val EXTRA_SITE_ID = "siteId"
        const val EXTRA_PORT = "port"
        const val EXTRA_DIR = "dir"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}

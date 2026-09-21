package com.alan.app.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alan.app.AlanApp
import com.alan.app.MainActivity
import com.alan.app.tunnel.CloudflaredManager
import com.alan.app.tunnel.TunnelState
import com.alan.app.tunnel.parsePublicUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TunnelService"
private const val URL_TIMEOUT_MS = 25_000L
private const val MAX_ATTEMPTS = 10

/**
 * Foreground service owning Cloudflare quick tunnels — the free production
 * path to the public internet without a VPS.
 *
 * One quick tunnel per hosted site (capped at [MAX_TUNNELS] concurrent),
 * each reading a server already bound to 127.0.0.1:[port]. Drops are
 * retried with exponential backoff (2s doubling, 60s ceiling). START_STICKY
 * so tunnels survive process pressure alongside the proxy. The persistent
 * notification reads "Alan live — N sites" and offers a stop action.
 *
 * Per-site lifecycle is published through [stateFor] so the Detail screen
 * can render Starting/Live/Error without binding to this service.
 */
class TunnelService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val procs = ConcurrentHashMap<String, Process>()
    private val liveUrls = ConcurrentHashMap<String, String>()

    private val manager: CloudflaredManager
        get() = (application as AlanApp).cloudflaredManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(AlanApp.NOTIF_ID_TUNNEL, buildNotif(0, null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    try {
                        stopEverything()
                    } catch (e: Exception) {
                        Log.e(TAG, "Stop-all failed", e)
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_STOP_SITE -> {
                val siteId = intent.getStringExtra(EXTRA_SITE_ID)
                if (siteId.isNullOrBlank()) return START_STICKY
                scope.launch { stopSite(siteId) }
                return START_STICKY
            }
            ACTION_START_SITE -> {
                val siteId = intent.getStringExtra(EXTRA_SITE_ID)
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                if (siteId.isNullOrBlank() || port <= 0) return START_STICKY
                if (jobs.containsKey(siteId)) return START_STICKY
                if (jobs.size >= MAX_TUNNELS) {
                    Log.w(TAG, "Tunnel cap reached ($MAX_TUNNELS) — refusing $siteId")
                    stateFor(siteId).value =
                        TunnelState.Error("Tunnel cap reached (5 concurrent) — unhost another site first.")
                    return START_STICKY
                }
                startForeground(AlanApp.NOTIF_ID_TUNNEL, buildNotif(liveUrls.size, liveUrls.values.firstOrNull()))
                runTunnel(siteId, port)
                return START_STICKY
            }
        }
        startForeground(AlanApp.NOTIF_ID_TUNNEL, buildNotif(liveUrls.size, liveUrls.values.firstOrNull()))
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            jobs.values.toList().forEach { it.cancel() }
            procs.values.toList().forEach { runCatching { manager.stop(it) } }
            scope.cancel()
        } catch (_: Exception) {
        }
    }

    private fun runTunnel(siteId: String, port: Int) {
        if (jobs.containsKey(siteId)) return
        val job = scope.launch {
            stateFor(siteId).value = TunnelState.Starting
            var attempt = 0
            while (isActive) {
                val proc = try {
                    manager.startQuickTunnel(port)
                } catch (e: Exception) {
                    Log.e(TAG, "Tunnel start failed for $siteId", e)
                    stateFor(siteId).value = TunnelState.Error(e.message ?: "cloudflared unavailable")
                    markUnlive(siteId)
                    break
                }
                procs[siteId] = proc
                try {
                    supervise(siteId, proc)
                } catch (e: CancellationException) {
                    runCatching { manager.stop(proc) }
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Tunnel supervise failed for $siteId: ${e.message}")
                } finally {
                    procs.remove(siteId)
                }
                if (!isActive) break
                attempt++
                if (attempt > MAX_ATTEMPTS) {
                    stateFor(siteId).value =
                        TunnelState.Error("Giving up after $MAX_ATTEMPTS attempts — tap Host to retry.")
                    markUnlive(siteId)
                    break
                }
                val delaySec = minOf(60, (1 shl attempt.coerceAtMost(5)) * 2)
                stateFor(siteId).value = TunnelState.Error("Tunnel dropped — retry in ${delaySec}s…")
                markUnlive(siteId)
                delay(delaySec * 1000L)
                stateFor(siteId).value = TunnelState.Starting
            }
        }
        val prev = jobs.putIfAbsent(siteId, job)
        if (prev != null) {
            job.cancel()
            return
        }
        job.invokeOnCompletion { jobs.remove(siteId) }
    }

    /**
     * Reads one tunnel process until its public URL appears (first
     * `https://[random].trycloudflare.com` line), marks the site live, then blocks
     * until the process exits (drop) or this coroutine is cancelled (unhost).
     */
    private suspend fun supervise(siteId: String, proc: Process) {
        val urlFound = CompletableDeferred<String>()
        val reader = scope.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { lines ->
                    while (isActive) {
                        val line = lines.readLine() ?: break
                        val found = parsePublicUrl(line)
                        if (found != null && !urlFound.isCompleted) {
                            urlFound.complete(found)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (!urlFound.isCompleted) {
                    urlFound.completeExceptionally(RuntimeException("Tunnel exited before publishing a URL"))
                }
            }
        }
        try {
            val url = withTimeoutOrNull(URL_TIMEOUT_MS) {
                try {
                    urlFound.await()
                } catch (_: Exception) {
                    null
                }
            }
            if (url == null) {
                stateFor(siteId).value =
                    TunnelState.Error("Timed out waiting for a public URL (~25s). Is the internet reachable?")
                markUnlive(siteId)
                runCatching { manager.stop(proc) }
                return
            }
            markLive(siteId, url)
            try {
                withContext(Dispatchers.IO) { proc.waitFor() }
            } catch (_: InterruptedException) {
            }
            // Process exited while we wanted it: a drop, retried by the caller loop.
            Log.w(TAG, "Tunnel process exited for $siteId (code=${runCatching { proc.exitValue() }.getOrNull()})")
        } finally {
            reader.cancel()
        }
    }

    private suspend fun markLive(siteId: String, url: String) {
        liveUrls[siteId] = url
        stateFor(siteId).value = TunnelState.Live(url)
        try {
            val dao = (application as? AlanApp)?.db?.siteDao() ?: return
            val cur = dao.getById(siteId) ?: return
            dao.upsert(cur.copy(publicUrl = url, hosted = true))
        } catch (e: Exception) {
            Log.w(TAG, "markLive DB failed: ${e.message}")
        }
        refreshNotif()
    }

    private suspend fun markUnlive(siteId: String) {
        liveUrls.remove(siteId)
        try {
            val dao = (application as? AlanApp)?.db?.siteDao() ?: return
            val cur = dao.getById(siteId) ?: return
            if (cur.hosted || cur.publicUrl.isNotBlank()) {
                dao.upsert(cur.copy(hosted = false, publicUrl = ""))
            }
        } catch (e: Exception) {
            Log.w(TAG, "markUnlive DB failed: ${e.message}")
        }
        refreshNotif()
    }

    private suspend fun stopSite(siteId: String) {
        jobs.remove(siteId)?.cancel()
        procs.remove(siteId)?.let { runCatching { manager.stop(it) } }
        stateFor(siteId).value = TunnelState.Idle
        markUnlive(siteId)
    }

    private suspend fun stopEverything() {
        jobs.keys().toList().forEach { id ->
            jobs.remove(id)?.cancel()
        }
        procs.values.toList().forEach { runCatching { manager.stop(it) } }
        procs.clear()
        liveUrls.clear()
        try {
            val dao = (application as? AlanApp)?.db?.siteDao()
            dao?.getAll()?.forEach { site ->
                if (site.hosted || site.publicUrl.isNotBlank()) {
                    dao.upsert(site.copy(hosted = false, publicUrl = ""))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop-all DB reset failed: ${e.message}")
        }
        states.values.forEach { it.value = TunnelState.Idle }
        refreshNotif()
    }

    private fun refreshNotif() {
        val notif = buildNotif(liveUrls.size, liveUrls.values.firstOrNull())
        runCatching {
            getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        }?.getOrNull()?.notify(AlanApp.NOTIF_ID_TUNNEL, notif)
    }

    private fun buildNotif(liveCount: Int, firstUrl: String?): Notification {
        val title = when (liveCount) {
            0 -> "Alan quick tunnel"
            1 -> "Alan live — 1 site"
            else -> "Alan live — $liveCount sites"
        }
        val text = firstUrl ?: "Waiting for tunnels…"
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 4, Intent(this, TunnelService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AlanApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "Stop tunnels", stopPi)
            .setOngoing(liveCount > 0)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_START_SITE = "com.alan.app.TUNNEL_START_SITE"
        const val ACTION_STOP_SITE = "com.alan.app.TUNNEL_STOP_SITE"
        const val ACTION_STOP = "com.alan.app.TUNNEL_STOP"

        const val EXTRA_SITE_ID = "tunnelSiteId"
        const val EXTRA_PORT = "tunnelPort"

        /** Hard cap: at most 5 concurrent quick tunnels. */
        const val MAX_TUNNELS = 5

        private val states = ConcurrentHashMap<String, MutableStateFlow<TunnelState>>()

        /** Observable lifecycle for one site's tunnel; Idle until first Host. */
        fun stateFor(siteId: String): MutableStateFlow<TunnelState> =
            states.getOrPut(siteId) { MutableStateFlow(TunnelState.Idle) }

        fun startSite(context: Context, siteId: String, port: Int) {
            val intent = Intent(context, TunnelService::class.java).apply {
                action = ACTION_START_SITE
                putExtra(EXTRA_SITE_ID, siteId)
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopSite(context: Context, siteId: String) {
            val intent = Intent(context, TunnelService::class.java).apply {
                action = ACTION_STOP_SITE
                putExtra(EXTRA_SITE_ID, siteId)
            }
            context.startService(intent)
        }

        fun stopAll(context: Context) {
            val intent = Intent(context, TunnelService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

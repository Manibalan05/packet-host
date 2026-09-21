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
import com.alan.app.net.RelayAuth
import com.alan.app.net.RelayState
import com.alan.app.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service owning the whole-internet VPS relay.
 *
 * The phone keeps serving locally through [ProxyService]; this service only
 * holds the OUTBOUND SSH connection (with remote port forwarding) to the
 * user's own VPS, which punches through NAT/CGNAT without any third-party
 * relay. START_STICKY so the relay survives process pressure alongside the
 * proxy. The password (when used) arrives via [EXTRA_PASSWORD] and is kept in
 * memory only — it is never written to prefs or logs.
 */
class RelayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? AlanApp
        if (app == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                try {
                    app.relayManager.disconnect()
                } catch (e: Exception) {
                    Log.e("RelayService", "Relay stop failed", e)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val keyPassphrase = intent?.getStringExtra(EXTRA_KEY_PASSPHRASE)
        var cfg = Prefs.relayConfig(this, password)
        if (Prefs.getRelayAuthType(this) == Prefs.RELAY_AUTH_KEY && !keyPassphrase.isNullOrEmpty()) {
            cfg = cfg.copy(auth = RelayAuth.KeyFile(Prefs.getRelayKeyPath(this), keyPassphrase))
        }
        val errors = cfg.validate().toMutableList()
        if (errors.isEmpty() && Prefs.getRelayAuthType(this) == Prefs.RELAY_AUTH_PASSWORD && password.isEmpty()) {
            errors += "Password is empty — enter your VPS password."
        }
        if (errors.isNotEmpty()) {
            startForeground(
                AlanApp.NOTIF_ID_RELAY,
                buildNotif("Alan relay: not started", errors.first())
            )
            // Keep the service up only to show the error; the user stops it
            // from the notification or the Relay screen.
            return START_STICKY
        }
        startForeground(
            AlanApp.NOTIF_ID_RELAY,
            buildNotif("Alan relay: starting…", cfg.forwardSpec())
        )
        observeState(app, cfg.vpsHost)
        val result = app.relayManager.connect(cfg)
        if (result.isFailure) {
            updateNotif("Alan relay: error", result.exceptionOrNull()?.message ?: "invalid config")
        }
        return START_STICKY
    }

    private fun observeState(app: AlanApp, host: String) {
        stateJob?.cancel()
        stateJob = scope.launch {
            app.relayManager.state.collect { state ->
                val nm = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    ?: return@collect
                val notif = when (state) {
                    is RelayState.Disconnected -> buildNotif("Alan relay: stopped", "VPS relay is off")
                    is RelayState.Connecting -> buildNotif("Alan relay: connecting…", "Contacting $host")
                    is RelayState.Live -> buildNotif("Alan relay: LIVE via $host", state.detail)
                    is RelayState.Error -> buildNotif("Alan relay: error", state.message)
                }
                nm.notify(AlanApp.NOTIF_ID_RELAY, notif)
            }
        }
    }

    private fun updateNotif(title: String, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
            ?: return
        nm.notify(AlanApp.NOTIF_ID_RELAY, buildNotif(title, text))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stateJob?.cancel()
            scope.cancel()
        } catch (_: Exception) {
        }
    }

    private fun buildNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 3, Intent(this, RelayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AlanApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "Stop relay", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .build()
    }

    companion object {
        const val ACTION_START = "com.alan.app.RELAY_START"
        const val ACTION_STOP = "com.alan.app.RELAY_STOP"

        /** VPS password for this connect only; never persisted. */
        const val EXTRA_PASSWORD = "relayPassword"

        /** Private-key passphrase for this connect only; never persisted. */
        const val EXTRA_KEY_PASSPHRASE = "relayKeyPassphrase"
    }
}

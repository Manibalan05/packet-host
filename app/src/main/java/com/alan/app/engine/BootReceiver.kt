package com.alan.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alan.app.util.Prefs

/**
 * Restarts the reverse proxy entrypoint after reboot when the user opted
 * into auto-start. Per-site servers are intentionally NOT restarted: the
 * user explicitly stopped them if they are STOPPED in the database.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.isAutoStartEnabled(context)) {
            Log.i("BootReceiver", "Boot completed — auto-start disabled, doing nothing")
            return
        }
        Log.i("BootReceiver", "Boot completed — restarting reverse proxy")
        try {
            val svc = Intent(context, ProxyService::class.java).apply {
                action = ProxyService.ACTION_START
                putExtra(ProxyService.EXTRA_PORT, Prefs.getProxyPort(context))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to restart proxy", e)
        }
    }
}

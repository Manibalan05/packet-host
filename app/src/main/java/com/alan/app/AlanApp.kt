package com.alan.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.alan.app.data.AppDatabase
import com.alan.app.data.SiteRepository
import com.alan.app.engine.HealthChecker
import com.alan.app.engine.ProxyManager
import com.alan.app.engine.RuntimeManager
import com.alan.app.net.RelayManager
import com.alan.app.tunnel.CloudflaredManager
import java.io.File

class AlanApp : Application() {
    lateinit var db: AppDatabase
        private set

    lateinit var repository: SiteRepository
        private set

    lateinit var runtimeManager: RuntimeManager
        private set

    lateinit var proxyManager: ProxyManager
        private set

    lateinit var healthChecker: HealthChecker
        private set

    /**
     * Single owner of the outbound VPS relay so the Relay screen and the
     * [com.alan.app.engine.RelayService] observe the same state.
     */
    lateinit var relayManager: RelayManager
        private set

    /**
     * Single owner of the cloudflared binary / quick-tunnel processes so the
     * Detail screen and [com.alan.app.engine.TunnelService] share one source
     * of truth, mirroring [proxyManager].
     */
    lateinit var cloudflaredManager: CloudflaredManager
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "alan.db")
            // One-time reset for pre-release v1 DBs (schema grew without a
            // version bump); from v2 onward only real migrations run.
            .fallbackToDestructiveMigrationFrom(1)
            .build()
        repository = SiteRepository(db.siteDao(), this)
        runtimeManager = RuntimeManager(this)
        proxyManager = ProxyManager(this)
        healthChecker = HealthChecker(repository)
        relayManager = RelayManager(File(filesDir, "ssh/known_hosts"))
        cloudflaredManager = CloudflaredManager(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "alan_foreground"
        const val NOTIF_ID_PROXY = 1001
        const val NOTIF_ID_SERVER = 1002
        const val NOTIF_ID_RELAY = 1003
        const val NOTIF_ID_TUNNEL = 1004
    }
}

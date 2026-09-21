package com.alan.app.engine

import android.content.Context
import com.alan.app.data.SiteRepository
import com.alan.app.data.SiteStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the single [ProxyServer] instance. Routes are rebuilt from the
 * database on every request, so hostRule/pathPrefix edits apply live.
 */
class ProxyManager(private val context: Context) {
    val logger = ProcessLogger()

    @Volatile
    private var server: ProxyServer? = null

    @Volatile
    var proxyPort: Int = 8080
        private set

    fun isRunning(): Boolean = server?.isRunning() == true

    fun start(port: Int, repository: SiteRepository) {
        if (isRunning() && port == proxyPort) return
        stop()
        proxyPort = port
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = ProxyServer(
            proxyPort = port,
            routeProvider = {
                // Blocking read is acceptable here: provider runs on CIO
                // worker threads, and Room forbids main-thread queries only.
                // Use a snapshot refreshed in the background instead.
                snapshot
            },
            logger = logger,
            onRequest = { targetPort ->
                appScope.launch {
                    runCatching {
                        repository.getAll()
                            .firstOrNull { it.port == targetPort && it.status == SiteStatus.RUNNING }
                            ?.let { repository.incrementRequestCount(it.id) }
                    }
                }
            }
        )
        refreshSnapshot(repository) {
            server?.start()
        }
    }

    @Volatile
    private var snapshot: List<RouteTarget> = emptyList()

    private fun refreshSnapshot(repository: SiteRepository, done: () -> Unit) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val sites = runCatching { repository.getAll() }.getOrDefault(emptyList())
            snapshot = sites
                .filter { it.status == SiteStatus.RUNNING }
                .map { RouteTarget(it.hostRule, it.pathPrefix, it.port) }
            // Reflective DB reads above need the repository off the main
            // thread; start the server after the first snapshot is ready.
            runCatching { done() }
        }
    }

    /** Rebuild routes from the DB without restarting the listener. */
    fun refresh(repository: SiteRepository) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val sites = runCatching { repository.getAll() }.getOrDefault(emptyList())
            snapshot = sites
                .filter { it.status == SiteStatus.RUNNING }
                .map { RouteTarget(it.hostRule, it.pathPrefix, it.port) }
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }
}

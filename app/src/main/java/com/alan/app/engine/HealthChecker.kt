package com.alan.app.engine

import com.alan.app.data.SiteRepository
import com.alan.app.data.SiteStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Periodically GETs 127.0.0.1:[port]/__alan_health for every RUNNING site
 * and records a short status string on the site row.
 */
class HealthChecker(private val repository: SiteRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start(intervalMs: Long = 30_000) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runCatching { checkOnce() }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun checkOnce() {
        val sites = repository.getAll().filter { it.status == SiteStatus.RUNNING }
        for (site in sites) {
            val result = probe(site.port)
            repository.updateHealth(site.id, result)
        }
    }

    private fun probe(port: Int): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("http://127.0.0.1:$port/__alan_health")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val code = conn.responseCode
            val body = runCatching {
                conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8).take(64) }
            }.getOrDefault("")
            if (code in 200..299) "healthy ($body)" else "unhealthy (http $code)"
        } catch (e: Exception) {
            "unreachable (${e.message})"
        } finally {
            conn?.disconnect()
        }
    }
}

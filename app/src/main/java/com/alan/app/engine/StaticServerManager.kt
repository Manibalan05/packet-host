package com.alan.app.engine

import java.util.concurrent.ConcurrentHashMap

object StaticServerManager {
    private val servers = ConcurrentHashMap<String, StaticServer>()

    fun get(siteId: String): StaticServer? = servers[siteId]

    fun put(siteId: String, server: StaticServer) {
        servers[siteId] = server
    }

    fun allIds(): Set<String> = servers.keys

    fun stop(siteId: String) {
        servers.remove(siteId)?.stop()
    }

    fun stopAll() {
        servers.values.toList().forEach { it.stop() }
        servers.clear()
    }

    fun isRunning(siteId: String): Boolean = servers[siteId]?.isRunning() == true
}

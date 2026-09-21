package com.alan.app.util

import java.net.ServerSocket
import kotlin.random.Random

object PortUtils {
    private const val DEFAULT_RANGE_START = 3000
    private const val DEFAULT_RANGE_END = 9000

    /** Find a free port, preferring [preferred] and staying clear of [taken]. */
    fun findFreePort(preferred: Int? = null, taken: Set<Int> = emptySet()): Int {
        preferred?.let {
            if (it !in taken && isPortAvailable(it)) return it
        }
        repeat(20) {
            val p = Random.nextInt(DEFAULT_RANGE_START, DEFAULT_RANGE_END)
            if (p !in taken && isPortAvailable(p)) return p
        }
        // Fallback: let the OS assign, then avoid collisions.
        var port = ServerSocket(0).use { it.localPort }
        var guard = 0
        while (port in taken && guard++ < 50) {
            port = ServerSocket(0).use { it.localPort }
        }
        return port
    }

    fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: Exception) {
            false
        }
    }
}

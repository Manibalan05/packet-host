package com.alan.app.engine

import android.util.Log
import io.ktor.http.ContentType
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

/**
 * Embedded static file server using Ktor CIO.
 * Binds to 127.0.0.1 only; LAN/internet reachability is provided by
 * the [ReverseProxy] entrypoint, which routes by Host header / path prefix.
 */
class StaticServer(
    private val siteDir: File,
    private val port: Int,
    private val logger: ProcessLogger,
    private val onRequest: (() -> Unit)? = null
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start() {
        if (server != null) return
        logger.append("Starting static server on 127.0.0.1:$port -> ${siteDir.absolutePath}")
        try {
            server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                val counter = createApplicationPlugin("RequestCounter") {
                    onCall {
                        try {
                            onRequest?.invoke()
                        } catch (_: Exception) {
                        }
                    }
                }
                install(counter)
                routing {
                    get("/__alan_health") {
                        call.respondText("ok", ContentType.Text.Plain)
                    }
                    staticFiles("/", siteDir) {
                        enableAutoHeadResponse()
                        default("index.html")
                    }
                }
            }
            // start(wait=false) throws immediately on bind failure (e.g. port
            // in use) so callers never report RUNNING for a dead server.
            server?.start(wait = false)
            logger.append("Static server started on 127.0.0.1:$port")
        } catch (e: Exception) {
            logger.append("Static server failed: ${e.message}")
            Log.e("StaticServer", "start failed", e)
            throw e
        }
    }

    fun stop() {
        try {
            server?.stop(500, 1000)
            logger.append("Static server stopped")
        } catch (e: Exception) {
            logger.append("Stop error: ${e.message}")
        } finally {
            server = null
        }
    }

    fun isRunning(): Boolean = server != null
}

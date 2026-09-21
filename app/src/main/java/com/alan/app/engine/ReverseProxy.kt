package com.alan.app.engine

import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveStream
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hostname + path-prefix route entry. Plain data so routing stays
 * Android-free and unit-testable.
 */
data class RouteTarget(
    val hostRule: String = "",
    val pathPrefix: String = "/",
    val port: Int
)

/**
 * Pure routing function: pick the target port for an incoming request.
 *
 * Rules:
 * - A blank [RouteTarget.hostRule] matches any host; otherwise the
 *   incoming host must equal the rule (case-insensitive, port stripped).
 * - [RouteTarget.pathPrefix] must be a prefix of the request path.
 * - Longest matching path prefix wins; ties prefer the non-blank host rule.
 *
 * Callers must pass a bare path WITHOUT query string or fragment
 * (e.g. "/blog", not "/blog?x=1") — query strings are not stripped here.
 *
 * Returns the target port, or null when nothing matches.
 */
fun resolveRoute(host: String, path: String, routes: List<RouteTarget>): Int? {
    val bareHost = host.substringBefore(":").trim().lowercase()
    val cleanPath = if (path.startsWith("/")) path else "/$path"
    return routes
        .filter { route ->
            val hostOk = route.hostRule.isBlank() ||
                route.hostRule.trim().lowercase() == bareHost
            val prefix = route.pathPrefix.ifBlank { "/" }
            val normPrefix = if (prefix.startsWith("/")) prefix else "/$prefix"
            hostOk && (normPrefix == "/" || cleanPath == normPrefix || cleanPath.startsWith(normPrefix.trimEnd('/') + "/"))
        }
        .sortedWith(
            compareByDescending<RouteTarget> {
                val p = it.pathPrefix.ifBlank { "/" }
                p.trimEnd('/').length
            }.thenByDescending { it.hostRule.isNotBlank() }
        )
        .firstOrNull()?.port
}

/**
 * Max bytes the proxy will buffer from an upstream response.
 * Phones have tight memory — anything bigger is refused with 502.
 */
const val MAX_PROXY_BODY_BYTES = 25 * 1024 * 1024

class BodyTooLargeException(limit: Int) : Exception("Body exceeds $limit bytes")

/** Read [input] fully unless it exceeds [limit], then throw [BodyTooLargeException]. */
fun readCapped(input: java.io.InputStream, limit: Int = MAX_PROXY_BODY_BYTES): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(32 * 1024)
    var total = 0
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > limit) throw BodyTooLargeException(limit)
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/**
 * LAN-facing reverse proxy entrypoint (PaaS-style front door).
 * Binds 0.0.0.0:[proxyPort] and forwards to per-site 127.0.0.1 servers
 * using [resolveRoute]. Also serves an aggregate /__alan_health endpoint.
 */
class ProxyServer(
    private var proxyPort: Int,
    private val routeProvider: () -> List<RouteTarget>,
    private val logger: ProcessLogger,
    private val onRequest: ((port: Int) -> Unit)? = null
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun currentPort(): Int = proxyPort

    fun start() {
        if (server != null) return
        logger.append("Starting reverse proxy on 0.0.0.0:$proxyPort")
        try {
            server = embeddedServer(CIO, host = "0.0.0.0", port = proxyPort) {
                routing {
                    get("/__alan_health") {
                        val routes = routeProvider()
                        call.respondText(
                            "ok sites=${routes.size}",
                            ContentType.Text.Plain
                        )
                    }
                    route("{proxyPath...}") {
                        handle {
                            val host = call.request.headers["Host"] ?: ""
                            val path = call.request.uri.substringBefore("?")
                            val query = call.request.uri.substringAfter("?", "")
                            val targetPort = resolveRoute(host, path, routeProvider())
                            if (targetPort == null) {
                                call.respondText(
                                    "No site matches host='$host' path='$path'",
                                    ContentType.Text.Plain,
                                    HttpStatusCode.NotFound
                                )
                                return@handle
                            }
                            forward(call, targetPort, path, query)
                        }
                    }
                }
            }
            server?.start(wait = false)
            logger.append("Reverse proxy listening on 0.0.0.0:$proxyPort")
        } catch (e: Exception) {
            logger.append("Reverse proxy failed: ${e.message}")
            Log.e("ProxyServer", "start failed", e)
            throw e
        }
    }

    private suspend fun forward(
        call: ApplicationCall,
        targetPort: Int,
        path: String,
        query: String
    ) {
        val target = buildString {
            append("http://127.0.0.1:")
            append(targetPort)
            append(path.ifBlank { "/" })
            if (query.isNotEmpty()) append("?").append(query)
        }
        var conn: HttpURLConnection? = null
        try {
            conn = URL(target).openConnection() as HttpURLConnection
            conn.requestMethod = call.request.httpMethod.value
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            for (name in call.request.headers.names()) {
                if (name.equals("Host", ignoreCase = true) ||
                    name.equals("Content-Length", ignoreCase = true)
                ) continue
                call.request.headers.getAll(name)?.forEach { value ->
                    conn.addRequestProperty(name, value)
                }
            }
            val method = call.request.httpMethod.value
            if (method != "GET" && method != "HEAD" && method != "DELETE") {
                conn.doOutput = true
                call.receiveStream().use { input ->
                    conn.outputStream.use { out -> input.copyTo(out) }
                }
            }
            val status = conn.responseCode
            conn.headerFields.forEach { (name, values) ->
                if (name != null && values != null) {
                    // Content-Type/Length are set by respondBytes; skip to avoid duplicates.
                    if (!name.equals("Content-Type", ignoreCase = true) &&
                        !name.equals("Content-Length", ignoreCase = true) &&
                        !name.equals("Transfer-Encoding", ignoreCase = true)
                    ) {
                        values.forEach { call.response.header(name, it) }
                    }
                }
            }
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            // Cap buffered upstream bodies so one huge file can't OOM the phone.
            val bytes = try {
                stream?.use { readCapped(it, MAX_PROXY_BODY_BYTES) } ?: ByteArray(0)
            } catch (e: BodyTooLargeException) {
                logger.append("Proxy refused oversized body from 127.0.0.1:$targetPort$path (> $MAX_PROXY_BODY_BYTES bytes)")
                call.respondText(
                    "Upstream response too large (limit ${MAX_PROXY_BODY_BYTES / 1024 / 1024} MB)",
                    ContentType.Text.Plain,
                    HttpStatusCode.BadGateway
                )
                return
            }
            val contentType = conn.contentType?.let {
                runCatching { ContentType.parse(it.substringBefore(";").trim()) }
                    .getOrDefault(ContentType.Application.OctetStream)
            } ?: ContentType.Application.OctetStream
            try {
                onRequest?.invoke(targetPort)
            } catch (_: Exception) {
            }
            call.respondBytes(bytes, contentType, HttpStatusCode.fromValue(status))
        } catch (e: Exception) {
            logger.append("Proxy error -> 127.0.0.1:$targetPort$path : ${e.message}")
            call.respondText(
                "Upstream unavailable (127.0.0.1:$targetPort): ${e.message}",
                ContentType.Text.Plain,
                HttpStatusCode.BadGateway
            )
        } finally {
            conn?.disconnect()
        }
    }

    fun stop() {
        try {
            server?.stop(500, 1000)
            logger.append("Reverse proxy stopped")
        } catch (e: Exception) {
            logger.append("Proxy stop error: ${e.message}")
        } finally {
            server = null
        }
    }

    fun isRunning(): Boolean = server != null
}

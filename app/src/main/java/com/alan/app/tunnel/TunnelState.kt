package com.alan.app.tunnel

/**
 * Lifecycle of one site's Cloudflare quick tunnel.
 *
 * Idle     — no tunnel requested for this site.
 * Starting — cloudflared spawned, waiting for the public URL (~3-8s cold start).
 * Live     — public [url] (https://[random].trycloudflare.com) is reachable.
 * Error    — last attempt failed with [msg]; the service may retry with backoff.
 */
sealed class TunnelState {
    object Idle : TunnelState()
    object Starting : TunnelState()
    data class Live(val url: String) : TunnelState()
    data class Error(val msg: String) : TunnelState()
}

package com.alan.app.net

/**
 * Pure, side-effect-free public-access guidance for direct hosting
 * (port-forward + Dynamic DNS), in the style of self-hosted PaaS docs.
 * No network calls are made here — these are checklist strings for the UI.
 */
object PublicAccess {
    val portForwardSteps: List<String> = listOf(
        "1. Keep this phone on Wi-Fi with a stable LAN address (or a hotspot clients join).",
        "2. On your router, forward external TCP port 8080 (or your proxy port) to this phone's LAN IP and the same port.",
        "3. Confirm your ISP allows inbound connections on that port (many mobile carriers use CGNAT and block inbound).",
        "4. Open http://<your-public-ip>:8080/__alan_health from another network to verify."
    )

    val ddnsSteps: List<String> = listOf(
        "1. Create a free hostname at a Dynamic DNS provider (e.g. DuckDNS, FreeDNS, desec).",
        "2. Point it at your home public IP (A record), and keep it updated with the provider's updater or your router's built-in DDNS client.",
        "3. Browse to http://<your-hostname>:8080/__alan_health to verify.",
        "4. For a custom domain, add a CNAME from your domain to the DDNS hostname, then set that domain as a site's host rule in Alan."
    )

    val customDomainSteps: List<String> = listOf(
        "1. Point your domain (A record) at your public IP, or CNAME it to your DDNS hostname.",
        "2. Forward the proxy port on your router to this phone.",
        "3. In the site's detail screen, set Host rule to your domain (e.g. app.example.com).",
        "4. Requests for that Host header are now routed to that site; other hosts fall through to catch-all sites."
    )

    val vpsRelaySteps: List<String> = listOf(
        "1. Rent any VPS with a public IP and SSH access (about \$4-6/mo).",
        "2. On the VPS, allow remote forwarding (GatewayPorts) and run Caddy " +
            "as a reverse proxy with automatic HTTPS (see the relay/ folder).",
        "3. In Alan's Relay screen, enter the VPS host, SSH user, and a " +
            "password or private key, then approve the host key and Connect.",
        "4. Share https://<your-domain>/ — TLS terminates at Caddy on the VPS; " +
            "the phone itself still serves plain HTTP locally."
    )

    val quickTunnelSteps: List<String> = listOf(
        "1. Run the site, then tap Host — Alan starts an anonymous Cloudflare " +
            "quick tunnel (no account, no VPS, no port-forwarding).",
        "2. You get a random public URL like https://<words>.trycloudflare.com " +
            "after a ~3-8s cold start. It changes on every Host.",
        "3. Best for demos and quick shares. The URL is public: anyone with " +
            "the link can open it, and traffic passes through Cloudflare.",
        "4. Need a stable address or your own domain? Use the VPS relay path instead."
    )

    fun summary(proxyPort: Int, lanIp: String?): String = buildString {
        appendLine("Hosting paths: LAN first, more internet options below:")
        appendLine("- LAN URL: http://${lanIp ?: "<phone-ip>"}:$proxyPort/__alan_health")
        appendLine("- Internet (after port-forward): http://<public-ip-or-ddns>:$proxyPort/")
        appendLine("- Custom domain: point DNS at your IP, then set the site's Host rule.")
        appendLine("- TLS: import a .p12 certificate to serve HTTPS; otherwise plain HTTP.")
    }
}

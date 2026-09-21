package com.alan.app.net

import java.io.File

/**
 * How the phone authenticates to the user's VPS over SSH.
 * Kept in memory only for passwords — nothing here touches Android APIs
 * so this file stays unit-testable on the JVM.
 */
sealed interface RelayAuth {
    /** Password for `username`. Never persisted; entered per connect. */
    data class Password(val password: String) : RelayAuth

    /** OpenSSH-format private key file already copied under filesDir/ssh/. */
    data class KeyFile(val path: String, val passphrase: String? = null) : RelayAuth
}

/**
 * Configuration for the whole-internet VPS relay: an OUTBOUND SSH connection
 * from the phone to the user's own VPS that requests SSH REMOTE port
 * forwarding (VPS public port -> phone's local reverse-proxy port).
 *
 * Pure data + validation — Android-free.
 */
data class RelayConfig(
    val vpsHost: String = "",
    val sshPort: Int = 22,
    val username: String = "",
    val auth: RelayAuth = RelayAuth.Password(""),
    /** Public port on the VPS that the world connects to. */
    val remotePort: Int = 8080,
    /** Phone-side port being exposed (normally the reverse-proxy port). */
    val localPort: Int = 8080,
    val autoReconnect: Boolean = true,
    /**
     * Must be true to allow remotePort < 1024: binding those needs root
     * (or extra capabilities) on the VPS sshd.
     */
    val allowPrivilegedRemotePort: Boolean = false,
) {
    /** Human-readable validation errors; empty means the config is usable. */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (vpsHost.isBlank()) errors += "VPS host is blank — enter your server's IP or hostname."
        if (username.isBlank()) errors += "SSH username is blank."
        if (sshPort !in 1..65535) errors += "SSH port must be 1..65535."
        if (remotePort !in 1..65535) errors += "Remote (VPS public) port must be 1..65535."
        if (localPort !in 1..65535) errors += "Local (phone proxy) port must be 1..65535."
        if (remotePort in 1..1023 && !allowPrivilegedRemotePort) {
            errors += "Remote port $remotePort is privileged (<1024) — it needs root " +
                "on the VPS. Use 1024 or higher, or confirm the VPS SSH user is root."
        }
        when (auth) {
            is RelayAuth.Password -> Unit // emptiness is checked at connect time, not here
            is RelayAuth.KeyFile -> {
                if (auth.path.isBlank()) {
                    errors += "Private-key file path is blank — import a key first."
                } else if (!File(auth.path).isFile) {
                    errors += "Private-key file not found: ${auth.path}"
                }
            }
        }
        return errors
    }

    /** One-line description of the forwarding, e.g. "0.0.0.0:8080 -> 127.0.0.1:8080". */
    fun forwardSpec(): String = "0.0.0.0:$remotePort -> 127.0.0.1:$localPort"
}

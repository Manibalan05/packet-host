package com.alan.app.util

import android.content.Context
import android.content.SharedPreferences
import com.alan.app.net.RelayAuth
import com.alan.app.net.RelayConfig

object Prefs {
    private const val PREFS_NAME = "alan_prefs"
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_FIRST_RUN_WARNING = "first_run_warning_shown"
    private const val KEY_FIRST_HOST_WARNING = "first_host_warning_shown"

    // Whole-internet VPS relay (one relay serves the whole proxy, all sites).
    // Note: passwords are NEVER persisted — entered per connect, kept in memory.
    private const val KEY_RELAY_HOST = "relay_host"
    private const val KEY_RELAY_SSH_PORT = "relay_ssh_port"
    private const val KEY_RELAY_USERNAME = "relay_username"
    private const val KEY_RELAY_AUTH_TYPE = "relay_auth_type" // "password" | "key"
    private const val KEY_RELAY_KEY_PATH = "relay_key_path"
    private const val KEY_RELAY_REMOTE_PORT = "relay_remote_port"
    private const val KEY_RELAY_AUTO_RECONNECT = "relay_auto_reconnect"
    private const val KEY_RELAY_ALLOW_PRIVILEGED = "relay_allow_privileged"

    const val RELAY_AUTH_PASSWORD = "password"
    const val RELAY_AUTH_KEY = "key"

    const val DEFAULT_PROXY_PORT = 8080

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProxyPort(context: Context): Int =
        prefs(context).getInt(KEY_PROXY_PORT, DEFAULT_PROXY_PORT).takeIf { it in 1..65535 }
            ?: DEFAULT_PROXY_PORT

    fun setProxyPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PROXY_PORT, port.coerceIn(1, 65535)).apply()
    }

    fun isAutoStartEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_START, false)

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun isFirstRunWarningNeeded(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_FIRST_RUN_WARNING, false)

    fun setFirstRunWarningShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_FIRST_RUN_WARNING, true).apply()
    }

    /** First-Host warning: quick tunnels expose the site to the public internet. */
    fun isFirstHostWarningNeeded(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_FIRST_HOST_WARNING, false)

    fun setFirstHostWarningShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_FIRST_HOST_WARNING, true).apply()
    }

    fun getRelayHost(context: Context): String =
        prefs(context).getString(KEY_RELAY_HOST, "") ?: ""

    fun setRelayHost(context: Context, host: String) {
        prefs(context).edit().putString(KEY_RELAY_HOST, host.trim()).apply()
    }

    fun getRelaySshPort(context: Context): Int =
        prefs(context).getInt(KEY_RELAY_SSH_PORT, 22).takeIf { it in 1..65535 } ?: 22

    fun setRelaySshPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_RELAY_SSH_PORT, port.coerceIn(1, 65535)).apply()
    }

    fun getRelayUsername(context: Context): String =
        prefs(context).getString(KEY_RELAY_USERNAME, "") ?: ""

    fun setRelayUsername(context: Context, username: String) {
        prefs(context).edit().putString(KEY_RELAY_USERNAME, username.trim()).apply()
    }

    fun getRelayAuthType(context: Context): String =
        prefs(context).getString(KEY_RELAY_AUTH_TYPE, RELAY_AUTH_PASSWORD)
            ?.takeIf { it == RELAY_AUTH_KEY || it == RELAY_AUTH_PASSWORD }
            ?: RELAY_AUTH_PASSWORD

    fun setRelayAuthType(context: Context, authType: String) {
        val safe = if (authType == RELAY_AUTH_KEY) RELAY_AUTH_KEY else RELAY_AUTH_PASSWORD
        prefs(context).edit().putString(KEY_RELAY_AUTH_TYPE, safe).apply()
    }

    fun getRelayKeyPath(context: Context): String =
        prefs(context).getString(KEY_RELAY_KEY_PATH, "") ?: ""

    fun setRelayKeyPath(context: Context, path: String) {
        prefs(context).edit().putString(KEY_RELAY_KEY_PATH, path).apply()
    }

    fun getRelayRemotePort(context: Context): Int =
        prefs(context).getInt(KEY_RELAY_REMOTE_PORT, DEFAULT_PROXY_PORT)
            .takeIf { it in 1..65535 } ?: DEFAULT_PROXY_PORT

    fun setRelayRemotePort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_RELAY_REMOTE_PORT, port.coerceIn(1, 65535)).apply()
    }

    fun isRelayAutoReconnect(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RELAY_AUTO_RECONNECT, true)

    fun setRelayAutoReconnect(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RELAY_AUTO_RECONNECT, enabled).apply()
    }

    fun isRelayAllowPrivileged(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RELAY_ALLOW_PRIVILEGED, false)

    fun setRelayAllowPrivileged(context: Context, allowed: Boolean) {
        prefs(context).edit().putBoolean(KEY_RELAY_ALLOW_PRIVILEGED, allowed).apply()
    }

    /**
     * Builds the in-memory relay config. [password] is supplied by the UI at
     * connect time and never persisted. [localPort] defaults to the current
     * reverse-proxy port since the relay exposes the whole proxy.
     */
    fun relayConfig(context: Context, password: String, localPort: Int = getProxyPort(context)): RelayConfig {
        val auth: RelayAuth = if (getRelayAuthType(context) == RELAY_AUTH_KEY) {
            RelayAuth.KeyFile(getRelayKeyPath(context))
        } else {
            RelayAuth.Password(password)
        }
        return RelayConfig(
            vpsHost = getRelayHost(context),
            sshPort = getRelaySshPort(context),
            username = getRelayUsername(context),
            auth = auth,
            remotePort = getRelayRemotePort(context),
            localPort = localPort,
            autoReconnect = isRelayAutoReconnect(context),
            allowPrivilegedRemotePort = isRelayAllowPrivileged(context),
        )
    }
}

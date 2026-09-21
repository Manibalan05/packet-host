package com.alan.app.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.attribute.PosixFilePermission
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** Lifecycle of the outbound SSH relay to the user's VPS. */
sealed interface RelayState {
    data object Disconnected : RelayState
    data object Connecting : RelayState
    data class Live(val detail: String) : RelayState
    data class Error(val message: String) : RelayState
}

/** Host key the VPS presented that is not yet trusted. Shown for user approval. */
data class PendingHostKey(
    val host: String,
    val fingerprint: String,
    val keyType: String,
    /** True when known_hosts has a DIFFERENT key for this host (possible MITM). */
    val changed: Boolean = false,
)

/**
 * Owns the outbound SSH connection to the user's VPS and the SSH REMOTE port
 * forward (VPS public port -> phone's local reverse-proxy port) over it.
 *
 * Pure-JVM + sshj; the only Android touchpoint is the [knownHostsFile] passed
 * in (normally filesDir/ssh/known_hosts). All network work runs on an
 * internal IO scope — never call from the main thread's caller side either,
 * [connect]/[disconnect] themselves are cheap and non-blocking.
 *
 * Host-key policy: keys are verified against [knownHostsFile] (created if
 * absent). An unknown/changed key is NEVER trusted silently — it surfaces on
 * [pendingHostKey] and the connection fails until the user approves it in the
 * UI, which appends it to known_hosts ([approvePendingHostKey]).
 */
class RelayManager(
    private val knownHostsFile: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow<RelayState>(RelayState.Disconnected)
    val state: StateFlow<RelayState> = _state

    private val _pendingHostKey = MutableStateFlow<PendingHostKey?>(null)
    val pendingHostKey: StateFlow<PendingHostKey?> = _pendingHostKey

    private val manualStop = AtomicBoolean(true)

    @Volatile
    private var loopJob: Job? = null

    @Volatile
    private var activeClient: SSHClient? = null

    @Volatile
    private var pendingKey: PublicKey? = null

    /**
     * Starts (or restarts) the relay loop for [cfg]. Returns failure without
     * touching the network when validation fails.
     */
    fun connect(cfg: RelayConfig): Result<Unit> {
        val errors = cfg.validate()
        if (errors.isNotEmpty()) return Result.failure(IllegalArgumentException(errors.joinToString("; ")))
        val password = (cfg.auth as? RelayAuth.Password)?.password.orEmpty()
        if (cfg.auth is RelayAuth.Password && password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Password is empty — enter your VPS password."))
        }
        manualStop.set(false)
        loopJob?.cancel()
        loopJob = scope.launch { runLoop(cfg) }
        return Result.success(Unit)
    }

    /** Stops the relay and cancels any reconnect attempts. */
    fun disconnect() {
        manualStop.set(true)
        loopJob?.cancel()
        loopJob = null
        runCatching { activeClient?.disconnect() }
        activeClient = null
        _state.value = RelayState.Disconnected
    }

    /**
     * Trusts the currently pending host key by appending it to known_hosts.
     * Returns false when there is nothing pending or the write fails.
     * The caller should call [connect] again afterwards.
     */
    fun approvePendingHostKey(): Boolean {
        val pending = _pendingHostKey.value ?: return false
        val key = pendingKey ?: return false
        return try {
            ensureKnownHostsFile()
            val kh = OpenSSHKnownHosts(knownHostsFile)
            kh.entries().add(
                OpenSSHKnownHosts.HostEntry(null, pending.host, KeyType.fromKey(key), key)
            )
            kh.write()
            chmod0600(knownHostsFile)
            _pendingHostKey.value = null
            pendingKey = null
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Discards the pending host key (connection stays down). */
    fun rejectPendingHostKey() {
        _pendingHostKey.value = null
        pendingKey = null
    }

    private suspend fun runLoop(cfg: RelayConfig) {
        var attempt = 0
        while (scope.isActive && !manualStop.get()) {
            _state.value = RelayState.Connecting
            try {
                holdSession(cfg)
                // holdSession returns only after the connection dropped.
                attempt = 0
                if (manualStop.get() || !cfg.autoReconnect) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_pendingHostKey.value != null) {
                    _state.value = RelayState.Error(
                        "Unknown VPS host key — approve it in the Relay screen, then reconnect."
                    )
                    break
                }
                if (manualStop.get() || !cfg.autoReconnect) {
                    _state.value = RelayState.Error(safeMsg(e, cfg))
                    break
                }
                attempt++
                val delayMs = min(30_000L, 1_000L * (1L shl min(attempt, 5)))
                _state.value = RelayState.Error("${safeMsg(e, cfg)} — retrying in ${delayMs / 1000}s")
                try {
                    delay(delayMs)
                } catch (e2: CancellationException) {
                    throw e2
                }
            }
        }
        if (manualStop.get() && _state.value is RelayState.Connecting) {
            _state.value = RelayState.Disconnected
        }
    }

    /**
     * Opens one SSH session, binds the remote forward, and suspends until the
     * connection drops or the job is cancelled.
     */
    private suspend fun holdSession(cfg: RelayConfig) {
        withContext(Dispatchers.IO) {
            val client = SSHClient()
            try {
                client.connectTimeout = 15_000
                ensureKnownHostsFile()
                client.addHostKeyVerifier(UiKnownHosts(knownHostsFile))
                client.connect(cfg.vpsHost.trim(), cfg.sshPort)
                when (val auth = cfg.auth) {
                    is RelayAuth.Password -> client.authPassword(cfg.username, auth.password)
                    is RelayAuth.KeyFile -> {
                        val keyFile = File(auth.path)
                        require(keyFile.isFile) { "Private-key file disappeared: ${auth.path}" }
                        chmod0600(keyFile)
                        val provider = if (auth.passphrase.isNullOrEmpty()) {
                            client.loadKeys(keyFile.absolutePath)
                        } else {
                            client.loadKeys(keyFile.absolutePath, auth.passphrase)
                        }
                        client.authPublickey(cfg.username, provider)
                    }
                }
                // Keep NAT / mobile-radio mappings warm; the VPS side also
                // gets ClientAliveInterval via relay/sshd_note.txt.
                runCatching { client.connection.keepAlive.keepAliveInterval = 30 }
                val forward = RemotePortForwarder.Forward("0.0.0.0", cfg.remotePort)
                val listener = SocketForwardingConnectListener(
                    InetSocketAddress("127.0.0.1", cfg.localPort)
                )
                // NOTE: 0.39.0 exposes the forwarder via getRemotePortForwarder()
                // (older docs called it newRemotePortForwarder()).
                client.remotePortForwarder.bind(forward, listener)
                activeClient = client
                _state.value = RelayState.Live(
                    "${cfg.forwardSpec()} via ${cfg.vpsHost.trim()}"
                )
                while (isActive && !manualStop.get() && client.isConnected) {
                    delay(2_000)
                }
                if (!manualStop.get() && !client.isConnected) {
                    throw java.io.IOException("SSH connection to ${cfg.vpsHost.trim()} dropped")
                }
            } finally {
                if (activeClient === client) activeClient = null
                runCatching { client.disconnect() }
                runCatching { client.close() }
            }
        }
    }

    /** Known-hosts verifier that records unknown keys for UI approval. */
    private inner class UiKnownHosts(file: File) : OpenSSHKnownHosts(file) {
        override fun hostKeyUnverifiableAction(hostname: String, key: PublicKey): Boolean {
            pendingKey = key
            _pendingHostKey.value = PendingHostKey(
                host = hostname,
                fingerprint = runCatching { SecurityUtils.getFingerprint(key) }.getOrDefault("unavailable"),
                keyType = runCatching { KeyType.fromKey(key).toString() }.getOrDefault("unknown"),
                changed = false,
            )
            return false // never trust silently; wait for explicit approval
        }

        override fun hostKeyChangedAction(hostname: String, key: PublicKey): Boolean {
            pendingKey = key
            _pendingHostKey.value = PendingHostKey(
                host = hostname,
                fingerprint = runCatching { SecurityUtils.getFingerprint(key) }.getOrDefault("unavailable"),
                keyType = runCatching { KeyType.fromKey(key).toString() }.getOrDefault("unknown"),
                changed = true,
            )
            return false
        }
    }

    private fun ensureKnownHostsFile() {
        knownHostsFile.parentFile?.mkdirs()
        if (!knownHostsFile.exists()) {
            knownHostsFile.createNewFile()
        }
        chmod0600(knownHostsFile)
    }

    private fun chmod0600(f: File) {
        // Best effort: private keys and known_hosts should not be world-readable.
        runCatching {
            f.setExecutable(false, false)
            f.setReadable(false, false)
            f.setWritable(false, false)
            f.setReadable(true, true)
            f.setWritable(true, true)
        }
        runCatching {
            java.nio.file.Files.setPosixFilePermissions(
                f.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        }
    }

    /** Short error text with any credential material redacted. */
    private fun safeMsg(e: Exception, cfg: RelayConfig): String {
        var msg = e.message?.take(220) ?: e.javaClass.simpleName
        val secrets = mutableListOf<String>()
        (cfg.auth as? RelayAuth.Password)?.password?.takeIf { it.isNotEmpty() }?.let(secrets::add)
        (cfg.auth as? RelayAuth.KeyFile)?.passphrase?.takeIf { it.isNotEmpty() }?.let(secrets::add)
        for (s in secrets) {
            if (s.length >= 3) msg = msg.replace(s, "***")
        }
        return msg.ifBlank { e.javaClass.simpleName }
    }
}

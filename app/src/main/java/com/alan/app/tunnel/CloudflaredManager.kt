package com.alan.app.tunnel

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "CloudflaredManager"

/** Matches a quick-tunnel public URL. `api.trycloudflare.com` is the API endpoint, not a tunnel. */
private val QUICK_TUNNEL_URL = Regex("""https://(?!api\.)[A-Za-z0-9-]+\.trycloudflare\.com""")

/**
 * Pure, Android-free parser for cloudflared's stdout.
 *
 * Returns the FIRST `https://[random].trycloudflare.com` URL on the line, or null
 * when the line carries no tunnel URL. Plain-`http://` lookalikes are
 * rejected (only TLS `https://` URLs are ever advertised). Null-safe.
 */
fun parsePublicUrl(line: String?): String? {
    if (line.isNullOrBlank()) return null
    return QUICK_TUNNEL_URL.find(line)?.value
}

/**
 * Owns the cloudflared binary and one-shot quick-tunnel processes.
 *
 * Packaging (mirrors the reference project): the real binaries live in
 * `app/src/main/jniLibs/<abi>/libcloudflared.so` so the installer extracts
 * them into nativeLibraryDir. [binaryFile] returns that copy DIRECTLY —
 * Android 10+ SELinux W^X forbids executing anything under filesDir, so the
 * binary is never copied there. Then [startQuickTunnel] runs:
 *
 *   cloudflared tunnel --url http://127.0.0.1:<port> --no-autoupdate
 *
 * App servers stay bound to 127.0.0.1; the tunnel reads the local port.
 * Long-lived supervision (reconnect, notification, DB updates) lives in
 * [com.alan.app.engine.TunnelService], not here.
 */
class CloudflaredManager(private val context: Context) {

    /**
     * Resolves the executable cloudflared binary.
     *
     * Android 10+ enforces SELinux W^X: NOTHING under filesDir can be
     * execve()'d, no matter the chmod bits. The only exec-allowed location
     * is nativeLibraryDir, populated at install from jniLibs (which is why
     * `jniLibs.useLegacyPackaging` is on). So this returns the
     * nativeLibraryDir copy DIRECTLY (`.../lib/arm64/libcloudflared.so` —
     * execve does not care about the .so extension). The assets fallback is
     * kept only as a diagnostic; a filesDir copy can never execute.
     */
    fun binaryFile(context: Context = this.context): File {
        // 1. jniLibs extraction — execute IN PLACE, never copy to filesDir
        // (W^X: copies there can never execute).
        try {
            val libName = System.mapLibraryName("cloudflared")
            val native = File(context.applicationInfo.nativeLibraryDir, libName)
            if (native.exists() && native.length() > 0 && native.canExecute()) {
                Log.i(TAG, "Using cloudflared in nativeLibraryDir (${native.length()} bytes)")
                return native
            }
            Log.w(TAG, "nativeLibraryDir copy unusable: exists=${native.exists()} exec=${native.canExecute()}")
        } catch (e: Exception) {
            Log.w(TAG, "jniLibs check failed: ${e.message}")
        }

        // Anything copied to filesDir could never execute (W^X), so there are
        // no further fallbacks — diagnose what the installer actually did.
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val present = try {
            libDir.list()?.joinToString(",") ?: "<unreadable>"
        } catch (e: Exception) {
            "<unreadable: ${e.message}>"
        }

        throw IllegalStateException(
            "cloudflared missing from nativeLibraryDir ($libDir: $present). " +
                "The APK must be built with jniLibs.useLegacyPackaging=true so the " +
                "installer extracts lib/<abi>/libcloudflared.so — then reinstall."
        )
    }

    /**
     * Starts one anonymous quick tunnel for a server already listening on
     * 127.0.0.1:[port]. Merges stderr into stdout so callers parse a single
     * stream with [parsePublicUrl]. The caller owns the returned process and
     * must eventually pass it to [stop].
     */
    fun startQuickTunnel(port: Int): Process {
        val bin = binaryFile()
        require(bin.exists()) { "cloudflared binary missing at ${bin.absolutePath}" }
        val cmd = listOf(
            bin.absolutePath, "tunnel",
            "--url", "http://127.0.0.1:$port",
            "--no-autoupdate"
        )
        Log.i(TAG, "Starting quick tunnel for 127.0.0.1:$port")
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        // Cloudflared needs a writable HOME; never log the full env map.
        pb.environment()["HOME"] = context.filesDir.absolutePath
        // cloudflared's TLS stack does not reliably use Android's system CA
        // store (seen: x509 unknown-authority on api.trycloudflare.com), so
        // ship the Mozilla bundle and point Go at it explicitly.
        pb.environment()["SSL_CERT_FILE"] = ensureCaBundle().absolutePath
        return pb.start()
    }

    /**
     * Copies `assets/cacert.pem` (Mozilla CA bundle) to
     * `filesDir/certs/cacert.pem` once. Returns the file (existing copy is
     * reused; a corrupt copy is recopied).
     */
    private fun ensureCaBundle(): File {
        val dir = File(context.filesDir, "certs").apply { mkdirs() }
        val out = File(dir, "cacert.pem")
        try {
            context.assets.open("cacert.pem").use { input ->
                val bytes = input.readBytes()
                if (!out.exists() || out.length() != bytes.size.toLong()) {
                    out.writeBytes(bytes)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CA bundle extract failed: ${e.message}")
        }
        return out
    }

    /** Destroys a tunnel process, escalating to forcible kill after a grace period. Null-safe. */
    fun stop(process: Process?) {
        if (process == null) return
        try {
            process.destroy()
            val deadline = System.currentTimeMillis() + 3000
            while (process.isAlive && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            if (process.isAlive) process.destroyForcibly()
        } catch (_: Exception) {
        }
    }
}

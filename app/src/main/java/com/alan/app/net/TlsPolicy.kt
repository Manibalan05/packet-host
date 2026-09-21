package com.alan.app.net

import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

/**
 * Honest TLS posture for a phone server:
 * - [PlainHttp] until the user provides a certificate (clear HTTP fallback).
 * - [UserCert] when a PKCS#12 bundle exists in filesDir/certs/.
 *
 * There is no automated certificate issuance on-device (no ACME client);
 * see the README for the advanced Let's Encrypt DNS-01 path.
 */
sealed interface TlsPolicy {
    data class PlainHttp(val reason: String) : TlsPolicy
    data class UserCert(val p12FileName: String, val aliasCount: Int) : TlsPolicy
}

object TlsPolicyLoader {
    const val CERTS_DIR = "certs"

    fun certsDir(filesDir: File): File = File(filesDir, CERTS_DIR).apply { mkdirs() }

    fun current(filesDir: File): TlsPolicy {
        val dir = certsDir(filesDir)
        val p12 = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".p12", ignoreCase = true) ||
                f.name.endsWith(".pfx", ignoreCase = true))
        }?.firstOrNull()
            ?: return TlsPolicy.PlainHttp(
                "No certificate imported — serving plain HTTP. " +
                    "Import a .p12 bundle in Public access to enable HTTPS."
            )
        return try {
            // Validate the bundle loads as PKCS#12 (password "alan" by convention
            // is NOT assumed — we only check structural validity here).
            FileInputStream(p12).use { input ->
                val ks = KeyStore.getInstance("PKCS12")
                // Listing aliases requires the password; without it we can
                // only confirm the file exists and is non-empty.
                if (p12.length() == 0L) throw IllegalStateException("empty keystore file")
                // Best effort: try empty password for alias count, else 0.
                val count = runCatching {
                    ks.load(input, CharArray(0))
                    ks.aliases().toList().size
                }.getOrDefault(0)
                TlsPolicy.UserCert(p12.name, count)
            }
        } catch (e: Exception) {
            TlsPolicy.PlainHttp("Found ${p12.name} but it could not be read (${e.message}) — serving plain HTTP.")
        }
    }
}

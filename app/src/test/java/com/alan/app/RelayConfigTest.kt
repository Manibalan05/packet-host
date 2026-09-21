package com.alan.app

import com.alan.app.net.RelayAuth
import com.alan.app.net.RelayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RelayConfigTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun base() = RelayConfig(
        vpsHost = "203.0.113.10",
        sshPort = 22,
        username = "alan",
        auth = RelayAuth.Password("secret"),
        remotePort = 8080,
        localPort = 8080,
    )

    @Test
    fun validConfigHasNoErrors() {
        assertTrue(base().validate().isEmpty())
    }

    @Test
    fun blankHostIsAnError() {
        val errors = base().copy(vpsHost = "   ").validate()
        assertTrue(errors.any { it.contains("host", ignoreCase = true) })
    }

    @Test
    fun blankUserIsAnError() {
        val errors = base().copy(username = "").validate()
        assertTrue(errors.any { it.contains("username", ignoreCase = true) })
    }

    @Test
    fun badPortsAreErrors() {
        assertTrue(base().copy(sshPort = 0).validate().any { it.contains("SSH port") })
        assertTrue(base().copy(sshPort = 70000).validate().any { it.contains("SSH port") })
        assertTrue(base().copy(remotePort = 0).validate().any { it.contains("Remote") })
        assertTrue(base().copy(remotePort = 99999).validate().any { it.contains("Remote") })
        assertTrue(base().copy(localPort = -1).validate().any { it.contains("Local") })
    }

    @Test
    fun privilegedRemotePortNeedsExplicitConfirmation() {
        val errors = base().copy(remotePort = 443).validate()
        assertTrue(errors.any { it.contains("privileged") })
        val allowed = base().copy(remotePort = 443, allowPrivilegedRemotePort = true).validate()
        assertTrue(allowed.isEmpty())
        // Boundary: 1024 is fine without confirmation.
        assertTrue(base().copy(remotePort = 1024).validate().isEmpty())
    }

    @Test
    fun missingKeyFileIsAnError() {
        val errors = base().copy(
            auth = RelayAuth.KeyFile("/does/not/exist/user_key")
        ).validate()
        assertTrue(errors.any { it.contains("not found", ignoreCase = true) })
    }

    @Test
    fun existingKeyFilePassesValidation() {
        val key = temp.newFile("id_ed25519")
        key.writeText("fake-key-bytes")
        val errors = base().copy(auth = RelayAuth.KeyFile(key.absolutePath)).validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun blankKeyPathIsAnError() {
        val errors = base().copy(auth = RelayAuth.KeyFile("")).validate()
        assertTrue(errors.any { it.contains("key", ignoreCase = true) })
    }

    @Test
    fun forwardSpecFormat() {
        assertEquals("0.0.0.0:8080 -> 127.0.0.1:8080", base().forwardSpec())
        assertEquals(
            "0.0.0.0:9000 -> 127.0.0.1:8080",
            base().copy(remotePort = 9000, localPort = 8080).forwardSpec()
        )
    }
}

package com.alan.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alan.app.AlanApp
import com.alan.app.engine.RelayService
import com.alan.app.net.RelayState
import com.alan.app.ui.navigation.Route
import com.alan.app.ui.theme.RelayBadge
import com.alan.app.ui.theme.SectionHeader
import com.alan.app.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayScreen(nav: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AlanApp }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf(Prefs.getRelayHost(context)) }
    var sshPortText by remember { mutableStateOf(Prefs.getRelaySshPort(context).toString()) }
    var username by remember { mutableStateOf(Prefs.getRelayUsername(context)) }
    var authType by remember { mutableStateOf(Prefs.getRelayAuthType(context)) }
    var password by remember { mutableStateOf("") }
    var keyPassphrase by remember { mutableStateOf("") }
    var keyPath by remember { mutableStateOf(Prefs.getRelayKeyPath(context)) }
    var remotePortText by remember { mutableStateOf(Prefs.getRelayRemotePort(context).toString()) }
    var autoReconnect by remember { mutableStateOf(Prefs.isRelayAutoReconnect(context)) }
    var allowPrivileged by remember { mutableStateOf(Prefs.isRelayAllowPrivileged(context)) }
    var formErrors by remember { mutableStateOf(emptyList<String>()) }

    val proxyPort = remember { Prefs.getProxyPort(context) }
    val relayState by app.relayManager.state.collectAsState()
    val pending by app.relayManager.pendingHostKey.collectAsState()

    fun persistForm() {
        Prefs.setRelayHost(context, host)
        Prefs.setRelaySshPort(context, sshPortText.toIntOrNull() ?: 22)
        Prefs.setRelayUsername(context, username)
        Prefs.setRelayAuthType(context, authType)
        Prefs.setRelayKeyPath(context, keyPath)
        Prefs.setRelayRemotePort(context, remotePortText.toIntOrNull() ?: Prefs.DEFAULT_PROXY_PORT)
        Prefs.setRelayAutoReconnect(context, autoReconnect)
        Prefs.setRelayAllowPrivileged(context, allowPrivileged)
    }

    fun doConnect() {
        persistForm()
        val cfg = Prefs.relayConfig(context, password)
        val errors = cfg.validate().toMutableList()
        if (authType == Prefs.RELAY_AUTH_PASSWORD && password.isEmpty()) {
            errors += "Password is empty — enter your VPS password."
        }
        formErrors = errors
        if (errors.isNotEmpty()) return
        val intent = Intent(context, RelayService::class.java).apply {
            action = RelayService.ACTION_START
            if (authType == Prefs.RELAY_AUTH_PASSWORD) {
                putExtra(RelayService.EXTRA_PASSWORD, password)
            }
            if (authType == Prefs.RELAY_AUTH_KEY && keyPassphrase.isNotEmpty()) {
                putExtra(RelayService.EXTRA_KEY_PASSPHRASE, keyPassphrase)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Toast.makeText(context, "Relay starting…", Toast.LENGTH_SHORT).show()
    }

    fun doDisconnect() {
        val intent = Intent(context, RelayService::class.java).apply {
            action = RelayService.ACTION_STOP
        }
        context.startService(intent)
    }

    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val sshDir = File(context.filesDir, "ssh")
                sshDir.mkdirs()
                val dest = File(sshDir, "user_key")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } ?: throw IllegalStateException("Cannot open key file")
                dest.setExecutable(false, false)
                dest.setReadable(false, false)
                dest.setWritable(false, false)
                dest.setReadable(true, true)
                dest.setWritable(true, true)
                Prefs.setRelayKeyPath(context, dest.absolutePath)
                withContext(Dispatchers.Main) {
                    keyPath = dest.absolutePath
                    Toast.makeText(context, "Private key imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Key import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPS relay (whole internet)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        val scheme = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Status ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(icon = Icons.Filled.Cable, title = "Status")
                    RelayBadge(state = relayState)
                    val statusText = when (val s = relayState) {
                        is RelayState.Disconnected -> "Stopped"
                        is RelayState.Connecting -> "Connecting…"
                        is RelayState.Live -> "LIVE — ${s.detail}"
                        is RelayState.Error -> "Error — ${s.message}"
                    }
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Forward: 0.0.0.0:${remotePortText.ifBlank { "?" }} -> " +
                            "127.0.0.1:$proxyPort (whole proxy, all sites)",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        "Outbound SSH only — no inbound ports needed on the phone, " +
                            "so NAT/CGNAT cannot block it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            // ---- Host-key approval banner ----
            pending?.let { p ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (p.changed) "Host key CHANGED — verify before trusting"
                            else "Unknown host key — approval needed",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("Host: ${p.host}", style = MaterialTheme.typography.bodySmall)
                        Text("Type: ${p.keyType}", style = MaterialTheme.typography.bodySmall)
                        Text("Fingerprint: ${p.fingerprint}", style = MaterialTheme.typography.bodySmall)
                        if (p.changed) {
                            Text(
                                "The VPS presents a different key than known_hosts records. " +
                                    "Approve ONLY if you rotated the key yourself; otherwise " +
                                    "someone may be intercepting the connection.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "Compare with `ssh-keygen -l -f /etc/ssh/ssh_host_*_key.pub` " +
                                    "on your VPS, then approve to save it.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val ok = app.relayManager.approvePendingHostKey()
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            Toast.makeText(
                                                context,
                                                "Host key approved — reconnecting",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            doConnect()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Could not save host key",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }) { Text("Approve & reconnect") }
                            OutlinedButton(onClick = { app.relayManager.rejectPendingHostKey() }) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }

            // ---- Server ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(icon = Icons.Filled.Dns, title = "Your VPS")
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("VPS host (IP or domain)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sshPortText,
                        onValueChange = { sshPortText = it.filter(Char::isDigit).take(5) },
                        label = { Text("SSH port (default 22)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("SSH username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ---- Auth ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(icon = Icons.Filled.VpnKey, title = "SSH credentials")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = authType == Prefs.RELAY_AUTH_PASSWORD,
                            onClick = { authType = Prefs.RELAY_AUTH_PASSWORD }
                        )
                        Text("Password", Modifier.padding(end = 16.dp))
                        RadioButton(
                            selected = authType == Prefs.RELAY_AUTH_KEY,
                            onClick = { authType = Prefs.RELAY_AUTH_KEY }
                        )
                        Text("Private key")
                    }
                    if (authType == Prefs.RELAY_AUTH_PASSWORD) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("VPS password (memory only, never stored)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    } else {
                        Text(
                            if (keyPath.isBlank()) "No key imported yet."
                            else "Key: $keyPath",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { keyPicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Import private-key file") }
                        OutlinedTextField(
                            value = keyPassphrase,
                            onValueChange = { keyPassphrase = it },
                            label = { Text("Key passphrase (optional, memory only)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                }
            }

            // ---- Forwarding ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(icon = Icons.Filled.Cable, title = "Forwarding")
                    OutlinedTextField(
                        value = remotePortText,
                        onValueChange = { remotePortText = it.filter(Char::isDigit).take(5) },
                        label = { Text("VPS public port (default 8080)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "Local proxy port: $proxyPort (change it in Settings; " +
                            "the relay always exposes the whole proxy).",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    val remotePort = remotePortText.toIntOrNull()
                    if (remotePort != null && remotePort in 1..1023) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = allowPrivileged, onCheckedChange = { allowPrivileged = it })
                            Text("VPS SSH user is root (needed for ports <1024)")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto-reconnect")
                        Switch(checked = autoReconnect, onCheckedChange = { autoReconnect = it })
                    }
                }
            }

            if (formErrors.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        formErrors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { doConnect() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Connect")
                }
                OutlinedButton(onClick = { doDisconnect() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("Disconnect")
                }
            }
            OutlinedButton(
                onClick = { nav.navigate(Route.Public.route) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Public-access checklist (LAN / port-forward / VPS)") }
        }
    }
}

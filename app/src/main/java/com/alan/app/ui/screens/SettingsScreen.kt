package com.alan.app.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alan.app.AlanApp
import com.alan.app.engine.ProxyService
import com.alan.app.engine.ServerService
import com.alan.app.net.NetworkUtils
import com.alan.app.ui.theme.SectionHeader
import com.alan.app.ui.theme.StatusBadge
import com.alan.app.ui.theme.liveContainer
import com.alan.app.ui.theme.onLiveContainer
import com.alan.app.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AlanApp }
    val scope = rememberCoroutineScope()
    var proxyPortText by remember { mutableStateOf(Prefs.getProxyPort(context).toString()) }
    var autoStart by remember { mutableStateOf(Prefs.isAutoStartEnabled(context)) }
    val sitesRoot = remember { File(context.filesDir, "sites") }
    val proxyRunning = app.proxyManager.isRunning()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(icon = Icons.Filled.Router, title = "Reverse proxy")
                    if (proxyRunning) {
                        StatusBadge("PROXY LIVE", liveContainer(), onLiveContainer())
                    } else {
                        StatusBadge(
                            "PROXY STOPPED",
                            scheme.surfaceVariant,
                            scheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = proxyPortText,
                        onValueChange = { proxyPortText = it.filter(Char::isDigit).take(5) },
                        label = { Text("Proxy port (default 8080; privileged ports need root)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val port = proxyPortText.toIntOrNull()?.coerceIn(1, 65535)
                                ?: Prefs.DEFAULT_PROXY_PORT
                            Prefs.setProxyPort(context, port)
                            val intent = Intent(context, ProxyService::class.java).apply {
                                action = ProxyService.ACTION_START
                                putExtra(ProxyService.EXTRA_PORT, port)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            Toast.makeText(context, "Proxy starting on :$port", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("Start proxy")
                        }
                        OutlinedButton(onClick = {
                            val intent = Intent(context, ProxyService::class.java).apply {
                                action = ProxyService.ACTION_STOP
                            }
                            context.startService(intent)
                        }) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Text("Stop proxy")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Auto-start proxy on boot",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary
                        )
                        Text(
                            "Bring the entry point back after a reboot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            Prefs.setAutoStartEnabled(context, it)
                        }
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(icon = Icons.Filled.Storage, title = "Storage")
                    val lanIp = remember { NetworkUtils.getDetailedInfo(context).primaryIp }
                    Text(
                        "Sites dir: ${sitesRoot.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        "Sites stored: ${
                            sitesRoot.listFiles()?.size ?: 0
                        } folders · LAN IP: ${lanIp ?: "unknown"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                val intent = Intent(context, ServerService::class.java).apply {
                                    action = ServerService.ACTION_STOP_ALL
                                }
                                context.startService(intent)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("Stop all servers")
                    }
                }
            }
        }
    }
}

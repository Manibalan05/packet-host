package com.alan.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alan.app.AlanApp
import com.alan.app.net.NetworkUtils
import com.alan.app.net.PublicAccess
import com.alan.app.net.RelayState
import com.alan.app.net.TlsPolicy
import com.alan.app.net.TlsPolicyLoader
import com.alan.app.ui.navigation.Route
import com.alan.app.ui.theme.AlanBrand
import com.alan.app.ui.theme.faultColor
import com.alan.app.ui.theme.liveColor
import com.alan.app.ui.theme.startingColor
import com.alan.app.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicAccessScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember { NetworkUtils.getDetailedInfo(context) }
    val proxyPort = remember { Prefs.getProxyPort(context) }
    var tls by remember { mutableStateOf<TlsPolicy?>(null) }
    val app = remember { context.applicationContext as AlanApp }
    val relayState by app.relayManager.state.collectAsState()

    fun reloadTls() {
        scope.launch(Dispatchers.IO) {
            val policy = TlsPolicyLoader.current(context.filesDir)
            withContext(Dispatchers.Main) { tls = policy }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { reloadTls() }

    val p12Picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val dest = File(TlsPolicyLoader.certsDir(context.filesDir), "user-cert.p12")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } ?: throw IllegalStateException("Cannot open certificate file")
                reloadTls()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Certificate imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Public access", fontWeight = FontWeight.Bold) },
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
            // Hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(AlanBrand.GradientStart, AlanBrand.GradientEnd)))
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = AlanBrand.OnGradient.copy(alpha = 0.18f)) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            tint = AlanBrand.OnGradient,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            "Put your sites on the internet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AlanBrand.OnGradient
                        )
                        Text(
                            "Entry: http://${info.primaryIp ?: "<phone-ip>"}:$proxyPort/__alan_health",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlanBrand.OnGradient.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            GuideCard(
                step = "•",
                icon = Icons.Filled.Language,
                title = "Direct hosting (self-hosted PaaS style)",
                body = listOf(
                    "Wi-Fi IP: ${info.wifiIp ?: "—"}",
                    "Hotspot IP: ${info.hotspotIp ?: "— (best effort)"}",
                    "Primary: ${info.primaryIp ?: "unknown"}"
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = scheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Free public link (quick tunnel)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSecondaryContainer
                        )
                    }
                    Text(
                        "Anonymous Cloudflare URL, no account and no VPS needed. " +
                            "Random address, best for demos and quick shares.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    PublicAccess.quickTunnelSteps.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSecondaryContainer
                        )
                    }
                    FilledTonalButton(
                        onClick = { nav.navigate(Route.Home.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open a site to Host it") }
                }
            }

            GuideCard(
                step = "1",
                icon = Icons.Filled.Router,
                title = "Port-forward this phone",
                body = PublicAccess.portForwardSteps
            )

            GuideCard(
                step = "2",
                icon = Icons.Filled.Dns,
                title = "Dynamic DNS",
                body = PublicAccess.ddnsSteps
            )

            GuideCard(
                step = "3",
                icon = Icons.Filled.Language,
                title = "Custom domains",
                body = PublicAccess.customDomainSteps
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.VpnKey,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Whole internet (your VPS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary
                        )
                    }
                    Text(
                        "No router access? Behind CGNAT? The phone opens an OUTBOUND " +
                            "SSH connection to your own VPS and asks it to forward a " +
                            "public VPS port back to this phone's proxy. " +
                            "Outbound punches through NAT/CGNAT; visitors use your " +
                            "domain with automatic HTTPS from Caddy on the VPS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    PublicAccess.vpsRelaySteps.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    val relayLabel = when (val s = relayState) {
                        is RelayState.Disconnected -> "Relay: stopped"
                        is RelayState.Connecting -> "Relay: connecting…"
                        is RelayState.Live -> "Relay: LIVE — ${s.detail}"
                        is RelayState.Error -> "Relay: error — ${s.message}"
                    }
                    val relayColor = when (relayState) {
                        is RelayState.Live -> liveColor()
                        is RelayState.Connecting -> startingColor()
                        is RelayState.Error -> faultColor()
                        is RelayState.Disconnected -> scheme.outline
                    }
                    Text(
                        relayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = relayColor
                    )
                    FilledTonalButton(
                        onClick = { nav.navigate(Route.Relay.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Set up VPS relay") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Https,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "TLS (your certificate)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary
                        )
                    }
                    when (val policy = tls) {
                        null -> Text("Checking…")
                        is TlsPolicy.PlainHttp -> Text(policy.reason, style = MaterialTheme.typography.bodySmall)
                        is TlsPolicy.UserCert -> Text(
                            "Certificate present: ${policy.p12FileName}. " +
                                "Serve HTTPS by terminating TLS in front of the proxy " +
                                "(e.g. router or reverse-proxy host with this bundle).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "Honesty note: Alan serves plain HTTP by default. There is no " +
                            "one-tap certificate issuance on-device. Advanced path: issue a " +
                            "Let's Encrypt certificate via DNS-01 on a computer, export .p12, " +
                            "and import it here as proof of ownership.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { p12Picker.launch("application/x-pkcs12") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import .p12 certificate") }
                }
            }
        }
    }
}

@Composable
private fun GuideCard(
    step: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: List<String>
) {
    val scheme = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = CircleShape, color = scheme.primaryContainer) {
                    Text(
                        step,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary
                    )
                }
            }
            body.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

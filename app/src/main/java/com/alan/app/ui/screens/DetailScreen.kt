package com.alan.app.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alan.app.AlanApp
import com.alan.app.data.Site
import com.alan.app.data.SiteStatus
import com.alan.app.data.SiteType
import com.alan.app.engine.ProxyService
import com.alan.app.engine.ServerService
import com.alan.app.engine.StaticServer
import com.alan.app.engine.StaticServerManager
import com.alan.app.engine.TunnelService
import com.alan.app.net.NetworkUtils
import com.alan.app.tunnel.TunnelState
import com.alan.app.ui.navigation.Route
import com.alan.app.ui.theme.AlanBrand
import com.alan.app.ui.theme.SectionHeader
import com.alan.app.ui.theme.SiteStatusBadges
import com.alan.app.ui.theme.StatusDot
import com.alan.app.ui.theme.TunnelBadge
import com.alan.app.ui.theme.faultColor
import com.alan.app.ui.theme.liveContainer
import com.alan.app.ui.theme.onLiveContainer
import com.alan.app.ui.theme.siteStatusDotColor
import com.alan.app.ui.theme.startingColor
import com.alan.app.ui.theme.startingContainer
import com.alan.app.util.Prefs
import com.alan.app.util.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(nav: NavController, siteId: String) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AlanApp }
    val scope = rememberCoroutineScope()
    val site by app.repository.observeById(siteId).collectAsState(initial = null)
    var busy by remember { mutableStateOf(false) }
    var showWarning by remember { mutableStateOf(false) }
    var pendingRun by remember { mutableStateOf(false) }
    var showHostWarning by remember { mutableStateOf(false) }
    var pendingHost by remember { mutableStateOf(false) }
    val tunnelState by TunnelService.stateFor(siteId).collectAsState(initial = TunnelState.Idle)

    var hostRule by remember { mutableStateOf<String?>(null) }
    var pathPrefix by remember { mutableStateOf<String?>(null) }
    var envJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(site?.hostRule) { if (hostRule == null) hostRule = site?.hostRule ?: "" }
    LaunchedEffect(site?.pathPrefix) { if (pathPrefix == null) pathPrefix = site?.pathPrefix ?: "/" }
    LaunchedEffect(site?.envJson) { if (envJson == null) envJson = site?.envJson ?: "{}" }

    fun parseEnv(raw: String): Map<String, String> {
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.optString(k, "")) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun doRun() {
        val s = site ?: return
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                val logger = app.runtimeManager.loggerFor(s.id)
                when (s.type) {
                    SiteType.STATIC -> {
                        val intent = Intent(context, ServerService::class.java).apply {
                            action = ServerService.ACTION_START_STATIC
                            putExtra(ServerService.EXTRA_SITE_ID, s.id)
                            putExtra(ServerService.EXTRA_PORT, s.port)
                            putExtra(ServerService.EXTRA_DIR, s.dirPath)
                            putExtra(ServerService.EXTRA_TITLE, "Serving ${s.name}")
                            putExtra(ServerService.EXTRA_TEXT, "http://127.0.0.1:${s.port}")
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                    SiteType.NODE -> {
                        val res = app.runtimeManager.startNode(
                            s.id, File(s.dirPath), s.port, "", parseEnv(s.envJson)
                        )
                        res.onFailure { throw it as? Exception ?: RuntimeException(it.message) }
                        app.repository.upsert(
                            s.copy(status = SiteStatus.RUNNING, uptimeStartMs = System.currentTimeMillis())
                        )
                    }
                    SiteType.PYTHON -> {
                        val entry = detectEntry(s)
                        val res = app.runtimeManager.startPython(
                            s.id, File(s.dirPath), s.port, entry, parseEnv(s.envJson)
                        )
                        res.onFailure { throw it as? Exception ?: RuntimeException(it.message) }
                        app.repository.upsert(
                            s.copy(status = SiteStatus.RUNNING, uptimeStartMs = System.currentTimeMillis())
                        )
                    }
                    SiteType.PHP -> {
                        val res = app.runtimeManager.startPhp(
                            s.id, File(s.dirPath), s.port, parseEnv(s.envJson)
                        )
                        res.onFailure { throw it as? Exception ?: RuntimeException(it.message) }
                        app.repository.upsert(
                            s.copy(status = SiteStatus.RUNNING, uptimeStartMs = System.currentTimeMillis())
                        )
                    }
                    SiteType.UNKNOWN -> {
                        // Serve unknown trees as static so something always works.
                        val server = StaticServer(File(s.dirPath), s.port, logger) {
                            scope.launch(Dispatchers.IO) {
                                runCatching { app.repository.incrementRequestCount(s.id) }
                            }
                        }
                        server.start()
                        StaticServerManager.put(s.id, server)
                        app.repository.upsert(
                            s.copy(status = SiteStatus.RUNNING, uptimeStartMs = System.currentTimeMillis())
                        )
                    }
                }
                ensureProxy(context, app)
                app.proxyManager.refresh(app.repository)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${s.name} running on :${s.port}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                app.repository.upsert(s.copy(status = SiteStatus.ERROR))
                app.runtimeManager.loggerFor(s.id).append("Run failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Run failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                busy = false
            }
        }
    }

    fun doStop() {
        val s = site ?: return
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                TunnelService.stopSite(context, s.id)
                StaticServerManager.stop(s.id)
                app.runtimeManager.stop(s.id)
                app.repository.upsert(
                    s.copy(status = SiteStatus.STOPPED, uptimeStartMs = null, hosted = false, publicUrl = "")
                )
                app.proxyManager.refresh(app.repository)
            } finally {
                busy = false
            }
        }
    }

    fun doHost() {
        val s = site ?: return
        if (s.status != SiteStatus.RUNNING) {
            // Start the local server first, then open the tunnel once it is up.
            doRun()
            scope.launch(Dispatchers.IO) {
                var waited = 0
                while (waited < 15_000) {
                    delay(500)
                    waited += 500
                    if (app.repository.getById(s.id)?.status == SiteStatus.RUNNING) break
                }
                val cur = app.repository.getById(s.id)
                if (cur?.status == SiteStatus.RUNNING) {
                    TunnelService.startSite(context, s.id, cur.port)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Starting free public link… (~3-8s)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Server did not start — fix Run first, then Host",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            return
        }
        TunnelService.startSite(context, s.id, s.port)
        Toast.makeText(context, "Starting free public link… (~3-8s)", Toast.LENGTH_SHORT).show()
    }

    fun doUnhost() {
        val s = site ?: return
        scope.launch(Dispatchers.IO) {
            TunnelService.stopSite(context, s.id)
            runCatching { app.repository.upsert(s.copy(hosted = false, publicUrl = "")) }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Unhosted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(site?.name ?: "Site", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        val s = site
        if (s == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Loading…")
            }
            return@Scaffold
        }
        val scheme = MaterialTheme.colorScheme
        val lanIp = remember { NetworkUtils.getDetailedInfo(context).primaryIp }
        val proxyPort = remember { Prefs.getProxyPort(context) }
        val isHosted = s.hosted && s.publicUrl.isNotBlank()
        val logger = remember(s.id) { app.runtimeManager.loggerFor(s.id) }
        val logLines by logger.flow.collectAsState()

        fun copyPublicUrl() {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("public-url", s.publicUrl))
            Toast.makeText(context, "Public URL copied", Toast.LENGTH_SHORT).show()
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Status overview ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusDot(siteStatusDotColor(s.status, isHosted))
                        Text(
                            s.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SiteStatusBadges(status = s.status, hosted = isHosted)
                    HorizontalDivider()
                    InfoLine(label = "Type", value = s.type.toString())
                    InfoLine(label = "Local", value = "http://127.0.0.1:${s.port}")
                    InfoLine(
                        label = "Via proxy",
                        value = "http://${lanIp ?: "<phone-ip>"}:$proxyPort${s.pathPrefix}"
                    )
                    InfoLine(label = "Requests", value = "${s.requestCount}")
                    InfoLine(label = "Health", value = s.lastHealth)
                    InfoLine(
                        label = "Uptime since",
                        value = s.uptimeStartMs?.let { java.util.Date(it).toString() } ?: "—"
                    )
                }
            }

            // ---- Host CTA ----
            Button(
                onClick = {
                    if (isHosted) {
                        doUnhost()
                    } else if (Prefs.isFirstHostWarningNeeded(context)) {
                        pendingHost = true
                        showHostWarning = true
                    } else {
                        doHost()
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isHosted) {
                    ButtonDefaults.buttonColors(
                        containerColor = liveContainer(),
                        contentColor = onLiveContainer()
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    if (isHosted) Icons.Filled.CloudOff else Icons.Filled.CloudUpload,
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (isHosted) "Unhost (stop public link)" else "Host (free public link)")
            }

            // ---- Public tunnel timeline ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(icon = Icons.Filled.Public, title = "Public tunnel")
                    TunnelBadge(state = tunnelState)
                    when (val ts = tunnelState) {
                        is TunnelState.Starting -> LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = startingColor(),
                            trackColor = startingContainer()
                        )
                        is TunnelState.Live -> {
                            if (!isHosted) {
                                Text(
                                    "Tunnel live at ${ts.url}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                        is TunnelState.Error -> Text(
                            "Tunnel: ${ts.msg}",
                            color = scheme.error
                        )
                        is TunnelState.Idle -> {
                            if (!isHosted) {
                                Text(
                                    "No tunnel yet — tap Host for a free public link.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---- Live public URL card ----
            if (isHosted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = liveContainer())
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = onLiveContainer()
                            )
                            Text(
                                "Live public link — tap URL or button to copy",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = onLiveContainer()
                            )
                        }
                        Text(
                            s.publicUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onLiveContainer(),
                            modifier = Modifier.clickable { copyPublicUrl() }
                        )
                        FilledTonalButton(onClick = { copyPublicUrl() }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Copy URL")
                        }
                    }
                }
            }

            // ---- Run / Open / Logs ----
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (s.status == SiteStatus.RUNNING) {
                            doStop()
                        } else if (Prefs.isFirstRunWarningNeeded(context)) {
                            pendingRun = true
                            showWarning = true
                        } else {
                            doRun()
                        }
                    },
                    enabled = !busy
                ) {
                    Icon(
                        if (s.status == SiteStatus.RUNNING) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (s.status == SiteStatus.RUNNING) "Stop" else "Run")
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("http://${lanIp ?: "127.0.0.1"}:$proxyPort${s.pathPrefix}")
                        )
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Open")
                }
                OutlinedButton(onClick = { nav.navigate(Route.Logs.create(s.id)) }) {
                    Icon(Icons.Filled.Terminal, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Logs")
                }
            }

            // ---- Reverse-proxy routing ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(icon = Icons.Filled.Lan, title = "Reverse-proxy routing")
                    OutlinedTextField(
                        value = hostRule ?: "",
                        onValueChange = { hostRule = it },
                        label = { Text("Host rule (blank = any host)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pathPrefix ?: "",
                        onValueChange = { pathPrefix = it },
                        label = { Text("Path prefix (e.g. /blog)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = envJson ?: "",
                        onValueChange = { envJson = it },
                        label = { Text("Env vars (JSON object)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.customDomain ?: "",
                        onValueChange = { v ->
                            scope.launch(Dispatchers.IO) {
                                app.repository.upsert(s.copy(customDomain = v.ifBlank { null }))
                            }
                        },
                        label = { Text("Custom domain (for port-forward setups)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val prefix = (pathPrefix ?: "/").ifBlank { "/" }
                                app.repository.upsert(
                                    s.copy(
                                        hostRule = (hostRule ?: "").trim(),
                                        pathPrefix = if (prefix.startsWith("/")) prefix else "/$prefix",
                                        envJson = (envJson ?: "{}").ifBlank { "{}" }
                                    )
                                )
                                app.proxyManager.refresh(app.repository)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Routing saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text("Save routing") }
                }
            }

            // ---- Logs preview (dark terminal) ----
            SectionHeader(icon = Icons.Filled.Terminal, title = "Logs")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AlanBrand.TerminalBg
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Live preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlanBrand.TerminalDim
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { nav.navigate(Route.Logs.create(s.id)) }) {
                            Text("View all", color = AlanBrand.TerminalText)
                        }
                    }
                    val preview = logLines.takeLast(5)
                    if (preview.isEmpty()) {
                        Text(
                            "No log lines yet.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = AlanBrand.TerminalDim
                        )
                    } else {
                        preview.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = AlanBrand.TerminalText
                            )
                        }
                    }
                }
            }

            // ---- QR share ----
            val qrText = if (isHosted) s.publicUrl else "http://${lanIp ?: "127.0.0.1"}:$proxyPort${s.pathPrefix}"
            val bitmap = remember(qrText) { QrGenerator.generate(qrText, 384) }
            if (bitmap != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            icon = Icons.Filled.QrCode2,
                            title = if (isHosted) "Share via QR (public link)" else "Share via QR"
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AlanBrand.QrPaper
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR for $qrText",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(192.dp)
                            )
                        }
                        Text(
                            qrText,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        TunnelService.stopSite(context, s.id)
                        StaticServerManager.stop(s.id)
                        app.runtimeManager.stop(s.id)
                        app.repository.delete(s.id)
                        withContext(Dispatchers.Main) { nav.popBackStack() }
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = faultColor())
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Delete site")
            }
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false; pendingRun = false },
            title = { Text(context.getString(com.alan.app.R.string.host_warning_title)) },
            text = { Text(context.getString(com.alan.app.R.string.host_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showWarning = false
                    Prefs.setFirstRunWarningShown(context)
                    if (pendingRun) {
                        pendingRun = false
                        doRun()
                    }
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false; pendingRun = false }) { Text("Cancel") }
            }
        )
    }

    if (showHostWarning) {
        AlertDialog(
            onDismissRequest = { showHostWarning = false; pendingHost = false },
            title = { Text(context.getString(com.alan.app.R.string.quick_host_warning_title)) },
            text = { Text(context.getString(com.alan.app.R.string.quick_host_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showHostWarning = false
                    Prefs.setFirstHostWarningShown(context)
                    if (pendingHost) {
                        pendingHost = false
                        doHost()
                    }
                }) { Text("I understand, Host") }
            },
            dismissButton = {
                TextButton(onClick = { showHostWarning = false; pendingHost = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun detectEntry(site: Site): String {
    val dir = File(site.dirPath)
    return when {
        File(dir, "app.py").exists() -> "app.py"
        File(dir, "main.py").exists() -> "main.py"
        File(dir, "wsgi.py").exists() -> "wsgi.py"
        else -> "app.py"
    }
}

private fun ensureProxy(context: android.content.Context, app: AlanApp) {
    if (app.proxyManager.isRunning()) return
    val intent = Intent(context, ProxyService::class.java).apply {
        action = ProxyService.ACTION_START
        putExtra(ProxyService.EXTRA_PORT, Prefs.getProxyPort(context))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

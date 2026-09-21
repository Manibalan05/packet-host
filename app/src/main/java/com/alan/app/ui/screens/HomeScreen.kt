package com.alan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alan.app.AlanApp
import com.alan.app.data.Site
import com.alan.app.data.SiteStatus
import com.alan.app.net.NetworkUtils
import com.alan.app.ui.navigation.Route
import com.alan.app.ui.theme.AlanBrand
import com.alan.app.ui.theme.SiteStatusBadges
import com.alan.app.ui.theme.StatusDot
import com.alan.app.ui.theme.siteStatusDotColor
import com.alan.app.util.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AlanApp }
    val sites by app.repository.observeAll().collectAsState(initial = emptyList())
    val proxyRunning = remember { app.proxyManager.isRunning() }
    val lanIp = remember { NetworkUtils.getDetailedInfo(context).primaryIp }
    val proxyPort = remember { Prefs.getProxyPort(context) }
    val liveCount = sites.count { it.status == SiteStatus.RUNNING }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alan", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { nav.navigate(Route.Public.route) }) {
                        Icon(Icons.Default.Public, contentDescription = "Public access")
                    }
                    IconButton(onClick = { nav.navigate(Route.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Route.Import.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Import site")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            DashboardHeader(
                liveCount = liveCount,
                totalCount = sites.size,
                proxyRunning = proxyRunning,
                entryUrl = "http://${lanIp ?: "<phone-ip>"}:$proxyPort"
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your sites",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (sites.isEmpty()) {
                EmptySitesCard(onImport = { nav.navigate(Route.Import.route) })
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sites, key = { it.id }) { site ->
                        SiteRow(
                            site = site,
                            onClick = { nav.navigate(Route.Detail.create(site.id)) },
                            onRunShortcut = { nav.navigate(Route.Detail.create(site.id)) },
                            onHostShortcut = { nav.navigate(Route.Detail.create(site.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    liveCount: Int,
    totalCount: Int,
    proxyRunning: Boolean,
    entryUrl: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(AlanBrand.GradientStart, AlanBrand.GradientEnd)))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = CircleShape, color = AlanBrand.OnGradient.copy(alpha = 0.18f)) {
                    Icon(
                        Icons.Filled.RocketLaunch,
                        contentDescription = null,
                        tint = AlanBrand.OnGradient,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(28.dp)
                    )
                }
                Column {
                    Text(
                        "Alan",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AlanBrand.OnGradient
                    )
                    Text(
                        "Self-hosted sites on your phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlanBrand.OnGradient.copy(alpha = 0.85f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderStatChip("$liveCount live")
                HeaderStatChip("$totalCount total")
                HeaderStatChip(if (proxyRunning) "Proxy LIVE" else "Proxy stopped")
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AlanBrand.OnGradient.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Lan,
                        contentDescription = null,
                        tint = AlanBrand.OnGradient,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            "Proxy entry",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlanBrand.OnGradient.copy(alpha = 0.8f)
                        )
                        Text(
                            entryUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AlanBrand.OnGradient
                        )
                    }
                }
            }
            Text(
                "Per-site servers stay on 127.0.0.1; the proxy routes by Host header / path prefix.",
                style = MaterialTheme.typography.bodySmall,
                color = AlanBrand.OnGradient.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun HeaderStatChip(label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AlanBrand.OnGradient.copy(alpha = 0.18f)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = AlanBrand.OnGradient,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SiteRow(
    site: Site,
    onClick: () -> Unit,
    onRunShortcut: () -> Unit,
    onHostShortcut: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusDot(siteStatusDotColor(site.status, site.hosted))
                Text(
                    site.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                SiteStatusBadges(status = site.status, hosted = site.hosted)
            }
            Text(
                "${site.type}  ·  :${site.port}  ·  " +
                    if (site.hostRule.isBlank()) "any host" else site.hostRule +
                        "  ·  ${site.pathPrefix}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onRunShortcut,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (site.status == SiteStatus.RUNNING) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = "Run site"
                    )
                }
                FilledTonalIconButton(
                    onClick = onHostShortcut,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = "Host site")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClick) { Text("Details") }
            }
        }
    }
}

@Composable
private fun EmptySitesCard(onImport: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = scheme.primaryContainer) {
                Icon(
                    Icons.Filled.RocketLaunch,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(40.dp)
                )
            }
            Text(
                "No sites yet — let's host one!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSecondaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                "Clone any public git repo and Alan will detect it, serve it locally, " +
                    "and give it a free public link.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSecondaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Button(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Import your first site")
            }
        }
    }
}

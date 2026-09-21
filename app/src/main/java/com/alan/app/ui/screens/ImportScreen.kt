package com.alan.app.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavController
import com.alan.app.AlanApp
import com.alan.app.engine.SiteDetector
import com.alan.app.ui.navigation.Route
import com.alan.app.util.GitImportHelper
import com.alan.app.util.PortUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * Git-only import: paste a repo link -> Clone -> auto-detect -> Run -> Host.
 */
private fun validateGitUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return "Paste a git repo link first — e.g. https://github.com/you/site.git"
    }
    val (url, _) = GitImportHelper.splitBranchSuffix(trimmed)
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return "That doesn't look like a git link — it should start with https:// (e.g. https://github.com/you/site.git)"
    }
    return null
}

private const val TAG = "ImportScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(nav: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AlanApp }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var gitUrl by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun finishImport(staging: File, displayName: String) {
        val detected = SiteDetector.detect(staging)
        val taken = app.repository.getAll().map { it.port }.toSet()
        val port = PortUtils.findFreePort(taken = taken)
        val site = app.repository.createSite(
            name = displayName.ifBlank { staging.name },
            type = detected.type,
            port = port,
            dir = staging
        )
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Imported as ${detected.type} on :$port", Toast.LENGTH_SHORT).show()
            nav.navigate(Route.Detail.create(site.id)) {
                popUpTo(Route.Home.route)
            }
        }
    }

    fun doClone() {
        val problem = validateGitUrl(gitUrl)
        if (problem != null) {
            error = problem
            return
        }
        error = null
        val (url, branch) = GitImportHelper.splitBranchSuffix(gitUrl.trim())
        // In-memory only: trimmed, passed to JGit, never logged or persisted.
        val token = accessToken.trim()
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                val staging = File(context.cacheDir, "import_${System.currentTimeMillis()}")
                staging.mkdirs()
                val cloneCmd = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(staging)
                if (branch != null) {
                    cloneCmd.setBranch("refs/heads/$branch")
                }
                if (token.isNotBlank()) {
                    // GitHub PAT-over-HTTPS pattern: token as username, empty password.
                    cloneCmd.setCredentialsProvider(
                        UsernamePasswordCredentialsProvider(token, "")
                    )
                }
                cloneCmd.call().close()
                finishImport(staging, url.substringAfterLast('/').removeSuffix(".git"))
            } catch (e: Exception) {
                val msg = GitImportHelper.describeCloneError(e, url, branch)
                // Redacted: URL credentials stripped, token never included.
                Log.w(
                    TAG,
                    "Clone failed for ${GitImportHelper.stripCredentialsFromUrl(url)}: " +
                        GitImportHelper.stripCredentialsFromUrl(msg)
                )
                error = msg
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import site", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val scheme = MaterialTheme.colorScheme
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(shape = CircleShape, color = scheme.primaryContainer) {
                            Icon(
                                Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                "Clone from git",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary
                            )
                            Text(
                                "Public https repos, or private with a token",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "Paste any git URL — Alan clones it, auto-detects the stack, " +
                            "and gets it ready to Run and Host. For a private repo, " +
                            "add a personal access token below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = gitUrl,
                        onValueChange = {
                            gitUrl = it
                            error = null
                        },
                        label = { Text("Git repo URL (https)") },
                        placeholder = { Text("https://github.com/you/site.git") },
                        supportingText = {
                            Text("Tip: add #branch to clone a specific branch (e.g. …/mysite#live).")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = error != null
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access token (only for private repos)") },
                        placeholder = { Text("ghp_…") },
                        supportingText = {
                            Text("Leave empty for public repos. Token stays on this phone, never uploaded.")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (error != null) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = scheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = scheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    error!!,
                                    color = scheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { doClone() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Clone")
                    }

                    if (busy) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.HourglassEmpty,
                                contentDescription = null,
                                tint = scheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Cloning… hang tight while we fetch your repo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.RocketLaunch,
                    contentDescription = null,
                    tint = scheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "After cloning: Run it locally, then Host it for a free public link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

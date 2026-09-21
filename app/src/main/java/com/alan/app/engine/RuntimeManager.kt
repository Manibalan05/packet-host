package com.alan.app.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

enum class RuntimeKind { NODE, PYTHON, PHP }

data class RunningProcess(
    val siteId: String,
    val port: Int,
    val process: Process,
    val logger: ProcessLogger,
    val job: Job
)

/**
 * Manages child processes for Node/Python/PHP runtimes.
 * Each site runs isolated on 127.0.0.1 with PORT/HOST env vars; the
 * reverse proxy is the only LAN-facing entrypoint.
 *
 * Runtimes require real interpreter binaries at filesDir/bin/ (node,
 * python3, php). When a binary is absent the start call fails with a
 * clear, actionable message instead of pretending to run.
 */
class RuntimeManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<String, RunningProcess>()
    private val loggers = ConcurrentHashMap<String, ProcessLogger>()

    fun loggerFor(siteId: String): ProcessLogger =
        loggers.getOrPut(siteId) { ProcessLogger() }

    fun isRunning(siteId: String): Boolean = running.containsKey(siteId)

    fun getRunning(siteId: String): RunningProcess? = running[siteId]

    private fun binDir(): File = File(context.filesDir, "bin")

    private fun canExecute(path: String): Boolean {
        if (path.contains("/")) {
            val f = File(path)
            return f.exists() && f.canExecute()
        }
        return try {
            ProcessBuilder("which", path).start().waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun missingBinaryMessage(binary: String, hint: String): String =
        "$binary runtime is not installed on this phone " +
            "(looked for ${binDir().absolutePath}/$binary).\n$hint"

    suspend fun startNode(
        siteId: String,
        siteDir: File,
        port: Int,
        startCommand: String,
        env: Map<String, String> = emptyMap()
    ): Result<ProcessLogger> {
        val nodeBin = File(binDir(), "node")
        if (!nodeBin.exists() || !nodeBin.canExecute()) {
            return Result.failure(
                RuntimeException(
                    missingBinaryMessage(
                        "node",
                        "Fix: on your computer run 'npm install && npm run build' with " +
                            "static export enabled, then import the built folder " +
                            "(the one containing index.html) as a STATIC site."
                    )
                )
            )
        }
        val cmd = startCommand.ifBlank { "node index.js" }
            .replace("\$PORT", port.toString())
            .replace("\$HOST", "127.0.0.1")
        val argv = when {
            cmd.startsWith("npm ") -> listOf("sh", "-c", "PORT=$port HOST=127.0.0.1 $cmd")
            else -> cmd.split(" ").filter { it.isNotBlank() }
        }
        return startGeneric(siteId, siteDir, port, argv, env, "node")
    }

    suspend fun startPython(
        siteId: String,
        siteDir: File,
        port: Int,
        entryFile: String,
        env: Map<String, String> = emptyMap()
    ): Result<ProcessLogger> {
        val pythonBin = File(binDir(), "python3")
        if (!pythonBin.exists() || !pythonBin.canExecute()) {
            return Result.failure(
                RuntimeException(
                    missingBinaryMessage(
                        "python3",
                        "Place a runnable CPython build at " +
                            "${binDir().absolutePath}/python3, then retry. " +
                            "Static sites need no interpreter."
                    )
                )
            )
        }
        val cmd = listOf(pythonBin.absolutePath, entryFile.ifBlank { "app.py" })
        return startGeneric(siteId, siteDir, port, cmd, env, "python")
    }

    suspend fun startPhp(
        siteId: String,
        siteDir: File,
        port: Int,
        env: Map<String, String> = emptyMap()
    ): Result<ProcessLogger> {
        val phpBin = File(binDir(), "php")
        if (!phpBin.exists() || !phpBin.canExecute()) {
            return Result.failure(
                RuntimeException(
                    missingBinaryMessage(
                        "php",
                        "Place a runnable php-cli build at " +
                            "${binDir().absolutePath}/php, then retry. " +
                            "Static sites need no interpreter."
                    )
                )
            )
        }
        val cmd = listOf(phpBin.absolutePath, "-S", "127.0.0.1:$port", "-t", ".")
        return startGeneric(siteId, siteDir, port, cmd, env, "php")
    }

    private suspend fun startGeneric(
        siteId: String,
        siteDir: File,
        port: Int,
        command: List<String>,
        env: Map<String, String>,
        tag: String
    ): Result<ProcessLogger> = withContext(Dispatchers.IO) {
        if (running.containsKey(siteId)) {
            return@withContext Result.failure(IllegalStateException("Already running"))
        }
        val logger = loggerFor(siteId)
        logger.append("== Starting $tag: ${command.joinToString(" ")} in ${siteDir.absolutePath} ==")
        try {
            val pb = ProcessBuilder(command)
                .directory(siteDir)
                .redirectErrorStream(false)
            pb.environment()["PORT"] = port.toString()
            pb.environment()["HOST"] = "127.0.0.1"
            pb.environment()["HOSTNAME"] = "127.0.0.1"
            env.forEach { (k, v) -> pb.environment()[k] = v }
            val proc = pb.start()
            val job = scope.launch {
                launch { streamToLogger(proc.inputStream, logger, "stdout") }
                launch { streamToLogger(proc.errorStream, logger, "stderr") }
                try {
                    val code = proc.waitFor()
                    logger.append("Process exited with code $code")
                } catch (e: Exception) {
                    logger.append("Wait interrupted: ${e.message}")
                }
            }
            running[siteId] = RunningProcess(siteId, port, proc, logger, job)
            delay(800)
            if (!proc.isAlive) {
                val logs = logger.tail(20).joinToString("\n")
                running.remove(siteId)
                return@withContext Result.failure(
                    RuntimeException("Process died immediately. Logs:\n$logs")
                )
            }
            logger.append("$tag started on 127.0.0.1:$port (served via reverse proxy)")
            Result.success(logger)
        } catch (e: Exception) {
            Log.e("RuntimeManager", "start failed", e)
            logger.append("Start failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun streamToLogger(
        input: java.io.InputStream,
        logger: ProcessLogger,
        prefix: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(input)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        logger.append("[$prefix] $line")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun stop(siteId: String) = withContext(Dispatchers.IO) {
        val rp = running.remove(siteId) ?: return@withContext
        try {
            rp.logger.append("Stopping process...")
            rp.process.destroy()
            withTimeoutOrNull(2000) {
                while (rp.process.isAlive) delay(100)
            }
            if (rp.process.isAlive) {
                rp.process.destroyForcibly()
                rp.logger.append("Force killed")
            }
            rp.job.cancel()
            rp.logger.append("Stopped")
        } catch (e: Exception) {
            rp.logger.append("Stop error: ${e.message}")
        }
    }

    suspend fun stopAll() {
        running.keys.toList().forEach { stop(it) }
    }

    fun getLoggerFlow(siteId: String): StateFlow<List<String>> = loggerFor(siteId).flow
}

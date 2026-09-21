package com.alan.app.engine

import com.alan.app.data.SiteType
import java.io.File

data class DetectedSite(
    val type: SiteType,
    val entryFile: String,
    val installCommand: String,
    val startCommand: String
)

/**
 * Pure, platform-free project-type detection.
 * Mirrors the reference detection rules: package.json -> NODE,
 * requirements/app.py/main.py -> PYTHON, index.php or .php files -> PHP,
 * *.html -> STATIC.
 */
object SiteDetector {
    fun detect(dir: File): DetectedSite {
        if (!dir.exists() || !dir.isDirectory) return DetectedSite(SiteType.UNKNOWN, "", "", "")
        val topLevel = dir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val allNames = dir.walkTopDown().maxDepth(3).map { it.name }.toSet()

        // Node
        if ("package.json" in topLevel || "package.json" in allNames) {
            val pkg = File(dir, "package.json").let { if (it.exists()) it.readText() else "" }
            val hasStart = pkg.contains("\"start\"")
            return DetectedSite(
                type = SiteType.NODE,
                entryFile = "package.json",
                installCommand = "npm install",
                startCommand = if (hasStart) "npm start" else "node index.js"
            )
        }
        // Python
        if ("requirements.txt" in topLevel || "app.py" in topLevel ||
            "main.py" in topLevel || "wsgi.py" in topLevel
        ) {
            val entry = when {
                File(dir, "app.py").exists() -> "app.py"
                File(dir, "main.py").exists() -> "main.py"
                File(dir, "wsgi.py").exists() -> "wsgi.py"
                else -> "app.py"
            }
            return DetectedSite(
                type = SiteType.PYTHON,
                entryFile = entry,
                installCommand = "pip install -r requirements.txt",
                startCommand = "python $entry"
            )
        }
        // PHP
        if ("index.php" in topLevel || allNames.any { it.endsWith(".php") }) {
            return DetectedSite(
                type = SiteType.PHP,
                entryFile = "index.php",
                installCommand = "",
                startCommand = "php -S 127.0.0.1:\$PORT -t ."
            )
        }
        // Static
        if ("index.html" in topLevel || allNames.any { it.endsWith(".html") }) {
            return DetectedSite(
                type = SiteType.STATIC,
                entryFile = "index.html",
                installCommand = "",
                startCommand = ""
            )
        }
        return DetectedSite(SiteType.UNKNOWN, "", "", "")
    }
}

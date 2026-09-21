package com.alan.app

import com.alan.app.data.SiteType
import com.alan.app.engine.SiteDetector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SiteDetectorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun nodeProjectDetectedByPackageJson() {
        val dir = temp.newFolder("node-site")
        java.io.File(dir, "package.json").writeText("""{"name":"x","scripts":{"start":"node server.js"}}""")
        val detected = SiteDetector.detect(dir)
        assertEquals(SiteType.NODE, detected.type)
        assertEquals("package.json", detected.entryFile)
        assertEquals("npm install", detected.installCommand)
        assertEquals("npm start", detected.startCommand)
    }

    @Test
    fun nodeWithoutStartScriptFallsBackToNodeIndex() {
        val dir = temp.newFolder("node-plain")
        java.io.File(dir, "package.json").writeText("""{"name":"x"}""")
        val detected = SiteDetector.detect(dir)
        assertEquals(SiteType.NODE, detected.type)
        assertEquals("node index.js", detected.startCommand)
    }

    @Test
    fun pythonProjectDetectedByRequirements() {
        val dir = temp.newFolder("py-site")
        java.io.File(dir, "requirements.txt").writeText("flask\n")
        java.io.File(dir, "main.py").writeText("print('hi')\n")
        val detected = SiteDetector.detect(dir)
        assertEquals(SiteType.PYTHON, detected.type)
        assertEquals("main.py", detected.entryFile)
    }

    @Test
    fun pythonPrefersAppPy() {
        val dir = temp.newFolder("py-app")
        java.io.File(dir, "app.py").writeText("print('hi')\n")
        java.io.File(dir, "main.py").writeText("print('hi')\n")
        val detected = SiteDetector.detect(dir)
        assertEquals(SiteType.PYTHON, detected.type)
        assertEquals("app.py", detected.entryFile)
    }

    @Test
    fun phpProjectDetectedByIndexPhp() {
        val dir = temp.newFolder("php-site")
        java.io.File(dir, "index.php").writeText("<?php echo 'hi';\n")
        val detected = SiteDetector.detect(dir)
        assertEquals(SiteType.PHP, detected.type)
        assertEquals("index.php", detected.entryFile)
    }

    @Test
    fun staticProjectDetectedByIndexHtml() {
        val dir = temp.newFolder("static-site")
        java.io.File(dir, "index.html").writeText("<h1>hi</h1>\n")
        val detected = SiteDetector.detect(dir)
        assertEquals(SiteType.STATIC, detected.type)
        assertEquals("index.html", detected.entryFile)
    }

    @Test
    fun emptyDirIsUnknown() {
        val dir = temp.newFolder("empty-site")
        val detected = SiteDetector.detect(dir)
        assertEquals(SiteType.UNKNOWN, detected.type)
    }

    @Test
    fun missingDirIsUnknown() {
        val detected = SiteDetector.detect(java.io.File(temp.root, "does-not-exist"))
        assertEquals(SiteType.UNKNOWN, detected.type)
    }
}

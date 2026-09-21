package com.alan.app.util

import android.content.Context
import java.io.File

object FileUtils {
    fun getSitesRoot(context: Context): File = File(context.filesDir, "sites").apply { mkdirs() }

    fun siteDir(context: Context, id: String): File = File(getSitesRoot(context), id)
}

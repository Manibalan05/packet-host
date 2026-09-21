package com.alan.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class SiteRepository(private val dao: SiteDao, private val context: Context) {
    fun observeAll(): Flow<List<Site>> = dao.observeAll()
    fun observeById(id: String): Flow<Site?> = dao.observeById(id)
    suspend fun getById(id: String): Site? = dao.getById(id)
    suspend fun getAll(): List<Site> = dao.getAll()

    suspend fun createSite(
        name: String,
        type: SiteType,
        port: Int,
        dir: File,
        hostRule: String = "",
        pathPrefix: String = "/",
        envJson: String = "{}"
    ): Site {
        val id = UUID.randomUUID().toString()
        val siteDir = File(context.filesDir, "sites/$id")
        siteDir.mkdirs()
        if (dir.absolutePath != siteDir.absolutePath && dir.exists()) {
            dir.copyRecursively(siteDir, overwrite = true)
        }
        val site = Site(
            id = id,
            name = name,
            type = type,
            dirPath = siteDir.absolutePath,
            port = port,
            hostRule = hostRule,
            pathPrefix = pathPrefix.ifBlank { "/" },
            envJson = envJson
        )
        dao.upsert(site)
        return site
    }

    suspend fun upsert(site: Site) = dao.upsert(site)

    suspend fun delete(id: String) {
        dao.getById(id)?.let { site ->
            runCatching { File(site.dirPath).deleteRecursively() }
        }
        dao.deleteById(id)
    }

    suspend fun updateStatus(id: String, status: SiteStatus) = dao.updateStatus(id, status)
    suspend fun updateHealth(id: String, health: String) = dao.updateLastHealth(id, health)
    suspend fun incrementRequestCount(id: String) = dao.incrementRequestCount(id)
    suspend fun updateUptimeStart(id: String, ts: Long?) = dao.updateUptimeStart(id, ts)
}

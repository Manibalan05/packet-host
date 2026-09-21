package com.alan.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SiteType { STATIC, NODE, PYTHON, PHP, UNKNOWN }
enum class SiteStatus { STOPPED, RUNNING, ERROR }

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey val id: String,
    val name: String,
    val type: SiteType,
    val dirPath: String,
    val port: Int,
    /** Hostname this site answers to at the reverse proxy. Blank = any host. */
    val hostRule: String = "",
    /** Path prefix this site serves at the reverse proxy. Longest match wins. */
    val pathPrefix: String = "/",
    val envJson: String = "{}",
    val status: SiteStatus = SiteStatus.STOPPED,
    /** Public quick-tunnel URL (https://[random].trycloudflare.com), blank when unhosted. */
    val publicUrl: String = "",
    /** True while this site's Cloudflare quick tunnel is live. */
    val hosted: Boolean = false,
    val customDomain: String? = null,
    val requestCount: Long = 0,
    val uptimeStartMs: Long? = null,
    val lastHealth: String = "unknown",
    val createdAt: Long = System.currentTimeMillis()
)

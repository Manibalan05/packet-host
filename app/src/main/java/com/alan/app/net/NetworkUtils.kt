package com.alan.app.net

import android.content.Context
import android.net.wifi.WifiManager
import java.net.NetworkInterface

object NetworkUtils {
    data class NetworkInfo(
        val wifiIp: String?,
        val hotspotIp: String?,
        val allIps: List<String>,
        val primaryIp: String?
    )

    fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr.isLoopbackAddress) continue
                    if (addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                            ips.add(ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ips
    }

    fun getPrimaryIp(): String? {
        val ips = getLocalIpAddresses()
        return ips.firstOrNull { it.startsWith("192.168.") }
            ?: ips.firstOrNull { it.startsWith("10.") }
            ?: ips.firstOrNull { it.startsWith("172.") }
            ?: ips.firstOrNull()
    }

    /** Best-effort hotspot address: usually the 192.168.43.x / 192.168.137.x gateway. */
    fun getDetailedInfo(context: Context): NetworkInfo {
        val all = getLocalIpAddresses()
        var wifiIp: String? = null
        var hotspotIp: String? = null
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wm.connectionInfo.ipAddress
            if (ipInt != 0) {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0") wifiIp = ip
            }
        } catch (_: Exception) {
        }
        // Best effort: interfaces other than wlan0 often carry hotspot/client subnets.
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (!iface.isUp || iface.isLoopback) continue
                if (iface.name == "wlan0") continue
                iface.inetAddresses.toList().forEach {
                    if (it is java.net.Inet4Address && !it.isLoopbackAddress) {
                        val ip = it.hostAddress
                        if (ip != null && !ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                            if (hotspotIp == null) hotspotIp = ip
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return NetworkInfo(wifiIp, hotspotIp, all, wifiIp ?: getPrimaryIp())
    }
}

package com.huiyang.quizlocktv

import java.net.NetworkInterface

/** 获取电视在局域网里的 IPv4 地址（手机据此访问控制台）。 */
object NetworkInfo {
    const val PORT = 8770

    fun localIp(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            for (intf in en) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") != true) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {}
        return "未知"
    }

    /** 手机浏览器要访问的完整地址，如 http://192.168.1.50:8770 */
    fun consoleUrl(): String = "http://${localIp()}:$PORT"
}


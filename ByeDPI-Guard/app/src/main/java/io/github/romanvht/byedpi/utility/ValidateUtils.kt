package io.github.romanvht.byedpi.utility

import android.net.InetAddresses
import android.os.Build

fun checkIp(ip: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        InetAddresses.isNumericAddress(ip)
    } else {
        // This pattern doesn't not support IPv6
        // @Suppress("DEPRECATION")
        // Patterns.IP_ADDRESS.matcher(ip).matches()
        true
    }
}

fun checkNotLocalIp(ip: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        InetAddresses.isNumericAddress(ip) && InetAddresses.parseNumericAddress(ip).let {
            !it.isAnyLocalAddress && !it.isLoopbackAddress
        }
    } else {
        // This pattern doesn't not support IPv6
        // @Suppress("DEPRECATION")
        // Patterns.IP_ADDRESS.matcher(ip).matches()
        true
    }
}

fun checkDomain(domain: String): Boolean {
    if (domain.isEmpty()) return false

    if (domain.length > 253) return false
    if (domain.startsWith(".") || domain.endsWith(".")) return false
    if (!domain.contains(".")) return false

    return true
}

fun checkPort(port: String): Boolean = port.toIntOrNull()?.let { it in 1..65535 } ?: false

package io.github.romanvht.byedpi.utility

import android.net.InetAddresses
import android.os.Build
import java.net.Inet6Address
import java.net.InetAddress

fun checkIp(ip: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        InetAddresses.isNumericAddress(ip)
    } else {
        parseNumericIp(ip) != null
    }
}

fun checkNotLocalIp(ip: String): Boolean {
    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (InetAddresses.isNumericAddress(ip)) InetAddresses.parseNumericAddress(ip) else null
    } else {
        parseNumericIp(ip)
    }
    return address != null && !address.isAnyLocalAddress && !address.isLoopbackAddress
}

private val IPV4 = Regex("""(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}""")
private val IPV6_CHARS = Regex("""[0-9A-Fa-f:.]+""")

/**
 * A numeric IPv4 or IPv6 address, or null, without ever looking a name up (for Android 9 and
 * older, which lack InetAddresses). An IPv6 literal always contains ':', so the platform parses
 * it directly rather than resolving it.
 */
internal fun parseNumericIp(ip: String): InetAddress? = when {
    IPV4.matches(ip) -> InetAddress.getByAddress(ip.split('.').map { it.toInt().toByte() }.toByteArray())
    ':' in ip && IPV6_CHARS.matches(ip) -> runCatching { InetAddress.getByName(ip) }.getOrNull()?.takeIf { it is Inet6Address || '.' in ip }
    else -> null
}

fun checkDomain(domain: String): Boolean {
    if (domain.isEmpty()) return false

    if (domain.length > 253) return false
    if (domain.startsWith(".") || domain.endsWith(".")) return false
    if (!domain.contains(".")) return false

    return true
}

fun checkPort(port: String): Boolean = port.toIntOrNull()?.let { it in 1..65535 } ?: false

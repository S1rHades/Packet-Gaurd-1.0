package io.github.romanvht.byedpi.utility

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

/**
 * Which strategy works on which network, kept on the phone only.
 *
 * A network is recognised without location permission (which reading the Wi-Fi name needs):
 * a Wi-Fi network by its router and DNS addresses and search domains, mobile data by the
 * operator's code. Only a short hash of these is stored, never the addresses themselves.
 */
object NetworkMemory {
    private const val PREF = "pg_network_strategies"
    private const val MAX = 30
    private val gson = Gson()

    data class Saved(val command: String, val at: Long)

    /** A stable, anonymous id for a network from what identifies it; pure, for tests. */
    internal fun keyOf(kind: String, parts: List<String>): String? {
        val clean = parts.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
        if (clean.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest((kind + "|" + clean.joinToString("|")).toByteArray())
        return kind + ":" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** The network the phone uses for the internet, ignoring Packet Guard's own VPN. */
    fun currentKey(ctx: Context): String? = runCatching {
        val cm = (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager) ?: return null
        @Suppress("DEPRECATION")
        val networks = cm.allNetworks.mapNotNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) null else caps to cm.getLinkProperties(n)
        }
        val wifi = networks.firstOrNull { it.first.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        val other = wifi ?: networks.firstOrNull() ?: return null
        when {
            other.first.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> keyOf("wifi", wifiParts(other.second))
            other.first.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val op = (ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.networkOperator.orEmpty()
                keyOf("cell", listOf(op))
            }
            else -> keyOf("net", wifiParts(other.second))
        }
    }.getOrNull()

    private fun wifiParts(lp: LinkProperties?): List<String> {
        lp ?: return emptyList()
        val gateways = lp.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway?.hostAddress }
        val dns = lp.dnsServers.mapNotNull { it.hostAddress }
        val dhcp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) listOfNotNull(lp.dhcpServerAddress?.hostAddress) else emptyList()
        return gateways.map { "gw=$it" } + dns.map { "dns=$it" } + dhcp.map { "dhcp=$it" } + listOfNotNull(lp.domains?.let { "dom=$it" })
    }

    private fun all(ctx: Context): MutableMap<String, Saved> = runCatching {
        val json = ctx.getPreferences().getString(PREF, null) ?: return@runCatching mutableMapOf()
        val type = object : TypeToken<Map<String, Saved?>>() {}.type
        val raw: Map<String, Saved?> = gson.fromJson(json, type) ?: emptyMap()
        @Suppress("SENSELESS_COMPARISON")
        raw.filterValues { it != null && it.command != null }.mapValues { it.value!! }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    /** The strategy last used on network [key], if any. */
    fun saved(ctx: Context, key: String?): String? = key?.let { all(ctx)[it]?.command }

    /** Remembers [command] for the network the phone is on now. */
    fun remember(ctx: Context, command: String, key: String? = currentKey(ctx)) {
        key ?: return
        if (command.isBlank()) return
        val map = all(ctx)
        map[key] = Saved(command, System.currentTimeMillis())
        val kept = map.entries.sortedByDescending { it.value.at }.take(MAX).associate { it.key to it.value }
        ctx.getPreferences().edit { putString(PREF, gson.toJson(kept)) }
    }

    fun clear(ctx: Context) = ctx.getPreferences().edit { remove(PREF) }
}

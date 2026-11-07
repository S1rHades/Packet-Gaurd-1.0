package io.github.romanvht.byedpi.ui

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A saved setting as a link anyone can open in Packet Guard, `packetguard://add?v=1&n=…&c=…`,
 * for a QR code or a message. The command line itself is in the link: there's no server.
 */
object StrategyShare {
    const val SCHEME = "packetguard"
    const val HOST = "add"

    data class Shared(val name: String, val command: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun link(name: String, command: String): String =
        "$SCHEME://$HOST?v=1&n=${enc(name.take(80))}&c=${enc(command.trim())}"

    /**
     * The setting in [text]: a link as made by [link], or a message that contains one.
     * Null when there's no link, or its command line isn't safe to add ([shareableCommand]).
     */
    fun read(text: String): Shared? {
        val raw = Regex("""packetguard://add\?\S+""").find(text)?.value ?: return null
        val query = runCatching { URI(raw).rawQuery }.getOrNull() ?: return null
        val params = query.split('&').mapNotNull { p ->
            val k = p.substringBefore('=', "")
            if (k.isEmpty()) null else k to runCatching { URLDecoder.decode(p.substringAfter('='), "UTF-8") }.getOrNull()
        }.toMap()
        val command = params["c"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!shareableCommand(command)) return null
        val name = params["n"]?.trim()?.take(80).orEmpty()
        return Shared(name, command)
    }
}

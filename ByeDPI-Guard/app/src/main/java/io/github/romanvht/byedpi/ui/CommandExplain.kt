package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.utility.shellSplit

/** The ciadpi options the explainer knows, by what they do. */
enum class Technique {
    Split, Disorder, Oob, Disoob, Fake, TlsRec, FakeTtl, FakeSni, FakeTlsMod, FakeOffset,
    UdpFake, ModHttp, Auto, Timeout, Proto, Hosts, Md5Sig
}

/** Where in the first packet a split happens: [offset] counted from [anchor] of [field]. */
data class CmdPos(
    val offset: Int,
    val field: Field = Field.Packet,
    val anchor: Anchor = Anchor.Start,
    val repeated: Boolean = false
) {
    enum class Field { Packet, SiteName, HostHeader }
    enum class Anchor { Start, End, Middle }
}

/** One option of a command line: the text as written, what it does, and its value. */
data class CmdPart(val text: String, val technique: Technique, val value: String = "", val pos: CmdPos? = null)

private class Opt(val technique: Technique?, val takesValue: Boolean)

/**
 * ciadpi's options (ByeDPI 17), short and long. Options with a null technique are understood
 * but not explained (addresses, ports, buffers and other plumbing).
 */
private val OPTIONS: Map<String, Opt> = buildMap {
    fun add(short: Char?, long: String, t: Technique?, value: Boolean = true) {
        val o = Opt(t, value)
        if (short != null) put("-$short", o)
        put("--$long", o)
    }
    add('s', "split", Technique.Split)
    add('d', "disorder", Technique.Disorder)
    add('o', "oob", Technique.Oob)
    add('q', "disoob", Technique.Disoob)
    add('f', "fake", Technique.Fake)
    add('r', "tlsrec", Technique.TlsRec)
    add('t', "ttl", Technique.FakeTtl)
    add('n', "fake-sni", Technique.FakeSni)
    add('Q', "fake-tls-mod", Technique.FakeTlsMod)
    add('O', "fake-offset", Technique.FakeOffset)
    add('a', "udp-fake", Technique.UdpFake)
    add('M', "mod-http", Technique.ModHttp)
    add('A', "auto", Technique.Auto)
    add('T', "timeout", Technique.Timeout)
    add('K', "proto", Technique.Proto)
    add('H', "hosts", Technique.Hosts)
    add('S', "md5sig", Technique.Md5Sig, value = false)
    add('Y', "drop-sack", null, value = false)
    add('i', "ip", null); add('p', "port", null); add('I', "conn-ip", null)
    add('c', "max-conn", null); add('b', "buf-size", null); add('g', "def-ttl", null)
    add('u', "cache-ttl", null); add('L', "auto-mode", null); add('y', "cache-dump", null)
    add('j', "ipset", null); add('V', "pf", null); add('R', "round", null)
    add('k', "ip-opt", null); add('l', "fake-data", null); add('e', "oob-data", null)
    add('w', "pidfile", null)
    add('N', "no-domain", null, value = false); add('U', "no-udp", null, value = false)
    add('F', "tfo", null, value = false); add('D', "daemon", null, value = false)
    add('E', "transparent", null, value = false); add('Z', "wait-send", null, value = false)
    add('h', "help", null, value = false); add('v', "version", null, value = false)
}

private val POSITIONED = setOf(Technique.Split, Technique.Disorder, Technique.Oob, Technique.Disoob, Technique.Fake, Technique.TlsRec)

/** Reads a position like `1`, `-5`, `1+s`, `-5+se`, `0+sm` or `3:2:1+h`; null if it isn't one. */
fun parsePos(raw: String): CmdPos? {
    val m = Regex("^(-?\\d+)(?::\\d+(?::\\d+)?)?(?:\\+([shn])([em]?))?$").matchEntire(raw.trim()) ?: return null
    val offset = m.groupValues[1].toIntOrNull() ?: return null
    val field = when (m.groupValues[2]) {
        "s" -> CmdPos.Field.SiteName
        "h" -> CmdPos.Field.HostHeader
        else -> CmdPos.Field.Packet
    }
    val anchor = when (m.groupValues[3]) {
        "e" -> CmdPos.Anchor.End
        "m" -> CmdPos.Anchor.Middle
        else -> if (field == CmdPos.Field.Packet && offset < 0) CmdPos.Anchor.End else CmdPos.Anchor.Start
    }
    return CmdPos(offset, field, anchor, repeated = ':' in raw)
}

/**
 * Splits a ciadpi command line into the options worth explaining, in order. Unknown options
 * and plumbing (addresses, buffers) are left out, so an empty list means "nothing to explain".
 */
fun explainCommand(command: String): List<CmdPart> {
    val tokens = shellSplit(normalizeCommand(command))
    val out = mutableListOf<CmdPart>()
    var i = 0
    while (i < tokens.size) {
        val tok = tokens[i++]
        if (!tok.startsWith("-") || tok == "-") continue
        val (name, attached) = when {
            tok.startsWith("--") -> tok.substringBefore('=') to tok.substringAfter('=', "").ifEmpty { null }
            else -> tok.take(2) to tok.drop(2).ifEmpty { null }
        }
        val opt = OPTIONS[name] ?: continue
        var value = ""
        var text = tok
        if (opt.takesValue) {
            value = attached ?: tokens.getOrNull(i)?.also { i++; text = "$tok $it" } ?: ""
        }
        val t = opt.technique ?: continue
        val pos = if (t in POSITIONED) parsePos(value) else null
        out.add(CmdPart(text, t, value, pos))
    }
    return out
}

/** Plumbing options a shared command line may still carry: they only change how a bypass behaves. */
private val SHAREABLE_PLUMBING = setOf(
    "-Y", "--drop-sack", "-N", "--no-domain", "-U", "--no-udp", "-F", "--tfo", "-g", "--def-ttl",
    "-R", "--round", "-e", "--oob-data", "-l", "--fake-data", "-u", "--cache-ttl", "-L", "--auto-mode"
)

/** Options whose value may be a file path on the phone; shared lines may only give them inline (`:…`). */
private val INLINE_ONLY = setOf("-H", "--hosts", "-l", "--fake-data")

/**
 * Whether a command line received from someone else is safe to add: every token is a ciadpi
 * bypass option (or its value) and at least one desync technique is there. Anything that changes
 * the proxy's address or port, runs it as a daemon, writes files or reads a file from the phone
 * is refused, as are unknown options. The `{sni}` placeholder is allowed.
 */
fun shareableCommand(command: String): Boolean {
    if (command.isBlank() || command.length > 2000) return false
    val tokens = runCatching { shellSplit(normalizeCommand(command)) }.getOrNull() ?: return false
    var i = 0
    var techniques = 0
    while (i < tokens.size) {
        val tok = tokens[i++]
        if (!tok.startsWith("-") || tok == "-") return false // a stray value
        val (name, attached) = when {
            tok.startsWith("--") -> tok.substringBefore('=') to tok.substringAfter('=', "").ifEmpty { null }
            else -> tok.take(2) to tok.drop(2).ifEmpty { null }
        }
        val opt = OPTIONS[name] ?: return false
        if (opt.technique == null && name !in SHAREABLE_PLUMBING) return false
        if (opt.technique in POSITIONED) techniques++
        if (opt.takesValue) {
            val value = attached ?: tokens.getOrNull(i)?.also { i++ } ?: return false
            if (name in INLINE_ONLY && !value.startsWith(":")) return false
        }
    }
    return techniques > 0
}

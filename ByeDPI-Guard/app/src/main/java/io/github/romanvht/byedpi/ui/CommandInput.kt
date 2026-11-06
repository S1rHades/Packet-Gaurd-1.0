package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.utility.shellSplit

/** Spaces that look like spaces but that the argument splitter wouldn't treat as one. */
private val ODD_SPACES = Regex("[\\u00A0\\u2007\\u202F\\u3000]")
private val INVISIBLE = Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]")
private val PROGRAM = Regex("^(?:.*/)?(?:ciadpi|byedpi)(?:\\.exe)?$", RegexOption.IGNORE_CASE)
private val LONG_NAME = Regex("^[a-z][a-z-]+(?:=.*)?$")
private val DASHES = "—–−‐‑‒"

/**
 * Tidies a ciadpi command line pasted from a chat, a website or a phone keyboard, without
 * changing what it means: curly quotes become straight ones, a dash that autocorrect turned
 * into “—” or “–” becomes `--` or `-` again, odd spaces become plain ones, a leading `ciadpi`
 * is dropped, and the options are joined with single spaces. Splitting the result with
 * [shellSplit] gives the same arguments the engine would get from the cleaned-up input.
 */
fun normalizeCommand(raw: String): String {
    val text = raw
        .replace(INVISIBLE, "")
        .replace(ODD_SPACES, " ")
        .replace('“', '"').replace('”', '"').replace('„', '"').replace('«', '"').replace('»', '"')
        .replace('‘', '\'').replace('’', '\'')
    val tokens = shellSplit(text).map(::fixDash).toMutableList()
    while (tokens.isNotEmpty() && PROGRAM.matches(tokens.first())) tokens.removeAt(0)
    return tokens.joinToString(" ", transform = ::quoteIfNeeded)
}

private fun fixDash(token: String): String {
    val first = token.firstOrNull() ?: return token
    if (first !in DASHES) return token
    val rest = token.substring(1).trimStart(*DASHES.toCharArray(), '-')
    // An em dash, or a dash before a whole word ("–disorder"), was a long option's "--".
    return if (first == '—' || LONG_NAME.matches(rest)) "--$rest" else "-$rest"
}

private fun quoteIfNeeded(token: String): String = when {
    token.isEmpty() -> "\"\""
    token.none { it.isWhitespace() || it == '"' || it == '\'' } -> token
    '"' !in token -> "\"$token\""
    '\'' !in token -> "'$token'"
    else -> token
}

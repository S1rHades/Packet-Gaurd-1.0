package io.github.romanvht.byedpi.update

import com.google.gson.JsonParser

/** A Packet Guard release on GitHub: its version, the APK to download, and what's new. */
data class Release(val version: String, val apkUrl: String, val sizeBytes: Long, val notes: List<String>)

/** Where an update has got to. */
enum class UpdatePhase { Idle, Checking, UpToDate, Available, Downloading, Installing, Failed }

/** Why an update step failed; the UI turns each into a plain sentence. */
enum class UpdateProblem {
    Offline, Timeout, Secure, NoApk, Server, Download, NeedsPermission,
    Cancelled, Conflict, Storage, Install
}

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    /** The newer release, once one is found. */
    val release: Release? = null,
    /** 0 to 1 while downloading. */
    val progress: Float = 0f,
    val problem: UpdateProblem? = null
) {
    val busy: Boolean get() = phase == UpdatePhase.Checking || phase == UpdatePhase.Downloading || phase == UpdatePhase.Installing
}

/** "2.4.0-debug" → "2.4.0": only the numbered part counts. */
fun versionCore(version: String): String = version.trim().removePrefix("v").substringBefore('-').substringBefore(' ')

/** True when [candidate] (like "2.5.0") is a later version than [current] ("2.4.0"). */
fun isNewer(candidate: String, current: String): Boolean {
    val a = versionCore(candidate).split('.').map { it.toIntOrNull() ?: 0 }
    val b = versionCore(current).split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** The universal APK that CI attaches to every release. */
private val ApkName = Regex("""^PacketGuard-v([0-9][0-9.]*)-universal\.apk$""")

/**
 * The release GitHub's API describes in [json]. The version comes from its universal APK
 * (PacketGuard-v2.5.0-universal.apk), the notes from the description's first section.
 * Null when it has no such APK or [json] isn't a release.
 */
fun parseRelease(json: String): Release? = runCatching {
    val o = JsonParser.parseString(json).asJsonObject
    val apk = o.getAsJsonArray("assets").map { it.asJsonObject }
        .firstOrNull { ApkName.matches(it.get("name")?.asString.orEmpty()) } ?: return null
    val name = apk.get("name").asString
    val version = ApkName.find(name)!!.groupValues[1].trimEnd('.')
    Release(
        version = version,
        apkUrl = apk.get("browser_download_url").asString,
        sizeBytes = apk.get("size")?.asLong ?: 0L,
        notes = releaseNotes(o.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty())
    )
}.getOrNull()

/** Up to three bullet points under the first "## " heading of a release description, without markdown. */
fun releaseNotes(body: String): List<String> {
    val lines = body.lines()
    val start = lines.indexOfFirst { it.startsWith("## ") }
    if (start < 0) return emptyList()
    return lines.drop(start + 1)
        .takeWhile { !it.startsWith("## ") }
        .filter { it.startsWith("- ") }
        .map { line ->
            line.removePrefix("- ")
                .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
                .replace("**", "").replace("*", "").replace("`", "")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .take(3)
}

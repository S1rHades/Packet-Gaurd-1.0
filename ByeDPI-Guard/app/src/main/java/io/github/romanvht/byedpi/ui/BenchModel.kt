package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.StrategyResult
import androidx.annotation.StringRes
import io.github.romanvht.byedpi.R

enum class ProfilePack(@StringRes val label: Int) {
    Basic(R.string.pg_pack_basic),
    Community(R.string.pg_pack_community),
    Custom(R.string.pg_pack_custom)
}

data class ProfileItem(val command: String, val name: String, val pack: ProfilePack)

data class ListItemUi(
    val id: String,
    val name: String,
    val domains: List<String>,
    val active: Boolean,
    val builtIn: Boolean
)

object BenchPresets {
    /**
     * Simple single-technique profiles, written in ciadpi's short flags:
     * -s split, -d disorder, -o out-of-band byte, -f fake packet, -r TLS record split;
     * a trailing "+s" means "relative to the SNI". The benchmark is empirical, so a profile
     * that doesn't help on your ISP simply scores low.
     */
    val basic: List<Pair<String, String>> = listOf(
        "Split @1" to "-s1",
        "Split @2 + Disorder" to "-s2 -d2",
        "Fake -1 + Split @2" to "-f-1 -s2",
        "Disorder @1" to "-d1",
        "OOB @1" to "-o1",
        "TLS record split (SNI)" to "-r1+s",
        "Split at SNI" to "-s1+s",
        "Fake + TLS record + SNI split" to "-f-1 -r1+s -s1+s",
        "ByeByeDPI default" to "-o1 -a1 -r-5+se"
    )
}

data class RankedStrategy(
    val rank: Int,
    val name: String,
    val result: StrategyResult,
    val avgLatencyMs: Long?
)

data class DomainBest(
    val site: String,
    val strategyName: String?,
    val command: String?,
    val latencyMs: Long?,
    val httpCode: Int?
) {
    val ok: Boolean get() = command != null
}

/** Average time-to-headers over the sites that actually answered. */
fun StrategyResult.avgLatencyMs(): Long? {
    val values = siteResults.mapNotNull { if (it.successCount > 0) it.latencyMs else null }
    return if (values.isEmpty()) null else values.sum() / values.size
}

/** Best first: most successful requests, then lowest average latency. */
fun rankStrategies(strategies: List<StrategyResult>, nameOf: (String) -> String): List<RankedStrategy> {
    val done = strategies.filter { it.isCompleted }
    val sorted = done.sortedWith(
        compareByDescending<StrategyResult> { it.successCount }
            .thenBy { it.avgLatencyMs() ?: Long.MAX_VALUE }
    )
    return sorted.mapIndexed { i, r -> RankedStrategy(i + 1, nameOf(r.command), r, r.avgLatencyMs()) }
}

fun winnerOf(ranked: List<RankedStrategy>): RankedStrategy? =
    ranked.firstOrNull()?.takeIf { it.result.successCount > 0 }

private fun better(a: SiteResult, b: SiteResult): Boolean =
    a.successCount > b.successCount ||
        (a.successCount == b.successCount &&
            (a.latencyMs ?: Long.MAX_VALUE) < (b.latencyMs ?: Long.MAX_VALUE))

/** For every tested domain: which profile reached it (most successes, then fastest). */
fun bestPerDomain(strategies: List<StrategyResult>, nameOf: (String) -> String): List<DomainBest> {
    val done = strategies.filter { it.isCompleted }
    val sites = done.flatMap { st -> st.siteResults.map { it.site } }.distinct()
    return sites.map { site ->
        var bestStrategy: StrategyResult? = null
        var bestSite: SiteResult? = null
        for (st in done) {
            val sr = st.siteResults.firstOrNull { it.site == site } ?: continue
            if (sr.successCount <= 0) continue
            val current = bestSite
            if (current == null || better(sr, current)) {
                bestStrategy = st
                bestSite = sr
            }
        }
        DomainBest(
            site = site,
            strategyName = bestStrategy?.command?.let(nameOf),
            command = bestStrategy?.command,
            latencyMs = bestSite?.latencyMs,
            httpCode = bestSite?.httpCode
        )
    }.sortedWith(compareByDescending<DomainBest> { it.ok }.thenBy { it.site })
}

/** Accepts pasted domains or links; scheme, path and query are dropped. */
fun parseDomains(raw: String): List<String> =
    raw.split(Regex("[\\s,;]+"))
        .map {
            it.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore('/')
                .substringBefore('?')
                .trim()
                .lowercase()
        }
        .filter { it.isNotEmpty() && '.' in it }
        .distinct()

/**
 * One profile per line; "Name | command" or just the command. Unnamed lines are called
 * [defaultName] of their 1-based line number among the non-blank lines.
 */
fun parseCustomProfiles(
    text: String,
    defaultName: (Int) -> String = { "Custom $it" }
): List<Pair<String, String>> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, line ->
        if ('|' in line) {
            val name = line.substringBefore('|').trim().ifEmpty { defaultName(i + 1) }
            name to line.substringAfter('|').trim()
        } else {
            defaultName(i + 1) to line
        }
    }.filter { it.second.isNotEmpty() }

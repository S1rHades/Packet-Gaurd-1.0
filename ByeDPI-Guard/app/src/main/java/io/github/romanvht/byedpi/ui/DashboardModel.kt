package io.github.romanvht.byedpi.ui

import androidx.annotation.StringRes
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.StrategyResult

/** What the dashboard chart compares. In every metric a taller bar is better. */
enum class DashMetric(@StringRes val label: Int) {
    Latency(R.string.pg_dash_metric_latency),
    Reach(R.string.pg_dash_metric_reach),
    Success(R.string.pg_dash_metric_success)
}

/** One finished profile, reduced to the numbers the dashboard shows. */
data class ProfileStat(
    val name: String,
    val command: String,
    /** Sites with at least one successful request. */
    val reachable: Int,
    val sites: Int,
    /** Successful requests as a percentage of all requests, 0–100. */
    val successRate: Int,
    /** Average time to headers over the sites that answered. */
    val avgLatencyMs: Long?,
    /** For each link list (by id): how many of its tested sites this profile reached. */
    val reachByList: Map<String, Int> = emptyMap()
) {
    val working: Boolean get() = reachable > 0
}

/** How one link list fared across the whole run. */
data class ListStat(
    val id: String,
    val name: String,
    /** Sites of this list that were tested. */
    val sites: Int,
    /** Of those, how many at least one profile reached. */
    val reachable: Int,
    /** Average of each reachable site's fastest latency. */
    val avgLatencyMs: Long?,
    /** The profile that reached most of this list, then the fastest on it. */
    val bestProfile: String?,
    val bestProfileReach: Int
) {
    val reachPercent: Int get() = if (sites == 0) 0 else reachable * 100 / sites
}

data class Dashboard(
    /** Finished profiles, in the order they were tested. */
    val profiles: List<ProfileStat> = emptyList(),
    /** Most reachable first, then fastest. */
    val lists: List<ListStat> = emptyList(),
    val sitesTotal: Int = 0,
    /** Sites at least one profile reached. */
    val sitesUnlocked: Int = 0,
    /** Successful requests over all finished profiles, 0–100. */
    val overallSuccessRate: Int? = null,
    val fastest: ProfileStat? = null,
    val widestReach: ProfileStat? = null,
    val mostReliable: ProfileStat? = null
) {
    val isEmpty: Boolean get() = profiles.isEmpty()
    val working: Int get() = profiles.count { it.working }

    /** Average latency of the working profiles, for the "faster than average" comparison. */
    val averageLatencyMs: Long?
        get() = profiles.mapNotNull { p -> p.avgLatencyMs.takeIf { p.working } }
            .takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

    /** The list with the lowest average latency (ties: the one that opens more). */
    val fastestList: ListStat?
        get() = lists.filter { it.avgLatencyMs != null }
            .minWithOrNull(compareBy<ListStat> { it.avgLatencyMs }.thenByDescending { it.reachPercent })

    /** The list the most of which could be opened (ties: the faster one). */
    val mostOpenList: ListStat?
        get() = lists.filter { it.reachable > 0 }
            .maxWithOrNull(compareBy<ListStat> { it.reachPercent }.thenBy { -(it.avgLatencyMs ?: Long.MAX_VALUE) })
}

private val latencyOrder = compareBy<ProfileStat> { it.avgLatencyMs ?: Long.MAX_VALUE }

/** For maxWith: the faster profile compares as greater. (Comparator.reversed() needs API 24.) */
private val fasterIsGreater = compareByDescending<ProfileStat> { it.avgLatencyMs ?: Long.MAX_VALUE }

fun buildDashboard(
    strategies: List<StrategyResult>,
    lists: List<ListItemUi>,
    nameOf: (String) -> String
): Dashboard {
    val done = strategies.filter { it.isCompleted }
    if (done.isEmpty()) return Dashboard()

    val sites = done.flatMap { st -> st.siteResults.map { it.site } }.distinct()
    val siteSet = sites.toSet()
    val listSites: List<Pair<ListItemUi, List<String>>> = lists
        .map { it to it.domains.filter { d -> d in siteSet }.distinct() }
        .filter { it.second.isNotEmpty() }

    val profiles = done.map { r ->
        val reached = r.siteResults.filter { it.successCount > 0 }.map { it.site }.toSet()
        ProfileStat(
            name = nameOf(r.command),
            command = r.command,
            reachable = reached.size,
            sites = r.siteResults.size,
            successRate = r.successPercentage,
            avgLatencyMs = r.avgLatencyMs(),
            reachByList = listSites.associate { (list, domains) -> list.id to domains.count { it in reached } }
        )
    }

    // Fastest successful latency per site, over every finished profile.
    val bestLatency = HashMap<String, Long?>()
    val reachedAnywhere = HashSet<String>()
    for (st in done) for (s in st.siteResults) {
        if (s.successCount <= 0) continue
        reachedAnywhere += s.site
        val current = bestLatency[s.site]
        if (s.latencyMs != null && (current == null || s.latencyMs < current)) bestLatency[s.site] = s.latencyMs
        else if (!bestLatency.containsKey(s.site)) bestLatency[s.site] = s.latencyMs
    }

    val listStats = listSites.map { (list, domains) ->
        val reached = domains.filter { it in reachedAnywhere }
        val latencies = reached.mapNotNull { bestLatency[it] }
        val best = profiles
            .filter { (it.reachByList[list.id] ?: 0) > 0 }
            .maxWithOrNull(compareBy<ProfileStat> { it.reachByList[list.id] ?: 0 }.then(fasterIsGreater))
        ListStat(
            id = list.id,
            name = list.name,
            sites = domains.size,
            reachable = reached.size,
            avgLatencyMs = if (latencies.isEmpty()) null else latencies.sum() / latencies.size,
            bestProfile = best?.name,
            bestProfileReach = best?.reachByList?.get(list.id) ?: 0
        )
    }.sortedWith(
        compareByDescending<ListStat> { it.reachPercent }.thenBy { it.avgLatencyMs ?: Long.MAX_VALUE }.thenBy { it.name }
    )

    val requests = done.sumOf { it.totalRequests }
    val working = profiles.filter { it.working }
    return Dashboard(
        profiles = profiles,
        lists = listStats,
        sitesTotal = sites.size,
        sitesUnlocked = reachedAnywhere.size,
        overallSuccessRate = if (requests == 0) null else done.sumOf { it.successCount } * 100 / requests,
        fastest = working.filter { it.avgLatencyMs != null }.minWithOrNull(latencyOrder),
        widestReach = working.maxWithOrNull(compareBy<ProfileStat> { it.reachable }.then(fasterIsGreater)),
        mostReliable = working.maxWithOrNull(compareBy<ProfileStat> { it.successRate }.then(fasterIsGreater))
    )
}

/** A chart bar: [height] is 0–1 and always "taller is better". */
data class DashBar(val profile: ProfileStat, val height: Float)

/** The best [limit] working profiles for [metric], best first. */
fun chartBars(profiles: List<ProfileStat>, metric: DashMetric, limit: Int = 8): List<DashBar> {
    val working = profiles.filter { it.working }
    return when (metric) {
        DashMetric.Latency -> {
            val timed = working.filter { it.avgLatencyMs != null }.sortedWith(latencyOrder).take(limit)
            val best = timed.firstOrNull()?.avgLatencyMs?.coerceAtLeast(1) ?: return emptyList()
            timed.map { DashBar(it, best.toFloat() / it.avgLatencyMs!!.coerceAtLeast(1)) }
        }
        DashMetric.Reach -> working
            .sortedWith(compareByDescending<ProfileStat> { it.reachable }.then(latencyOrder))
            .take(limit)
            .map { DashBar(it, if (it.sites == 0) 0f else it.reachable.toFloat() / it.sites) }
        DashMetric.Success -> working
            .sortedWith(compareByDescending<ProfileStat> { it.successRate }.then(latencyOrder))
            .take(limit)
            .map { DashBar(it, it.successRate / 100f) }
    }
}

/** The series a KPI sparkline draws, in test order. Missing points are skipped. */
fun sparkSeries(profiles: List<ProfileStat>, metric: DashMetric): List<Float> = when (metric) {
    // Inverted so that, like the bars, up means better.
    DashMetric.Latency -> profiles.mapNotNull { p -> p.avgLatencyMs?.takeIf { p.working }?.let { -it.toFloat() } }
    DashMetric.Reach -> profiles.map { it.reachable.toFloat() }
    DashMetric.Success -> profiles.map { it.successRate.toFloat() }
}

/** A plain-language speed rating for people who don't read milliseconds. */
enum class Speed(@StringRes val label: Int) {
    Fast(R.string.pg_speed_fast),
    Ok(R.string.pg_speed_ok),
    Slow(R.string.pg_speed_slow);

    companion object {
        fun of(latencyMs: Long): Speed = when {
            latencyMs < 200 -> Fast
            latencyMs < 500 -> Ok
            else -> Slow
        }
    }
}

package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.utility.shellSplit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns Benchmark results into decisions: one score per command line, one recommendation with a
 * reason, and for each goal (fastest, most reliable, a site group) the option and what you'd gain
 * or give up compared with the recommendation. Kept free of Android so it can be unit-tested.
 */

/**
 * A command line's score, 0–100, from three parts that are each 0–100:
 *  - [reach]: share of the tested sites it opened;
 *  - [success]: share of all requests that worked (how dependable it is);
 *  - [speed]: from its average latency, 100 at [FAST_MS] or less down to 0 at [SLOW_MS].
 * Reach counts most, because a fast setting that doesn't open your site is no use.
 */
data class CmdScore(val reach: Int, val success: Int, val speed: Int) {
    val overall: Int get() = (REACH_WEIGHT * reach + SUCCESS_WEIGHT * success + SPEED_WEIGHT * speed).roundToInt().coerceIn(0, 100)

    companion object {
        const val REACH_WEIGHT = 0.5
        const val SUCCESS_WEIGHT = 0.3
        const val SPEED_WEIGHT = 0.2
        const val FAST_MS = 100L
        const val SLOW_MS = 1500L

        fun speedOf(latencyMs: Long?): Int = when {
            latencyMs == null -> 0
            latencyMs <= FAST_MS -> 100
            latencyMs >= SLOW_MS -> 0
            else -> (100.0 * (SLOW_MS - latencyMs) / (SLOW_MS - FAST_MS)).roundToInt()
        }

        fun of(opened: Int, sites: Int, successRate: Int, latencyMs: Long?): CmdScore = CmdScore(
            reach = if (sites <= 0) 0 else opened * 100 / sites,
            success = successRate.coerceIn(0, 100),
            speed = if (opened == 0) 0 else speedOf(latencyMs)
        )
    }
}

/** How dependable a command line was, in one word. */
enum class Reliability { Always, Mostly, Sometimes;
    companion object {
        fun of(successRate: Int) = when {
            successRate >= 95 -> Always
            successRate >= 70 -> Mostly
            else -> Sometimes
        }
    }
}

/** A tested command line with its score. */
data class Choice(val stat: ProfileStat, val score: CmdScore) {
    val name: String get() = stat.name
    val command: String get() = stat.command
    val reliability: Reliability get() = Reliability.of(stat.successRate)
    val speed: Speed? get() = stat.avgLatencyMs?.let { Speed.of(it) }

    companion object {
        fun of(p: ProfileStat) = Choice(p, CmdScore.of(p.reachable, p.sites, p.successRate, p.avgLatencyMs))
    }
}

/** One thing you gain (positive) or give up (negative) by picking an option instead of the recommendation. */
sealed interface Tradeoff {
    val good: Boolean
    data class Sites(val diff: Int) : Tradeoff { override val good get() = diff > 0 }
    data class Latency(val diffMs: Long) : Tradeoff { override val good get() = diffMs < 0 } // negative = faster
    data class Dependability(val diffPoints: Int) : Tradeoff { override val good get() = diffPoints > 0 }
    data class ListSites(val listName: String, val opened: Int, val sites: Int) : Tradeoff { override val good get() = opened >= sites }
}

/** Why the recommendation was chosen, for its one-line reason. */
enum class Reason { OnlyAllSites, AllSitesFastest, MostSites, BestBalance }

enum class GoalKind { Fastest, Reliable, Group }

/** An option for one goal; [same] when the recommendation already is the best for it. */
data class GoalOption(
    val kind: GoalKind,
    val choice: Choice,
    val same: Boolean,
    val tradeoffs: List<Tradeoff>,
    /** For [GoalKind.Group]: the site group. */
    val listId: String? = null,
    val listName: String? = null,
    val listOpened: Int = 0,
    val listSites: Int = 0
)

/** How the recommendation does on one site group. */
data class GroupRow(val listId: String, val name: String, val opened: Int, val sites: Int)

/** One command line that uses each group's best setting on that group's sites. */
data class Combined(
    val command: String,
    /** Group name to the setting used for it; the last entry (null name) is "everything else". */
    val parts: List<Pair<String?, String>>,
    /** Sites the parts opened when tested on their own; the combination itself isn't tested yet. */
    val expectedOpened: Int,
    val sites: Int
)

data class Decision(
    val recommended: Choice?,
    val reason: Reason?,
    /** Every working command line, best score first. */
    val ranked: List<Choice>,
    /** How many working lines another line beats on every count (folded into "All results"). */
    val outclassed: Int,
    val goals: List<GoalOption>,
    val groups: List<GroupRow>,
    val combined: Combined?
) {
    val isEmpty: Boolean get() = ranked.isEmpty()
}

/** True when [a] is at least as good as [b] on sites, dependability and speed, and better on one. */
fun beats(a: ProfileStat, b: ProfileStat): Boolean {
    val la = a.avgLatencyMs ?: Long.MAX_VALUE
    val lb = b.avgLatencyMs ?: Long.MAX_VALUE
    val notWorse = a.reachable >= b.reachable && a.successRate >= b.successRate && la <= lb
    val better = a.reachable > b.reachable || a.successRate > b.successRate || la < lb
    return notWorse && better
}

private const val LATENCY_NOTICE_MS = 10
private const val SUCCESS_NOTICE_POINTS = 5

/** What you'd gain or give up with [option] instead of [base], biggest effect first. */
fun tradeoffs(option: ProfileStat, base: ProfileStat): List<Tradeoff> = buildList {
    val sites = option.reachable - base.reachable
    if (sites != 0) add(Tradeoff.Sites(sites))
    val lo = option.avgLatencyMs
    val lb = base.avgLatencyMs
    if (lo != null && lb != null && abs(lo - lb) >= LATENCY_NOTICE_MS) add(Tradeoff.Latency(lo - lb))
    val dep = option.successRate - base.successRate
    if (abs(dep) >= SUCCESS_NOTICE_POINTS) add(Tradeoff.Dependability(dep))
}

/**
 * The Benchmark's answer: the best-scoring line among those no other line beats on every count,
 * and for each goal the option that suits it best.
 */
fun decide(dash: Dashboard, lists: List<ListItemUi>): Decision {
    val working = dash.profiles.filter { it.working }
    if (working.isEmpty()) return Decision(null, null, emptyList(), 0, emptyList(), emptyList(), null)

    val choices = working.map { Choice.of(it) }
    val ranked = choices.sortedWith(
        compareByDescending<Choice> { it.score.overall }
            .thenByDescending { it.stat.reachable }
            .thenBy { it.stat.avgLatencyMs ?: Long.MAX_VALUE }
            .thenBy { it.name }
    )
    val front = ranked.filter { c -> working.none { other -> beats(other, c.stat) } }
    val rec = front.first()

    val reason = when {
        rec.stat.reachable >= rec.stat.sites && working.count { it.reachable >= it.sites } == 1 -> Reason.OnlyAllSites
        rec.stat.reachable >= rec.stat.sites -> Reason.AllSitesFastest
        rec.stat.reachable >= working.maxOf { it.reachable } -> Reason.MostSites
        else -> Reason.BestBalance
    }

    val goals = buildList {
        ranked.filter { it.stat.avgLatencyMs != null }.minWithOrNull(
            compareBy<Choice> { it.stat.avgLatencyMs }.thenByDescending { it.score.overall }
        )?.let { add(GoalOption(GoalKind.Fastest, it, it.command == rec.command, tradeoffs(it.stat, rec.stat))) }
        ranked.maxWithOrNull(compareBy<Choice> { it.stat.successRate }.thenBy { it.score.overall })
            ?.let { add(GoalOption(GoalKind.Reliable, it, it.command == rec.command, tradeoffs(it.stat, rec.stat))) }
        for (l in dash.lists) {
            if (l.sites == 0) continue
            val best = ranked.maxWithOrNull(
                compareBy<Choice> { it.stat.reachByList[l.id] ?: 0 }.thenBy { it.score.overall }
            ) ?: continue
            val opened = best.stat.reachByList[l.id] ?: 0
            if (opened == 0) continue
            val recOpened = rec.stat.reachByList[l.id] ?: 0
            // When the recommendation opens just as much of this group, it already is the answer.
            val pick = if (recOpened >= opened) rec else best
            val t = tradeoffs(pick.stat, rec.stat)
            add(GoalOption(GoalKind.Group, pick, pick.command == rec.command, t, l.id, l.name, pick.stat.reachByList[l.id] ?: 0, l.sites))
        }
    }

    val groups = dash.lists.filter { it.sites > 0 }.map { GroupRow(it.id, it.name, rec.stat.reachByList[it.id] ?: 0, it.sites) }

    return Decision(rec, reason, ranked, ranked.size - front.size, goals, groups, combine(rec, goals, lists))
}

/** Options that would break a host-limited group if put inside one. */
private val GROUPING = setOf("-A", "--auto", "-H", "--hosts")

private fun groupable(command: String) = shellSplit(command).none { t -> GROUPING.any { t == it || (it.length == 2 && t.startsWith(it)) } }

/**
 * When some site group opens more with another line than with the recommendation, one command
 * line that uses that line on that group's sites (`-H ":sites" … -An`) and the recommendation
 * everywhere else. Null when it wouldn't open more sites, or a line can't be grouped.
 */
fun combine(rec: Choice, goals: List<GoalOption>, lists: List<ListItemUi>): Combined? {
    val better = goals.filter { it.kind == GoalKind.Group && !it.same && it.listOpened > (rec.stat.reachByList[it.listId] ?: 0) }
    if (better.isEmpty() || !groupable(rec.command) || better.any { !groupable(it.choice.command) }) return null
    val byId = lists.associateBy { it.id }
    val used = HashSet<String>()
    val groups = mutableListOf<Pair<String?, String>>()
    val parts = mutableListOf<String>()
    var gained = 0
    for (g in better) {
        val domains = byId[g.listId]?.domains.orEmpty().filter { it.isNotBlank() && used.add(it) }
        if (domains.isEmpty()) continue
        parts += "-H \":${domains.joinToString(" ")}\" ${g.choice.command} -An"
        groups += g.listName to g.choice.name
        gained += g.listOpened - (rec.stat.reachByList[g.listId] ?: 0)
    }
    if (parts.isEmpty() || gained <= 0) return null
    groups += null to rec.name
    return Combined(
        command = (parts + rec.command).joinToString(" "),
        parts = groups,
        expectedOpened = (rec.stat.reachable + gained).coerceAtMost(rec.stat.sites),
        sites = rec.stat.sites
    )
}

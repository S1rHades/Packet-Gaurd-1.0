package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.data.StrategyResult

/**
 * The command line generator's search, kept free of Android so it can be unit-tested.
 *
 * Candidates are [Recipe]s, built only from ciadpi options that exist in the bundled engine
 * (split, disorder, oob, disoob, fake + ttl, tlsrec), so every generated command line is valid.
 * The search runs in rounds: a fixed set of single techniques first, then small variations of
 * the best ones, then a re-test of the top few so one lucky result can't win.
 */

/** A ciadpi position: an offset, optionally counted from the SNI (s) or Host (h), or the middle (m). */
data class Pos(val offset: Int, val flags: String = "") {
    override fun toString(): String = if (flags.isEmpty()) "$offset" else "$offset+$flags"
}

enum class Method(val flag: String) {
    Split("-s"),
    Disorder("-d"),
    Oob("-o"),
    Disoob("-q"),
    Fake("-f")
}

/** One candidate command line, as structure rather than text. */
data class Recipe(
    val method: Method,
    val pos: Pos,
    /** Only for [Method.Fake]: the fake packet's TTL. */
    val ttl: Int? = null,
    /** An optional second desync step (never a fake). */
    val second: Pair<Method, Pos>? = null,
    /** An optional TLS record split. */
    val tlsrec: Pos? = null
) {
    val command: String
        get() = buildList {
            add(method.flag + pos)
            if (method == Method.Fake) add("-t${ttl ?: 8}")
            second?.let { add(it.first.flag + it.second) }
            tlsrec?.let { add("-r$it") }
        }.joinToString(" ")

    /** Number of techniques combined; simpler recipes are preferred when results tie. */
    val parts: Int get() = 1 + (if (second != null) 1 else 0) + (if (tlsrec != null) 1 else 0)
}

/** How one candidate did in one test run. */
data class Trial(val opened: Int, val sites: Int, val successRate: Int, val latencyMs: Long?) {
    companion object {
        fun of(r: StrategyResult) = Trial(
            opened = r.siteResults.count { it.successCount > 0 },
            sites = r.siteResults.size,
            successRate = r.successPercentage,
            latencyMs = r.avgLatencyMs()
        )
    }
}

/** A candidate with its averaged results over every run it was in. */
data class Scored(
    val recipe: Recipe,
    val opened: Int,
    val sites: Int,
    val successRate: Int,
    val latencyMs: Long?,
    val runs: Int,
    val score: Double
) {
    val command: String get() = recipe.command
    val works: Boolean get() = opened > 0
}

/** How big a search to run. */
enum class SearchPlan(val rounds: Int, val perRound: Int, val verify: Int) {
    /** About 30 candidates: a few minutes. */
    Quick(rounds = 3, perRound = 8, verify = 3),
    /** About 50 candidates: closer to ten minutes. */
    Thorough(rounds = 5, perRound = 10, verify = 3);

    /** Candidates the plan tests at most, including the re-test round. */
    val budget: Int get() = SEEDS.size + (rounds - 1) * perRound + verify
}

/** The no-bypass check: ciadpi with only a comment desyncs nothing. */
const val BASELINE_COMMAND = "--comment=baseline"

/** Round 1: each basic technique at the positions that most often matter. */
val SEEDS: List<Recipe> = listOf(
    Recipe(Method.Split, Pos(1)),
    Recipe(Method.Split, Pos(1, "s")),
    Recipe(Method.Split, Pos(0, "sm")),
    Recipe(Method.Disorder, Pos(1)),
    Recipe(Method.Disorder, Pos(1, "s")),
    Recipe(Method.Oob, Pos(1)),
    Recipe(Method.Disoob, Pos(1)),
    Recipe(Method.Fake, Pos(-1), ttl = 8),
    Recipe(Method.Fake, Pos(-1), ttl = 5),
    Recipe(Method.Split, Pos(1), tlsrec = Pos(1, "s")),
    Recipe(Method.Disorder, Pos(1), tlsrec = Pos(1, "s")),
    Recipe(Method.Fake, Pos(-1), ttl = 8, second = Method.Split to Pos(1, "s")),
)

private val POSITIONS = listOf(Pos(1), Pos(2), Pos(3), Pos(1, "s"), Pos(2, "s"), Pos(0, "sm"), Pos(1, "h"))
private val TTLS = listOf(3, 5, 8, 12)
private val TLSREC_POSITIONS = listOf(Pos(1, "s"), Pos(0, "sm"), Pos(3, "s"))

/**
 * Score: sites opened dominates, then success rate, then speed; each extra technique costs a
 * little so a simpler command line wins a tie.
 */
fun scoreOf(opened: Int, sites: Int, successRate: Int, latencyMs: Long?, parts: Int): Double {
    if (sites <= 0) return 0.0
    val reach = opened.toDouble() / sites
    val speed = if (latencyMs == null || opened == 0) 0.0 else (1.0 - (latencyMs.coerceIn(0, 3000) / 3000.0))
    return reach * 1000 + successRate * 2 + speed * 100 - parts * 5
}

/** Small, valid variations of [r]: other positions, techniques, TTLs, and adding or removing a step. */
fun neighbours(r: Recipe): List<Recipe> = buildList {
    POSITIONS.filter { it != r.pos && r.method != Method.Fake }.forEach { add(r.copy(pos = it)) }
    Method.entries.filter { it != r.method }.forEach { m ->
        add(
            if (m == Method.Fake) r.copy(method = m, pos = Pos(-1), ttl = 8, second = r.second?.takeIf { it.first != Method.Fake })
            else r.copy(method = m, pos = if (r.method == Method.Fake) Pos(1) else r.pos, ttl = null)
        )
    }
    if (r.method == Method.Fake) TTLS.filter { it != r.ttl }.forEach { add(r.copy(ttl = it)) }
    if (r.tlsrec == null) add(r.copy(tlsrec = Pos(1, "s")))
    else {
        TLSREC_POSITIONS.filter { it != r.tlsrec }.forEach { add(r.copy(tlsrec = it)) }
        add(r.copy(tlsrec = null))
    }
    if (r.second == null) {
        if (r.method != Method.Split) add(r.copy(second = Method.Split to Pos(1, "s")))
        if (r.method != Method.Disorder) add(r.copy(second = Method.Disorder to Pos(1)))
    } else add(r.copy(second = null))
}.distinctBy { it.command }

class CommandSearch(val plan: SearchPlan) {
    private val recipes = LinkedHashMap<String, Recipe>()
    private val trials = HashMap<String, MutableList<Trial>>()

    val tested: Int get() = trials.size

    fun record(recipe: Recipe, trial: Trial) {
        recipes[recipe.command] = recipe
        trials.getOrPut(recipe.command) { mutableListOf() } += trial
    }

    /** Records a finished batch; results are matched to recipes by command line. */
    fun recordAll(batch: List<Recipe>, results: List<StrategyResult>) {
        val byCommand = results.filter { it.isCompleted }.associateBy { it.command }
        batch.forEach { r -> byCommand[r.command]?.let { record(r, Trial.of(it)) } }
    }

    /** Every tested candidate, best first. */
    fun ranking(): List<Scored> = recipes.values.mapNotNull { r ->
        val t = trials[r.command]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val opened = t.sumOf { it.opened } / t.size
        val sites = t.maxOf { it.sites }
        val rate = t.sumOf { it.successRate } / t.size
        val lat = t.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
        Scored(r, opened, sites, rate, lat, t.size, scoreOf(opened, sites, rate, lat, r.parts))
    }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.recipe.parts }.thenBy { it.command })

    fun best(): Scored? = ranking().firstOrNull()?.takeIf { it.works }

    /** Opens every tested site quickly: no need to keep searching. */
    fun solved(): Boolean = best()?.let { it.opened >= it.sites && it.sites > 0 && (it.latencyMs ?: Long.MAX_VALUE) < 200 } ?: false

    fun firstRound(): List<Recipe> = SEEDS.filter { it.command !in trials }

    /** Variations of the best few, not yet tested, spread across parents so one doesn't hog the round. */
    fun nextRound(): List<Recipe> {
        val parents = ranking().filter { it.works }.take(3).map { it.recipe }
            .ifEmpty { SEEDS.take(3) }
        val pools = parents.map { p -> neighbours(p).filter { it.command !in trials }.toMutableList() }
        val out = LinkedHashMap<String, Recipe>()
        while (out.size < plan.perRound && pools.any { it.isNotEmpty() }) {
            for (pool in pools) {
                if (out.size >= plan.perRound) break
                while (pool.isNotEmpty()) {
                    val r = pool.removeAt(0)
                    if (r.command !in out) { out[r.command] = r; break }
                }
            }
        }
        return out.values.toList()
    }

    /** The top candidates once more, to confirm they weren't a fluke. */
    fun verificationRound(): List<Recipe> = ranking().filter { it.works }.take(plan.verify).map { it.recipe }
}

/** Sites that failed with no bypass at all: the only ones worth searching for. */
fun blockedSites(baseline: StrategyResult?): List<String> =
    baseline?.siteResults?.filter { it.successCount <= 0 }?.map { it.site }.orEmpty()

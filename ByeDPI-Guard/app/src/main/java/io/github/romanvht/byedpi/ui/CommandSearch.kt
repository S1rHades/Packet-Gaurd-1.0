package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.data.LineRecord
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.utility.shellSplit
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The command line generator's search, kept free of Android so it can be unit-tested.
 *
 * It learns from the test instead of picking from a list:
 *  1. Round 1 sends a set of **probes**: one of each basic technique. They are experiments,
 *     not answers: their job is to show which techniques open which of your blocked sites.
 *  2. Every result is recorded **per site**, and each feature of a command line (technique,
 *     where it splits, the fake's TTL, the TLS record split, ...) gets credit for the sites it
 *     opened. That gives a learned picture of what your provider lets through.
 *  3. Each later round **builds new command lines** from that picture:
 *     - *tuning*: the best ones, changed where the learning says it should help (new
 *       positions, a different technique, a different TTL, an extra step), with fresh values
 *       rather than a fixed menu;
 *     - *combining*: two command lines that open different sites are merged into one;
 *     - *fallbacks*: when no single line opens everything, the best one gets a second set of
 *       options (`-At,s`) that ciadpi switches to when a site is blocked, chosen for the sites
 *       the first one missed;
 *     - *exploring*: one line drawn entirely from what was learned, to avoid getting stuck.
 *  4. The top few are re-tested so a lucky moment can't win.
 *
 * It also starts from what this phone already knows ([CommandSearch.prime]): every line tested
 * before, in a Benchmark (ready-made and community lines) or an earlier search, credits its
 * features for the sites it opened, and the best of them join round 1 as **known** lines. So
 * the search begins where earlier tests left off, builds on the best known lines, and can't
 * end up with a line that scores worse than them on your blocked sites.
 *
 * Every option used exists in the bundled ByeDPI engine, so every command line is valid.
 */

/** A ciadpi position: an offset, optionally counted from the SNI (s) or Host (h), or the middle (m). */
data class Pos(val offset: Int, val flags: String = "") {
    override fun toString(): String = if (flags.isEmpty()) "$offset" else "$offset+$flags"

    /** Rough place in a TLS ClientHello, so steps are written in the order they happen. */
    val order: Int
        get() = when {
            flags.startsWith("h") -> 200 + offset
            flags == "sm" -> 520
            flags.startsWith("s") && 'e' in flags -> 540 + offset
            flags.startsWith("s") -> 500 + offset
            offset < 0 -> 10_000 + offset
            else -> offset
        }

    /** The kind of position, for learning ("1", "2" and "3" are all "near the start"). */
    val kind: String
        get() = when {
            flags.isNotEmpty() -> flags
            offset < 0 -> "end"
            else -> "abs"
        }
}

enum class Method(val flag: String) {
    Split("-s"),
    Disorder("-d"),
    Oob("-o"),
    Disoob("-q"),
    Fake("-f")
}

/** One desync step: a technique at a position. */
data class Step(val method: Method, val pos: Pos) {
    val text: String get() = method.flag + pos
}

/** How the fake packet is made (`-Q`): random contents, or a copy of the real one. */
enum class FakeMod(val flag: String) { Rand("-Qr"), Orig("-Qo") }

/** How a candidate came about, for the result's "how it was made" line. */
enum class Origin { Probe, Known, Tuned, Combined, Fallback, Explored }

/** One candidate command line, as structure rather than text. */
data class Recipe(
    val steps: List<Step>,
    /** The fake packet's TTL; only when a step is a fake. */
    val ttl: Int? = null,
    val fakeMod: FakeMod? = null,
    /** An optional TLS record split. */
    val tlsrec: Pos? = null,
    /** Options ciadpi switches to when this one looks blocked (`-At,s`). */
    val fallback: Recipe? = null,
    val origin: Origin = Origin.Probe,
    /** The command lines this one was built from. */
    val parents: List<String> = emptyList(),
    /**
     * A line tested before (e.g. a community one), tested exactly as written. The fields above
     * are a simplified reading of it, used for learning and for building new lines from it;
     * anything built from it drops [raw] (see [tidy]).
     */
    val raw: String? = null
) {
    /** The older one-technique form, kept for the probes. */
    constructor(method: Method, pos: Pos, ttl: Int? = null, second: Pair<Method, Pos>? = null, tlsrec: Pos? = null) :
        this(listOfNotNull(Step(method, pos), second?.let { Step(it.first, it.second) }), ttl, null, tlsrec)

    val hasFake: Boolean get() = steps.any { it.method == Method.Fake }

    val command: String
        get() = raw ?: buildList {
            body(this)
            fallback?.let { fb ->
                add("-At,s")
                fb.body(this)
            }
        }.joinToString(" ")

    private fun body(out: MutableList<String>) {
        var fakeDone = false
        steps.sortedBy { it.pos.order }.forEach { s ->
            out.add(s.text)
            if (s.method == Method.Fake && !fakeDone) {
                fakeDone = true
                out.add("-t${ttl ?: 8}")
                fakeMod?.let { out.add(it.flag) }
            }
        }
        tlsrec?.let { out.add("-r$it") }
    }

    /** Number of techniques combined; simpler recipes are preferred when results tie. */
    val parts: Int
        get() = steps.size + (if (tlsrec != null) 1 else 0) + (if (fakeMod != null) 1 else 0) + (fallback?.parts ?: 0)

    /** The same recipe made valid: 1 to 3 steps, at most one fake, a TTL only with a fake. */
    fun tidy(): Recipe {
        val seen = HashSet<String>()
        var fakes = 0
        val s = steps.filter { st ->
            if (st.method == Method.Fake && fakes++ > 0) false else seen.add(st.text)
        }.take(3).ifEmpty { listOf(Step(Method.Split, Pos(1))) }
        val fake = s.any { it.method == Method.Fake }
        return copy(
            raw = null,
            steps = s,
            ttl = if (fake) (ttl ?: 8).coerceIn(1, 32) else null,
            fakeMod = if (fake) fakeMod else null,
            fallback = fallback?.copy(fallback = null)?.tidy()
        )
    }

    /** Names of what this recipe does, for crediting what opened which site. */
    val features: Set<String>
        get() = buildSet {
            steps.forEach { s ->
                add("m:${s.method}")
                add("mp:${s.method}@${s.pos.kind}")
                add("p:${s.pos.kind}")
            }
            add("n:${steps.size}")
            add("tls:${tlsrec?.kind ?: "none"}")
            if (hasFake) {
                add("ttl:${ttlBucket(ttl ?: 8)}")
                add("fm:${fakeMod ?: "none"}")
            }
            if (fallback != null) add("fb")
        }
}

private fun ttlBucket(ttl: Int) = when {
    ttl <= 3 -> "low"
    ttl <= 6 -> "mid"
    ttl <= 10 -> "high"
    else -> "vhigh"
}

/** How one candidate did in one test run, including which sites it opened. */
data class Trial(
    val opened: Int,
    val sites: Int,
    val successRate: Int,
    val latencyMs: Long?,
    val openedSites: Set<String> = emptySet(),
    val testedSites: Set<String> = emptySet()
) {
    companion object {
        fun of(r: StrategyResult) = Trial(
            opened = r.siteResults.count { it.successCount > 0 },
            sites = r.siteResults.size,
            successRate = r.successPercentage,
            latencyMs = r.avgLatencyMs(),
            openedSites = r.siteResults.filter { it.successCount > 0 }.map { it.site }.toSet(),
            testedSites = r.siteResults.map { it.site }.toSet()
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
    val score: Double,
    /** Sites it opened in every run. */
    val openedSites: Set<String> = emptySet()
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
    val budget: Int get() = KNOWN_MAX + SEEDS.size + (rounds - 1) * perRound + verify
}

/** Lines tested before that join round 1 at most. */
const val KNOWN_MAX = 5

/** Of those, the best on the blocked sites alone; the rest are the best on every site ([CHAMPIONS]). */
private const val KNOWN_ON_BLOCKED = 3

/** Remembered lines that scored best on every site, which the search always tries to beat. */
private const val CHAMPIONS = 2

/** Longest command line the search builds by extending a known one. */
private const val MAX_RAW = 600

/** The no-bypass check: ciadpi with only a comment desyncs nothing. */
const val BASELINE_COMMAND = "--comment=baseline"

/** Round 1's probes: one of each basic technique, to see what your provider lets through. */
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

private val SEED_COMMANDS: Set<String> by lazy { SEEDS.map { it.command }.toSet() }

/** True for a command line the search built itself, rather than one of its probes. */
fun isGenerated(command: String): Boolean = command !in SEED_COMMANDS

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

/** A random position of the given kind, with a fresh offset rather than one from a fixed menu. */
private fun randomPos(kind: String, rnd: Random): Pos = when (kind) {
    "s" -> Pos(rnd.nextInt(-2, 8), "s")
    "sm" -> Pos(0, "sm")
    "se" -> Pos(-rnd.nextInt(1, 6), "se")
    "h" -> Pos(rnd.nextInt(0, 6), "h")
    "end" -> Pos(-rnd.nextInt(1, 4))
    else -> Pos(rnd.nextInt(1, 8))
}

private val POS_KINDS = listOf("abs", "s", "sm", "se", "h", "end")

private fun ttlIn(bucket: String, rnd: Random): Int = when (bucket) {
    "low" -> rnd.nextInt(1, 4)
    "mid" -> rnd.nextInt(4, 7)
    "high" -> rnd.nextInt(7, 11)
    else -> rnd.nextInt(11, 17)
}

/**
 * One small random change to [r]: move a step, change its technique, add or drop one, and so on.
 * [pick] chooses among options; each option is rated by the feature `prefix + option`
 * ("m:" techniques, "p:" position kinds, "tls:" TLS record splits, "ttl:" TTL ranges).
 */
fun mutate(r: Recipe, rnd: Random, pick: (List<String>, String) -> String = { o, _ -> o.random(rnd) }): Recipe {
    val steps = r.steps.toMutableList()
    val i = rnd.nextInt(steps.size)
    val s = steps[i]
    val out = when (rnd.nextInt(8)) {
        0 -> { // nudge the offset
            val d = (if (rnd.nextBoolean()) 1 else -1) * rnd.nextInt(1, 4)
            val off = (s.pos.offset + d).let { if (s.pos.flags.isEmpty() && s.pos.offset > 0) it.coerceAtLeast(1) else it }
            steps[i] = s.copy(pos = s.pos.copy(offset = off)); r.copy(steps = steps)
        }
        1 -> { // move it to another kind of position
            steps[i] = s.copy(pos = randomPos(pick(POS_KINDS, "p:"), rnd)); r.copy(steps = steps)
        }
        2 -> { // another technique at the same place
            val m = Method.valueOf(pick(Method.entries.filter { it != s.method }.map { it.name }, "m:"))
            steps[i] = Step(m, if (m == Method.Fake && s.pos.offset > 0 && s.pos.flags.isEmpty()) Pos(-1) else s.pos)
            r.copy(steps = steps, ttl = if (m == Method.Fake) r.ttl ?: 8 else r.ttl)
        }
        3 -> { // add a step
            val m = Method.valueOf(pick(Method.entries.map { it.name }, "m:"))
            steps += Step(m, if (m == Method.Fake) Pos(-1) else randomPos(pick(POS_KINDS, "p:"), rnd))
            r.copy(steps = steps, ttl = r.ttl ?: if (m == Method.Fake) 8 else null)
        }
        4 -> if (steps.size > 1) { steps.removeAt(i); r.copy(steps = steps) } else r.copy(tlsrec = if (r.tlsrec == null) Pos(1, "s") else null)
        5 -> {
            val kind = pick(listOf("none", "s", "sm", "abs"), "tls:")
            r.copy(tlsrec = if (kind == "none") null else randomPos(kind, rnd))
        }
        6 -> if (r.hasFake) {
            // Mostly a nudge from the current TTL, sometimes a jump to the range that did best.
            val t = r.ttl ?: 8
            r.copy(ttl = if (rnd.nextInt(3) > 0) (t + (if (rnd.nextBoolean()) 1 else -1) * rnd.nextInt(1, 4)).coerceIn(1, 20)
            else ttlIn(pick(listOf("low", "mid", "high", "vhigh"), "ttl:"), rnd))
        } else r.copy(steps = steps + Step(Method.Fake, Pos(-1)), ttl = ttlIn(pick(listOf("low", "mid", "high", "vhigh"), "ttl:"), rnd))
        else -> if (r.hasFake) r.copy(fakeMod = listOf(null, FakeMod.Rand, FakeMod.Orig).filter { it != r.fakeMod }.random(rnd))
        else { steps[i] = s.copy(pos = randomPos(s.pos.kind, rnd)); r.copy(steps = steps) }
    }
    return out.tidy().copy(origin = Origin.Tuned, parents = listOf(r.command), fallback = r.fallback)
}

/** Every step of [a] and [b] in one command line (up to three), for sites each opens alone. */
fun combine(a: Recipe, b: Recipe): Recipe = Recipe(
    steps = (a.steps + b.steps),
    ttl = a.ttl ?: b.ttl,
    fakeMod = a.fakeMod ?: b.fakeMod,
    tlsrec = a.tlsrec ?: b.tlsrec,
    origin = Origin.Combined,
    parents = listOf(a.command, b.command)
).tidy()

/** [primary], switching to [helper] when a site looks blocked (timeout, reset or TLS error). */
fun withFallback(primary: Recipe, helper: Recipe): Recipe =
    primary.copy(fallback = helper.copy(fallback = null), origin = Origin.Fallback, parents = listOf(primary.command, helper.command)).tidy()

/**
 * What the test has taught so far: for every feature of a command line and every blocked site,
 * how often command lines with that feature opened it.
 */
class Learning {
    private val stats = HashMap<String, HashMap<String, DoubleArray>>() // feature -> site -> [opened, tried]
    val sites = LinkedHashSet<String>()

    fun learn(recipe: Recipe, trial: Trial) {
        if (trial.testedSites.isEmpty()) return
        sites += trial.testedSites
        for (site in trial.testedSites) add(recipe, site, if (site in trial.openedSites) 1.0 else 0.0, 1.0)
    }

    /**
     * Earlier evidence about [site]: [opened] of [tried] tries, counted at [weight] each.
     * Doesn't add [site] to [sites]: only sites tested in this search are targets.
     */
    fun learnPrior(recipe: Recipe, site: String, opened: Double, tried: Double, weight: Double) {
        if (tried <= 0 || weight <= 0) return
        add(recipe, site, opened * weight, tried * weight)
    }

    private fun add(recipe: Recipe, site: String, opened: Double, tried: Double) {
        for (f in recipe.features) {
            val c = stats.getOrPut(f) { HashMap() }.getOrPut(site) { DoubleArray(2) }
            c[0] += opened
            c[1] += tried
        }
    }

    /** Estimated chance that a line with [feature] opens [site] (0..1), with a mild prior. */
    fun chance(feature: String, site: String): Double {
        val c = stats[feature]?.get(site) ?: return 0.5
        return (c[0] + 1.0) / (c[1] + 2.0)
    }

    private fun tries(feature: String): Double = stats[feature]?.values?.sumOf { it[1] } ?: 0.0

    /**
     * How promising [feature] is for [targets] (by default every site), plus a bonus for
     * features tried little, so untested ideas still get a chance.
     */
    fun value(feature: String, targets: Collection<String> = sites, rnd: Random? = null): Double {
        if (targets.isEmpty()) return 0.5
        val mean = targets.sumOf { chance(feature, it) } / targets.size
        val explore = 0.35 / sqrt(tries(feature) + 1.0)
        return mean + explore * (rnd?.nextDouble() ?: 1.0)
    }

    /** Picks one option, weighted by how well its feature has done on [targets]. */
    fun choose(options: List<String>, feature: (String) -> String, targets: Collection<String>, rnd: Random): String =
        options.maxByOrNull { value(feature(it), targets, rnd) } ?: options.random(rnd)
}

class CommandSearch(val plan: SearchPlan, private val rnd: Random = Random.Default) {
    private val recipes = LinkedHashMap<String, Recipe>()
    private val trials = HashMap<String, MutableList<Trial>>()
    val learning = Learning()
    private var known: List<Recipe> = emptyList()

    val tested: Int get() = trials.size

    /** Lines tested before whose results [prime] used. */
    var learnedFrom: Int = 0
        private set

    /** The known lines round 1 tests again (up to [KNOWN_MAX]). */
    val knownLines: List<Recipe> get() = known

    /**
     * The remembered lines with the best score on every site, the way the Benchmark scores them
     * (best first). These are the lines to beat: the search keeps them whole and builds on them.
     */
    var champions: List<Recipe> = emptyList()
        private set

    /**
     * Starts from what this phone already knows: every remembered line's results on the [blocked]
     * sites teach the learning (older ones count less, halving every [halfLifeDays]), and the
     * best-looking ones are tested again in round 1, exactly as written.
     */
    fun prime(
        memory: List<LineRecord>,
        blocked: Collection<String>,
        now: Long,
        halfLifeDays: Double = 14.0,
        everySite: Collection<String> = emptyList()
    ) {
        val targets = blocked.toSet()
        if (targets.isEmpty()) return
        val every = everySite.toSet()
        val candidates = mutableListOf<Pair<Recipe, Double>>()
        val onEvery = mutableListOf<Pair<Recipe, Double>>()
        for (rec in memory) {
            val parsed = parseRecipe(rec.command) ?: continue
            val age = ((now - rec.updatedAt).coerceAtLeast(0)) / 86_400_000.0
            // Its score on every site, as the Benchmark shows it, when it was tested on most of
            // them; a site it was never tested on counts as not opened.
            if (every.isNotEmpty()) {
                val all = rec.sites.filterKeys { it in every }.filterValues { it.size >= 2 && it[1] > 0 }
                if (all.size * 5 >= every.size * 4) {
                    val opened = all.count { it.value[0] > 0 }
                    val rate = all.values.sumOf { it[0] } * 100 / all.values.sumOf { it[1] }.coerceAtLeast(1)
                    val overall = CmdScore.of(opened, every.size, rate, rec.avgLatencyMs).overall
                    // Older results count a little less, but a strong line stays a strong line.
                    if (opened > 0) onEvery += parsed to overall * (0.5 + 0.5 * 0.5.pow(age / halfLifeDays))
                }
            }
            val seen = rec.sites.filterKeys { it in targets }.filterValues { it.size >= 2 && it[1] > 0 }
            if (seen.isEmpty()) continue
            learnedFrom++
            val ageDays = ((now - rec.updatedAt).coerceAtLeast(0)) / 86_400_000.0
            // At most about three tries' worth per site, so this search's own tests soon count more.
            val weight = 0.5.pow(ageDays / halfLifeDays)
            seen.forEach { (site, c) ->
                val n = minOf(c[1].toDouble(), 3.0)
                learning.learnPrior(parsed, site, c[0].toDouble() / c[1] * n, n, weight)
            }
            if (rec.command in SEED_COMMANDS) continue
            // How it would score on these sites, counting sites it never saw as not opened.
            val opened = seen.count { it.value[0] > 0 }
            if (opened == 0) continue
            val rate = seen.values.sumOf { it[0] } * 100 / seen.values.sumOf { it[1] }.coerceAtLeast(1)
            candidates += parsed to scoreOf(opened, targets.size, rate, rec.avgLatencyMs, parsed.parts) * weight
        }
        champions = onEvery.sortedByDescending { it.second }.map { it.first }.distinctBy { it.command }.take(CHAMPIONS)
        val onBlocked = candidates.sortedByDescending { it.second }.map { it.first }
            .filter { c -> champions.none { it.command == c.command } }.take(KNOWN_ON_BLOCKED)
        known = (champions + onBlocked).distinctBy { it.command }.take(KNOWN_MAX)
    }

    fun record(recipe: Recipe, trial: Trial) {
        if (recipe.command !in recipes) recipes[recipe.command] = recipe
        trials.getOrPut(recipe.command) { mutableListOf() } += trial
        learning.learn(recipe, trial)
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
        val always = t.map { it.openedSites }.reduce { a, b -> a intersect b }
        Scored(r, opened, sites, rate, lat, t.size, scoreOf(opened, sites, rate, lat, r.parts), always)
    }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.recipe.parts }.thenBy { it.command })

    fun best(): Scored? = ranking().firstOrNull()?.takeIf { it.works }

    /** Opens every tested site quickly: no need to keep searching. */
    fun solved(): Boolean = best()?.let { it.opened >= it.sites && it.sites > 0 && (it.latencyMs ?: Long.MAX_VALUE) < 200 } ?: false

    fun firstRound(): List<Recipe> = (known + SEEDS).distinctBy { it.command }.filter { it.command !in trials }

    /**
     * New command lines built from what the test has shown: fallbacks for the sites the best
     * one misses, combinations of lines that open different sites, tuned versions of the best
     * ones and one line drawn from the learning alone. None of them has been tested before.
     */
    fun nextRound(): List<Recipe> {
        val out = LinkedHashMap<String, Recipe>()
        fun offer(r: Recipe): Boolean {
            val c = r.command
            if (c in trials || c in out || out.size >= plan.perRound) return false
            out[c] = r
            return true
        }
        val ranked = ranking()
        val working = ranked.filter { it.works }
        val best = working.firstOrNull()
        val all = learning.sites
        val missing = if (best == null) all.toSet() else all - best.openedSites

        // 0. Extend lines tested before (community lines and the like) exactly as written: keep
        //     every group they have and add one more group (`-At,s`) for the sites they miss,
        //     taken from the line that opens most of those. Simplifying a long community line
        //     into a few steps would throw away what makes it work.
        //     The lines that scored best on every site ([champions]) always get this, even when
        //     other lines did better on the blocked sites alone.
        val champ = champions.map { it.command }.toSet()
        val bases = (working.filter { it.command in champ } + working.filter { it.recipe.raw != null }.take(2))
            .distinctBy { it.command }.take(3)
        bases.forEach { known ->
            val gap = all - known.openedSites
            if (gap.isEmpty()) return@forEach
            helpersFor(known, gap, working).take(if (known.command in champ) 2 else 1)
                .ifEmpty { listOf(explore(gap)) }
                .forEach { h -> offer(extend(known.recipe, h)) }
        }

        // 1. Fallbacks: the best line, switching to whichever line opens most of what it misses.
        if (best != null && missing.isNotEmpty()) {
            working.drop(1)
                .map { it to (it.openedSites intersect missing).size }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(2)
                .forEach { (helper, _) -> offer(withFallback(best.recipe.copy(fallback = null), helper.recipe.copy(fallback = null))) }
        }

        // 1b. Cover: the two or three lines that together open the most sites (each picked for
        //     what the previous ones miss), joined into one line at once rather than pair by
        //     pair over several rounds. Matters most when earlier tests left lines that each
        //     open a different part of the sites.
        val cover = mutableListOf<Scored>()
        var covered = emptySet<String>()
        while (cover.size < 3) {
            val next = working.filter { it !in cover }.maxByOrNull { (it.openedSites - covered).size * 1000 + it.score } ?: break
            if ((next.openedSites - covered).isEmpty()) break
            cover += next
            covered = covered + next.openedSites
        }
        if (cover.size >= 2 && covered.size > (best?.openedSites?.size ?: 0)) {
            val (a, b) = cover[0].recipe.copy(fallback = null) to cover[1].recipe.copy(fallback = null)
            val c = cover.getOrNull(2)?.recipe?.copy(fallback = null)
            if (c == null) {
                offer(combine(a, b))
                offer(withFallback(a, b))
            } else {
                offer(withFallback(combine(a, b), c).copy(origin = Origin.Combined, parents = listOf(a.command, b.command, c.command)))
                offer(withFallback(a, combine(b, c)).copy(origin = Origin.Combined, parents = listOf(a.command, b.command, c.command)))
                offer(combine(combine(a, c), b))
            }
        }

        // 2. Combinations of lines that open different sites.
        val elites = working.take(5)
        val pairs = elites.flatMapIndexed { i, a -> elites.drop(i + 1).map { b -> Triple(a, b, (a.openedSites union b.openedSites).size - maxOf(a.openedSites.size, b.openedSites.size)) } }
            .sortedByDescending { it.third }
        pairs.take(3).forEach { (a, b, _) -> offer(combine(a.recipe.copy(fallback = null), b.recipe.copy(fallback = null))) }

        val targets = missing.ifEmpty { all }

        // 3. Targeted: add to the best lines the ingredient the learning rates most likely to
        //    open the sites they still miss (including ideas not tried yet).
        if (missing.isNotEmpty()) {
            working.take(2).forEach { w -> targetedAdditions(w.recipe.copy(fallback = null), missing).take(3).forEach { offer(it) } }
        }

        // 4. Tuned versions of the best ones, steered by the learning towards the missing sites.
        val pick: (List<String>, String) -> String = { options, prefix ->
            learning.choose(options, { o -> prefix + o }, targets, rnd)
        }
        val parents = (working.ifEmpty { ranked }).take(3).map { it.recipe }.ifEmpty { SEEDS.take(3) }
        var tries = 0
        while (out.size < plan.perRound - 1 && tries++ < 200) {
            val p = parents[tries % parents.size]
            var child = mutate(p, rnd, pick)
            if (rnd.nextInt(3) == 0) child = mutate(child, rnd, pick).copy(parents = listOf(p.command))
            // A shift that changes nothing the learning can tell apart teaches little: mostly skip it.
            if (child.features == p.features && rnd.nextInt(3) > 0) continue
            offer(child)
        }

        // 5. One line from the learning alone, so the search can leave a dead end.
        for (k in 0 until 20) if (out.size >= plan.perRound || offer(explore(targets))) break
        while (out.size < plan.perRound && tries++ < 400) offer(mutate(parents.random(rnd), rnd, pick))
        return out.values.toList()
    }

    /**
     * [r] with one ingredient added or changed for the [missing] sites, best-rated first:
     * a fake at a TTL range, a TLS record split, or another technique at another place.
     */
    private fun targetedAdditions(r: Recipe, missing: Set<String>): List<Recipe> {
        // The best idea of each kind, so a new TTL range isn't crowded out by new positions.
        val fake = listOf("low", "mid", "high", "vhigh").maxBy { learning.value("ttl:$it", missing, rnd) }.let { b ->
            val ttl = ttlIn(b, rnd)
            if (r.hasFake) r.copy(ttl = ttl) else r.copy(steps = r.steps + Step(Method.Fake, Pos(-1)), ttl = ttl)
        }
        val tls = listOf("s", "sm", "abs").filter { it != r.tlsrec?.kind }
            .maxBy { learning.value("tls:$it", missing, rnd) }.let { r.copy(tlsrec = randomPos(it, rnd)) }
        val step = Method.entries.filter { it != Method.Fake }.flatMap { m -> POS_KINDS.map { m to it } }
            .maxBy { (m, k) -> learning.value("mp:$m@$k", missing, rnd) }
            .let { (m, k) -> r.copy(steps = r.steps + Step(m, randomPos(k, rnd))) }
        return listOf(fake, tls, step)
            .map { it.tidy().copy(origin = Origin.Tuned, parents = listOf(r.command)) }
            .filter { it.command != r.command }
            .distinctBy { it.command }
    }

    /** A fresh line: the technique and position kind the learning rates best, with new offsets. */
    private fun explore(targets: Collection<String>): Recipe {
        val m = Method.valueOf(learning.choose(Method.entries.map { it.name }, { "m:$it" }, targets, rnd))
        val kind = learning.choose(POS_KINDS, { "mp:$m@$it" }, targets, rnd)
        val steps = mutableListOf(Step(m, if (m == Method.Fake) Pos(-1) else randomPos(kind, rnd)))
        if (learning.value("n:2", targets, rnd) > learning.value("n:1", targets, rnd)) {
            val m2 = Method.valueOf(learning.choose(Method.entries.filter { it != m && it != Method.Fake }.map { it.name }, { "m:$it" }, targets, rnd))
            steps += Step(m2, randomPos(learning.choose(POS_KINDS, { "mp:$m2@$it" }, targets, rnd), rnd))
        }
        val tls = learning.choose(listOf("none", "s", "sm"), { "tls:$it" }, targets, rnd)
        return Recipe(
            steps = steps,
            ttl = if (m == Method.Fake) ttlIn(learning.choose(listOf("low", "mid", "high", "vhigh"), { "ttl:$it" }, targets, rnd), rnd) else null,
            tlsrec = if (tls == "none") null else randomPos(tls, rnd),
            origin = Origin.Explored
        ).tidy()
    }

    /**
     * Lines that open the most of [gap] (the sites [base] misses), ready to add to [base] as a
     * last group: never [base] itself or a line already built on it.
     */
    private fun helpersFor(base: Scored, gap: Set<String>, pool: List<Scored>): List<Recipe> = pool
        .filter { it.command != base.command && base.command !in it.command && (it.openedSites intersect gap).isNotEmpty() }
        .sortedByDescending { (it.openedSites intersect gap).size * 1000 + it.score }
        .map { it.recipe.copy(fallback = null).tidy() }
        .distinctBy { it.command }

    /**
     * New lines meant to beat [best], a line known before that won the all-sites test
     * ([tested] holds that test's results, so the sites it missed are known exactly): [best]
     * kept whole, with one or two more groups for the sites it missed, taken from the lines
     * that opened them in that test or in the search. None of them was tested before.
     */
    fun challengers(best: Scored, tested: List<Scored>, every: Collection<String>, max: Int = 4): List<Recipe> {
        val gap = every.toSet() - best.openedSites
        if (gap.isEmpty()) return emptyList()
        val pool = (tested + ranking().filter { it.works }).distinctBy { it.command }
        val helpers = helpersFor(best, gap, pool)
        val out = LinkedHashMap<String, Recipe>()
        fun add(r: Recipe) {
            if (r.command != best.command && r.command !in trials && r.command !in tested.map { it.command } && r.command !in out) out[r.command] = r
        }
        helpers.take(3).forEach { add(extend(best.recipe, it)) }
        // Two groups: the helper that opens most of the gap, then the one that opens most of the rest.
        val first = helpers.firstOrNull()
        if (first != null) {
            val opens = pool.firstOrNull { it.recipe.copy(fallback = null).tidy().command == first.command }?.openedSites.orEmpty()
            val rest = gap - opens
            val second = helpersFor(best, rest, pool).firstOrNull { it.command != first.command }
            if (second != null && rest.isNotEmpty()) {
                val one = extend(best.recipe, first)
                if (one.raw != null) add(extend(one, second).copy(parents = listOf(best.command, first.command, second.command)))
            }
        }
        if (out.isEmpty()) {
            val targets = gap.filter { it in learning.sites }.ifEmpty { learning.sites.toList() }
            if (targets.isNotEmpty()) add(extend(best.recipe, explore(targets)))
        }
        return out.values.take(max)
    }

    /** [known] as written, plus a last group with [helper] for the sites it misses. */
    private fun extend(known: Recipe, helper: Recipe): Recipe {
        val text = known.command + " -At,s " + helper.command
        return known.copy(
            raw = text,
            fallback = helper.copy(fallback = null, raw = null),
            origin = Origin.Fallback,
            parents = listOf(known.command, helper.command)
        ).takeIf { text.length <= MAX_RAW } ?: helper
    }

    /** The top candidates once more, to confirm they weren't a fluke. */
    fun verificationRound(): List<Recipe> = ranking().filter { it.works }.take(plan.verify).map { it.recipe }
}

/** Sites that failed with no bypass at all: the only ones worth searching for. */
fun blockedSites(baseline: StrategyResult?): List<String> =
    baseline?.siteResults?.filter { it.successCount <= 0 }?.map { it.site }.orEmpty()

/** Kept for callers that list a recipe's simple variations; now drawn by [mutate]. */
fun neighbours(r: Recipe, count: Int = 12, rnd: Random = Random(r.command.hashCode())): List<Recipe> {
    val out = LinkedHashMap<String, Recipe>()
    var tries = 0
    while (out.size < count && tries++ < count * 20) {
        val m = mutate(r, rnd)
        if (m.command != r.command && m.command !in out) out[m.command] = m
    }
    return out.values.toList()
}

private val POS_TEXT = Regex("""^(-?\d+)(?::\d+(?::\d+)?)?(?:\+([a-z]+))?$""")

/** A ciadpi position such as `1`, `-1`, `3+s`, `2:5+sm` or `1+h`, simplified to a [Pos]. */
internal fun readPos(text: String): Pos? {
    val m = POS_TEXT.matchEntire(text) ?: return null
    val off = m.groupValues[1].toIntOrNull() ?: return null
    val f = m.groupValues[2]
    return when {
        f.startsWith("s") && 'm' in f -> Pos(0, "sm")
        f.startsWith("s") && 'e' in f -> Pos(if (off >= 0) -1 else off, "se")
        f.startsWith("s") -> Pos(off, "s")
        f.startsWith("h") -> Pos(off, "h")
        else -> Pos(off)
    }
}

private val SHORT_STEPS = mapOf("-s" to Method.Split, "-d" to Method.Disorder, "-o" to Method.Oob, "-q" to Method.Disoob, "-f" to Method.Fake)
private val LONG_STEPS = mapOf("--split" to Method.Split, "--disorder" to Method.Disorder, "--oob" to Method.Oob, "--disoob" to Method.Disoob, "--fake" to Method.Fake)

/**
 * A simplified [Recipe] for any tested command line, so the search can learn from it and build
 * new lines from it: the first group's techniques (up to three, in packet order), the fake's TTL
 * and kind, the TLS record split, and the next group as the fallback. Options the search doesn't
 * build with (host lists, `-a`, `-M`, fake data, ...) are left out. The line itself is kept in
 * [Recipe.raw] and tested as written. Null when it has no desync step at all.
 */
fun parseRecipe(command: String): Recipe? {
    val tokens = runCatching { shellSplit(command) }.getOrNull() ?: return null
    val groups = mutableListOf(mutableListOf<String>())
    tokens.forEach { t -> if (t.startsWith("-A") || t.startsWith("--auto")) groups += mutableListOf<String>() else groups.last() += t }
    fun read(group: List<String>): Recipe? {
        val steps = mutableListOf<Step>()
        var ttl: Int? = null
        var mod: FakeMod? = null
        var tls: Pos? = null
        var i = 0
        // The option's value: attached ("-s1+s", "--ttl=6") or the next token ("--ttl 6").
        fun valueOf(attached: String?): String? = attached?.takeIf { it.isNotEmpty() } ?: group.getOrNull(i)?.also { i++ }
        while (i < group.size) {
            val t = group[i++]
            val isLong = t.startsWith("--")
            val name = if (isLong) t.substringBefore('=') else t.take(2)
            val attached: String? = if (isLong) (if ('=' in t) t.substringAfter('=') else null) else t.drop(2)
            val method = if (isLong) LONG_STEPS[name] else SHORT_STEPS[name]
            when {
                method != null -> {
                    val p = valueOf(attached)?.let { readPos(it) }
                    if (p != null) steps += Step(method, p)
                }
                name == "--ttl" || name == "-t" -> {
                    val v = valueOf(attached)?.toIntOrNull()
                    if (ttl == null) ttl = v
                }
                name == "--tlsrec" || name == "-r" -> {
                    val p = valueOf(attached)?.let { readPos(it) }
                    if (tls == null) tls = p
                }
                t == "-Qr" -> mod = FakeMod.Rand
                t == "-Qo" -> mod = FakeMod.Orig
            }
        }
        if (steps.isEmpty()) return null
        return Recipe(steps.sortedBy { it.pos.order }.take(3), ttl, mod, tls).tidy()
    }
    val first = read(groups[0]) ?: groups.drop(1).firstNotNullOfOrNull { read(it) } ?: return null
    val fallback = groups.drop(1).firstNotNullOfOrNull { read(it) }?.takeIf { it.command != first.command }
    return first.copy(fallback = fallback).tidy().copy(raw = command.trim(), origin = Origin.Known)
}

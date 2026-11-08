package io.github.romanvht.byedpi.ui

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.LineMemoryStore
import io.github.romanvht.byedpi.utility.getPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlinx.coroutines.withContext

enum class GenPhase { Idle, Checking, Searching, Verifying, Done }

/** Another command line that also worked, offered next to the winner. */
data class GenAlternative(
    val command: String,
    val opened: Int,
    val sites: Int,
    val latencyMs: Long?,
    /** Successful requests, 0–100; null in results saved by older versions. */
    val successRate: Int? = null
) {
    val score: CmdScore get() = CmdScore.of(opened, sites, successRate ?: if (opened > 0) opened * 100 / sites.coerceAtLeast(1) else 0, latencyMs)
}

/** What a finished search found. Counts are over the sites that were blocked without a bypass. */
data class GenResult(
    /** The winning command line; null when nothing worked or nothing was blocked. */
    val command: String?,
    val opened: Int,
    val blocked: Int,
    val latencyMs: Long?,
    /** All sites checked, blocked or not. */
    val totalSites: Int,
    val tested: Int,
    val alternatives: List<GenAlternative> = emptyList(),
    /** True when the winner is a command line the search built, not one of its probes. */
    val generated: Boolean = false,
    /** How the winner was made (an [Origin] name) and the lines it was built from. */
    val how: String? = null,
    val madeFrom: List<String>? = null,
    /** How many new command lines the search built and tested. */
    val built: Int = 0,
    /** The winner's successful requests, 0–100; null in results saved by older versions. */
    val successRate: Int? = null,
    /** How many lines tested earlier on this phone the search learned from. */
    val learnedFrom: Int = 0,
    /** The best of those earlier lines as it did in this search, to compare with the winner. */
    val knownBest: GenAlternative? = null,
    /** True when the winner is a line tested earlier (no new line beat it). */
    val knownWon: Boolean = false,
    /**
     * When set, [opened] counts all [sites] tested (the Benchmark's measure), not just the
     * blocked ones; [openedBlocked] is how many of the [blocked] sites it opened.
     */
    val sites: Int? = null,
    val openedBlocked: Int? = null
) {
    /** The winner's 0–100 score over the blocked sites, the same scale as the Benchmark's. */
    val score: CmdScore
        get() {
            val of = sites ?: blocked
            return CmdScore.of(opened, of, successRate ?: if (of > 0) opened * 100 / of else 0, latencyMs)
        }

    val nothingBlocked: Boolean get() = blocked == 0
}

data class GenState(
    val running: Boolean = false,
    val stopping: Boolean = false,
    val phase: GenPhase = GenPhase.Idle,
    val round: Int = 0,
    val rounds: Int = 0,
    /** Candidates finished so far and the most the plan will test (for the progress bar). */
    val done: Int = 0,
    val budget: Int = 0,
    val leader: Scored? = null,
    val result: GenResult? = null,
    val failed: Boolean = false,
    /** When this search started, for the time-left estimate; and which plan it ran. */
    val startedAt: Long = 0L,
    val plan: SearchPlan? = null
)

/**
 * Runs a [CommandSearch] through the regular [TestService]: each round is one test run with the
 * round's candidates as its command list, limited to the sites that were blocked without a bypass.
 * Lives outside any screen so the search survives leaving the Benchmark tab.
 */
object CommandGenerator {
    private const val TAG = "CommandGenerator"
    private const val PREF_RESULT = "bb_gen_result"
    private const val PREF_USER_COMMANDS = "byedpi_proxytest_usercommands"
    private const val PREF_TEST_COMMANDS = "byedpi_proxytest_commands"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gson = Gson()
    private val mutable = MutableStateFlow(GenState())
    val state: StateFlow<GenState> = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var stopRequested = false

    val isRunning: Boolean get() = job?.isActive == true

    /** Restores the last result after the app was closed. */
    fun load(ctx: Context) {
        if (isRunning || mutable.value.result != null) return
        val json = ctx.getPreferences().getString(PREF_RESULT, null) ?: return
        // Gson skips Kotlin's defaults and null checks, and results saved by 3.0.0 and older
        // used field names R8 had renamed: drop anything that didn't read back as a real result.
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
        val saved = runCatching { gson.fromJson(json, GenResult::class.java) }.getOrNull()
            ?.takeIf { it.tested > 0 && it.totalSites > 0 }
            ?.let { r -> r.copy(alternatives = (r.alternatives ?: emptyList()).filter { it != null && it.command != null }) }
        if (saved == null) {
            ctx.getPreferences().edit { remove(PREF_RESULT) }
            return
        }
        mutable.value = GenState(phase = GenPhase.Done, result = saved)
    }

    /** Forgets the last result, e.g. when a regular benchmark starts. */
    fun clear(ctx: Context) {
        if (isRunning) return
        ctx.getPreferences().edit { remove(PREF_RESULT) }
        mutable.value = GenState()
    }

    fun start(ctx: Context, plan: SearchPlan) {
        if (isRunning || TestService.isRunning) return
        val app = ctx.applicationContext
        stopRequested = false
        mutable.value = GenState(
            running = true, phase = GenPhase.Checking, rounds = plan.rounds, budget = plan.budget + 1,
            startedAt = System.currentTimeMillis(), plan = plan
        )
        job = scope.launch {
            try {
                TestService.holdBetweenRuns = true
                search(app, plan)
            } catch (e: CancellationException) {
                mutable.update { it.copy(phase = GenPhase.Idle, leader = null) }
            } catch (e: Exception) {
                Log.e(TAG, "Command line search failed", e)
                mutable.update { it.copy(phase = GenPhase.Idle, failed = true) }
            } finally {
                withContext(NonCancellable) {
                    TestService.release()
                    app.getPreferences().edit(commit = true) { remove(TestService.SITES_OVERRIDE_PREF) }
                    mutable.update { it.copy(running = false, stopping = false) }
                }
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        stopRequested = true
        mutable.update { it.copy(stopping = true) }
        TestService.stop()
    }

    private suspend fun search(app: Context, plan: SearchPlan) {
        // 1. Which sites are blocked at all?
        val baseline = runBatch(app, listOf(BASELINE_COMMAND), sites = null)
            .firstOrNull { it.command == BASELINE_COMMAND && it.isCompleted }
            ?: throw IllegalStateException("The no-bypass check did not run")
        val allSites = baseline.siteResults.size
        val blocked = blockedSites(baseline)
        if (blocked.isEmpty()) {
            finish(app, GenResult(null, 0, 0, null, allSites, 0))
            return
        }

        // 2. Rounds of candidates, each built from what worked so far.
        val search = CommandSearch(plan, Random(System.nanoTime()))
        val memory = withContext(Dispatchers.IO) { LineMemoryStore.load(app) }
        search.prime(memory, blocked, System.currentTimeMillis())
        // Fewer known lines than the most it could take: the progress bar's total shrinks to match.
        mutable.update { it.copy(budget = it.budget - (KNOWN_MAX - search.knownLines.size)) }
        var batch = search.firstRound()
        for (round in 1..plan.rounds) {
            if (batch.isEmpty()) break
            mutable.update { it.copy(phase = GenPhase.Searching, round = round) }
            search.recordAll(batch, runBatch(app, batch.map { it.command }, blocked))
            mutable.update { it.copy(leader = search.best()) }
            // Learn for at least two rounds of new lines before settling, even if a probe works.
            if (round >= 3 && search.solved()) break
            batch = search.nextRound()
        }

        // 3. Re-test the best few, and the best line known before, on every site: a lucky run
        //    can't win, a line that breaks a site that opened without a bypass is caught, and the
        //    score means the same as the Benchmark's (all sites, not just the blocked ones).
        val newLine = { o: Origin -> o != Origin.Probe && o != Origin.Known }
        val knownCommands = search.knownLines.map { it.command }.toSet()
        val ranking = search.ranking()
        val knownOnBlocked = ranking.firstOrNull { it.command in knownCommands }
        val finalists = (search.verificationRound() + listOfNotNull(knownOnBlocked?.recipe))
            .distinctBy { it.command }
        val byCommand = finalists.associateBy { it.command }
        var onAll: List<Scored> = emptyList()
        if (finalists.isNotEmpty()) {
            mutable.update { it.copy(phase = GenPhase.Verifying) }
            onAll = runBatch(app, finalists.map { it.command }, sites = null)
                .filter { it.isCompleted && it.command in byCommand }
                .map { r ->
                    val t = Trial.of(r)
                    val recipe = byCommand.getValue(r.command)
                    Scored(recipe, t.opened, t.sites, t.successRate, t.latencyMs, 1,
                        scoreOf(t.opened, t.sites, t.successRate, t.latencyMs, recipe.parts), t.openedSites)
                }
                .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.recipe.parts })
        }

        if (onAll.isNotEmpty()) {
            val working = onAll.filter { it.works }
            val best = working.firstOrNull()
            val knownBest = onAll.firstOrNull { it.command in knownCommands }
            finish(
                app,
                GenResult(
                    command = best?.command,
                    opened = best?.opened ?: 0,
                    blocked = blocked.size,
                    latencyMs = best?.latencyMs,
                    totalSites = allSites,
                    tested = search.tested,
                    alternatives = working.drop(1).take(2).map { GenAlternative(it.command, it.opened, it.sites, it.latencyMs, it.successRate) },
                    successRate = best?.successRate,
                    generated = best != null && newLine(best.recipe.origin),
                    how = best?.recipe?.origin?.name,
                    madeFrom = best?.recipe?.parents?.take(2),
                    built = ranking.count { newLine(it.recipe.origin) },
                    learnedFrom = search.learnedFrom,
                    knownBest = knownBest?.let { GenAlternative(it.command, it.opened, it.sites, it.latencyMs, it.successRate) },
                    knownWon = best != null && best.command in knownCommands,
                    sites = best?.sites ?: allSites,
                    openedBlocked = best?.openedSites?.count { it in blocked }
                )
            )
            return
        }

        // The all-sites run didn't finish: answer from the blocked-sites results.
        val working = ranking.filter { it.works }
        val best = working.firstOrNull()
        finish(
            app,
            GenResult(
                command = best?.command,
                opened = best?.opened ?: 0,
                blocked = blocked.size,
                latencyMs = best?.latencyMs,
                totalSites = allSites,
                tested = search.tested,
                alternatives = working.drop(1).take(2).map { GenAlternative(it.command, it.opened, it.sites, it.latencyMs, it.successRate) },
                successRate = best?.successRate,
                generated = best != null && newLine(best.recipe.origin),
                how = best?.recipe?.origin?.name,
                madeFrom = best?.recipe?.parents?.take(2),
                built = ranking.count { newLine(it.recipe.origin) },
                learnedFrom = search.learnedFrom,
                knownBest = knownOnBlocked?.let { GenAlternative(it.command, it.opened, it.sites, it.latencyMs, it.successRate) },
                knownWon = best != null && best.command in knownCommands
            )
        )
    }

    private fun finish(app: Context, result: GenResult) {
        app.getPreferences().edit { putString(PREF_RESULT, gson.toJson(result)) }
        mutable.update { it.copy(phase = GenPhase.Done, result = result, done = it.budget, leader = null) }
    }

    /** One test run of [commands] on [sites] (or the active link lists); returns its results. */
    private suspend fun runBatch(app: Context, commands: List<String>, sites: List<String>?): List<StrategyResult> {
        if (stopRequested) throw CancellationException("Search stopped")
        app.getPreferences().edit(commit = true) {
            putBoolean(PREF_USER_COMMANDS, true)
            putString(PREF_TEST_COMMANDS, commands.joinToString("\n"))
            if (sites == null) remove(TestService.SITES_OVERRIDE_PREF)
            else putString(TestService.SITES_OVERRIDE_PREF, sites.joinToString("\n"))
        }
        TestService.start(app)
        if (!TestService.isRunning) throw IllegalStateException("The test could not start")

        val before = mutable.value.done
        val progress = scope.launch {
            TestService.state.collect { st ->
                mutable.update { it.copy(done = (before + st.strategies.count { s -> s.isCompleted }).coerceAtMost(it.budget)) }
            }
        }
        try {
            TestService.awaitStopped()
        } finally {
            progress.cancel()
        }
        if (stopRequested) throw CancellationException("Search stopped")
        val final = TestService.state.value
        if (final.failed && final.strategies.none { it.isCompleted }) throw IllegalStateException("The test failed")
        return final.strategies
    }
}

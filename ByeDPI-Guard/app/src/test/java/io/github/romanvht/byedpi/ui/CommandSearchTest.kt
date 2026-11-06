package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.utility.shellSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CommandSearchTest {

    /** Every token is a ciadpi option the bundled engine accepts, with a value in its syntax. */
    private val validToken = Regex("""^-(s|d|o|q|r|f)-?\d+(\+[shn][em]?)?$|^-t\d+$|^-Q[ro]$|^-At,s$""")

    private fun assertValid(command: String) {
        val tokens = shellSplit(command)
        assertTrue(command, tokens.isNotEmpty())
        tokens.forEach { assertTrue("'$it' in '$command'", validToken.matches(it)) }
        // Each group: a fake always carries its TTL right after it, and has at most one fake.
        command.split(" -At,s ").forEach { group ->
            val t = shellSplit(group)
            assertTrue(command, t.count { it.startsWith("-f") } <= 1)
            val f = t.indexOfFirst { it.startsWith("-f") }
            if (f >= 0) assertTrue(command, t.getOrNull(f + 1)?.startsWith("-t") == true)
            assertTrue(command, t.count { it.startsWith("-") && !it.startsWith("-t") && !it.startsWith("-Q") && !it.startsWith("-r") } in 1..3)
        }
    }

    private fun result(command: String, opened: Int, sites: Int = 4, latency: Long? = 100, completed: Boolean = true) =
        StrategyResult(
            command = command,
            successCount = opened,
            totalRequests = sites,
            isCompleted = completed,
            siteResults = (0 until sites).map { i ->
                SiteResult("s$i.com", if (i < opened) 1 else 0, 1, if (i < opened) latency else null)
            }.toMutableList()
        )

    // ---- recipes ---------------------------------------------------------------------------

    @Test
    fun recipesRenderToCiadpiSyntaxInPacketOrder() {
        assertEquals("-s1", Recipe(Method.Split, Pos(1)).command)
        assertEquals("-d1+s", Recipe(Method.Disorder, Pos(1, "s")).command)
        assertEquals("-f-1 -t5", Recipe(Method.Fake, Pos(-1), ttl = 5).command)
        // Steps are written in the order they happen in the packet; the fake's TTL follows it.
        assertEquals(
            "-s1+s -f-1 -t8 -r1+s",
            Recipe(Method.Fake, Pos(-1), ttl = 8, second = Method.Split to Pos(1, "s"), tlsrec = Pos(1, "s")).command
        )
        val chained = withFallback(Recipe(Method.Disorder, Pos(1)), Recipe(Method.Fake, Pos(-1), ttl = 4).copy(fakeMod = FakeMod.Rand))
        assertEquals("-d1 -At,s -f-1 -t4 -Qr", chained.command)
        assertEquals(Origin.Fallback, chained.origin)
        assertEquals(listOf("-d1", "-f-1 -t4 -Qr"), chained.parents)
    }

    @Test
    fun tidyKeepsRecipesValid() {
        val messy = Recipe(
            steps = listOf(Step(Method.Fake, Pos(-1)), Step(Method.Fake, Pos(-2)), Step(Method.Split, Pos(1)), Step(Method.Split, Pos(1)), Step(Method.Oob, Pos(2)), Step(Method.Disorder, Pos(3))),
            fakeMod = FakeMod.Orig
        ).tidy()
        assertEquals(1, messy.steps.count { it.method == Method.Fake })
        assertEquals(3, messy.steps.size)
        assertEquals(8, messy.ttl)
        assertValid(messy.command)
        assertNull(Recipe(steps = listOf(Step(Method.Split, Pos(1))), ttl = 5, fakeMod = FakeMod.Rand).tidy().ttl)
    }

    @Test
    fun seedsAreValidDistinctAndNotGenerated() {
        SEEDS.forEach { assertValid(it.command); assertFalse(isGenerated(it.command)) }
        assertEquals(SEEDS.size, SEEDS.map { it.command }.toSet().size)
        assertTrue(isGenerated("-d3+s -f-1 -t4"))
    }

    @Test
    fun mutationsAndCombinationsAreAlwaysValidAndNew() {
        val rnd = Random(7)
        repeat(500) {
            val p = SEEDS.random(rnd)
            val m = mutate(p, rnd)
            assertValid(m.command)
            assertEquals(Origin.Tuned, m.origin)
            val c = combine(SEEDS.random(rnd), SEEDS.random(rnd))
            assertValid(c.command)
        }
        // Mutations reach offsets and TTLs that no fixed menu had.
        val seen = (1..400).map { mutate(Recipe(Method.Fake, Pos(-1), ttl = 8), rnd).command }.toSet()
        assertTrue(seen.size > 20)
        assertTrue(seen.any { Regex("-t(2|4|6|7|9|1[0-6])\\b").containsMatchIn(it) })
    }

    // ---- scoring ---------------------------------------------------------------------------

    @Test
    fun scorePrefersReachThenReliabilityThenSpeedThenSimplicity() {
        assertTrue(scoreOf(4, 4, 50, 2000, 3) > scoreOf(3, 4, 100, 10, 1))
        assertTrue(scoreOf(4, 4, 100, 900, 1) > scoreOf(4, 4, 80, 100, 1))
        assertTrue(scoreOf(4, 4, 100, 100, 1) > scoreOf(4, 4, 100, 900, 1))
        assertTrue(scoreOf(4, 4, 100, 100, 1) > scoreOf(4, 4, 100, 100, 3))
        assertEquals(0.0, scoreOf(0, 0, 0, null, 1), 0.0)
    }

    // ---- learning --------------------------------------------------------------------------

    @Test
    fun learningCreditsFeaturesPerSite() {
        val l = Learning()
        val disorder = Recipe(Method.Disorder, Pos(1, "s"))
        val split = Recipe(Method.Split, Pos(1))
        val sites = setOf("a.com", "b.com")
        repeat(3) {
            l.learn(disorder, Trial(1, 2, 50, 100, setOf("a.com"), sites))
            l.learn(split, Trial(0, 2, 0, null, emptySet(), sites))
        }
        assertTrue(l.chance("m:Disorder", "a.com") > 0.7)
        assertTrue(l.chance("m:Disorder", "b.com") < 0.3)
        assertTrue(l.chance("m:Split", "a.com") < 0.3)
        assertTrue(l.value("mp:Disorder@s", listOf("a.com"), null) > l.value("mp:Split@abs", listOf("a.com"), null))
        assertEquals(0.5, l.chance("m:Oob", "a.com"), 0.0) // untested: no opinion yet
    }

    // ---- the search ------------------------------------------------------------------------

    @Test
    fun firstRoundIsTheProbesAndResultsAreMatchedByCommand() {
        val search = CommandSearch(SearchPlan.Quick, Random(1))
        val round = search.firstRound()
        assertEquals(SEEDS, round)
        search.recordAll(round, listOf(result("-d1", 3), result("-s1", 1), result("-q1", 4, completed = false)))
        assertEquals(2, search.tested)
        assertEquals("-d1", search.best()?.command)
        assertTrue(search.firstRound().none { it.command == "-d1" || it.command == "-s1" })
    }

    @Test
    fun laterRoundsAreAllNewValidAndBuiltFromResults() {
        val search = CommandSearch(SearchPlan.Thorough, Random(3))
        val round = search.firstRound()
        search.recordAll(round, round.map { r ->
            when (r.command) {
                "-d1" -> result(r.command, 3)
                "-o1" -> result(r.command, 2)
                "-f-1 -t8" -> result(r.command, 1).apply {
                    // It opens the one site "-d1" misses.
                    siteResults.clear()
                    siteResults += (0 until 4).map { i -> SiteResult("s$i.com", if (i == 3) 1 else 0, 1, if (i == 3) 100L else null) }
                }
                else -> result(r.command, 0)
            }
        })
        val next = search.nextRound()
        assertEquals(SearchPlan.Thorough.perRound, next.size)
        assertTrue(next.none { n -> round.any { it.command == n.command } })
        assertTrue(next.all { isGenerated(it.command) })
        next.forEach { assertValid(it.command) }
        // With "-d1" missing a site, the search proposes a fallback and a combination.
        assertTrue(next.any { it.origin == Origin.Fallback && it.command.startsWith("-d1 -At,s") })
        assertTrue(next.any { it.origin == Origin.Combined })
    }

    @Test
    fun nextRoundStillProposesSomethingWhenNothingWorked() {
        val search = CommandSearch(SearchPlan.Quick, Random(5))
        val round = search.firstRound()
        search.recordAll(round, round.map { result(it.command, 0) })
        assertNull(search.best())
        val next = search.nextRound()
        assertTrue(next.isNotEmpty())
        next.forEach { assertValid(it.command) }
    }

    @Test
    fun verificationAveragesRunsSoAFlukeLoses() {
        val search = CommandSearch(SearchPlan.Quick)
        val lucky = Recipe(Method.Split, Pos(1))
        val steady = Recipe(Method.Disorder, Pos(1))
        search.record(lucky, Trial(4, 4, 100, 100))
        search.record(steady, Trial(3, 4, 75, 100))
        assertEquals(lucky.command, search.best()?.command)
        assertEquals(listOf(lucky, steady), search.verificationRound())
        search.record(lucky, Trial(0, 4, 0, null))
        search.record(steady, Trial(3, 4, 75, 100))
        assertEquals(steady.command, search.best()?.command)
        assertEquals(2, search.best()?.runs)
    }

    @Test
    fun solvedOnlyWhenEverySiteOpensQuickly() {
        val search = CommandSearch(SearchPlan.Quick)
        search.record(Recipe(Method.Split, Pos(1)), Trial(4, 4, 100, 450))
        assertFalse(search.solved())
        search.record(Recipe(Method.Disorder, Pos(1)), Trial(4, 4, 100, 90))
        assertTrue(search.solved())
    }

    /**
     * A made-up provider that no probe gets past completely:
     *  - a.com opens with a disorder anywhere in the site name;
     *  - b.com opens with a fake whose TTL is 3 or 4 (the probes use 5 and 8);
     *  - c.com opens only when the TLS record is split in the middle of the site name.
     * The search has to learn each of these and build one line that does all three.
     */
    private fun provider(command: String): Set<String> {
        val groups = command.split(" -At,s ").map { shellSplit(it) }
        fun opens(t: List<String>, site: String) = when (site) {
            "a.com" -> t.any { Regex("^-d-?\\d+\\+s").containsMatchIn(it) }
            "b.com" -> t.any { it.startsWith("-f") } && t.any { it == "-t3" || it == "-t4" }
            else -> t.any { it == "-r0+sm" }
        }
        return listOf("a.com", "b.com", "c.com").filter { s -> groups.any { opens(it, s) } }.toSet()
    }

    private fun run(search: CommandSearch, batch: List<Recipe>) {
        batch.forEach { r ->
            val opened = provider(r.command)
            search.record(r, Trial(opened.size, 3, opened.size * 100 / 3, if (opened.isEmpty()) null else 120, opened, setOf("a.com", "b.com", "c.com")))
        }
    }

    /** Runs a whole search against [provider] and returns its best line. */
    private fun searchOnce(plan: SearchPlan, seed: Int): Scored {
        val search = CommandSearch(plan, Random(seed))
        var batch = search.firstRound()
        for (round in 1..plan.rounds) {
            run(search, batch)
            batch = search.nextRound()
        }
        run(search, search.verificationRound())
        return search.best()!!
    }

    private fun solvedShare(plan: SearchPlan, tries: Int): Int = (1..tries).count { seed ->
        val best = searchOnce(plan, seed)
        assertValid(best.command)
        // Even when it doesn't get all three, it never does worse than the best probe.
        assertTrue(best.opened >= SEEDS.maxOf { provider(it.command).size })
        if (best.opened == 3) assertTrue(best.command, isGenerated(best.command))
        best.opened == 3
    }

    @Test
    fun theSearchLearnsAndBuildsALineNoProbeHad() {
        assertTrue(SEEDS.none { provider(it.command).size == 3 })
        val thorough = solvedShare(SearchPlan.Thorough, 20)
        assertTrue("thorough solved $thorough of 20", thorough >= 16)
        val quick = solvedShare(SearchPlan.Quick, 20)
        println("quick solved $quick of 20, thorough $thorough of 20")
        assertTrue("quick solved $quick of 20", quick >= 8)
    }

    @Test
    fun blockedSitesAreTheOnesTheBaselineCouldNotOpen() {
        assertEquals(listOf("s2.com", "s3.com"), blockedSites(result(BASELINE_COMMAND, 2)))
        assertTrue(blockedSites(result(BASELINE_COMMAND, 4)).isEmpty())
        assertTrue(blockedSites(null).isEmpty())
    }

    @Test
    fun baselineDesyncsNothing() {
        assertEquals(listOf("--comment=baseline"), shellSplit(BASELINE_COMMAND))
    }

    @Test
    fun planBudgets() {
        assertEquals(SEEDS.size + 2 * 8 + 3, SearchPlan.Quick.budget)
        assertEquals(SEEDS.size + 4 * 10 + 3, SearchPlan.Thorough.budget)
    }
}

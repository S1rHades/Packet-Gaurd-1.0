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
        assertEquals(KNOWN_MAX + SEEDS.size + 2 * 8 + 3, SearchPlan.Quick.budget)
        assertEquals(KNOWN_MAX + SEEDS.size + 4 * 10 + 3, SearchPlan.Thorough.budget)
    }

    // ---- learning from lines tested before ---------------------------------------------------

    private val community = listOf(
        "-f-200 -Qr -s3:5+sm -a1 -As -d1 -s4+sm -s8+s -f-300 -d6+s -a1 -At,r,s -o2 -f-30 -As -r5 -Mh -r6+s",
        "-n {sni} -Qr -f-204 -s1:5+sm -a1 -As -d1 -s3+s -s5+s -q7 -a1",
        "-o1 -d1 -a1 -At,r,s -s1 -d1 -s5+s -s10+s -s15+s -s20+s -r1+s -S -a1",
        "--split 2 --disorder=3+s --tlsrec 1+s --ttl 6 --fake -1"
    )

    @Test
    fun anyTestedLineIsReadIntoARecipeThatKeepsTheLineAsWritten() {
        community.forEach { c ->
            val r = parseRecipe(c)!!
            assertEquals(c, r.command)
            assertEquals(Origin.Known, r.origin)
            assertTrue(r.steps.size in 1..3)
            // Lines built from it are new, valid, and no longer the community line.
            val rnd = Random(c.hashCode())
            repeat(30) {
                val m = mutate(r, rnd)
                assertNull(m.raw)
                assertValid(m.command)
            }
            assertValid(combine(r, SEEDS[0]).command)
            assertValid(withFallback(r, SEEDS[7]).command)
        }
        val long = parseRecipe(community[3])!!
        assertEquals(setOf(Method.Split, Method.Disorder, Method.Fake), long.steps.map { it.method }.toSet())
        assertEquals(6, long.ttl)
        assertEquals(Pos(1, "s"), long.tlsrec)
        assertEquals(Pos(0, "sm"), readPos("3:5+sm"))
        assertEquals(Pos(-1, "se"), readPos("1+se"))
        assertNull(parseRecipe("-a1 -Mh"))
        assertNull(parseRecipe(BASELINE_COMMAND))
    }

    private fun memoryOf(vararg lines: String, now: Long = 0L): List<io.github.romanvht.byedpi.data.LineRecord> = lines.map { c ->
        val opened = provider(c)
        io.github.romanvht.byedpi.data.LineRecord(
            command = c,
            sites = listOf("a.com", "b.com", "c.com").associateWith { s -> intArrayOf(if (s in opened) 2 else 0, 2) }.toMutableMap(),
            latencySum = 240, latencyCount = 2, updatedAt = now
        )
    }

    @Test
    fun theBestKnownLinesJoinRoundOneAndTheirResultsTeach() {
        val search = CommandSearch(SearchPlan.Quick, Random(1))
        val memory = memoryOf("-d3+s -a1", "-f-1 -t4 -Qr -a1", "-o1 -a1", "-s1")
        search.prime(memory, listOf("a.com", "b.com", "c.com"), now = 0)
        assertEquals(4, search.learnedFrom)
        // "-o1 -a1" opened nothing and "-s1" is a probe anyway: neither is a known candidate.
        assertEquals(listOf("-d3+s -a1", "-f-1 -t4 -Qr -a1"), search.knownLines.map { it.command }.sorted().reversed().sortedBy { it.length })
        assertEquals(search.knownLines.map { it.command } + SEEDS.map { it.command }, search.firstRound().map { it.command })
        // The learning already knows disorders near the SNI open a.com.
        assertTrue(search.learning.chance("mp:Disorder@s", "a.com") > 0.7)
        assertTrue(search.learning.chance("m:Oob", "a.com") < 0.4)
        // Only this search's sites are targets.
        assertTrue(search.learning.sites.isEmpty())
    }

    @Test
    fun olderResultsCountLess() {
        val day = 86_400_000L
        val fresh = CommandSearch(SearchPlan.Quick, Random(1)).apply { prime(memoryOf("-d3+s", now = 60 * day), listOf("a.com"), now = 60 * day) }
        val old = CommandSearch(SearchPlan.Quick, Random(1)).apply { prime(memoryOf("-d3+s", now = 0), listOf("a.com"), now = 60 * day) }
        assertTrue(fresh.learning.chance("m:Disorder", "a.com") > old.learning.chance("m:Disorder", "a.com"))
    }

    @Test
    fun memoryKeepsRecentLinesAndRecentResults() {
        val r1 = result("-d1", 2)
        val merged = io.github.romanvht.byedpi.utility.LineMemoryStore.merge(emptyList(), listOf(r1, result(BASELINE_COMMAND, 1), result("-s1", 1, completed = false)), now = 5)
        assertEquals(listOf("-d1"), merged.map { it.command })
        assertEquals(listOf(1, 1), merged[0].sites.getValue("s0.com").toList())
        var m = merged
        repeat(20) { m = io.github.romanvht.byedpi.utility.LineMemoryStore.merge(m, listOf(result("-d1", 0)), now = 6) }
        val c = m[0].sites.getValue("s0.com")
        assertTrue(c[1] <= io.github.romanvht.byedpi.utility.LineMemoryStore.MAX_TRIES)
        assertTrue("recent failures dominate: ${c.toList()}", c[0] * 4 < c[1])
    }

    /** Like [searchOnce], but starting from what earlier tests on this phone showed. */
    private fun searchWithMemory(plan: SearchPlan, seed: Int, memory: List<io.github.romanvht.byedpi.data.LineRecord>): Scored {
        val search = CommandSearch(plan, Random(seed))
        search.prime(memory, listOf("a.com", "b.com", "c.com"), now = 0)
        var batch = search.firstRound()
        for (round in 1..plan.rounds) {
            run(search, batch)
            batch = search.nextRound()
        }
        run(search, search.verificationRound())
        return search.best()!!
    }

    @Test
    fun startingFromEarlierTestsFindsBetterLinesThanTheKnownOnes() {
        // Earlier Benchmark: community-style lines that each open some sites, none all three.
        val memory = memoryOf(
            "-d3+s -s5+s -a1 -As -o2",
            "-f-1 -t3 -Qr -s1:5+sm -a1",
            "-r0+sm -o1 -a1",
            "-s2 -d4 -a1",
            "-o1 -q2 -a1"
        )
        val knownBest = memory.maxOf { provider(it.command).size }
        assertTrue(knownBest < 3)
        val primed = (1..20).count { seed ->
            val best = searchWithMemory(SearchPlan.Quick, seed, memory)
            assertTrue(best.opened >= knownBest) // never worse than what was known
            best.opened == 3
        }
        val cold = (1..20).count { seed -> searchOnce(SearchPlan.Quick, seed).opened == 3 }
        println("quick: solved $primed of 20 with memory, $cold of 20 without")
        assertTrue("with memory $primed vs without $cold", primed >= cold && primed >= 14)
    }

    @Test
    fun aKnownLineThatAlreadyOpensEverythingIsNeverLost() {
        val full = "-d2+s -f-1 -t4 -r0+sm -a1 -As -o1"
        assertEquals(3, provider(full).size)
        (1..5).forEach { seed ->
            val best = searchWithMemory(SearchPlan.Quick, seed, memoryOf(full, "-o1 -a1"))
            assertEquals(3, best.opened)
        }
    }

    /**
     * Like the phone in the bug report: a community line opens most blocked sites, partly thanks
     * to an option the search can't rebuild (here `-Mh` for d.com), and misses c.com, which needs
     * a TLS record split in the middle of the site name.
     */
    private fun communityProvider(command: String): Set<String> {
        val groups = command.split(Regex(" -A[a-z,]+ ")).map { shellSplit(it) }
        val all = shellSplit(command)
        fun opens(t: List<String>, site: String) = when (site) {
            "a.com" -> t.any { Regex("^-d-?\\d+\\+s").containsMatchIn(it) }
            "b.com" -> t.any { it.startsWith("-f") } && t.any { it == "-t3" || it == "-t4" }
            "c.com" -> t.any { it == "-r0+sm" }
            else -> "-Mh" in all // d.com: only the community line's extra option opens it
        }
        return SITES4.filter { s -> groups.any { opens(it, s) } }.toSet()
    }

    private val SITES4 = listOf("a.com", "b.com", "c.com", "d.com")

    @Test
    fun aStrongCommunityLineIsKeptWholeAndExtendedForWhatItMisses() {
        val community = "-d3+s -s5+s -Mh -a1 -As -f-1 -t3 -Qr -a1"
        assertEquals(setOf("a.com", "b.com", "d.com"), communityProvider(community))
        val memory = listOf(community, "-r0+sm -o1 -a1", "-o1 -a1").map { c ->
            val opened = communityProvider(c)
            io.github.romanvht.byedpi.data.LineRecord(
                command = c,
                sites = SITES4.associateWith { s -> intArrayOf(if (s in opened) 2 else 0, 2) }.toMutableMap(),
                latencySum = 240, latencyCount = 2, updatedAt = 0
            )
        }
        val solved = (1..20).count { seed ->
            val search = CommandSearch(SearchPlan.Quick, Random(seed))
            search.prime(memory, SITES4, now = 0)
            var batch = search.firstRound()
            for (round in 1..SearchPlan.Quick.rounds) {
                batch.forEach { r ->
                    val o = communityProvider(r.command)
                    search.record(r, Trial(o.size, 4, o.size * 25, if (o.isEmpty()) null else 150, o, SITES4.toSet()))
                }
                batch = search.nextRound()
            }
            val best = search.best()!!
            // Never below the community line, and when it gets all four it's the community line
            // plus a group for c.com, not a simplified copy that loses d.com.
            assertTrue(best.command, best.opened >= 3)
            if (best.opened == 4) assertTrue(best.command, best.command.startsWith(community))
            best.opened == 4
        }
        println("community extended: solved $solved of 20")
        assertTrue("solved $solved of 20", solved >= 18)
    }
}

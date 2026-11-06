package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.utility.shellSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSearchTest {

    /** Every token is a known ciadpi short option with a value in its accepted syntax. */
    private val validToken = Regex("""^-(s|d|o|q|r)-?\d+(\+[shmne]+)?$|^-f-?\d+(\+[shmne]+)?$|^-t\d+$""")

    private fun assertValid(command: String) {
        val tokens = shellSplit(command)
        assertTrue(command, tokens.isNotEmpty())
        tokens.forEach { assertTrue("'$it' in '$command'", validToken.matches(it)) }
        // A fake always carries its TTL right after it.
        val f = tokens.indexOfFirst { it.startsWith("-f") }
        if (f >= 0) assertTrue(command, tokens.getOrNull(f + 1)?.startsWith("-t") == true)
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
    fun recipesRenderToCiadpiSyntax() {
        assertEquals("-s1", Recipe(Method.Split, Pos(1)).command)
        assertEquals("-d1+s", Recipe(Method.Disorder, Pos(1, "s")).command)
        assertEquals("-s0+sm", Recipe(Method.Split, Pos(0, "sm")).command)
        assertEquals("-f-1 -t5", Recipe(Method.Fake, Pos(-1), ttl = 5).command)
        assertEquals("-f-1 -t8", Recipe(Method.Fake, Pos(-1)).command)
        assertEquals(
            "-f-1 -t8 -s1+s -r1+s",
            Recipe(Method.Fake, Pos(-1), ttl = 8, second = Method.Split to Pos(1, "s"), tlsrec = Pos(1, "s")).command
        )
        assertEquals(3, Recipe(Method.Oob, Pos(1), second = Method.Split to Pos(1), tlsrec = Pos(1, "s")).parts)
    }

    @Test
    fun seedsAreValidAndDistinct() {
        SEEDS.forEach { assertValid(it.command) }
        assertEquals(SEEDS.size, SEEDS.map { it.command }.toSet().size)
    }

    @Test
    fun neighboursAreValidDistinctAndDifferentFromTheParent() {
        val parents = SEEDS + Recipe(Method.Oob, Pos(2, "s"), second = Method.Disorder to Pos(1), tlsrec = Pos(0, "sm"))
        for (p in parents) {
            val n = neighbours(p)
            assertTrue(p.command, n.isNotEmpty())
            assertEquals(n.size, n.map { it.command }.toSet().size)
            assertFalse(p.command, n.any { it.command == p.command })
            n.forEach { assertValid(it.command) }
            // A second step is never a fake, and only fakes have a TTL.
            n.forEach { assertTrue(it.second?.first != Method.Fake) }
            n.forEach { assertEquals(it.command, it.method == Method.Fake, it.ttl != null) }
        }
    }

    @Test
    fun neighboursOfAFakeVaryTheTtl() {
        val cmds = neighbours(Recipe(Method.Fake, Pos(-1), ttl = 8)).map { it.command }
        assertTrue(cmds.containsAll(listOf("-f-1 -t3", "-f-1 -t5", "-f-1 -t12")))
    }

    // ---- scoring ---------------------------------------------------------------------------

    @Test
    fun scorePrefersReachThenReliabilityThenSpeedThenSimplicity() {
        assertTrue(scoreOf(4, 4, 50, 2000, 3) > scoreOf(3, 4, 100, 10, 1))   // opening more sites wins
        assertTrue(scoreOf(4, 4, 100, 900, 1) > scoreOf(4, 4, 80, 100, 1))   // then success rate
        assertTrue(scoreOf(4, 4, 100, 100, 1) > scoreOf(4, 4, 100, 900, 1))  // then speed
        assertTrue(scoreOf(4, 4, 100, 100, 1) > scoreOf(4, 4, 100, 100, 3))  // then fewer techniques
        assertEquals(0.0, scoreOf(0, 0, 0, null, 1), 0.0)
    }

    // ---- the search ------------------------------------------------------------------------

    @Test
    fun firstRoundIsTheSeedsAndResultsAreMatchedByCommand() {
        val search = CommandSearch(SearchPlan.Quick)
        val round = search.firstRound()
        assertEquals(SEEDS, round)
        search.recordAll(round, listOf(result("-d1", 3), result("-s1", 1), result("-q1", 4, completed = false)))
        assertEquals(2, search.tested)                     // unfinished results are ignored
        assertEquals("-d1", search.best()?.command)
        assertTrue(search.firstRound().none { it.command == "-d1" || it.command == "-s1" })
    }

    @Test
    fun nextRoundVariesTheBestCandidatesAndSkipsTestedOnes() {
        val search = CommandSearch(SearchPlan.Quick)
        val round = search.firstRound()
        search.recordAll(round, round.map { r ->
            when (r.command) {
                "-d1" -> result(r.command, 3)
                "-o1" -> result(r.command, 2)
                "-f-1 -t8" -> result(r.command, 1)
                else -> result(r.command, 0)
            }
        })
        val next = search.nextRound()
        assertEquals(SearchPlan.Quick.perRound, next.size)
        assertTrue(next.none { n -> round.any { it.command == n.command } })
        // Candidates come from all three best parents, not just the top one.
        val fromParents = listOf("-d1", "-o1", "-f-1 -t8").map { p -> neighbours(round.first { it.command == p }).map { it.command } }
        fromParents.forEach { pool -> assertTrue(next.any { it.command in pool }) }
        next.forEach { assertValid(it.command) }
    }

    @Test
    fun nextRoundStillProposesSomethingWhenNothingWorked() {
        val search = CommandSearch(SearchPlan.Quick)
        val round = search.firstRound()
        search.recordAll(round, round.map { result(it.command, 0) })
        assertNull(search.best())
        assertTrue(search.nextRound().isNotEmpty())
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

        search.record(lucky, Trial(0, 4, 0, null))   // the re-test fails
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

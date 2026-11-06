package io.github.romanvht.byedpi.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchDecisionTest {

    private fun p(name: String, reach: Int, rate: Int, ms: Long?, sites: Int = 12, byList: Map<String, Int> = emptyMap()) =
        ProfileStat(name, "-$name", reach, sites, rate, ms, byList)

    private fun dash(vararg ps: ProfileStat, lists: List<ListStat> = emptyList()) = Dashboard(profiles = ps.toList(), lists = lists)

    // ---- the score ---------------------------------------------------------------------------

    @Test
    fun scoreWeighsReachThenSuccessThenSpeed() {
        val perfect = CmdScore.of(12, 12, 100, 80)
        assertEquals(CmdScore(100, 100, 100), perfect)
        assertEquals(100, perfect.overall)
        // 6 of 12 sites, 50% success, 800 ms (half way to "slow"): 0.5*50 + 0.3*50 + 0.2*50.
        assertEquals(50, CmdScore.of(6, 12, 50, 800).overall)
        // Opening more sites usually wins over being a little faster or more dependable...
        assertTrue(CmdScore.of(12, 12, 90, 300).overall > CmdScore.of(9, 12, 100, 60).overall)
        // ...but not when it often fails and is slow: the score weighs all three together.
        assertTrue(CmdScore.of(12, 12, 70, 900).overall < CmdScore.of(9, 12, 100, 60).overall)
        // Opens nothing: no credit for speed.
        assertEquals(0, CmdScore.of(0, 12, 0, 50).overall)
        assertEquals(0, CmdScore.speedOf(null))
        assertEquals(0, CmdScore.speedOf(5000))
        assertEquals(100, CmdScore.speedOf(40))
    }

    @Test
    fun reliabilityWords() {
        assertEquals(Reliability.Always, Reliability.of(100))
        assertEquals(Reliability.Mostly, Reliability.of(80))
        assertEquals(Reliability.Sometimes, Reliability.of(40))
    }

    // ---- the decision -----------------------------------------------------------------------

    @Test
    fun recommendsTheBestScoreAmongLinesNothingBeats() {
        val tls = p("tls", 12, 100, 96)
        val disorder = p("disorder", 9, 100, 74)
        val split = p("split", 8, 80, 300)   // beaten on every count by tls
        val dead = p("dead", 0, 0, null)
        val d = decide(dash(tls, disorder, split, dead), emptyList())
        assertEquals("tls", d.recommended?.name)
        assertEquals(Reason.OnlyAllSites, d.reason)
        assertEquals(listOf("tls", "disorder", "split"), d.ranked.map { it.name }) // nothing-opened lines are left out
        assertEquals(1, d.outclassed)
        assertTrue(beats(tls, split))
        assertFalse(beats(tls, disorder))
    }

    @Test
    fun goalsSayWhatYouGainAndGiveUp() {
        val tls = p("tls", 12, 100, 96)
        val disorder = p("disorder", 9, 100, 74)
        val steady = p("steady", 11, 100, 130)
        val d = decide(dash(tls, disorder, steady), emptyList())
        val fastest = d.goals.first { it.kind == GoalKind.Fastest }
        assertEquals("disorder", fastest.choice.name)
        assertFalse(fastest.same)
        assertEquals(listOf(Tradeoff.Sites(-3), Tradeoff.Latency(-22)), fastest.tradeoffs)
        assertTrue(fastest.tradeoffs[1].good)
        assertFalse(fastest.tradeoffs[0].good)
        // The recommendation is already the most dependable: it says so.
        val reliable = d.goals.first { it.kind == GoalKind.Reliable }
        assertTrue(reliable.same)
    }

    @Test
    fun siteGroupsPickTheirOwnBestAndCombineWhenThatOpensMore() {
        val yt = ListStat("youtube", "YouTube", 8, 8, 90, "tls", 8)
        val dc = ListStat("discord", "Discord", 4, 4, 120, "fake", 4)
        val tls = p("tls", 9, 100, 90, byList = mapOf("youtube" to 8, "discord" to 1))
        val fake = p("fake", 6, 90, 150, byList = mapOf("youtube" to 2, "discord" to 4))
        val lists = listOf(
            ListItemUi("youtube", "YouTube", (1..8).map { "y$it.com" }, true, true),
            ListItemUi("discord", "Discord", listOf("discord.com", "discord.gg", "d3.com", "d4.com"), true, true)
        )
        val d = decide(dash(tls, fake, lists = listOf(yt, dc)), lists)
        assertEquals("tls", d.recommended?.name)
        assertEquals(listOf(GroupRow("youtube", "YouTube", 8, 8), GroupRow("discord", "Discord", 1, 4)), d.groups)
        val ytGoal = d.goals.first { it.listId == "youtube" }
        assertTrue(ytGoal.same)
        val dcGoal = d.goals.first { it.listId == "discord" }
        assertEquals("fake", dcGoal.choice.name)
        assertEquals(4, dcGoal.listOpened)

        val c = assertNotNull(d.combined).let { d.combined!! }
        assertEquals("-H \":discord.com discord.gg d3.com d4.com\" -fake -An -tls", c.command)
        assertEquals(listOf<Pair<String?, String>>("Discord" to "fake", null to "tls"), c.parts)
        assertEquals(12, c.expectedOpened)
        // The combined line splits into the arguments the engine expects.
        assertEquals(
            listOf("-H", ":discord.com discord.gg d3.com d4.com", "-fake", "-An", "-tls"),
            io.github.romanvht.byedpi.utility.shellSplit(normalizeCommand(c.command))
        )
    }

    @Test
    fun noCombinationWhenALineAlreadyUsesGroups() {
        val dc = ListStat("discord", "Discord", 4, 4, 120, "fake", 4)
        val rec = ProfileStat("rec", "-o1 -At,s -d1", 9, 12, 100, 90, mapOf("discord" to 1))
        val fake = p("fake", 5, 90, 150, byList = mapOf("discord" to 4))
        val lists = listOf(ListItemUi("discord", "Discord", listOf("discord.com"), true, true))
        assertNull(decide(dash(rec, fake, lists = listOf(dc)), lists).combined)
    }

    @Test
    fun nothingWorkedGivesAnEmptyDecision() {
        val d = decide(dash(p("a", 0, 0, null), p("b", 0, 0, null)), emptyList())
        assertTrue(d.isEmpty)
        assertNull(d.recommended)
        assertNull(d.combined)
    }

    @Test
    fun reasons() {
        assertEquals(Reason.AllSitesFastest, decide(dash(p("a", 12, 100, 90), p("b", 12, 90, 200)), emptyList()).reason)
        assertEquals(Reason.MostSites, decide(dash(p("a", 10, 100, 90), p("b", 7, 100, 60)), emptyList()).reason)
    }
}

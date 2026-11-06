package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.StrategyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModelTest {

    private fun site(name: String, ok: Int, latency: Long? = null, total: Int = 1) = SiteResult(name, ok, total, latency)

    private fun strategy(command: String, vararg sites: SiteResult, completed: Boolean = true) = StrategyResult(
        command = command,
        successCount = sites.sumOf { it.successCount },
        totalRequests = sites.sumOf { it.totalCount },
        isCompleted = completed,
        siteResults = sites.toMutableList()
    )

    private fun list(id: String, vararg domains: String) = ListItemUi(id, id.uppercase(), domains.toList(), active = true, builtIn = false)

    private val video = list("video", "yt.com", "vimeo.com")
    private val chat = list("chat", "discord.gg", "t.me")
    private val unused = list("unused", "nothing.org")

    // fast opens 3/4 sites, slow opens all 4, broken opens nothing.
    private val fast = strategy("fast", site("yt.com", 1, 40), site("vimeo.com", 1, 60), site("discord.gg", 1, 20), site("t.me", 0))
    private val slow = strategy("slow", site("yt.com", 1, 300), site("vimeo.com", 1, 500), site("discord.gg", 1, 200), site("t.me", 1, 100))
    private val broken = strategy("broken", site("yt.com", 0), site("vimeo.com", 0), site("discord.gg", 0), site("t.me", 0))

    private val dash = buildDashboard(listOf(fast, slow, broken), listOf(video, chat, unused)) { "P-$it" }

    @Test
    fun emptyWhenNothingFinished() {
        val d = buildDashboard(listOf(strategy("running", site("a.b", 1, 5), completed = false)), listOf(video)) { it }
        assertTrue(d.isEmpty)
        assertNull(d.fastest)
    }

    @Test
    fun profileStatsFollowTestOrder() {
        assertEquals(listOf("fast", "slow", "broken"), dash.profiles.map { it.command })
        val f = dash.profiles[0]
        assertEquals("P-fast", f.name)
        assertEquals(3, f.reachable)
        assertEquals(4, f.sites)
        assertEquals(75, f.successRate)
        assertEquals(40L, f.avgLatencyMs)
        assertEquals(mapOf("video" to 2, "chat" to 1), f.reachByList)
        assertEquals(2, dash.working)
    }

    @Test
    fun headlineNumbers() {
        assertEquals("fast", dash.fastest?.command)
        assertEquals("slow", dash.widestReach?.command)
        assertEquals("slow", dash.mostReliable?.command)
        assertEquals(4, dash.sitesTotal)
        assertEquals(4, dash.sitesUnlocked)
        assertEquals(58, dash.overallSuccessRate) // 7 of 12 requests
        assertEquals((40L + 275L) / 2, dash.averageLatencyMs)
    }

    @Test
    fun listsOnlyIncludeTestedOnesAndUseTheFastestLatencyPerSite() {
        assertEquals(listOf("video", "chat"), dash.lists.map { it.id }.sorted().reversed())
        val v = dash.lists.first { it.id == "video" }
        assertEquals(2, v.sites)
        assertEquals(2, v.reachable)
        assertEquals(50L, v.avgLatencyMs) // fast: 40 and 60
        assertEquals("P-fast", v.bestProfile) // both open 2/2, fast wins on latency
        val c = dash.lists.first { it.id == "chat" }
        assertEquals(60L, c.avgLatencyMs) // discord 20 (fast), t.me 100 (slow)
        assertEquals("P-slow", c.bestProfile) // only slow opens both
        assertEquals(2, c.bestProfileReach)
    }

    @Test
    fun listOrderAndBadges() {
        // Both lists are fully open, so the faster one comes first.
        assertEquals(listOf("video", "chat"), dash.lists.map { it.id })
        assertEquals("video", dash.fastestList?.id)
        assertEquals("video", dash.mostOpenList?.id)

        val partial = buildDashboard(listOf(fast), listOf(video, chat)) { it }
        assertEquals(listOf("video", "chat"), partial.lists.map { it.id })
        assertEquals(50, partial.lists[1].reachPercent)
        assertEquals("chat", partial.fastestList?.id) // discord.gg at 20 ms
        assertEquals("video", partial.mostOpenList?.id)
    }

    @Test
    fun listWithNothingOpenHasNoBestProfile() {
        val d = buildDashboard(listOf(broken), listOf(video)) { it }
        val v = d.lists.single()
        assertEquals(0, v.reachable)
        assertNull(v.avgLatencyMs)
        assertNull(v.bestProfile)
        assertNull(d.fastestList)
        assertNull(d.mostOpenList)
    }

    @Test
    fun chartBarsAreBestFirstAndTallerIsBetter() {
        val latency = chartBars(dash.profiles, DashMetric.Latency)
        assertEquals(listOf("fast", "slow"), latency.map { it.profile.command })
        assertEquals(1f, latency[0].height, 0.001f)
        assertEquals(40f / 275f, latency[1].height, 0.001f)

        val reach = chartBars(dash.profiles, DashMetric.Reach)
        assertEquals(listOf("slow", "fast"), reach.map { it.profile.command })
        assertEquals(listOf(1f, 0.75f), reach.map { it.height })

        val success = chartBars(dash.profiles, DashMetric.Success)
        assertEquals(listOf("slow", "fast"), success.map { it.profile.command })
    }

    @Test
    fun chartBarsSkipBrokenProfilesAndRespectTheLimit() {
        val many = (1..12).map { strategy("p$it", site("a.b", 1, it * 10L)) }
        val d = buildDashboard(many + broken, emptyList()) { it }
        val bars = chartBars(d.profiles, DashMetric.Latency, limit = 8)
        assertEquals(8, bars.size)
        assertEquals("p1", bars.first().profile.command)
        assertTrue(bars.none { it.profile.command == "broken" })
        assertTrue(bars.all { it.height in 0f..1f })
    }

    @Test
    fun sparkSeriesPointsUpForBetterLatency() {
        assertEquals(listOf(-40f, -275f), sparkSeries(dash.profiles, DashMetric.Latency))
        assertEquals(listOf(3f, 4f, 0f), sparkSeries(dash.profiles, DashMetric.Reach))
        assertEquals(listOf(75f, 100f, 0f), sparkSeries(dash.profiles, DashMetric.Success))
    }
}

package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.StrategyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchModelTest {

    private fun site(name: String, ok: Int, total: Int = 1, latency: Long? = null, code: Int? = null) =
        SiteResult(name, ok, total, latency, code)

    private fun strategy(command: String, vararg sites: SiteResult, completed: Boolean = true) =
        StrategyResult(
            command = command,
            successCount = sites.sumOf { it.successCount },
            totalRequests = sites.sumOf { it.totalCount },
            isCompleted = completed,
            siteResults = sites.toMutableList()
        )

    private val names: (String) -> String = { "name:$it" }

    // ---- avgLatencyMs --------------------------------------------------------------------

    @Test
    fun avgLatency_ignoresSitesThatFailed() {
        val r = strategy("-s1", site("a", 1, latency = 100), site("b", 1, latency = 300), site("c", 0, latency = 5))
        assertEquals(200L, r.avgLatencyMs())
    }

    @Test
    fun avgLatency_isNullWhenNothingAnswered() {
        assertNull(strategy("-s1", site("a", 0), site("b", 0)).avgLatencyMs())
    }

    // ---- rankStrategies / winnerOf -------------------------------------------------------

    @Test
    fun rank_ordersBySuccessesThenLatency() {
        val slowButBest = strategy("best", site("a", 1, latency = 900), site("b", 1, latency = 900))
        val fast = strategy("fast", site("a", 1, latency = 50), site("b", 0))
        val faster = strategy("faster", site("a", 1, latency = 10), site("b", 0))

        val ranked = rankStrategies(listOf(fast, slowButBest, faster), names)

        assertEquals(listOf("best", "faster", "fast"), ranked.map { it.result.command })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals("name:best", ranked.first().name)
        assertEquals(900L, ranked.first().avgLatencyMs)
    }

    @Test
    fun rank_putsProfilesWithoutLatencyLastAmongEqualSuccesses() {
        val noLatency = strategy("none", site("a", 1, latency = null))
        val withLatency = strategy("some", site("a", 1, latency = 400))
        assertEquals(
            listOf("some", "none"),
            rankStrategies(listOf(noLatency, withLatency), names).map { it.result.command }
        )
    }

    @Test
    fun rank_skipsProfilesStillRunning() {
        val done = strategy("done", site("a", 1, latency = 10))
        val running = strategy("running", site("a", 1, latency = 1), completed = false)
        assertEquals(listOf("done"), rankStrategies(listOf(done, running), names).map { it.result.command })
    }

    @Test
    fun winner_isTopRankedProfileThatReachedSomething() {
        val ranked = rankStrategies(listOf(strategy("x", site("a", 0)), strategy("y", site("a", 1, latency = 5))), names)
        assertEquals("y", winnerOf(ranked)?.result?.command)
    }

    @Test
    fun winner_isNullWhenEveryProfileFailed() {
        val ranked = rankStrategies(listOf(strategy("x", site("a", 0)), strategy("y", site("a", 0))), names)
        assertNull(winnerOf(ranked))
        assertNull(winnerOf(emptyList()))
    }

    // ---- bestPerDomain -------------------------------------------------------------------

    @Test
    fun bestPerDomain_picksTheFastestProfileForEachSite() {
        val s1 = strategy("s1", site("a.com", 1, latency = 300, code = 200), site("b.com", 1, latency = 50, code = 301))
        val s2 = strategy("s2", site("a.com", 1, latency = 100, code = 204), site("b.com", 0))

        val rows = bestPerDomain(listOf(s1, s2), names).associateBy { it.site }

        assertEquals("s2", rows.getValue("a.com").command)
        assertEquals("name:s2", rows.getValue("a.com").strategyName)
        assertEquals(100L, rows.getValue("a.com").latencyMs)
        assertEquals(204, rows.getValue("a.com").httpCode)
        assertEquals("s1", rows.getValue("b.com").command)
        assertEquals(301, rows.getValue("b.com").httpCode)
    }

    @Test
    fun bestPerDomain_prefersMoreSuccessesOverSpeed() {
        val flaky = strategy("flaky", site("a.com", 1, total = 3, latency = 10))
        val steady = strategy("steady", site("a.com", 3, total = 3, latency = 500))
        assertEquals("steady", bestPerDomain(listOf(flaky, steady), names).single().command)
    }

    @Test
    fun bestPerDomain_listsReachableSitesFirstThenAlphabetically() {
        val s = strategy("s", site("zeta.com", 1, latency = 1), site("alpha.com", 0), site("beta.com", 1, latency = 1))

        val rows = bestPerDomain(listOf(s), names)

        assertEquals(listOf("beta.com", "zeta.com", "alpha.com"), rows.map { it.site })
        assertFalse(rows.last().ok)
        assertNull(rows.last().command)
        assertNull(rows.last().strategyName)
    }

    @Test
    fun bestPerDomain_ignoresProfilesStillRunning() {
        val running = strategy("running", site("a.com", 1, latency = 1), completed = false)
        assertTrue(bestPerDomain(listOf(running), names).isEmpty())
    }

    // ---- parseDomains --------------------------------------------------------------------

    @Test
    fun parseDomains_acceptsLinksAndMixedSeparators() {
        val raw = """
            https://News.Example.org/page?id=1
            http://example.com, youtube.com;  discord.gg
            example.com
        """.trimIndent()
        assertEquals(
            listOf("news.example.org", "example.com", "youtube.com", "discord.gg"),
            parseDomains(raw)
        )
    }

    @Test
    fun parseDomains_dropsWordsWithoutADot() {
        assertEquals(listOf("a.b"), parseDomains("localhost  a.b  https://"))
    }

    // ---- parseCustomProfiles -------------------------------------------------------------

    @Test
    fun parseCustomProfiles_readsNamedAndUnnamedLines() {
        val text = """
            My split | -s1+s -d3

            -o1 -r1+s
             | -f-1
            Broken |
        """.trimIndent()

        val parsed = parseCustomProfiles(text) { "Custom #$it" }

        assertEquals(
            listOf("My split" to "-s1+s -d3", "Custom #2" to "-o1 -r1+s", "Custom #3" to "-f-1"),
            parsed
        )
    }

    @Test
    fun parseCustomProfiles_defaultNameIsEnglish() {
        assertEquals(listOf("Custom 1" to "-s1"), parseCustomProfiles("-s1"))
    }

    @Test
    fun basicPresets_haveUniqueNamesAndCommands() {
        val presets = BenchPresets.basic
        assertEquals(presets.size, presets.map { it.first }.toSet().size)
        assertEquals(presets.size, presets.map { it.second }.toSet().size)
        assertTrue(presets.all { it.second.startsWith("-") })
    }
}

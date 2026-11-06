package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.utility.shellSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputCleanupTest {

    // ---- domains ----------------------------------------------------------------------------

    @Test
    fun domainsFromMessyPastes() {
        val raw = """
            https://www.YouTube.com/watch?v=dQw4#t=1
            discord.com, discord.gg ; x.com/
            "rutracker.org".  <https://user@news.example.org:8443/a>
            *.googlevideo.com
            //cdn.example.net/x
        """.trimIndent()
        assertEquals(
            listOf(
                "www.youtube.com", "discord.com", "discord.gg", "x.com", "rutracker.org",
                "news.example.org", "googlevideo.com", "cdn.example.net"
            ),
            parseDomains(raw)
        )
    }

    @Test
    fun wordsThatAreNotDomainsAreDropped() {
        assertNull(cleanDomain("hello"))
        assertNull(cleanDomain("https://"))
        assertNull(cleanDomain("a..b"))
        assertNull(cleanDomain("-bad.com"))
        assertNull(cleanDomain("..."))
        assertEquals("пример.рф", cleanDomain("https://Пример.РФ/"))
    }

    // ---- command lines ----------------------------------------------------------------------

    @Test
    fun commandCleanup() {
        assertEquals("-d1 -r1+s", normalizeCommand("  ciadpi  \"-d1\"   -r1+s \n"))
        assertEquals("-d1 -r1+s", normalizeCommand("./ciadpi -d1 -r1+s"))
        assertEquals("--disorder 1 -s1", normalizeCommand("—disorder 1 –s1"))
        assertEquals("--split 1 -o1", normalizeCommand("–split 1 −o1"))
        assertEquals("-n google.com -Qr", normalizeCommand("-n “google.com” -Qr"))
        assertEquals("-o1 -a1", normalizeCommand("-o1 -a1​"))
        assertEquals("", normalizeCommand("   "))
    }

    @Test
    fun cleanCommandsAreLeftAlone() {
        listOf("-o1 -a1 -r-5+se", "-Ku -a3 -An -Kt,h -d1 -s0+s", "--fake -1 --ttl 8").forEach {
            assertEquals(it, normalizeCommand(it))
        }
    }

    @Test
    fun cleanedCommandSplitsIntoTheSameArguments() {
        val raw = "ciadpi -f-1 -n 'a b' -t 8 \"\" -q"
        val clean = normalizeCommand(raw)
        assertEquals(shellSplit(raw).drop(1), shellSplit(clean))
        assertEquals(clean, normalizeCommand(clean))
    }

    // ---- time left --------------------------------------------------------------------------

    @Test
    fun minutesLeftEstimate() {
        assertNull(minutesLeft(5_000, 0.5f))     // too early
        assertNull(minutesLeft(60_000, 0.01f))   // too little done
        assertNull(minutesLeft(60_000, 1f))      // finished
        assertEquals(3, minutesLeft(60_000, 0.25f))
        assertEquals(1, minutesLeft(60_000, 0.9f))
        assertEquals(2, minutesLeft(90_000, 0.5f)) // 1.5 min left rounds up
    }
}

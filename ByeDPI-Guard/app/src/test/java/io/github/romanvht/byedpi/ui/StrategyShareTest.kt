package io.github.romanvht.byedpi.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyShareTest {
    @Test
    fun aLinkCarriesTheNameAndCommandLine() {
        val link = StrategyShare.link("Home Wi-Fi · fast", "-d1+s -f-1 -t4 -Qr -r1+s")
        assertTrue(link.startsWith("packetguard://add?v=1&"))
        assertEquals(StrategyShare.Shared("Home Wi-Fi · fast", "-d1+s -f-1 -t4 -Qr -r1+s"), StrategyShare.read(link))
        // Found inside a message, too.
        assertEquals("-d1+s -f-1 -t4 -Qr -r1+s", StrategyShare.read("Try this: $link thanks")?.command)
    }

    @Test
    fun communityStyleLinesAreShareable() {
        assertTrue(shareableCommand("-n {sni} -Qr -f-204 -s1:5+sm -a1 -As -d1 -s3+s -s5+s -q7 -a1"))
        assertTrue(shareableCommand("-o1 -d1 -a1 -At,r,s -s1 -d1 -s5+s -r1+s -S -a1"))
        assertTrue(shareableCommand("-H :youtube.com -d1 -An -s1"))
        assertTrue(shareableCommand("--split 2 --disorder=3+s --tlsrec 1+s --ttl 6 --fake -1"))
    }

    @Test
    fun linesThatChangeMoreThanTheBypassAreRefused() {
        assertFalse(shareableCommand("-i 0.0.0.0 -d1"))          // opens the proxy to the network
        assertFalse(shareableCommand("-d1 -p 9999"))              // moves the port
        assertFalse(shareableCommand("--ip=0.0.0.0 -s1"))
        assertFalse(shareableCommand("-d1 -D"))                   // daemon
        assertFalse(shareableCommand("-d1 -w /sdcard/x"))         // writes a file
        assertFalse(shareableCommand("-H /sdcard/hosts.txt -d1")) // reads a file
        assertFalse(shareableCommand("-l /data/fake.bin -f-1"))
        assertFalse(shareableCommand("-d1 --unknown"))
        assertFalse(shareableCommand("-a1 -Mh"))                  // no technique at all
        assertFalse(shareableCommand("-d1 stray"))
        assertNull(StrategyShare.read(StrategyShare.link("x", "-i 0.0.0.0 -d1")))
        assertNull(StrategyShare.read("https://example.com"))
    }
}

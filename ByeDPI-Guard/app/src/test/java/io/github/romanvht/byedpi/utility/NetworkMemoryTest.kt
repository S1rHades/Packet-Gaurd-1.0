package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMemoryTest {
    @Test
    fun aNetworkGetsTheSameAnonymousKeyEveryTime() {
        val home = NetworkMemory.keyOf("wifi", listOf("gw=192.168.1.1", "dns=192.168.1.1", "dom=lan"))
        // Order doesn't matter; the addresses themselves never appear in the key.
        assertEquals(home, NetworkMemory.keyOf("wifi", listOf("dom=lan", "dns=192.168.1.1", "gw=192.168.1.1")))
        assertTrue(home!!.startsWith("wifi:"))
        assertTrue("192.168" !in home)
        assertEquals(5 + 16, home.length)
    }

    @Test
    fun differentNetworksGetDifferentKeys() {
        val home = NetworkMemory.keyOf("wifi", listOf("gw=192.168.1.1", "dns=192.168.1.1"))
        val work = NetworkMemory.keyOf("wifi", listOf("gw=10.0.0.1", "dns=10.0.0.53"))
        val cell = NetworkMemory.keyOf("cell", listOf("25001"))
        assertNotEquals(home, work)
        assertNotEquals(home, cell)
        assertNull(NetworkMemory.keyOf("wifi", listOf(" ", "")))
    }
}

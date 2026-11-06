package io.github.romanvht.byedpi.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ListOrderTest {
    private fun l(id: String, builtIn: Boolean = true) = ListItemUi(id, id, emptyList(), false, builtIn)

    // Assets arrive alphabetically, which put YouTube last.
    private val assets = listOf("cloudflare", "discord", "general", "googlevideo", "news", "social", "mine", "türkiye", "youtube")
        .map { if (it == "mine") l(it, builtIn = false) else l(it) }

    @Test
    fun popularListsFirstOwnListsLast() {
        assertEquals(
            listOf("youtube", "googlevideo", "social", "discord", "news", "cloudflare", "general", "türkiye", "mine"),
            orderLists(assets, "en").map { it.id }
        )
    }

    @Test
    fun turkishPutsTurkiyeFirst() {
        assertEquals("türkiye", orderLists(assets, "tr").first().id)
        assertEquals("mine", orderLists(assets, "tr").last().id)
    }
}

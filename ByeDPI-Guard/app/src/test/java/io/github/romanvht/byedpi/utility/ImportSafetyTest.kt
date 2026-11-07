package io.github.romanvht.byedpi.utility

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.data.DomainList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A hand-edited or foreign backup must not store values the app would crash reading. */
class ImportSafetyTest {

    @Test
    fun numbersBecomeTextBecauseTheAppKeepsNumbersAsText() {
        assertEquals("1080", SettingsUtils.importValue("byedpi_proxy_port", 1080.0, null))
        assertEquals("1080", SettingsUtils.importValue("byedpi_proxy_port", 1080.0, "1080"))
    }

    @Test
    fun switchesStayTrueOrFalse() {
        assertEquals(true, SettingsUtils.importValue("byedpi_enable_cmd_settings", "true", null))
        assertEquals(false, SettingsUtils.importValue("autostart", false, null))
        assertNull(SettingsUtils.importValue("autostart", "yes", null))
        assertNull(SettingsUtils.importValue("autostart", 1.0, null))
        // An unknown key that is a switch on this phone keeps that type.
        assertEquals(true, SettingsUtils.importValue("some_new_switch", "true", false))
    }

    @Test
    fun choicesOutsideTheKnownValuesAreDropped() {
        assertEquals("proxy", SettingsUtils.importValue("byedpi_mode", "proxy", null))
        assertNull(SettingsUtils.importValue("byedpi_mode", "x", null))
        assertNull(SettingsUtils.importValue("byedpi_desync_method", "teleport", null))
        assertEquals("whitelist", SettingsUtils.importValue("byedpi_hosts_mode", "whitelist", null))
    }

    @Test
    fun setsOnlyWhereTheAppReadsSets() {
        assertEquals(setOf("a", "b"), SettingsUtils.importValue("bb_selected_profiles", listOf("a", "b"), null))
        assertNull(SettingsUtils.importValue("bb_selected_profiles", "a", null))
        assertNull(SettingsUtils.importValue("byedpi_cmd_args", listOf("a"), null))
    }

    @Test
    fun brokenSiteListsAreRepairedOrDropped() {
        // Gson fills in nulls despite Kotlin's non-null types.
        val json = """[{"id":"a"}, null, {"id":"b","name":"B","domains":["x.com"," ",null]}, {"name":"no id"}]"""
        val type = object : TypeToken<List<DomainList?>>() {}.type
        val fixed = DomainListUtils.sanitize(Gson().fromJson<List<DomainList?>>(json, type))
        assertEquals(listOf("a", "b"), fixed.map { it.id })
        assertEquals("a", fixed[0].name)
        assertEquals(emptyList<String>(), fixed[0].domains)
        assertEquals(listOf("x.com"), fixed[1].domains)
    }
}

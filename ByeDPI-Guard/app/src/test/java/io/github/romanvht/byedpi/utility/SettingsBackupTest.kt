package io.github.romanvht.byedpi.utility

import io.github.romanvht.byedpi.data.AppSettings
import io.github.romanvht.byedpi.data.Command
import io.github.romanvht.byedpi.data.DomainList
import io.github.romanvht.byedpi.utility.SettingsUtils.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Export → JSON → import must give back the same preference values and lists. */
class SettingsBackupTest {

    private val prefs: Map<String, Any?> = mapOf(
        "byedpi_mode" to "vpn",
        "byedpi_cmd_args" to "-o1 -a1 -r-5+se",
        "byedpi_enable_cmd_settings" to true,
        "ipv6_enable" to false,
        "byedpi_proxy_port" to "1080",
        "some_int" to 42,
        "negative_int" to -7,
        "big_long" to 5_000_000_000L,
        "benchmark_selected" to setOf("-s1", "-d1"),
    )

    private val backup = AppSettings(
        app = "com.packetguard.app",
        version = "2.0.0",
        history = listOf(Command("-s1", pinned = true, name = "Split"), Command("-d1")),
        apps = listOf("org.telegram.messenger", "com.discord"),
        domainLists = listOf(
            DomainList("youtube", "YouTube", listOf("youtube.com", "googlevideo.com"), isBuiltIn = true, isModified = true),
            DomainList("mine", "Mine", listOf("example.com"), isActive = false)
        ),
        settings = prefs
    )

    private fun roundTrip(settings: AppSettings) = SettingsUtils.parseJson(SettingsUtils.toJson(settings))!!

    @Test
    fun preferenceValuesSurviveTheRoundTrip() {
        val restored = roundTrip(backup).settings!!.mapValues { SettingsUtils.preferenceValue(it.value) }
        assertEquals(prefs, restored)
    }

    @Test
    fun wholeNumbersComeBackAsIntUnlessTheyNeedLong() {
        assertEquals(3, SettingsUtils.preferenceValue(3.0))
        assertEquals(-3, SettingsUtils.preferenceValue(-3.0))
        assertEquals(5_000_000_000L, SettingsUtils.preferenceValue(5_000_000_000.0))
        assertEquals(1.5f, SettingsUtils.preferenceValue(1.5))
    }

    @Test
    fun unstorableValuesAreSkipped() {
        assertNull(SettingsUtils.preferenceValue(null))
        assertNull(SettingsUtils.preferenceValue(listOf("a", 1.0)))
        assertNull(SettingsUtils.preferenceValue(mapOf("a" to "b")))
    }

    @Test
    fun listsAndHistorySurviveTheRoundTrip() {
        val restored = roundTrip(backup)
        assertEquals(backup.app, restored.app)
        assertEquals(backup.version, restored.version)
        assertEquals(backup.history, restored.history)
        assertEquals(backup.apps, restored.apps)
        assertEquals(backup.domainLists, restored.domainLists)
    }

    @Test
    fun onlyExportedSectionsAreOfferedOnImport() {
        assertEquals(Section.entries.toSet(), SettingsUtils.getAvailableSections(roundTrip(backup)))

        val partial = backup.copy(history = null, apps = null, domainLists = null)
        assertEquals(setOf(Section.SETTINGS), SettingsUtils.getAvailableSections(roundTrip(partial)))
    }

    @Test
    fun readsABackupWrittenByAnOlderBuild() {
        // A backup with only some sections, as upstream's exporter writes it.
        val json = """{"app":"com.packetguard.app","version":"1.0","settings":{"byedpi_mode":"proxy","autostart":true}}"""
        val parsed = SettingsUtils.parseJson(json)!!
        assertEquals(setOf(Section.SETTINGS), SettingsUtils.getAvailableSections(parsed))
        assertEquals("proxy", SettingsUtils.preferenceValue(parsed.settings!!["byedpi_mode"]))
        assertEquals(true, SettingsUtils.preferenceValue(parsed.settings["autostart"]))
    }
}

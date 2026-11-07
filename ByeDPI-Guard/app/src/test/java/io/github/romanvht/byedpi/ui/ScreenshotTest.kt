package io.github.romanvht.byedpi.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.romanvht.byedpi.data.TestState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Screenshots of the main screens in English and Russian.
 * The goldens live in app/src/test/snapshots; CI's verifyPaparazziDebug fails when a screen
 * changes. After an intended change, run ./gradlew recordPaparazziDebug and commit the images.
 */
@RunWith(Parameterized::class)
class ScreenshotTest(private val locale: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = listOf("en", "ru").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(locale = locale),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.5
    )

    private val lists = listOf(
        ListItemUi("youtube", "YouTube", (1..8).map { "y$it.com" }, true, true),
        ListItemUi("discord", "Discord", listOf("discord.com", "discord.gg", "d3.com", "d4.com"), true, true),
        ListItemUi("social", "Social", listOf("a.com", "b.com"), false, true)
    )
    private val decision = decide(
        Dashboard(
            profiles = listOf(
                ProfileStat("TLS record split (SNI)", "-s1 -r1+s", 9, 12, 100, 96, mapOf("youtube" to 8, "discord" to 1)),
                ProfileStat("Fake + TLS record + SNI split", "-f-1 -t8 -r1+s", 6, 12, 90, 150, mapOf("youtube" to 2, "discord" to 4)),
                ProfileStat("Disorder @1", "-d1", 7, 12, 100, 62, mapOf("youtube" to 6, "discord" to 1))
            ),
            lists = listOf(ListStat("youtube", "YouTube", 8, 8, 90, "tls", 8), ListStat("discord", "Discord", 4, 4, 120, "fake", 4))
        ),
        lists
    )
    private val ui = BenchUi(
        lists = lists,
        profiles = BenchPresets.basic.map { ProfileItem(it.second, it.first, ProfilePack.Basic) },
        selected = BenchPresets.basic.map { it.second }.toSet()
    )
    private val noResults = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
        }
    }

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true, LocalActivityResultRegistryOwner provides noResults) {
            PacketGuardTheme { content() }
        }
    }

    @Test
    fun connectProtected() = shot {
        GlassBackground {
            ConnectScreen(
                ConnectionState.Connected(io.github.romanvht.byedpi.data.Mode.VPN), LiveMetrics(96, 100),
                ActiveProfile("TLS record split (SNI)", "-s1 -r1+s", "VPN"), onToggle = {}
            )
        }
    }

    @Test
    fun connectOff() = shot {
        GlassBackground {
            ConnectScreen(ConnectionState.Disconnected, LiveMetrics(), ActiveProfile("TLS record split (SNI)", "-s1 -r1+s", "VPN"), onToggle = {})
        }
    }

    @Test
    fun benchmarkRecommended() = shot {
        GlassBackground { Column(Modifier.padding(16.dp)) { RecommendedCard(decision, false, null, {}, {}, {}) } }
    }

    @Test
    fun benchmarkTest() = shot {
        GlassBackground {
            Column(Modifier.padding(16.dp)) {
                TestCard(
                    ui, TestState(), GenState(), SearchPlan.Quick, generate = false, anyDone = true, bypassActive = false,
                    sni = "google.com", customName = "Custom", linesOpen = false, onToggleLines = {}, onToggleList = {},
                    onSelectPack = {}, onSelectAll = {}, onClear = {}, onToggleProfile = {}, onTest = {}, onGenerate = {},
                    onStop = {}, onPlan = {}
                )
            }
        }
    }

    @Test
    fun firstRunWelcome() = shot { FirstRunGuide(onDone = {}, onFind = {}) }

    @Test
    fun firstRunFind() = shot { FirstRunGuide(onDone = {}, onFind = {}, startPage = 2) }
}

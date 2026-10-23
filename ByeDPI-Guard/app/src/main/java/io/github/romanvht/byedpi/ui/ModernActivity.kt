package io.github.romanvht.byedpi.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

/** The single Packet Guard screen host: Connect, Benchmark and Settings tabs in Compose. */
class ModernActivity : ComponentActivity() {

    companion object {
        /** Optional extra: "connect", "benchmark" or "settings" opens that tab. */
        const val EXTRA_OPEN_TAB = "open_tab"
    }

    private val requestedTab = mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PacketGuardTheme {
                AppRoot(
                    requestedTab = requestedTab.value,
                    onTabRequestHandled = { requestedTab.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        requestedTab.value = when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            "connect" -> Tab.Connect
            "benchmark" -> Tab.Benchmark
            "settings" -> Tab.Settings
            else -> null
        }
    }
}

package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.update.AppUpdates
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

    override fun onResume() {
        super.onResume()
        AppUpdates.onAppResumed(this)
    }

    override fun onPause() {
        AppUpdates.inForeground = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // A shared setting (packetguard://add?…) from a QR code or a link: ask before adding it.
        intent?.data?.takeIf { it.scheme == StrategyShare.SCHEME }?.let { uri ->
            if (!StrategyInbox.offer(uri.toString())) {
                android.widget.Toast.makeText(this, R.string.pg_import_invalid, android.widget.Toast.LENGTH_LONG).show()
            }
            intent.data = null
        }
        requestedTab.value = when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            "connect" -> Tab.Connect
            "benchmark" -> Tab.Benchmark
            "settings" -> Tab.Settings
            else -> null
        }
    }
}

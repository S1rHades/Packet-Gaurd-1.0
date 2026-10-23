package io.github.romanvht.byedpi.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.FAILED_BROADCAST
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.STARTED_BROADCAST
import io.github.romanvht.byedpi.data.STOPPED_BROADCAST
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.DomainListUtils
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.SiteCheckUtils
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getCmdEnable
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getProxyIpAndPort
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val mode: Mode) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class LiveMetrics(val latencyMs: Long? = null, val successRate: Int? = null)

data class ActiveProfile(val name: String, val args: String, val modeLabel: String)

/**
 * Front-end state for the Connect tab. All real work is done by upstream's ServiceManager;
 * this class only translates its status into UI state and runs a light health probe.
 */
class ConnectViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx: Context = app

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(LiveMetrics())
    val metrics: StateFlow<LiveMetrics> = _metrics.asStateFlow()

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<ActiveProfile> = _profile.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var connectJob: Job? = null
    private var healthJob: Job? = null
    private val samples = ArrayDeque<Long?>() // last probes; null = failed

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                STOPPED_BROADCAST -> if (connectJob?.isActive != true) {
                    stopHealth()
                    _state.value = ConnectionState.Disconnected
                }

                FAILED_BROADCAST -> {
                    stopHealth()
                    _state.value = ConnectionState.Failed("The bypass service stopped unexpectedly")
                }

                else -> syncFromService()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(receiver, filter)
        }
        ServiceManager.refresh(appCtx)
        syncFromService()
    }

    /** VPN permission must already be granted (the UI handles that step). */
    fun connect() {
        if (TestService.isRunning) {
            say("A benchmark is running. Stop it first.")
            return
        }
        if (connectJob?.isActive == true) return
        val mode = appCtx.getPreferences().mode()
        _state.value = ConnectionState.Connecting
        connectJob = viewModelScope.launch {
            val ok = try {
                ServiceManager.waitStart(appCtx, mode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            connectJob = null
            if (ok && appStatus.first == AppStatus.Running) {
                _state.value = ConnectionState.Connected(mode)
                startHealth()
            } else {
                _state.value = ConnectionState.Failed("Could not start the bypass engine")
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        stopHealth()
        ServiceManager.stop()
        _state.value = ConnectionState.Disconnected
    }

    fun refreshProfile() {
        _profile.value = readProfile()
    }

    fun syncFromService() {
        if (connectJob?.isActive == true) return
        val (status, mode) = appStatus
        if (status == AppStatus.Running) {
            _state.value = ConnectionState.Connected(mode)
            startHealth()
        } else {
            stopHealth()
            if (_state.value !is ConnectionState.Failed) _state.value = ConnectionState.Disconnected
        }
    }

    private fun readProfile(): ActiveProfile {
        val prefs = appCtx.getPreferences()
        val modeLabel = if (prefs.mode() == Mode.VPN) "System-wide (VPN)" else "Local proxy"
        if (!prefs.getCmdEnable()) {
            return ActiveProfile("Custom UI settings", "Configured in full settings", modeLabel)
        }
        val args = prefs.getCmdArgs()
        val name = HistoryUtils(appCtx).getHistory()
            .firstOrNull { it.text == args }?.name?.takeIf { it.isNotBlank() }
        return ActiveProfile(name ?: "Custom strategy", args, modeLabel)
    }

    // ---- live latency / success rate -----------------------------------------------------

    private fun startHealth() {
        if (healthJob?.isActive == true) return
        samples.clear()
        _metrics.value = LiveMetrics()
        healthJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { DomainListUtils.syncLists(appCtx) }
            delay(1_500) // let the proxy settle
            while (isActive) {
                probeOnce()
                delay(20_000)
            }
        }
    }

    private fun stopHealth() {
        healthJob?.cancel()
        healthJob = null
        samples.clear()
        _metrics.value = LiveMetrics()
    }

    private suspend fun probeOnce() {
        val sites = withContext(Dispatchers.IO) { DomainListUtils.getActiveDomains(appCtx) }
            .take(3).ifEmpty { listOf("google.com") }
        val (ipRaw, portRaw) = appCtx.getPreferences().getProxyIpAndPort()
        val host = when (ipRaw) {
            "0.0.0.0" -> "127.0.0.1"
            "::", "[::]" -> "::1"
            else -> ipRaw
        }
        val port = portRaw.toIntOrNull() ?: 1080

        val results = mutableListOf<Long?>()
        try {
            SiteCheckUtils(host, port).checkSitesAsync(
                sites = sites,
                requestsCount = 1,
                requestTimeout = 4,
                concurrentRequests = sites.size,
                fullLog = true,
                onSiteProbed = { p ->
                    synchronized(results) {
                        results.add(if (p.successCount > 0) p.avgLatencyMs ?: 0L else null)
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            repeat(sites.size) { results.add(null) }
        }

        synchronized(samples) {
            results.forEach { samples.addLast(it) }
            while (samples.size > 12) samples.removeFirst()
            val ok = samples.filterNotNull()
            _metrics.value = LiveMetrics(
                latencyMs = if (ok.isEmpty()) null else ok.sum() / ok.size,
                successRate = if (samples.isEmpty()) null else ok.size * 100 / samples.size
            )
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    override fun onCleared() {
        runCatching { appCtx.unregisterReceiver(receiver) }
        super.onCleared()
    }
}

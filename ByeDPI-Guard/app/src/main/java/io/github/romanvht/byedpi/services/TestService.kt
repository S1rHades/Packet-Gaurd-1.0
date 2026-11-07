package io.github.romanvht.byedpi.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.AtomicFile
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.ui.CommandGenerator
import io.github.romanvht.byedpi.ui.GenPhase
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.utility.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class TestService : Service() {

    companion object {
        private const val TAG = "TestService"
        private const val CHANNEL = "Proxy test"
        private const val RESULTS_FILE = "proxy_test_results.json"
        private const val NOTIFICATION_ID = 4
        /** Its own request code, so this tap intent (which opens Benchmark) isn't shared with the VPN notification's. */
        private const val OPEN_BENCHMARK_REQUEST = 4

        /** When set (one domain per line), a run tests these sites instead of the active link lists. */
        const val SITES_OVERRIDE_PREF = "bb_test_sites_override"
        private val mutableState = MutableStateFlow(TestState())
        private val resultsLock = Any()
        val state: StateFlow<TestState> = mutableState.asStateFlow()
        val isRunning: Boolean
            get() = state.value.isRunning

        /**
         * Set by the command line generator for a whole search: between its runs the service stays
         * in the foreground ([held]) and the next run starts inside it, instead of stopping and
         * being started again. Android 12+ refuses to start a foreground service while the app is
         * in the background, so a restart between rounds could end a long search early.
         */
        @Volatile var holdBetweenRuns = false

        /** The service kept in the foreground between runs; only touched on the main thread. */
        private var held: TestService? = null

        /** Starts a run. Call on the main thread. */
        fun start(context: Context) {
            if (isRunning) return
            ServiceManager.refresh(context)
            val previous = state.value
            mutableState.value = previous.copy(
                runId = previous.runId + 1,
                isRunning = true,
                isStopping = false,
                currentStrategy = 0,
                failed = false,
                wasInterrupted = false,
            )
            held?.takeIf { !it.destroyed && it.testJob == null }?.let { service ->
                service.runHeld()
                return
            }
            try {
                ContextCompat.startForegroundService(context, Intent(context, TestService::class.java).setAction(START_ACTION))
            } catch (e: Exception) {
                mutableState.value = previous.copy(failed = true)
                Log.e(TAG, "Failed to start proxy tests", e)
            }
        }

        fun stop() {
            if (!isRunning) return
            mutableState.value = state.value.copy(isStopping = true)
        }

        /** Ends [holdBetweenRuns]: a service kept between runs leaves the foreground and stops. */
        fun release() {
            holdBetweenRuns = false
            held?.let { service ->
                held = null
                if (service.testJob == null && !service.destroyed) service.finish()
            }
        }

        suspend fun awaitStopped() {
            state.first { !it.isRunning }
        }

        suspend fun loadResults(context: Context) {
            val previous = state.value
            if (previous.isLoaded || previous.isRunning) return
            val app = context.applicationContext
            val loaded = withContext(Dispatchers.IO) {
                val results = try {
                    synchronized(resultsLock) {
                        AtomicFile(File(app.filesDir, RESULTS_FILE)).openRead().bufferedReader().use { reader ->
                            val type = object : TypeToken<List<StrategyResult>>() {}.type
                            Gson().fromJson<List<StrategyResult>>(reader, type) ?: emptyList()
                        }
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                previous.copy(
                    isLoaded = true,
                    strategies = results,
                    wasInterrupted = app.getPreferences().getBoolean("is_test_running", false),
                )
            }
            withContext(Dispatchers.Main.immediate) {
                if (mutableState.compareAndSet(previous, loaded)) {
                    app.getPreferences().edit { putBoolean("is_test_running", false) }
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gson = Gson()
    private val engineMutex = Mutex()
    private var engine: NativeEngine? = null
    private var testJob: Job? = null
    private var lastStartId = 0
    private var destroyed = false
    private var notifiedStrategy = -1
    private var notifiedCompleted = -1

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(this, CHANNEL, R.string.title_test)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Android 15 limits dataSync services to 6 hours a day and crashes the app unless the
    // service stops within seconds of this call. A test never runs that long, but stop anyway.
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service time limit reached; stopping the test")
        if (CommandGenerator.isRunning) CommandGenerator.stop()
        if (held === this) held = null
        stop()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            START_ACTION -> {
                try {
                    startForeground()
                    if (!isRunning) {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        stopSelfResult(startId)
                    } else if (testJob == null) {
                        startTesting()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to show proxy test notification", e)
                    if (testJob == null) {
                        mutableState.value = state.value.copy(isRunning = false, isStopping = false, failed = true)
                        stopSelfResult(startId)
                    } else {
                        stop()
                    }
                }
            }
            STOP_ACTION -> {
                // From the notification: a search is stopped as a whole, not just its current run.
                if (CommandGenerator.isRunning) CommandGenerator.stop()
                stop()
                if (testJob == null) {
                    if (held === this) held = null
                    stopSelfResult(startId)
                }
            }
            else -> if (testJob == null) stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        if (held === this) held = null
        if (testJob != null) stop()
        scope.cancel()
        super.onDestroy()
    }

    /** The next run of a search, in this service that stayed in the foreground. */
    private fun runHeld() {
        notifiedStrategy = -1
        notifiedCompleted = -1
        runCatching { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification()) }
        startTesting()
    }

    /** Leaves the foreground and stops. */
    private fun finish() {
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    private fun startTesting() {
        val previous = state.value.copy(isRunning = false, isStopping = false)
        testJob = scope.launch(start = CoroutineStart.LAZY) {
            val testingJob = coroutineContext.job
            var settings: TestSettings? = null
            var currentStrategy: StrategyResult? = null
            var prepared = false
            var failed = false
            val strategies = mutableListOf<StrategyResult>()
            val stopWatcher = launch {
                try {
                    state.first { it.isStopping }
                    testingJob.cancel()
                } finally {
                    stopEngine()
                }
            }

            try {
                if (state.value.isStopping) {
                    throw CancellationException("Proxy test stopped")
                }
                val options = withContext(Dispatchers.IO) { loadSettings() }
                settings = options
                if (options.sites.isEmpty()) {
                    Toast.makeText(this@TestService, R.string.test_settings_domain_empty, Toast.LENGTH_LONG).show()
                    return@launch
                }
                strategies.addAll(options.commands.map { StrategyResult(command = it) })
                prepared = true
                getPreferences().edit(commit = true) { putBoolean("is_test_running", true) }
                publish(strategies, 0)
                ServiceManager.waitStop()
                val configurations = withContext(Dispatchers.IO) {
                    options.commands.map { command ->
                        runCatching { testConfiguration(this@TestService, command, options.host, options.port) }
                    }
                }

                for ((index, strategy) in strategies.withIndex()) {
                    ensureActive()
                    currentStrategy = strategy
                    strategy.totalRequests = options.sites.size * options.requestsCount
                    publish(strategies, index + 1)
                    val configuration = configurations[index].getOrNull()
                    if (configuration == null || !checkStrategy(strategy, strategies, configuration, options)) {
                        resetStrategyResult(strategy, options)
                    }
                    strategy.isCompleted = true
                    publish(strategies)
                    saveResults(strategies)
                    stopEngine()
                    currentStrategy = null
                    delay(options.delaySec * 500L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run proxy tests", e)
                failed = true
            } finally {
                stopWatcher.cancel()
                withContext(NonCancellable) {
                    mutableState.value = state.value.copy(isStopping = true)
                    settings?.let { options ->
                        currentStrategy?.takeIf { !it.isCompleted }?.let {
                            resetStrategyResult(it, options)
                            it.isCompleted = true
                        }
                    }
                    stopEngine()
                    if (prepared) {
                        // Remembered for the command line generator, which learns from every line tested.
                        withContext(Dispatchers.IO) { runCatching { LineMemoryStore.record(this@TestService, strategies) } }
                        try {
                            saveResults(strategies)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save proxy test results", e)
                            failed = true
                        }
                        getPreferences().edit(commit = true) { putBoolean("is_test_running", false) }
                    }
                    testJob = null
                    if (holdBetweenRuns && !destroyed) {
                        // A search's next run follows: stay in the foreground for it.
                        held = this@TestService
                    } else {
                        if (held === this@TestService) held = null
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        stopSelfResult(lastStartId)
                    }
                    mutableState.value = if (prepared) TestState(
                        runId = previous.runId,
                        isLoaded = true,
                        strategies = snapshot(strategies),
                        failed = failed,
                    ) else previous.copy(failed = failed)
                }
            }
        }
        testJob?.start()
    }

    private suspend fun checkStrategy(
        strategy: StrategyResult,
        strategies: List<StrategyResult>,
        configuration: Configuration,
        settings: TestSettings,
    ): Boolean {
        return try {
            val current = startEngine(configuration)
            supervisorScope {
                val engineExit = async { current.awaitExit() }
                val check = async {
                    delay(settings.delaySec * 500L)
                    val host = when (configuration.host) {
                        "0.0.0.0" -> "127.0.0.1"
                        "::", "[::]" -> "::1"
                        else -> configuration.host
                    }
                    SiteCheckUtils(host, configuration.port).checkSitesAsync(
                        sites = settings.sites,
                        requestsCount = settings.requestsCount,
                        requestTimeout = settings.requestTimeout,
                        concurrentRequests = settings.requestLimit,
                        fullLog = true,
                        onSiteProbed = { probe ->
                            withContext(Dispatchers.Main.immediate) {
                                strategy.currentProgress += probe.totalCount
                                strategy.successCount += probe.successCount
                                strategy.siteResults.add(
                                    SiteResult(
                                        probe.site, probe.successCount, probe.totalCount,
                                        probe.avgLatencyMs, probe.httpCode
                                    )
                                )
                                publish(strategies)
                            }
                        }
                    )
                    true
                }
                try {
                    select {
                        engineExit.onAwait { false }
                        check.onAwait { it }
                    }
                } finally {
                    engineExit.cancel()
                    check.cancel()
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (state.value.isStopping) throw CancellationException("Proxy test stopped")
            Log.e(TAG, "Proxy test engine failed", e)
            Toast.makeText(this, getString(R.string.failed_to_start, Sender.Proxy.name), Toast.LENGTH_SHORT).show()
            false
        }
    }

    private suspend fun startEngine(configuration: Configuration): NativeEngine {
        if (state.value.isStopping) throw CancellationException("Proxy test stopped")
        val current = NativeEngine(applicationContext, Mode.Proxy, foreground = false)
        engine = current
        current.start(configuration)
        return current
    }

    private suspend fun stopEngine() = withContext(NonCancellable) {
        engineMutex.withLock {
            val current = engine ?: return@withLock
            var stopFailed = false
            while (true) {
                try {
                    current.stop()
                    engine = null
                    break
                } catch (e: Exception) {
                    if (!stopFailed) Log.e(TAG, "Failed to stop proxy test engine", e)
                    stopFailed = true
                    delay(500)
                }
            }
        }
    }

    private fun loadSettings(): TestSettings {
        val prefs = getPreferences()
        val userCommands = prefs.getBoolean("byedpi_proxytest_usercommands", false)
        val sniValue = prefs.getStringNotNull("byedpi_proxytest_sni", "google.com")
        val content = if (userCommands) prefs.getStringNotNull("byedpi_proxytest_commands", "")
        else assets.open("proxytest_strategies.list").bufferedReader().use { it.readText() }
        val commands = content.replace("{sni}", "\"${sniValue}\"").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val host = prefs.getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
        val port = prefs.getIntStringNotNull("byedpi_proxy_port", 1080)
        val delaySec = prefs.getIntStringNotNull("byedpi_proxytest_delay", 1).coerceAtLeast(0)
        val requestsCount = prefs.getIntStringNotNull("byedpi_proxytest_requests", 1).coerceAtLeast(1)
        val requestTimeout = prefs.getLongStringNotNull("byedpi_proxytest_timeout", 5).coerceAtLeast(1)
        val requestLimit = prefs.getIntStringNotNull("byedpi_proxytest_limit", 20).coerceAtLeast(1)
        DomainListUtils.syncLists(this)
        // The command line generator narrows a run to the sites that are actually blocked.
        val override = prefs.getStringNotNull(SITES_OVERRIDE_PREF, "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val sites = override.ifEmpty { DomainListUtils.getActiveDomains(this).toList() }
        return TestSettings(sites, commands, host, port,
            delaySec, requestsCount, requestTimeout, requestLimit)
    }

    private fun resetStrategyResult(strategy: StrategyResult, settings: TestSettings) {
        strategy.successCount = 0
        strategy.currentProgress = 0
        strategy.siteResults.clear()
        strategy.siteResults.addAll(settings.sites.map { SiteResult(it, 0, settings.requestsCount) })
    }

    private fun snapshot(strategies: List<StrategyResult>): List<StrategyResult> =
        strategies.map { it.copy(siteResults = it.siteResults.toMutableList()) }

    private suspend fun saveResults(strategies: List<StrategyResult>) {
        val results = snapshot(strategies)
        withContext(Dispatchers.IO) {
            synchronized(resultsLock) {
                val file = AtomicFile(File(filesDir, RESULTS_FILE))
                val output = file.startWrite()
                try {
                    output.write(gson.toJson(results).toByteArray(Charsets.UTF_8))
                    file.finishWrite(output)
                } catch (e: Exception) {
                    file.failWrite(output)
                    throw e
                }
            }
        }
    }

    private fun publish(strategies: List<StrategyResult>, currentStrategy: Int = state.value.currentStrategy) {
        mutableState.value = state.value.copy(isLoaded = true, strategies = snapshot(strategies), currentStrategy = currentStrategy)
        val completed = strategies.count { it.isCompleted }
        if (!destroyed && (notifiedStrategy != currentStrategy || notifiedCompleted != completed)) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification())
            notifiedStrategy = currentStrategy
            notifiedCompleted = completed
        }
    }

    private fun notification(): Notification {
        val value = state.value
        val open = LiveNotifications.openApp(this, "benchmark", OPEN_BENCHMARK_REQUEST)
        val stop = PendingIntent.getService(this, 0, Intent(this, TestService::class.java).setAction(STOP_ACTION), PendingIntent.FLAG_IMMUTABLE)
        val gen = CommandGenerator.state.value
        if (gen.running) {
            // A command line search runs many short tests: show the whole search's progress.
            val pct = if (gen.budget > 0) gen.done * 100 / gen.budget else 0
            val text = when (gen.phase) {
                GenPhase.Checking -> getString(R.string.pg_notif_gen_checking)
                GenPhase.Verifying -> getString(R.string.pg_notif_gen_verifying, pct)
                else -> getString(R.string.pg_notif_gen_round, gen.round.coerceAtLeast(1), gen.rounds, pct)
            }
            return LiveNotifications.progress(this, CHANNEL, getString(R.string.pg_notif_gen_title), text, gen.done, gen.budget, stop, open)
        }
        val total = value.strategies.size
        val done = value.strategies.count { it.isCompleted }
        val pct = if (total > 0) done * 100 / total else 0
        return LiveNotifications.progress(
            this, CHANNEL, getString(R.string.pg_notif_testing),
            if (total > 0) getString(R.string.pg_notif_testing_text, value.currentStrategy.coerceAtLeast(1), total, pct)
            else getString(R.string.pg_notif_gen_checking),
            done, total, stop, open
        )
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        notifiedStrategy = state.value.currentStrategy
        notifiedCompleted = state.value.strategies.count { it.isCompleted }
    }
}

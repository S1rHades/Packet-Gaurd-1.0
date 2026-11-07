package io.github.romanvht.byedpi.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.TestState
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.DomainListUtils
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getCmdEnable
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getStringNotNull
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.romanvht.byedpi.R

const val PREF_CUSTOM_PROFILES = "bb_custom_profiles"
private const val PREF_SELECTED = "bb_selected_profiles"
private const val PREF_USER_COMMANDS = "byedpi_proxytest_usercommands"
private const val PREF_TEST_COMMANDS = "byedpi_proxytest_commands"
private const val PREF_MODE = "bb_bench_mode"
private const val PREF_PLAN = "bb_gen_plan"

/** Step 2's choice: test the ready-made profiles, or let the generator build a command line. */
enum class BenchMode { ReadyMade, Generate }

private data class BenchLocal(
    val lists: List<ListItemUi> = emptyList(),
    val profiles: List<ProfileItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val pack: ProfilePack = ProfilePack.Basic,
    val applied: String? = null,
    val loaded: Boolean = false,
    val startedAt: Long = 0L
)

data class BenchUi(
    val lists: List<ListItemUi> = emptyList(),
    val profiles: List<ProfileItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val pack: ProfilePack = ProfilePack.Basic,
    val applied: String? = null,
    val test: TestState = TestState(),
    /** When the current (or last) ready-made run started, for the time-left estimate. */
    val startedAt: Long = 0L
) {
    val activeSites: Int get() = lists.filter { it.active }.sumOf { it.domains.size }
    val visibleProfiles: List<ProfileItem> get() = profiles.filter { it.pack == pack }

    /** Maps the command text recorded in results (with {sni} filled in) to a friendly name. */
    fun nameFor(resolvedCommand: String, sni: String, fallback: String): String =
        profiles.firstOrNull { it.command.replace("{sni}", "\"$sni\"") == resolvedCommand }?.name
            ?: fallback
}

/**
 * Drives the Benchmark tab. The actual testing loop is upstream's TestService (restarts the
 * native engine per profile, probes every domain through its SOCKS5 port, records results);
 * this class feeds it the selected lists/profiles and turns its results into a ranking.
 */
class BenchmarkViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app
    private val prefs get() = ctx.getPreferences()

    private val local = MutableStateFlow(BenchLocal())

    val ui: StateFlow<BenchUi> = combine(local, TestService.state) { l, t ->
        BenchUi(l.lists, l.profiles, l.selected, l.pack, l.applied, t, l.startedAt)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BenchUi())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val generator: StateFlow<GenState> = CommandGenerator.state

    private val _mode = MutableStateFlow(
        runCatching { BenchMode.valueOf(prefs.getStringNotNull(PREF_MODE, BenchMode.ReadyMade.name)) }.getOrDefault(BenchMode.ReadyMade)
    )
    val mode: StateFlow<BenchMode> = _mode

    private val _plan = MutableStateFlow(
        runCatching { SearchPlan.valueOf(prefs.getStringNotNull(PREF_PLAN, SearchPlan.Quick.name)) }.getOrDefault(SearchPlan.Quick)
    )
    val plan: StateFlow<SearchPlan> = _plan

    init {
        reload()
        viewModelScope.launch { TestService.loadResults(ctx) }
        CommandGenerator.load(ctx)
        // Note when each run starts, so step 2 can say roughly how long is left.
        viewModelScope.launch {
            var lastRun = -1L
            TestService.state.collect { t ->
                if (t.isRunning && t.runId != lastRun) {
                    lastRun = t.runId
                    local.update { it.copy(startedAt = System.currentTimeMillis()) }
                }
            }
        }
        // After an apply elsewhere, or an Undo, "In use" must match what really is in use.
        viewModelScope.launch {
            StrategyApplier.changes.collect {
                val active = if (prefs.getCmdEnable()) prefs.getCmdArgs() else null
                local.update { l -> l.copy(applied = l.applied?.takeIf { normalizeCommand(it) == active }) }
            }
        }
    }

    /** From a run that opened nothing: switch step 2 to a thorough "Make one just for me". */
    fun suggestGenerator() {
        selectMode(BenchMode.Generate)
        selectPlan(SearchPlan.Thorough)
    }

    fun selectMode(mode: BenchMode) {
        _mode.value = mode
        prefs.edit { putString(PREF_MODE, mode.name) }
    }

    fun selectPlan(plan: SearchPlan) {
        _plan.value = plan
        prefs.edit { putString(PREF_PLAN, plan.name) }
    }

    val sni: String get() = prefs.getStringNotNull("byedpi_proxytest_sni", "google.com")

    // ---- loading ------------------------------------------------------------------------

    fun reload() {
        viewModelScope.launch {
            val (lists, profiles) = guarded { withContext(Dispatchers.IO) {
                DomainListUtils.syncLists(ctx)
                val lists = orderLists(
                    DomainListUtils.getLists(ctx).map { ListItemUi(it.id, it.name, it.domains, it.isActive, it.isBuiltIn) },
                    SettingsUtils.getCurrentLanguage(ctx)
                )
                val community = ctx.assets.open("proxytest_strategies.list").bufferedReader()
                    .useLines { seq -> seq.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
                val custom = parseCustomProfiles(prefs.getString(PREF_CUSTOM_PROFILES, "") ?: "") {
                    ctx.getString(R.string.pg_custom_n, it)
                }
                lists to (
                    BenchPresets.basic.map { ProfileItem(it.second, it.first, ProfilePack.Basic) } +
                        community.mapIndexed { i, c -> ProfileItem(c, ctx.getString(R.string.pg_community_n, i + 1), ProfilePack.Community) } +
                        custom.map { ProfileItem(it.second, it.first, ProfilePack.Custom) }
                    )
            } } ?: return@launch
            local.update { old ->
                val all = profiles.map { it.command }.toSet()
                val saved = prefs.getStringSet(PREF_SELECTED, null)
                val selected = when {
                    old.loaded -> old.selected.filter { it in all }.toSet()
                    saved != null -> saved.filter { it in all }.toSet()
                    else -> profiles.filter { it.pack == ProfilePack.Basic }.map { it.command }.toSet()
                }
                old.copy(lists = lists, profiles = profiles, selected = selected, loaded = true)
            }
        }
    }

    // ---- lists --------------------------------------------------------------------------

    fun toggleList(id: String) = io { DomainListUtils.toggleListActive(ctx, id) }

    /** Returns an error message to show in the dialog, or null when the save was accepted. */
    fun saveList(id: String?, name: String, rawDomains: String): String? {
        val cleanName = name.trim()
        val domains = parseDomains(rawDomains)
        if (cleanName.isEmpty()) return ctx.getString(R.string.pg_err_list_name)
        if (domains.isEmpty()) return ctx.getString(R.string.pg_err_list_domains)
        io {
            val ok = if (id == null) DomainListUtils.addList(ctx, cleanName, domains)
            else DomainListUtils.updateList(ctx, id, cleanName, domains)
            if (!ok) say(ctx.getString(R.string.pg_err_list_exists))
        }
        return null
    }

    fun deleteList(id: String) = io { DomainListUtils.deleteList(ctx, id) }

    fun resetLists() = io { DomainListUtils.resetLists(ctx) }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch {
            guarded { withContext(Dispatchers.IO) { block() } }
            reload()
        }
    }

    /** Runs [block], turning a storage failure into a message instead of a crash. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("BenchmarkViewModel", "Storage failure", e)
        say(ctx.getString(R.string.pg_err_storage))
        null
    }

    // ---- profiles -----------------------------------------------------------------------

    fun selectPack(pack: ProfilePack) = local.update { it.copy(pack = pack) }

    fun toggleProfile(command: String) = updateSelection { s ->
        if (command in s) s - command else s + command
    }

    fun selectAllVisible() = updateSelection { s -> s + ui.value.visibleProfiles.map { it.command } }

    fun clearVisible() = updateSelection { s -> s - ui.value.visibleProfiles.map { it.command }.toSet() }

    private fun updateSelection(change: (Set<String>) -> Set<String>) {
        local.update { it.copy(selected = change(it.selected)) }
        prefs.edit { putStringSet(PREF_SELECTED, local.value.selected) }
    }

    // ---- run ----------------------------------------------------------------------------

    /** Starts whichever test step 2 has selected. */
    fun start() {
        val s = ui.value
        if (s.activeSites == 0) return say(ctx.getString(R.string.pg_err_no_list))
        if (_mode.value == BenchMode.Generate) {
            if (appStatus.first == AppStatus.Running) say(ctx.getString(R.string.pg_bench_paused))
            CommandGenerator.start(ctx, _plan.value)
            return
        }
        val commands = s.profiles.filter { it.command in s.selected }.map { it.command }
        if (commands.isEmpty()) return say(ctx.getString(R.string.pg_err_no_profile))
        // A regular run tests every active list and replaces the generator's last answer.
        CommandGenerator.clear(ctx)
        // TestService reads its profile list from these two upstream preferences.
        prefs.edit(commit = true) {
            putBoolean(PREF_USER_COMMANDS, true)
            putString(PREF_TEST_COMMANDS, commands.joinToString("\n"))
            remove(TestService.SITES_OVERRIDE_PREF)
        }
        if (appStatus.first == AppStatus.Running) say(ctx.getString(R.string.pg_bench_paused))
        TestService.start(ctx)
    }

    fun stop() {
        if (CommandGenerator.isRunning) CommandGenerator.stop() else TestService.stop()
    }

    // ---- apply --------------------------------------------------------------------------

    /** Makes [resolvedCommand] the active strategy and restarts the tunnel if it is running. */
    fun apply(resolvedCommand: String, label: String) {
        // Show "In use" at once; the app's snackbar then says what happened, with Undo.
        local.update { it.copy(applied = resolvedCommand) }
        viewModelScope.launch {
            guarded { withContext(Dispatchers.IO) { StrategyApplier.apply(ctx, resolvedCommand, label) } }
        }
    }

    fun importDomains(uri: Uri, onText: (String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(256_000) }
                }.getOrNull()
            }
            if (text == null) say(ctx.getString(R.string.pg_err_read_file)) else onText(text)
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }
}

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
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getStringNotNull
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

const val PREF_CUSTOM_PROFILES = "bb_custom_profiles"
private const val PREF_SELECTED = "bb_selected_profiles"
private const val PREF_USER_COMMANDS = "byedpi_proxytest_usercommands"
private const val PREF_TEST_COMMANDS = "byedpi_proxytest_commands"

private data class BenchLocal(
    val lists: List<ListItemUi> = emptyList(),
    val profiles: List<ProfileItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val pack: ProfilePack = ProfilePack.Basic,
    val applied: String? = null,
    val loaded: Boolean = false
)

data class BenchUi(
    val lists: List<ListItemUi> = emptyList(),
    val profiles: List<ProfileItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val pack: ProfilePack = ProfilePack.Basic,
    val applied: String? = null,
    val test: TestState = TestState()
) {
    val activeSites: Int get() = lists.filter { it.active }.sumOf { it.domains.size }
    val visibleProfiles: List<ProfileItem> get() = profiles.filter { it.pack == pack }

    /** Maps the command text recorded in results (with {sni} filled in) to a friendly name. */
    fun nameFor(resolvedCommand: String, sni: String): String =
        profiles.firstOrNull { it.command.replace("{sni}", "\"$sni\"") == resolvedCommand }?.name
            ?: "Custom strategy"
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
        BenchUi(l.lists, l.profiles, l.selected, l.pack, l.applied, t)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BenchUi())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        reload()
        viewModelScope.launch { TestService.loadResults(ctx) }
    }

    val sni: String get() = prefs.getStringNotNull("byedpi_proxytest_sni", "google.com")

    // ---- loading ------------------------------------------------------------------------

    fun reload() {
        viewModelScope.launch {
            val (lists, profiles) = withContext(Dispatchers.IO) {
                DomainListUtils.syncLists(ctx)
                val lists = DomainListUtils.getLists(ctx).map {
                    ListItemUi(it.id, it.name, it.domains, it.isActive, it.isBuiltIn)
                }
                val community = ctx.assets.open("proxytest_strategies.list").bufferedReader()
                    .useLines { seq -> seq.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
                val custom = parseCustomProfiles(prefs.getString(PREF_CUSTOM_PROFILES, "") ?: "")
                lists to (
                    BenchPresets.basic.map { ProfileItem(it.second, it.first, ProfilePack.Basic) } +
                        community.mapIndexed { i, c -> ProfileItem(c, "Community #${i + 1}", ProfilePack.Community) } +
                        custom.map { ProfileItem(it.second, it.first, ProfilePack.Custom) }
                    )
            }
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
        if (cleanName.isEmpty()) return "Give the list a name"
        if (domains.isEmpty()) return "Add at least one domain, e.g. example.com"
        io {
            val ok = if (id == null) DomainListUtils.addList(ctx, cleanName, domains)
            else DomainListUtils.updateList(ctx, id, cleanName, domains)
            if (!ok) say("A list with that name already exists")
        }
        return null
    }

    fun deleteList(id: String) = io { DomainListUtils.deleteList(ctx, id) }

    fun resetLists() = io { DomainListUtils.resetLists(ctx) }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            reload()
        }
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

    fun start() {
        val s = ui.value
        val commands = s.profiles.filter { it.command in s.selected }.map { it.command }
        when {
            commands.isEmpty() -> return say("Select at least one profile")
            s.activeSites == 0 -> return say("Turn on at least one link list")
        }
        // TestService reads its profile list from these two upstream preferences.
        prefs.edit(commit = true) {
            putBoolean(PREF_USER_COMMANDS, true)
            putString(PREF_TEST_COMMANDS, commands.joinToString("\n"))
        }
        if (appStatus.first == AppStatus.Running) say("Bypass paused while the benchmark runs")
        TestService.start(ctx)
    }

    fun stop() = TestService.stop()

    // ---- apply --------------------------------------------------------------------------

    /** Makes [resolvedCommand] the active strategy and restarts the tunnel if it is running. */
    fun apply(resolvedCommand: String, label: String) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                StrategyApplier.apply(ctx, resolvedCommand, label)
            }
            local.update { it.copy(applied = resolvedCommand) }
            say(message)
        }
    }

    fun importDomains(uri: Uri, onText: (String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(256_000) }
                }.getOrNull()
            }
            if (text == null) say("Could not read that file") else onText(text)
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }
}

package io.github.romanvht.byedpi.utility

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.google.gson.Gson
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppSettings
import java.io.File

object SettingsUtils {
    private const val TAG = "SettingsUtils"
    private val separatePreferenceKeys = setOf("byedpi_command_history", "selected_apps", "autostart_last_boot_count")

    enum class Section {
        SETTINGS,
        HISTORY,
        APPS,
        DOMAIN_LISTS
    }

    fun getCurrentLanguage(context: Context): String {
        val lang = context.getPreferences().getStringNotNull("language", "system")
        if (lang != "system") return lang

        val locales = AppCompatDelegate.getApplicationLocales()
        return locales[0]?.language ?: java.util.Locale.getDefault().language
    }

    fun setLang(lang: String) {
        val appLocale = localeByName(lang) ?: LocaleListCompat.getEmptyLocaleList()

        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != appLocale.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    private fun localeByName(lang: String): LocaleListCompat? = when (lang) {
        "system" -> LocaleListCompat.getEmptyLocaleList()
        "ru" -> LocaleListCompat.forLanguageTags("ru")
        "en" -> LocaleListCompat.forLanguageTags("en")
        "tr" -> LocaleListCompat.forLanguageTags("tr")
        "kk" -> LocaleListCompat.forLanguageTags("kk")
        "vi" -> LocaleListCompat.forLanguageTags("vi")
        else -> {
            Log.w(TAG, "Invalid value for language: $lang")
            null
        }
    }

    fun setTheme(name: String) {
        val appTheme = themeByName(name) ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

        if (AppCompatDelegate.getDefaultNightMode() != appTheme) {
            AppCompatDelegate.setDefaultNightMode(appTheme)
        }
    }

    private fun themeByName(name: String): Int? = when (name) {
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            Log.w(TAG, "Invalid value for app_theme: $name")
            null
        }
    }

    /** Writes the backup to [uri]; false (after showing why) when it couldn't. */
    fun exportSettings(context: Context, uri: Uri, sections: Set<Section>): Boolean {
        return try {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw java.io.IOException("No output stream for $uri")
            stream.use { it.write(settingsJson(context, sections).toByteArray()) }
            true
        } catch (e: Exception) {
            exportFailed(context, e)
            false
        }
    }

    fun exportSettings(context: Context, file: File, sections: Set<Section>) {
        try {
            file.writeText(settingsJson(context, sections))
        } catch (e: Exception) {
            exportFailed(context, e)
        }
    }

    fun readSettings(context: Context, uri: Uri): AppSettings? {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return null

            parseSettings(context, json)
        } catch (e: Exception) {
            importFailed(context, e)
            null
        }
    }

    fun readSettings(context: Context, file: File): AppSettings? {
        return try {
            parseSettings(context, file.readText())
        } catch (e: Exception) {
            importFailed(context, e)
            null
        }
    }

    private fun settingsJson(context: Context, sections: Set<Section>): String {
        val prefs = context.getPreferences()
        val export = AppSettings(
            app = BuildConfig.APPLICATION_ID,
            version = BuildConfig.VERSION_NAME,
            history = if (Section.HISTORY in sections) {
                HistoryUtils(context).getHistory()
            } else {
                null
            },
            apps = if (Section.APPS in sections) prefs.getSelectedApps() else null,
            domainLists = if (Section.DOMAIN_LISTS in sections) {
                DomainListUtils.getAllLists(context)
            } else {
                null
            },
            settings = if (Section.SETTINGS in sections) {
                prefs.all.filterKeys { it !in separatePreferenceKeys }
            } else {
                null
            }
        )
        return toJson(export)
    }

    private fun parseSettings(context: Context, json: String): AppSettings? {
        val settings = parseJson(json)
        if (settings?.app == BuildConfig.APPLICATION_ID) return settings

        Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show()
        return null
    }

    private fun exportFailed(context: Context, exception: Exception) {
        Log.e(TAG, "Failed to export settings", exception)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFailed(context: Context, exception: Exception) {
        Log.e(TAG, "Failed to read settings", exception)
        Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
    }

    /**
     * Converts a value read back from a backup into what SharedPreferences should store.
     * Gson reads every JSON number as a Double, so whole numbers become Int (or Long when they
     * don't fit) and the rest Float; string arrays become string sets. Anything else is null
     * and is skipped on import.
     */
    internal fun preferenceValue(value: Any?): Any? = when (value) {
        is Int, is Long, is Float, is Boolean, is String -> value
        is Double -> when (value) {
            value.toInt().toDouble() -> value.toInt()
            value.toLong().toDouble() -> value.toLong()
            else -> value.toFloat()
        }
        is Collection<*> -> if (value.all { it is String }) value.filterIsInstance<String>().toSet() else null
        else -> null
    }

    /** Settings the app reads with getBoolean, so a backup can't store them as text or numbers. */
    private val booleanKeys = setOf(
        "auto_connect", "autostart", "byedpi_desync_http", "byedpi_desync_https", "byedpi_desync_udp",
        "byedpi_domain_mixed_case", "byedpi_drop_sack", "byedpi_enable_cmd_settings", "byedpi_host_mixed_case",
        "byedpi_host_remove_spaces", "byedpi_no_domain", "byedpi_proxytest_usercommands", "byedpi_split_at_host",
        "byedpi_tcp_fast_open", "byedpi_tlsrec_at_sni", "byedpi_tlsrec_enabled", "is_test_running",
        "battery_optimization_requested", "pg_show_advanced", "updates_auto_check"
    )

    /** Settings the app reads as string sets. */
    private val setKeys = setOf("selected_apps", "bb_selected_profiles")

    /** Settings with a fixed set of values; anything else in a backup is dropped. */
    private val choiceKeys = mapOf(
        "byedpi_mode" to setOf("vpn", "proxy"),
        "byedpi_desync_method" to setOf("none", "split", "disorder", "fake", "oob", "disoob"),
        "byedpi_hosts_mode" to setOf("disable", "blacklist", "whitelist")
    )

    /**
     * What to store for one imported setting, in the type the app reads it as: true/false for
     * switches, text for everything else (the app keeps numbers as text), string sets for the
     * two list settings. [current] is the value stored before the import, if any. Null means
     * skip it, so a hand-edited or foreign backup can't leave a value that crashes on read.
     */
    internal fun importValue(key: String, raw: Any?, current: Any?): Any? {
        val value = preferenceValue(raw) ?: return null
        val wantsBoolean = key in booleanKeys || current is Boolean
        return when {
            key in setKeys || current is Set<*> -> value as? Set<*>
            wantsBoolean -> when (value) {
                is Boolean -> value
                "true" -> true
                "false" -> false
                else -> null
            }
            value is Set<*> -> null
            else -> {
                val text = value.toString()
                val allowed = choiceKeys[key]
                if (allowed != null && text !in allowed) null else text
            }
        }
    }

    /** The backup file contents: the inverse of [parseJson]. */
    internal fun toJson(settings: AppSettings): String = Gson().toJson(settings)

    internal fun parseJson(json: String): AppSettings? = Gson().fromJson(json, AppSettings::class.java)

    fun getAvailableSections(settings: AppSettings): Set<Section> = buildSet {
        if (settings.settings != null) add(Section.SETTINGS)
        if (settings.history != null) add(Section.HISTORY)
        if (settings.apps != null) add(Section.APPS)
        if (settings.domainLists != null) add(Section.DOMAIN_LISTS)
    }

    fun importSettings(
        context: Context,
        import: AppSettings,
        sections: Set<Section>,
        onRestart: () -> Unit
    ) {
        try {
            val prefs = context.getPreferences()

            if (Section.SETTINGS in sections && import.settings != null) {
                val before = prefs.all
                prefs.edit(commit = true) {
                    before.keys
                        .filter { it !in separatePreferenceKeys }
                        .forEach { remove(it) }

                    import.settings
                        .filterKeys { it !in separatePreferenceKeys }
                        .forEach { (key, raw) ->
                            when (val value = importValue(key, raw, before[key])) {
                                is Boolean -> putBoolean(key, value)
                                is String -> putString(key, value)
                                is Set<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    putStringSet(key, value as Set<String>)
                                }
                            }
                        }
                }
            }

            if (Section.APPS in sections && import.apps != null) {
                prefs.edit(commit = true) { putStringSet("selected_apps", import.apps.toSet()) }
            }

            if (Section.HISTORY in sections && import.history != null) {
                HistoryUtils(context).saveHistory(import.history)
            }

            if (Section.DOMAIN_LISTS in sections && import.domainLists != null) {
                val normalized = import.domainLists.map {
                    if (it.isBuiltIn) {
                        it.copy(
                            isModified = it.isModified,
                            isDeleted = it.isDeleted
                        )
                    } else {
                        it.copy(
                            isModified = false,
                            isDeleted = false
                        )
                    }
                }

                DomainListUtils.saveLists(context, normalized)
            }

            if (Section.SETTINGS in sections) {
                val newLang = prefs.getString("language", "system") ?: "system"
                val newTheme = prefs.getString("app_theme", "system") ?: "system"
                setLang(newLang)
                setTheme(newTheme)
            }

            Handler(Looper.getMainLooper()).post {
                onRestart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import settings", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun resetSettings(context: Context, onRestart: () -> Unit) {
        try {
            val prefs = context.getPreferences()

            prefs.edit(commit = true) {
                clear()
            }

            HistoryUtils(context).saveHistory(emptyList())
            DomainListUtils.resetLists(context)
            LineMemoryStore.clear(context)

            setLang("system")
            setTheme("system")

            Handler(Looper.getMainLooper()).post {
                onRestart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset settings", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, context.getString(R.string.pg_err_reset), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

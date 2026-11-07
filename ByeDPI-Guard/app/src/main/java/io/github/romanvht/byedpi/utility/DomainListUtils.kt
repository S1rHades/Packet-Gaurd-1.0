package io.github.romanvht.byedpi.utility

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.AtomicFile
import io.github.romanvht.byedpi.data.DomainList
import java.io.File

/**
 * The site lists, kept in one JSON file. Every public function is @Synchronized for its
 * whole read-change-write, because the Connect and Benchmark screens and the test service sync the
 * lists at the same time; writes go through [AtomicFile] so a reader never sees a half-written file.
 */
object DomainListUtils {
    private const val DOMAIN_LISTS_FILE = "domain_lists.json"

    private val gson = Gson()

    private fun getDefaultActiveIds(lang: String): Set<String> = when (lang) {
        "tr" -> setOf("türkiye", "discord")
        else -> setOf("youtube", "googlevideo", "social")
    }

    @Synchronized
    fun syncLists(context: Context) {
        // A file that exists but can't be read is left alone rather than replaced by the
        // built-in lists, which would throw away the user's own lists.
        val currentLists = readLists(context)?.toMutableList() ?: return

        val builtInMap = currentLists
            .filter { it.isBuiltIn }
            .associateBy { it.id }

        val assetFiles = context.assets.list("")?.filter {
            it.startsWith("proxytest_") && it.endsWith(".sites")
        } ?: emptyList()

        val newBuiltInIds = mutableSetOf<String>()

        for (assetFile in assetFiles) {
            val id = assetFile
                .removePrefix("proxytest_")
                .removeSuffix(".sites")

            newBuiltInIds.add(id)

            val domains = context.assets.open(assetFile)
                .bufferedReader()
                .useLines { it.toList() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val existing = builtInMap[id]

            when {
                existing == null -> {
                    currentLists.add(
                        DomainList(
                            id = id,
                            name = id.replaceFirstChar { it.uppercase() },
                            domains = domains,
                            isActive = id in getDefaultActiveIds(SettingsUtils.getCurrentLanguage(context)),
                            isBuiltIn = true
                        )
                    )
                }

                existing.isDeleted -> {
                    continue
                }

                existing.isModified -> {
                    continue
                }

                else -> {
                    val index = currentLists.indexOfFirst { it.id == id }

                    currentLists[index] = existing.copy(
                        domains = domains
                    )
                }
            }
        }

        currentLists.removeAll {
            it.isBuiltIn && it.id !in newBuiltInIds
        }

        saveLists(context, currentLists)
    }

    @Synchronized
    fun getAllLists(context: Context): MutableList<DomainList> =
        readLists(context)?.toMutableList() ?: mutableListOf()

    /** The stored lists; empty when there is no file yet, null when the file can't be read. */
    private fun readLists(context: Context): List<DomainList>? {
        val listsFile = File(context.filesDir, DOMAIN_LISTS_FILE)
        // AtomicFile keeps a .bak while it writes; if that's all there is, it restores from it.
        if (!listsFile.exists() && !File(listsFile.path + ".bak").exists()) return emptyList()
        return try {
            val json = String(AtomicFile(listsFile).readFully(), Charsets.UTF_8)
            val type = object : TypeToken<List<DomainList?>>() {}.type
            val lists: List<DomainList?> = gson.fromJson(json, type) ?: emptyList()
            sanitize(lists)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gson ignores Kotlin's non-null types, so a damaged file or an imported backup can hold
     * lists with a missing id, name or domains. Drop those without an id and fill in the rest.
     */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    internal fun sanitize(lists: List<DomainList?>): List<DomainList> = lists.mapNotNull { l ->
        if (l == null || l.id == null || l.id.isBlank()) return@mapNotNull null
        val domains = (l.domains ?: emptyList()).filter { it != null && it.isNotBlank() }
        if (l.name == null || l.domains == null || domains.size != l.domains.size) {
            l.copy(name = l.name ?: l.id, domains = domains)
        } else l
    }

    fun getLists(context: Context): List<DomainList> {
        return getAllLists(context).filter { !it.isDeleted }
    }

    fun getActiveDomains(context: Context): List<String> {
        return getLists(context).filter { it.isActive }.flatMap { it.domains }.distinct()
    }

    @Synchronized
    fun saveLists(context: Context, lists: List<DomainList?>) {
        val file = AtomicFile(File(context.filesDir, DOMAIN_LISTS_FILE))
        val out = file.startWrite()
        try {
            out.write(gson.toJson(sanitize(lists)).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    @Synchronized
    fun addList(context: Context, name: String, domains: List<String>): Boolean {
        val lists = getAllLists(context).toMutableList()
        val id = name.lowercase().replace(" ", "_")

        val existing = lists.find { it.id == id }

        if (existing != null) {
            if (existing.isBuiltIn && existing.isDeleted) {
                val index = lists.indexOf(existing)

                lists[index] = existing.copy(
                    name = name,
                    domains = domains,
                    isDeleted = false,
                    isModified = true,
                    isActive = true
                )

                saveLists(context, lists)
                return true
            }

            return false
        }

        lists.add(
            DomainList(
                id = id,
                name = name,
                domains = domains,
                isActive = true,
                isBuiltIn = false
            )
        )

        saveLists(context, lists)
        return true
    }

    @Synchronized
    fun updateList(context: Context, id: String, name: String, domains: List<String>): Boolean {
        val lists = getAllLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val oldList = lists[index]

        lists[index] = oldList.copy(
            name = name,
            domains = domains,
            isModified = oldList.isBuiltIn
        )

        saveLists(context, lists)
        return true
    }

    @Synchronized
    fun toggleListActive(context: Context, id: String): Boolean {
        val lists = getAllLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val list = lists[index]
        lists[index] = list.copy(isActive = !list.isActive)

        saveLists(context, lists)
        return true
    }

    @Synchronized
    fun deleteList(context: Context, id: String): Boolean {
        val lists = getAllLists(context).toMutableList()
        val index = lists.indexOfFirst { it.id == id }

        if (index == -1) return false

        val list = lists[index]

        if (list.isBuiltIn) {
            lists[index] = list.copy(
                isDeleted = true,
                isActive = false
            )
        } else {
            lists.removeAt(index)
        }

        saveLists(context, lists)
        return true
    }

    @Synchronized
    fun resetLists(context: Context) {
        AtomicFile(File(context.filesDir, DOMAIN_LISTS_FILE)).delete()
        syncLists(context)
    }
}

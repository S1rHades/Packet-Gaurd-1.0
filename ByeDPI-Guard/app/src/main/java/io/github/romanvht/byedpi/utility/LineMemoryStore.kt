package io.github.romanvht.byedpi.utility

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.data.LineRecord
import io.github.romanvht.byedpi.data.StrategyResult
import java.io.File

/**
 * Every command line tested on this phone and how it did on each site, kept on the phone only,
 * so the command line generator can learn from lines tested before (ready-made, community and
 * its own) instead of starting from nothing each time.
 */
object LineMemoryStore {
    private const val TAG = "LineMemory"
    private const val FILE = "line_memory.json"
    /** Lines kept, most recently tested first. */
    const val MAX_LINES = 300
    /** Tries kept per site; beyond it older counts are halved so recent results weigh more. */
    const val MAX_TRIES = 12

    private val gson = Gson()

    private fun file(context: Context) = AtomicFile(File(context.filesDir, FILE))

    @Synchronized
    fun load(context: Context): List<LineRecord> = try {
        val f = File(context.filesDir, FILE)
        if (!f.exists() && !File(f.path + ".bak").exists()) emptyList()
        else {
            val type = object : TypeToken<List<LineRecord?>>() {}.type
            val raw: List<LineRecord?> = gson.fromJson(String(file(context).readFully(), Charsets.UTF_8), type) ?: emptyList()
            @Suppress("SENSELESS_COMPARISON")
            raw.filter { it != null && it.command != null && it.sites != null }.map { it!! }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the line memory", e)
        emptyList()
    }

    /** Adds a finished test run's results. */
    @Synchronized
    fun record(context: Context, results: List<StrategyResult>, now: Long = System.currentTimeMillis()) {
        val merged = merge(load(context), results, now)
        val f = file(context)
        val out = f.startWrite()
        try {
            out.write(gson.toJson(merged).toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            Log.w(TAG, "Couldn't save the line memory", e)
        }
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    /** [memory] with [results] added; pure, for tests. */
    internal fun merge(memory: List<LineRecord>, results: List<StrategyResult>, now: Long): List<LineRecord> {
        val byCommand = LinkedHashMap<String, LineRecord>()
        memory.forEach { byCommand[it.command] = it }
        for (r in results) {
            if (!r.isCompleted || r.siteResults.isEmpty() || r.command.isBlank() || r.command.startsWith("--comment")) continue
            val rec = byCommand.remove(r.command) ?: LineRecord(r.command)
            for (s in r.siteResults) {
                if (s.totalCount <= 0) continue
                val c = rec.sites.getOrPut(s.site) { IntArray(2) }
                c[0] += s.successCount.coerceIn(0, s.totalCount)
                c[1] += s.totalCount
                while (c[1] > MAX_TRIES) { c[0] /= 2; c[1] /= 2 }
                s.latencyMs?.let { rec.latencySum += it; rec.latencyCount++ }
            }
            if (rec.latencyCount > MAX_TRIES) { rec.latencySum /= 2; rec.latencyCount /= 2 }
            rec.updatedAt = now
            byCommand[r.command] = rec // re-inserted last: most recent
        }
        return byCommand.values.toList().takeLast(MAX_LINES)
    }
}

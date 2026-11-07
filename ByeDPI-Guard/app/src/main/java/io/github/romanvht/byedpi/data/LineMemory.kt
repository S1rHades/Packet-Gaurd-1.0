package io.github.romanvht.byedpi.data

/**
 * What the app remembers about one command line it has tested on this phone, from any
 * Benchmark or command line search: per site, how often it opened. The generator learns from it.
 */
data class LineRecord(
    val command: String,
    /** Site to [opened, tried]. */
    val sites: MutableMap<String, IntArray> = HashMap(),
    var latencySum: Long = 0,
    var latencyCount: Int = 0,
    /** When it was last tested, in ms since the epoch. */
    var updatedAt: Long = 0
) {
    val avgLatencyMs: Long? get() = if (latencyCount > 0) latencySum / latencyCount else null
}

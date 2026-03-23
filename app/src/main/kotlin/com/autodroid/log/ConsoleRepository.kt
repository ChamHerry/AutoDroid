package com.autodroid.log

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * In-memory log storage shared by HTTP server middleware and UI console.
 * Thread-safe via CopyOnWriteArrayList — no Compose runtime dependency.
 */
object ConsoleRepository {
    private val _logs = CopyOnWriteArrayList<LogEntry>()
    /** Returns a snapshot copy, safe for iteration without ConcurrentModificationException. */
    val logs: List<LogEntry> get() = ArrayList(_logs)

    private const val MAX_LINES = 1000

    private val _flow = MutableSharedFlow<LogEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Subscribe to log events (new entries and clears). */
    val events: SharedFlow<LogEvent> = _flow

    @JvmStatic
    fun append(level: String, message: String) {
        val entry = LogEntry(level, message, System.currentTimeMillis())
        _logs.add(entry)
        _flow.tryEmit(LogEvent.NewEntry(entry))
        // Trim oldest entries if over limit
        while (_logs.size > MAX_LINES) {
            try { _logs.removeAt(0) } catch (_: IndexOutOfBoundsException) { break }
        }
    }

    fun clear() {
        _logs.clear()
        _flow.tryEmit(LogEvent.Cleared)
    }
}

data class LogEntry(val level: String, val message: String, val timestamp: Long)

sealed class LogEvent {
    data class NewEntry(val entry: LogEntry) : LogEvent()
    data object Cleared : LogEvent()
}

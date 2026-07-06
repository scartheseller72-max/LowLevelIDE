package com.example.lowlevelide.terminal

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the live list of [TerminalSession]s and the currently focused index.
 * Exposes flows for the UI to bind to.
 */
class TerminalSessionManager(private val context: Context) {

    private val provider = TerminalSessionProvider(context)
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeIndex = MutableStateFlow(-1)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    // The session list is mutated from both the UI thread (tab actions) and the PtyService
    // thread (onStartCommand/onDestroy). Guard every read-modify-write so a concurrent
    // newSession/finishAll can't lose a session and leak its PTY fd / child process.
    private val lock = Any()

    fun newSession(
        client: TerminalSessionClient,
        options: TerminalSessionProvider.Options = TerminalSessionProvider.Options()
    ): TerminalSession {
        val session = provider.createSession(client, options)
        synchronized(lock) {
            _sessions.value = _sessions.value + session
            _activeIndex.value = _sessions.value.size - 1
        }
        return session
    }

    fun closeSession(index: Int) {
        synchronized(lock) {
            val list = _sessions.value.toMutableList()
            if (index !in list.indices) return
            // Remember which session was focused so closing a *different* tab doesn't yank
            // focus away from it. Keying off the closed index (the old behaviour) moved the
            // active tab whenever the user closed any tab above the active one.
            val activeBefore = list.getOrNull(_activeIndex.value)
            val closing = list[index]
            runCatching { closing.finishIfRunning() }
            list.removeAt(index)
            _sessions.value = list
            _activeIndex.value = when {
                list.isEmpty() -> -1
                // A tab other than the active one was closed: keep the same session focused.
                activeBefore != null && activeBefore !== closing -> list.indexOf(activeBefore)
                // The active tab was closed: focus its neighbour (next, or the new last).
                else -> index.coerceAtMost(list.lastIndex)
            }
        }
    }

    fun setActive(index: Int) {
        synchronized(lock) {
            if (index in _sessions.value.indices) _activeIndex.value = index
        }
    }

    fun activeSession(): TerminalSession? =
        _sessions.value.getOrNull(_activeIndex.value)

    fun finishAll() {
        synchronized(lock) {
            _sessions.value.forEach { runCatching { it.finishIfRunning() } }
            _sessions.value = emptyList()
            _activeIndex.value = -1
        }
    }
}

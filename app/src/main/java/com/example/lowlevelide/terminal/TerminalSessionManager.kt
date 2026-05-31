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

    fun newSession(
        client: TerminalSessionClient,
        options: TerminalSessionProvider.Options = TerminalSessionProvider.Options()
    ): TerminalSession {
        val session = provider.createSession(client, options)
        _sessions.value = _sessions.value + session
        _activeIndex.value = _sessions.value.size - 1
        return session
    }

    fun closeSession(index: Int) {
        val list = _sessions.value.toMutableList()
        if (index !in list.indices) return
        runCatching { list[index].finishIfRunning() }
        list.removeAt(index)
        _sessions.value = list
        _activeIndex.value = (index - 1).coerceAtLeast(if (list.isEmpty()) -1 else 0)
    }

    fun setActive(index: Int) {
        if (index in _sessions.value.indices) _activeIndex.value = index
    }

    fun activeSession(): TerminalSession? =
        _sessions.value.getOrNull(_activeIndex.value)

    fun finishAll() {
        _sessions.value.forEach { runCatching { it.finishIfRunning() } }
        _sessions.value = emptyList()
        _activeIndex.value = -1
    }
}

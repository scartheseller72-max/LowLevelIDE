package com.example.lowlevelide.ui.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lowlevelide.App
import com.example.lowlevelide.R
import com.example.lowlevelide.databinding.FragmentTerminalBinding
import com.example.lowlevelide.databinding.ItemTabBinding
import com.example.lowlevelide.terminal.PtyService
import com.example.lowlevelide.terminal.TerminalPalette
import com.example.lowlevelide.terminal.TerminalSessionManager
import com.example.lowlevelide.terminal.TerminalSessionProvider
import com.example.lowlevelide.util.Logger
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the [com.termux.view.TerminalView] plus a tab strip and the [ExtraKeysView]
 * keyboard helper bar. Owns no session lifecycle — that lives in [TerminalSessionManager],
 * either inside this fragment (transient mode) or inside [PtyService] (persistent mode).
 */
class TerminalFragment : Fragment() {

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!

    private var localManager: TerminalSessionManager? = null
    private var boundService: PtyService? = null
    private val manager: TerminalSessionManager
        get() = boundService?.sessionManager ?: localManager
            ?: throw IllegalStateException("No session manager")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        binding.btnNewSession.setOnClickListener { newSession() }

        viewLifecycleOwner.lifecycleScope.launch {
            val showExtra = (requireActivity().applicationContext as App)
                .settings.extraKeysFlow.first()
            binding.extraKeysView.visibility = if (showExtra) View.VISIBLE else View.GONE
            binding.extraKeysView.attach { text -> sendToActiveSession(text) }
        }

        // If the host activity already bound to PtyService, it'll call attachService().
        // Otherwise create a local-only manager so the fragment is self-sufficient.
        if (boundService == null && localManager == null) {
            localManager = TerminalSessionManager(requireContext())
        }
        ensureAtLeastOneSession()
        rebuildTabs()
        return binding.root
    }

    fun attachService(service: PtyService) {
        boundService = service
        ensureAtLeastOneSession()
        rebuildTabs()
        bindActiveSession()
    }

    private fun newSession(): TerminalSession {
        val app = requireActivity().applicationContext as App
        val opts = TerminalSessionProvider.Options(preferRoot = true, preferProot = true)
        val client = createSessionClient()
        val session = manager.newSession(client, opts)
        rebuildTabs()
        bindActiveSession()
        boundService?.refreshNotification()
        return session
    }

    private fun ensureAtLeastOneSession() {
        if (manager.sessions.value.isEmpty()) newSession()
    }

    private fun bindActiveSession() {
        val active = manager.activeSession() ?: return
        binding.terminalView.setTerminalViewClient(createViewClient())
        binding.terminalView.attachSession(active)
        binding.terminalView.requestFocus()
        applyTerminalPalette()
    }

    /**
     * Apply the user's chosen [TerminalPalette]. The view background is always set
     * (TerminalView is a plain View). If the bundled terminal-emulator exposes the
     * indexed colour table we also push the full 16-colour ANSI palette + default
     * fg/bg via reflection — guarded so an API mismatch can never crash the app.
     */
    private fun applyTerminalPalette() {
        val binding = _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val key = (requireActivity().applicationContext as App).settings.terminalPaletteFlow.first()
            val palette = TerminalPalette.byKey(key)
            binding.terminalView.setBackgroundColor(palette.background)
            runCatching { applyAnsiReflectively(palette) }
                .onFailure { Logger.d("Terminal", "ANSI palette not applied: ${it.message}") }
            binding.terminalView.onScreenUpdated()
        }
    }

    /**
     * Best-effort: reach into TerminalSession.getEmulator().mColors.mCurrentColors and
     * overwrite the indexed colour table. All access is reflective and wrapped by the
     * caller's runCatching, so it degrades gracefully on any emulator build.
     */
    private fun applyAnsiReflectively(palette: TerminalPalette) {
        val session = manager.activeSession() ?: return
        val emulator = session.emulator ?: return
        val colorsField = emulator.javaClass.getDeclaredField("mColors").apply { isAccessible = true }
        val colors = colorsField.get(emulator) ?: return
        val currentField = colors.javaClass.getDeclaredField("mCurrentColors").apply { isAccessible = true }
        val table = currentField.get(colors) as? IntArray ?: return
        for (i in palette.ansi.indices) {
            if (i < table.size) table[i] = palette.ansi[i]
        }
        // Termux indexed colours: 256 = foreground, 257 = background, 258 = cursor.
        if (table.size > 257) {
            table[256] = palette.foreground
            table[257] = palette.background
        }
        if (table.size > 258) table[258] = palette.foreground
    }

    private fun rebuildTabs() {
        val container = binding.terminalTabsContainer
        container.removeAllViews()
        val sessions = manager.sessions.value
        sessions.forEachIndexed { idx, _ ->
            val tab = ItemTabBinding.inflate(layoutInflater, container, false)
            tab.tabTitle.text = "sh #${idx + 1}"
            tab.root.setOnClickListener {
                manager.setActive(idx)
                bindActiveSession()
                rebuildTabs()
            }
            tab.tabClose.setOnClickListener {
                manager.closeSession(idx)
                rebuildTabs()
                if (manager.sessions.value.isEmpty()) newSession() else bindActiveSession()
                boundService?.refreshNotification()
            }
            if (idx == manager.activeIndex.value) tab.root.alpha = 1f else tab.root.alpha = 0.55f
            container.addView(tab.root)
        }
    }

    fun sendToActiveSession(text: String) {
        manager.activeSession()?.write(text)
    }

    private fun createSessionClient() = object : TerminalSessionClient {
        override fun onTextChanged(s: TerminalSession?) { binding.terminalView.onScreenUpdated() }
        override fun onTitleChanged(s: TerminalSession?) {}
        override fun onSessionFinished(s: TerminalSession?) { Logger.i("Terminal", "Session finished") }
        override fun onCopyTextToClipboard(s: TerminalSession?, t: String?) {}
        override fun onPasteTextFromClipboard(s: TerminalSession?) {}
        override fun onBell(s: TerminalSession?) {}
        override fun onColorsChanged(s: TerminalSession?) {}
        override fun onTerminalCursorStateChange(s: Boolean) {}
        override fun setTerminalShellPid(s: TerminalSession?, pid: Int) {}
        override fun getTerminalCursorStyle() = null
        override fun logError(tag: String?, message: String?) { Logger.e(tag ?: "Term", message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Logger.w(tag ?: "Term", message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Logger.i(tag ?: "Term", message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Logger.d(tag ?: "Term", message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Logger.d(tag ?: "Term", message ?: "") }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Logger.e(tag ?: "Term", message ?: "", e)
        }
        override fun logStackTrace(tag: String?, e: Exception?) { Logger.e(tag ?: "Term", "stack", e) }
    }

    private fun createViewClient() = object : TerminalViewClient {
        override fun scaleFontSize(): Float = 1f
        override fun onScale(scale: Float): Float = scale
        override fun onSingleTapUp(e: android.view.MotionEvent?) { binding.terminalView.requestFocus() }
        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = false
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: TerminalSession?) = false
        override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?) = false
        override fun onLongPress(event: android.view.MotionEvent?) = false
        override fun readControlKey() = binding.extraKeysView.ctrlActive
        override fun readAltKey() = binding.extraKeysView.altActive
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false
        override fun onEmulatorSet() {}
        override fun logError(tag: String?, message: String?) { Logger.e(tag ?: "Term", message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Logger.w(tag ?: "Term", message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Logger.i(tag ?: "Term", message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Logger.d(tag ?: "Term", message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Logger.d(tag ?: "Term", message ?: "") }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Logger.e(tag ?: "Term", message ?: "", e)
        }
        override fun logStackTrace(tag: String?, e: Exception?) { Logger.e(tag ?: "Term", "stack", e) }
    }

    override fun onDestroyView() {
        if (boundService == null) localManager?.finishAll()
        _binding = null
        super.onDestroyView()
    }
}

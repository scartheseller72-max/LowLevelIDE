package com.example.lowlevelide.ui.editor

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lowlevelide.App
import com.example.lowlevelide.databinding.FragmentEditorBinding
import com.example.lowlevelide.files.FileInterface
import com.example.lowlevelide.bootstrap.CodeMirrorInstaller
import com.example.lowlevelide.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

/**
 * The editor pane. Embeds a WebView whose page (assets/editor/index.html) loads CodeMirror
 * from <filesDir>/editor/, where [CodeMirrorInstaller] dropped the JS/CSS at first launch.
 *
 * Tabs are tracked here on the Kotlin side (open file paths). The actual document state
 * lives inside CodeMirror; we swap docs by sending JS messages to the WebView.
 */
class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)

        val webView = binding.webviewEditor
        with(webView.settings) {
            javaScriptEnabled = true
            allowFileAccess = true
            domStorageEnabled = true
            allowUniversalAccessFromFileURLs = false
            // Don't let scripts on a file:// page issue local-file XHR; the Java bridge below
            // is reachable from page scripts, so keep the file:// origin as locked down as
            // possible. Editor assets are served through shouldInterceptRequest instead.
            allowFileAccessFromFileURLs = false
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Logger.d("EditorJS", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
        // Map file:///cm/* to <filesDir>/editor/* so the editor page can <script src="cm/...">.
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (!url.contains("/cm/")) return null
                val rel = url.substringAfter("/cm/")
                val installer = CodeMirrorInstaller(requireContext())
                val editorDir = installer.editorDir().canonicalFile
                val file = File(editorDir, rel).canonicalFile
                // Confine to the editor asset dir so a crafted `cm/../...` URL can't read
                // arbitrary files through the interceptor.
                val inside = file.path == editorDir.path ||
                    file.path.startsWith(editorDir.path + File.separator)
                if (!inside || !file.exists()) return null
                val mime = when (file.extension.lowercase()) {
                    "js" -> "text/javascript"
                    "css" -> "text/css"
                    else -> "application/octet-stream"
                }
                return WebResourceResponse(mime, "UTF-8", FileInputStream(file))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                applyEditorPreferences()
            }
        }
        webView.addJavascriptInterface(FileInterface(requireContext()), "Android")
        webView.loadUrl("file:///android_asset/editor/index.html")

        binding.btnSave.setOnClickListener { saveActive() }
        binding.btnNewFile.setOnClickListener { promptNewFile() }
        binding.btnRun.setOnClickListener { buildAndRunActive() }
        binding.btnFind.setOnClickListener { openFind() }

        // Preferences (font size, colour scheme, word wrap) are applied in
        // onPageFinished so the JS bridge functions are guaranteed to exist.

        return binding.root
    }

    /** Push the persisted editor preferences into the CodeMirror page. */
    private fun applyEditorPreferences() {
        val binding = _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = (requireActivity().applicationContext as App).settings
            val size = settings.editorFontSizeFlow.first()
            val theme = settings.editorThemeFlow.first()
            val wrap = settings.wordWrapFlow.first()
            binding.webviewEditor.evaluateJavascript("setEditorTheme(${jsString(theme)});", null)
            binding.webviewEditor.evaluateJavascript("setEditorFontSize($size);", null)
            binding.webviewEditor.evaluateJavascript("setWordWrap($wrap);", null)
        }
    }

    private fun jsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun saveActive() {
        binding.webviewEditor.evaluateJavascript("saveActive();", null)
    }

    fun saveAll() {
        binding.webviewEditor.evaluateJavascript("saveAll();", null)
    }

    fun openFind() {
        binding.webviewEditor.evaluateJavascript("openFind();", null)
    }

    /**
     * Compile + run the active file in the bottom terminal pane.
     * Asks JS for the active file path, then maps the extension to a build/run command
     * and pipes it into the active shell session.
     */
    fun buildAndRunActive() {
        binding.webviewEditor.evaluateJavascript("getActiveFile()") { raw ->
            // raw comes back JSON-encoded ("foo.c" or null)
            val tab = raw?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "null" }
                ?: return@evaluateJavascript
            val cmd = inferRunCommand(tab) ?: return@evaluateJavascript
            (requireActivity().supportFragmentManager
                .findFragmentById(com.example.lowlevelide.R.id.terminalContainer)
                as? com.example.lowlevelide.ui.terminal.TerminalFragment)
                ?.sendToActiveSession("$cmd\n")
        }
    }

    /** Single-quote a path for safe shell interpolation, escaping embedded single quotes. */
    private fun sh(s: String): String =
        if (s.isEmpty()) "''" else "'" + s.replace("'", "'\\''") + "'"

    private fun inferRunCommand(rel: String): String? {
        // $HOME stays an unquoted shell variable; every file-derived value is single-quoted so a
        // filename containing shell metacharacters can't inject commands into the user's session.
        val home = "\$HOME"
        val name = rel.substringAfterLast('/')
        return when {
            name.endsWith(".c") -> {
                val base = name.removeSuffix(".c")
                "cd $home && gcc ${sh(rel)} -o ${sh(base)} && ${sh("./$base")}"
            }
            name.endsWith(".cpp") || name.endsWith(".cc") -> {
                val base = name.substringBeforeLast('.')
                "cd $home && g++ ${sh(rel)} -o ${sh(base)} && ${sh("./$base")}"
            }
            name.endsWith(".py") -> "cd $home && python3 ${sh(rel)}"
            name.endsWith(".sh") -> "cd $home && sh ${sh(rel)}"
            name.endsWith(".js") -> "cd $home && node ${sh(rel)}"
            name.endsWith(".rs") -> {
                val base = name.removeSuffix(".rs")
                "cd $home && rustc ${sh(rel)} -o ${sh(base)} && ${sh("./$base")}"
            }
            else -> null
        }
    }

    private fun promptNewFile() {
        binding.webviewEditor.evaluateJavascript("promptNewFile();", null)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

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
            allowFileAccessFromFileURLs = true
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
                val file = File(installer.editorDir(), rel)
                if (!file.exists()) return null
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

    private fun inferRunCommand(rel: String): String? {
        val home = "\$HOME"
        val name = rel.substringAfterLast('/')
        return when {
            name.endsWith(".c") -> "cd $home && gcc \"$rel\" -o ${name.removeSuffix(".c")} && ./${name.removeSuffix(".c")}"
            name.endsWith(".cpp") || name.endsWith(".cc") -> {
                val base = name.substringBeforeLast('.')
                "cd $home && g++ \"$rel\" -o $base && ./$base"
            }
            name.endsWith(".py") -> "cd $home && python3 \"$rel\""
            name.endsWith(".sh") -> "cd $home && sh \"$rel\""
            name.endsWith(".js") -> "cd $home && node \"$rel\""
            name.endsWith(".rs") -> {
                val base = name.removeSuffix(".rs")
                "cd $home && rustc \"$rel\" -o $base && ./$base"
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

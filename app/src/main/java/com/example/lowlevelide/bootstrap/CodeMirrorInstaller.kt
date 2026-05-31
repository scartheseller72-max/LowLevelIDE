package com.example.lowlevelide.bootstrap

import android.content.Context
import com.example.lowlevelide.util.Logger
import java.io.File
import java.io.IOException

/**
 * Auto-fetches CodeMirror v5 JS/CSS/modes/themes into the WebView-readable
 * directory `<filesDir>/editor/` at first launch. The HTML loaded by the WebView
 * resolves these via the file:// scheme (see [com.example.lowlevelide.ui.editor.EditorFragment]).
 *
 * If any file already exists locally we skip it. To force a re-download, delete
 * the editor directory.
 */
class CodeMirrorInstaller(
    private val context: Context,
    private val downloader: AssetDownloader = AssetDownloader()
) {

    /** Files we ship: relative path under <filesDir>/editor/ -> CDN URL */
    private val files: Map<String, String> = buildMap {
        val ver = VERSION
        put("codemirror.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/lib/codemirror.js")
        put("codemirror.css", "https://cdn.jsdelivr.net/npm/codemirror@$ver/lib/codemirror.css")
        // Editor colour schemes (selectable from Settings).
        for (theme in THEMES) {
            put("theme/$theme.css", "https://cdn.jsdelivr.net/npm/codemirror@$ver/theme/$theme.css")
        }
        put("addon/edit/matchbrackets.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/edit/matchbrackets.js")
        put("addon/edit/closebrackets.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/edit/closebrackets.js")
        put("addon/dialog/dialog.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/dialog/dialog.js")
        put("addon/dialog/dialog.css", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/dialog/dialog.css")
        put("addon/search/search.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/search/search.js")
        put("addon/search/searchcursor.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/search/searchcursor.js")
        put("addon/search/jump-to-line.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/search/jump-to-line.js")
        put("addon/scroll/annotatescrollbar.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/scroll/annotatescrollbar.js")
        put("addon/search/matchesonscrollbar.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/search/matchesonscrollbar.js")
        put("addon/search/matchesonscrollbar.css", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/search/matchesonscrollbar.css")
        put("addon/selection/active-line.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/addon/selection/active-line.js")
        // Language modes
        for (mode in MODES) {
            put("mode/$mode/$mode.js", "https://cdn.jsdelivr.net/npm/codemirror@$ver/mode/$mode/$mode.js")
        }
    }

    fun editorDir(): File = File(context.filesDir, "editor")

    fun isInstalled(): Boolean {
        val root = editorDir()
        return files.keys.all { File(root, it).exists() }
    }

    @Throws(IOException::class)
    fun install(onProgress: (AssetDownloader.Progress) -> Unit = {}) {
        if (isInstalled()) { onProgress(AssetDownloader.Progress.Done); return }
        val root = editorDir().apply { mkdirs() }
        var done = 0
        val total = files.size
        files.forEach { (rel, url) ->
            val out = File(root, rel)
            if (out.exists()) { done++; return@forEach }
            try {
                downloader.download(url, out) { p ->
                    if (p is AssetDownloader.Progress.Bytes) {
                        // Forward as overall progress (file count granularity)
                        onProgress(AssetDownloader.Progress.Bytes(done.toLong(), total.toLong()))
                    }
                }
                done++
                onProgress(AssetDownloader.Progress.Bytes(done.toLong(), total.toLong()))
            } catch (e: IOException) {
                Logger.w(TAG, "Optional editor asset failed: $url -> ${e.message}")
                // Don't abort the whole install for non-essential addons.
                if (rel == "codemirror.js" || rel == "codemirror.css") throw e
            }
        }
        onProgress(AssetDownloader.Progress.Done)
    }

    companion object {
        private const val TAG = "CodeMirrorInstaller"
        const val VERSION = "5.65.16"
        val MODES = listOf(
            "clike", "python", "javascript", "shell", "rust",
            "go", "xml", "css", "htmlmixed", "markdown",
            "yaml", "sql"
        )

        /** CodeMirror colour schemes shipped for the in-editor theme picker. */
        val THEMES = listOf(
            "material-darker", "dracula", "monokai", "ayu-dark", "eclipse", "idea"
        )
    }
}

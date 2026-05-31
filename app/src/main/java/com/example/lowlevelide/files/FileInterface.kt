package com.example.lowlevelide.files

import android.content.Context
import android.webkit.JavascriptInterface
import com.example.lowlevelide.util.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge exposed to the CodeMirror editor in the WebView under the global name `Android`.
 *
 * Methods are JavascriptInterface-annotated so R8 keeps them. Every path passes through
 * [FileRepository.resolve] which rejects escapes from the home directory.
 */
class FileInterface(context: Context) {

    private val repo = FileRepository(context)

    @JavascriptInterface
    fun homePath(): String = repo.homeRoot.absolutePath

    @JavascriptInterface
    fun openFile(relative: String): String {
        val file = repo.resolve(relative) ?: return "// Path rejected"
        return if (file.isFile) file.readText() else "// Not a file: $relative"
    }

    @JavascriptInterface
    fun saveFile(relative: String, content: String): Boolean {
        val file = repo.resolve(relative) ?: return false
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Save failed: $relative", e); false
        }
    }

    @JavascriptInterface
    fun listFiles(relative: String): String {
        val dir = repo.resolve(relative) ?: return "[]"
        val arr = JSONArray()
        repo.list(dir).forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("isDir", f.isDirectory)
                put("size", f.length())
                put("modified", f.lastModified())
            })
        }
        return arr.toString()
    }

    @JavascriptInterface
    fun deleteFile(relative: String): Boolean {
        val file = repo.resolve(relative) ?: return false
        return repo.delete(file)
    }

    @JavascriptInterface
    fun fileExists(relative: String): Boolean {
        val f = repo.resolve(relative) ?: return false
        return f.exists()
    }

    companion object { private const val TAG = "FileInterface" }
}

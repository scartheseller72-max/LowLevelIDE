package com.example.lowlevelide.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.lowlevelide.bootstrap.BootstrapInstaller
import java.io.File

/**
 * Tiny wrapper around the home directory used by the file browser & editor.
 * Centralises path-traversal protection so call sites can't accidentally
 * write outside the sandbox.
 */
class FileRepository(private val context: Context) {

    val homeRoot: File by lazy { BootstrapInstaller.homeDir(context).apply { mkdirs() } }

    /** Resolve a relative path against home root, rejecting escapes. */
    fun resolve(relative: String): File? {
        val candidate = File(homeRoot, relative).canonicalFile
        return if (candidate.path.startsWith(homeRoot.canonicalPath)) candidate else null
    }

    fun list(dir: File): List<File> = dir.listFiles()
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        ?: emptyList()

    fun mkdir(parent: File, name: String): File? {
        val safe = sanitize(name) ?: return null
        val target = File(parent, safe)
        return if (target.mkdirs() || target.isDirectory) target else null
    }

    fun touch(parent: File, name: String): File? {
        val safe = sanitize(name) ?: return null
        val target = File(parent, safe)
        return if (target.exists() || target.createNewFile()) target else null
    }

    fun rename(file: File, newName: String): File? {
        val safe = sanitize(newName) ?: return null
        val parent = file.parentFile ?: return null
        val target = File(parent, safe)
        return if (file.renameTo(target)) target else null
    }

    fun delete(file: File): Boolean = file.deleteRecursively()

    /**
     * Import a file picked through the Storage Access Framework into [destDir]
     * (defaults to home root). Resolves the display name from the content provider,
     * de-duplicates against existing names, and streams the bytes in. Returns the
     * created file, or null on failure.
     */
    fun importFromUri(uri: Uri, destDir: File = homeRoot): File? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: "imported-${System.currentTimeMillis()}"
        val safe = sanitize(displayName) ?: return null
        val target = uniqueChild(destDir, safe)
        return try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    /** Append " (n)" before the extension until the name is free. */
    private fun uniqueChild(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun sanitize(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\")) return null
        return trimmed
    }
}

package com.example.lowlevelide.bootstrap

import android.content.Context
import com.example.lowlevelide.util.Logger
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Streams a tar.gz Alpine rootfs into <filesDir>/bootstrap/. No `tar` binary required.
 *
 * Steps:
 *  1. Source the tarball (bundled asset or network) via [BootstrapDownloader].
 *  2. Extract, honouring directory/symlink/permission entries where Android allows.
 *  3. Drop a `.installed` marker so the next launch is fast.
 *
 * Safe to call multiple times: a marker check makes subsequent calls a no-op.
 */
object BootstrapInstaller {

    private const val TAG = "BootstrapInstaller"
    const val MARKER_FILE = ".installed"

    fun bootstrapDir(context: Context): File = File(context.filesDir, "bootstrap")
    fun homeDir(context: Context): File = File(context.filesDir, "home")
    fun tmpDir(context: Context): File = File(context.filesDir, "tmp")
    fun isInstalled(context: Context): Boolean = File(bootstrapDir(context), MARKER_FILE).exists()

    @Throws(IOException::class)
    fun install(
        context: Context,
        downloader: BootstrapDownloader = BootstrapDownloader(context),
        onProgress: (AssetDownloader.Progress) -> Unit = {}
    ): Boolean {
        if (isInstalled(context)) {
            Logger.i(TAG, "Bootstrap already installed.")
            onProgress(AssetDownloader.Progress.Done)
            return true
        }

        homeDir(context).mkdirs()
        tmpDir(context).mkdirs()
        val target = bootstrapDir(context).apply { mkdirs() }

        val src = downloader.fetch(onProgress)
        try {
            src.cachedFile.inputStream().use { raw ->
                extractTarGz(BufferedInputStream(raw), target)
            }
            File(target, MARKER_FILE).createNewFile()
            Logger.i(TAG, "Bootstrap installed -> ${target.absolutePath}")
            return true
        } catch (e: IOException) {
            Logger.e(TAG, "Extraction failed", e)
            target.deleteRecursively()
            target.mkdirs()
            throw e
        } finally {
            src.cachedFile.delete()
        }
    }

    fun reset(context: Context) {
        bootstrapDir(context).deleteRecursively()
        Logger.i(TAG, "Bootstrap state cleared.")
    }

    @Throws(IOException::class)
    private fun extractTarGz(input: InputStream, dest: File) {
        val destCanonical = dest.canonicalFile
        GZIPInputStream(input).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    val outFile = File(dest, entry.name)
                    if (!isInside(destCanonical, outFile)) {
                        throw IOException("Tar entry escapes destination: ${entry.name}")
                    }

                    when {
                        entry.isDirectory -> outFile.mkdirs()
                        entry.isSymbolicLink -> writeSymlink(entry.linkName.orEmpty(), outFile, destCanonical)
                        else -> {
                            outFile.parentFile?.mkdirs()
                            // Re-validate after parent dirs exist: a previously-extracted symlink
                            // could otherwise redirect this write outside `dest`.
                            if (!isInside(destCanonical, outFile)) {
                                throw IOException("Tar entry resolves outside destination: ${entry.name}")
                            }
                            FileOutputStream(outFile).use { tar.copyTo(it) }
                            applyMode(outFile, entry.mode)
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    /** True when [child] canonically resolves to [root] itself or a path strictly under it. */
    private fun isInside(root: File, child: File): Boolean {
        val rootPath = root.canonicalPath
        val childPath = child.canonicalFile.path
        return childPath == rootPath || childPath.startsWith(rootPath + File.separator)
    }

    /**
     * Best-effort symlink. The link **target** is attacker-controlled (the rootfs is fetched
     * over the network), so we only create a real symlink when the resolved target stays inside
     * [dest]; otherwise we fall back to writing the target string as a plain file, which can't
     * redirect later writes out of the sandbox.
     */
    private fun writeSymlink(target: String, outFile: File, dest: File) {
        outFile.parentFile?.mkdirs()
        val resolvedTarget = if (target.startsWith("/")) File(target) else File(outFile.parentFile, target)
        val safeTarget = runCatching { isInside(dest, resolvedTarget) }.getOrDefault(false)
        if (!safeTarget) {
            // Don't materialize an escaping link; record the intended target inertly.
            outFile.writeText(target)
            return
        }
        val tryRealLink = runCatching {
            Runtime.getRuntime().exec(
                arrayOf("/system/bin/ln", "-sf", target, outFile.absolutePath)
            ).waitFor() == 0
        }.getOrDefault(false)
        if (!tryRealLink) outFile.writeText(target)
    }

    private fun applyMode(file: File, mode: Int) {
        // POSIX owner mode bits → Java's coarse setReadable/Writable/Executable.
        // ownerOnly=true: never widen extracted rootfs files to world access.
        if ((mode and 0b001_000_000) != 0) file.setExecutable(true, true)
        if ((mode and 0b010_000_000) != 0) file.setWritable(true, true)
        if ((mode and 0b100_000_000) != 0) file.setReadable(true, true)
        // Anything inside */bin or */sbin should be runnable regardless of header.
        val parent = file.parentFile?.name.orEmpty()
        if (parent == "bin" || parent == "sbin") file.setExecutable(true, true)
    }
}

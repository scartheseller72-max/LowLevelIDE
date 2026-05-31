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
        GZIPInputStream(input).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    val outFile = File(dest, entry.name)
                    if (!outFile.canonicalPath.startsWith(dest.canonicalPath)) {
                        throw IOException("Tar entry escapes destination: ${entry.name}")
                    }

                    when {
                        entry.isDirectory -> outFile.mkdirs()
                        entry.isSymbolicLink -> writeSymlink(entry.linkName.orEmpty(), outFile)
                        else -> {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { tar.copyTo(it) }
                            applyMode(outFile, entry.mode)
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    /** Best-effort: real symlink via /system/bin/ln, else a regular file containing the target. */
    private fun writeSymlink(target: String, outFile: File) {
        outFile.parentFile?.mkdirs()
        val tryRealLink = runCatching {
            Runtime.getRuntime().exec(
                arrayOf("/system/bin/ln", "-sf", target, outFile.absolutePath)
            ).waitFor() == 0
        }.getOrDefault(false)
        if (!tryRealLink) outFile.writeText(target)
    }

    private fun applyMode(file: File, mode: Int) {
        // POSIX mode bits → Java's coarse setReadable/Writable/Executable
        if ((mode and 0b001_000_000) != 0) file.setExecutable(true, false)
        if ((mode and 0b010_000_000) != 0) file.setWritable(true, false)
        if ((mode and 0b100_000_000) != 0) file.setReadable(true, false)
        // Anything inside */bin or */sbin should be runnable regardless of header.
        val parent = file.parentFile?.name.orEmpty()
        if (parent == "bin" || parent == "sbin") file.setExecutable(true, false)
    }
}

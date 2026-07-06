package com.example.lowlevelide.bootstrap

import android.content.Context
import android.system.Os
import android.system.OsConstants
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

    /** Hard cap on total extracted bytes — defends against a decompression bomb (network case). */
    private const val MAX_EXTRACT_BYTES = 512L * 1024 * 1024

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
        var extractedBytes = 0L
        GZIPInputStream(input).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    val outFile = File(dest, entry.name)
                    // Reject any entry whose *location* resolves outside dest. canonicalFile
                    // follows symlinks, so once an escaping link exists on disk this also catches
                    // a later write routed through it (e.g. link -> /elsewhere, then link/file).
                    if (!isInside(destCanonical, outFile)) {
                        throw IOException("Tar entry escapes destination: ${entry.name}")
                    }

                    when {
                        entry.isDirectory -> outFile.mkdirs()
                        // Links are preserved verbatim (see writeLink): their targets are resolved
                        // inside the PRoot guest, not against the host filesystem.
                        entry.isSymbolicLink -> writeLink(entry.linkName.orEmpty(), outFile)
                        entry.isLink -> writeLink(toGuestAbsolute(entry.linkName.orEmpty()), outFile)
                        else -> {
                            outFile.parentFile?.mkdirs()
                            // Re-validate after parent dirs exist: a previously-extracted symlink
                            // must not redirect this write outside `dest`.
                            if (!isInside(destCanonical, outFile)) {
                                throw IOException("Tar entry resolves outside destination: ${entry.name}")
                            }
                            extractedBytes += FileOutputStream(outFile).use { tar.copyTo(it) }
                            if (extractedBytes > MAX_EXTRACT_BYTES) {
                                throw IOException(
                                    "Bootstrap archive exceeds ${MAX_EXTRACT_BYTES / (1024 * 1024)} MiB; aborting"
                                )
                            }
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
     * Recreate a tar link (symbolic or hard) **verbatim**.
     *
     * The rootfs is consumed through PRoot, which resolves a link target inside the guest root at
     * runtime — e.g. an Alpine busybox applet `bin/ls -> /bin/busybox` must keep the absolute
     * target `/bin/busybox` so PRoot maps it to `<root>/bin/busybox`. Rewriting or refusing such
     * targets because `/bin/busybox` is not a real *host* path would turn the entire busybox
     * userland into inert text files. Extraction-time escape safety does NOT rely on refusing
     * links: every regular-file write is re-canonicalised and rejected if it resolves outside
     * `dest` (see extractTarGz), so a hostile `link -> /elsewhere` followed by a write through it
     * is caught there and by the per-entry location check.
     *
     * Uses [Os.symlink] (one syscall) instead of spawning `/system/bin/ln` per entry — a real
     * Alpine rootfs has hundreds of busybox symlinks, so this is also a large extraction speedup.
     */
    private fun writeLink(target: String, linkFile: File) {
        linkFile.parentFile?.mkdirs()
        runCatching { if (linkFile.exists() || isSymlink(linkFile)) linkFile.delete() }
        val created = runCatching { Os.symlink(target, linkFile.absolutePath); true }.getOrDefault(false)
        if (!created) {
            val viaLn = runCatching {
                Runtime.getRuntime()
                    .exec(arrayOf("/system/bin/ln", "-sf", target, linkFile.absolutePath))
                    .waitFor() == 0
            }.getOrDefault(false)
            // Last resort: record the target inertly rather than silently dropping the entry.
            if (!viaLn) linkFile.writeText(target)
        }
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode) }.getOrDefault(false)

    /** Tar hard-link targets are archive-root-relative; express them as guest-absolute paths. */
    private fun toGuestAbsolute(name: String): String = if (name.startsWith("/")) name else "/$name"

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

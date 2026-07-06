package com.example.lowlevelide.bootstrap

import android.content.Context
import com.example.lowlevelide.util.Logger
import java.io.File

/**
 * Resolves the PRoot binary and its loader.
 *
 * These ship *inside the APK* as native libraries under `jniLibs/<abi>/` (`libproot.so`,
 * `libproot_loader.so`). At install time Android copies the matching-ABI files into
 * [android.content.pm.ApplicationInfo.nativeLibraryDir] — the one app-owned location from which a
 * binary may be executed / mmap'd with PROT_EXEC on API 29+ (executing from `filesDir` is blocked
 * by SELinux since Android 10). Because the bytes live in the developer-signed APK, their integrity
 * is guaranteed by the APK signature: there is no download, no network URL, and no SHA-256 pin to
 * maintain (all of which the previous network-fetch implementation required).
 *
 * Only a *same-arch* loader is shipped (aarch64 loader for arm64, arm loader for armv7). The app
 * always installs an Alpine rootfs matching the host ABI, so PRoot never launches a foreign
 * (32-bit-on-64-bit) guest and therefore never needs the separate 32-bit loader / PROOT_LOADER_32.
 */
class PRootInstaller(private val context: Context) {

    private val libDir: File get() = File(context.applicationInfo.nativeLibraryDir)

    /** Absolute path to the packaged PRoot executable in nativeLibraryDir. */
    fun prootBinary(): File = File(libDir, LIB_PROOT)

    /** Absolute path to PRoot's loader (referenced via the PROOT_LOADER env var at launch). */
    fun loader(): File = File(libDir, LIB_LOADER)

    /** True when the packaged PRoot binary is present and executable for this device's ABI. */
    fun isInstalled(): Boolean = prootBinary().canExecute()

    /**
     * Compatibility shim for the onboarding flow. The binaries are packaged in the APK, so there is
     * nothing to fetch; this just verifies PRoot is runnable and reports progress uniformly with
     * the other install steps. Never throws — a missing lib degrades to the non-PRoot shell.
     */
    fun install(onProgress: (AssetDownloader.Progress) -> Unit = {}): Boolean {
        val ok = isInstalled()
        if (ok) {
            Logger.i(TAG, "PRoot available at ${prootBinary().absolutePath}")
            onProgress(AssetDownloader.Progress.Done)
        } else {
            Logger.w(TAG, "PRoot native library missing at ${prootBinary().absolutePath}")
            onProgress(AssetDownloader.Progress.Failed("proot native library not found"))
        }
        return ok
    }

    companion object {
        private const val TAG = "PRootInstaller"
        const val LIB_PROOT = "libproot.so"
        const val LIB_LOADER = "libproot_loader.so"
    }
}

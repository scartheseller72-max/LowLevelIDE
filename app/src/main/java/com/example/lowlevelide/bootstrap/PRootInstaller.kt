package com.example.lowlevelide.bootstrap

import android.content.Context
import com.example.lowlevelide.system.DeviceInfo
import com.example.lowlevelide.util.Logger
import java.io.File
import java.io.IOException

/**
 * Downloads and installs a static `proot` binary so we can chroot into the Alpine
 * rootfs without root.
 *
 * URLs point at the upstream PRoot project's prebuilt static binaries. If those
 * URLs change, override via [overrideUrl]. The user can also drop `proot-arm64`
 * or `proot-armv7` into `assets/bootstrap/` for offline mode.
 */
class PRootInstaller(
    private val context: Context,
    private val downloader: AssetDownloader = AssetDownloader()
) {

    fun prootBinary(): File = File(File(context.filesDir, "bootstrap"), "proot-bin")
    fun isInstalled(): Boolean = prootBinary().canExecute()

    @Throws(IOException::class)
    fun install(
        overrideUrl: String? = null,
        onProgress: (AssetDownloader.Progress) -> Unit = {}
    ) {
        if (isInstalled()) {
            onProgress(AssetDownloader.Progress.Done); return
        }
        val abi = DeviceInfo.primaryAbi()
        val assetName = when (abi) {
            DeviceInfo.Abi.ARM64 -> "proot-arm64"
            DeviceInfo.Abi.ARMV7 -> "proot-armv7"
            else -> throw IOException("Unsupported ABI for PRoot")
        }

        val out = prootBinary().apply { parentFile?.mkdirs() }

        // Bundled offline copy first.
        try {
            context.assets.open("bootstrap/$assetName").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out.setExecutable(true, false)
            Logger.i(TAG, "Used bundled $assetName")
            onProgress(AssetDownloader.Progress.Done)
            return
        } catch (_: IOException) { /* fall through to network */ }

        val url = overrideUrl ?: when (abi) {
            DeviceInfo.Abi.ARM64 -> "https://proot.gitlab.io/proot/bin/proot-aarch64"
            DeviceInfo.Abi.ARMV7 -> "https://proot.gitlab.io/proot/bin/proot-armv7l"
            else -> throw IOException("Unsupported")
        }
        downloader.download(url, out, expectedSha = null, onProgress = onProgress)
        out.setExecutable(true, false)
        Logger.i(TAG, "PRoot installed at ${out.absolutePath}")
    }

    companion object { private const val TAG = "PRootInstaller" }
}

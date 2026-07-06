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
        // A custom overrideUrl is developer-supplied, so only enforce the pin for the default URLs.
        val pinned = if (overrideUrl == null) PINNED_SHA256[abi].orEmpty() else ""
        if (pinned.isBlank()) {
            Logger.w(TAG, "No pinned SHA-256 for $assetName; integrity relies on TLS only")
        }
        downloader.download(url, out, expectedSha = pinned.ifBlank { null }, onProgress = onProgress)
        out.setExecutable(true, false)
        Logger.i(TAG, "PRoot installed at ${out.absolutePath}")
    }

    companion object {
        private const val TAG = "PRootInstaller"

        /**
         * SHA-256 of each prebuilt PRoot binary, pinned at build time.
         *
         * NOTE (verified 2026-07-06): the default download URLs below —
         *   https://proot.gitlab.io/proot/bin/proot-aarch64  and  .../proot-armv7l
         * currently return HTTP 404. That host only serves a single x86-64 desktop `proot`
         * (e_machine 0x3e), not Android ARM builds, so the network path does not work on real
         * devices as-is. Ship a known-good Android proot binary as a signed APK asset
         * (assets/bootstrap/proot-arm64, assets/bootstrap/proot-armv7 — install()'s bundled-asset
         * branch already prefers these), or point overrideUrl at a versioned, pinnable artifact.
         * Bundling is both the fix for the dead URLs and the strongest integrity guarantee, since
         * the bytes are then covered by the APK signature.
         *
         * To pin a binary once you have it:  sha256sum proot-arm64  → paste the lowercase hex
         * below. Blank = not pinned (integrity relies on TLS only, and install() logs a warning).
         */
        private val PINNED_SHA256 = mapOf(
            DeviceInfo.Abi.ARM64 to "", // TODO(security): sha256 of the shipped arm64 proot binary
            DeviceInfo.Abi.ARMV7 to ""  // TODO(security): sha256 of the shipped armv7 proot binary
        )
    }
}

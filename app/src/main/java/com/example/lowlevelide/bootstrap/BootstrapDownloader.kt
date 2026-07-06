package com.example.lowlevelide.bootstrap

import android.content.Context
import com.example.lowlevelide.system.DeviceInfo
import com.example.lowlevelide.util.Logger
import java.io.File
import java.io.IOException

/**
 * Decides where the Alpine minirootfs tarball comes from and stages it for
 * [BootstrapInstaller] to extract:
 *
 *  1. If the user bundled a tarball under `assets/bootstrap/<name>`, use that
 *     (offline mode). Copy it to the cache dir and return.
 *  2. Otherwise download from Alpine's CDN.
 *
 * Default version constant is the most recent stable line as of writing. Bump
 * when you want to ship a newer Alpine.
 */
class BootstrapDownloader(
    private val context: Context,
    private val downloader: AssetDownloader = AssetDownloader()
) {

    data class Source(val url: String, val sha256: String?, val assetName: String, val cachedFile: File)

    @Throws(IOException::class)
    fun fetch(onProgress: (AssetDownloader.Progress) -> Unit = {}): Source {
        val abi = DeviceInfo.primaryAbi()
        val arch = when (abi) {
            DeviceInfo.Abi.ARM64 -> "aarch64"
            DeviceInfo.Abi.ARMV7 -> "armv7"
            DeviceInfo.Abi.OTHER -> throw IOException("Unsupported ABI for Alpine bootstrap")
        }

        val assetName = when (abi) {
            DeviceInfo.Abi.ARM64 -> "alpine-arm64.tar.gz"
            DeviceInfo.Abi.ARMV7 -> "alpine-armv7.tar.gz"
            else -> throw IOException("unsupported")
        }

        // Prefer bundled asset if present.
        try {
            context.assets.open("bootstrap/$assetName").use { input ->
                val out = File(context.cacheDir, assetName)
                out.outputStream().use { input.copyTo(it) }
                Logger.i(TAG, "Used bundled asset bootstrap/$assetName")
                onProgress(AssetDownloader.Progress.Done)
                return Source(url = "asset://bootstrap/$assetName", sha256 = null, assetName, out)
            }
        } catch (_: IOException) {
            Logger.i(TAG, "No bundled bootstrap; downloading from network")
        }

        val url = "$BASE_URL/v$ALPINE_VER_MAJOR/releases/$arch/" +
            "alpine-minirootfs-${ALPINE_VERSION}-${arch}.tar.gz"
        val out = File(context.cacheDir, assetName)
        val pinned = PINNED_SHA256[arch].orEmpty()
        if (pinned.isBlank()) {
            // TLS still protects the transport, but without a compile-time pin the app is trusting
            // whatever the mirror/CDN serves. Pin the digest (see PINNED_SHA256) to close that gap.
            Logger.w(TAG, "No pinned SHA-256 for Alpine $ALPINE_VERSION/$arch; integrity relies on TLS only")
        }
        downloader.download(url = url, dest = out, expectedSha = pinned.ifBlank { null }, onProgress = onProgress)
        return Source(url, pinned.ifBlank { null }, assetName, out)
    }

    companion object {
        private const val TAG = "BootstrapDownloader"

        // Bump as new Alpine versions ship. The CDN keeps multiple releases live.
        const val ALPINE_VERSION = "3.20.3"
        private const val ALPINE_VER_MAJOR = "3.20"
        private const val BASE_URL = "https://dl-cdn.alpinelinux.org/alpine"

        /**
         * SHA-256 of each Alpine minirootfs tarball, pinned at build time. This is the real
         * integrity anchor: it ties the download to a value baked into the (developer-signed) APK,
         * so a compromised mirror/CDN or a TLS bypass cannot swap in a different rootfs. A checksum
         * fetched from the same server at runtime would NOT help — an attacker who can serve a bad
         * tarball can serve a matching bad checksum; the value must come from the signed binary.
         *
         * Refresh whenever [ALPINE_VERSION] changes. Obtain each digest out of band from Alpine's
         * published sidecar, e.g.:
         *   curl -fsSL $BASE_URL/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz.sha256
         * Blank = not pinned (integrity then relies on TLS only, and the installer logs a warning).
         */
        private val PINNED_SHA256 = mapOf(
            // Verified 2026-07-06 against Alpine's published .tar.gz.sha256 sidecars AND by
            // recomputing the digest of the downloaded artifact. Refresh when ALPINE_VERSION bumps.
            "aarch64" to "041fa34a81788242df9e78fa69b97ab45b8ec47ddbf88864755610414a7bf3de",
            "armv7" to "ea8823fb4c4cf5f71f1d180e47904fb36ae74d3ded06c980230116b129fc5f07"
        )
    }
}

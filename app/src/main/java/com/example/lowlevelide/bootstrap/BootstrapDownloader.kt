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
        downloader.download(url = url, dest = out, expectedSha = null, onProgress = onProgress)
        return Source(url, null, assetName, out)
    }

    companion object {
        private const val TAG = "BootstrapDownloader"

        // Bump as new Alpine versions ship. The CDN keeps multiple releases live.
        const val ALPINE_VERSION = "3.20.3"
        private const val ALPINE_VER_MAJOR = "3.20"
        private const val BASE_URL = "https://dl-cdn.alpinelinux.org/alpine"
    }
}

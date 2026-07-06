package com.example.lowlevelide.bootstrap

import com.example.lowlevelide.util.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based downloader with progress callbacks.
 * Works for both small (CodeMirror JS, ~50KB) and medium (Alpine rootfs, ~3MB) downloads.
 *
 * Caller passes a SHA256 expectedSha (lowercase hex) — we verify after download and
 * delete the file on mismatch. No fancy retry; the onboarding UI gives the user a
 * "Retry" button.
 */
class AssetDownloader(
    private val client: OkHttpClient = defaultClient()
) {

    sealed class Progress {
        object Start : Progress()
        data class Bytes(val read: Long, val total: Long) : Progress()
        object Done : Progress()
        data class Failed(val reason: String) : Progress()
    }

    @Throws(IOException::class)
    fun download(
        url: String,
        dest: File,
        expectedSha: String? = null,
        onProgress: (Progress) -> Unit = {}
    ) {
        Logger.i(TAG, "Download: $url -> ${dest.absolutePath}")
        onProgress(Progress.Start)
        dest.parentFile?.mkdirs()

        // Only ever fetch over TLS. The bundled-asset paths bypass download() entirely, so every
        // URL that reaches here should already be https; enforce it so a mis-set overrideUrl (or a
        // future dynamic URL) can't silently fetch executable content over plaintext.
        if (!url.startsWith("https://", ignoreCase = true)) {
            onProgress(Progress.Failed("insecure URL"))
            throw IOException("Refusing non-HTTPS asset URL: $url")
        }

        // Any failure (HTTP error, mid-stream network drop, checksum mismatch) must leave no
        // partial file behind: callers use File.exists() to decide whether an asset is already
        // installed, so a truncated download would otherwise masquerade as a valid one and
        // silently break the editor/bootstrap on the next launch.
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for $url")
                }
                val body = response.body ?: throw IOException("Empty body for $url")
                val total = body.contentLength()
                val source = body.source()

                dest.sink().buffer().use { sink ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = source.read(buf)
                        if (n == -1) break
                        sink.write(buf, 0, n)
                        read += n
                        onProgress(Progress.Bytes(read, total))
                    }
                }
            }

            if (expectedSha != null) {
                val actual = sha256(dest)
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    throw IOException("SHA256 mismatch for ${dest.name}: expected $expectedSha got $actual")
                }
            }
        } catch (e: IOException) {
            runCatching { if (dest.exists()) dest.delete() }
            onProgress(Progress.Failed(e.message ?: "download failed"))
            throw e
        }

        onProgress(Progress.Done)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "AssetDownloader"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            // Follow same-protocol redirects, but never HTTPS -> HTTP: a hostile redirect must
            // not be able to downgrade a security-sensitive download to plaintext.
            .followSslRedirects(false)
            .build()
    }
}

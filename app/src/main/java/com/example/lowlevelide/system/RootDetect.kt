package com.example.lowlevelide.system

import java.io.File

/**
 * Multi-path su detection with caching. Considered "rooted" only if a known su binary
 * exists AND `id -u` reports 0 (truly elevated). Apps like Magisk Hide can defeat this,
 * which is the user's prerogative.
 */
object RootDetect {

    private val SU_CANDIDATES = listOf(
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su",
        "/data/local/tmp/su"
    )

    @Volatile private var cached: Boolean? = null

    fun suBinary(): String? = SU_CANDIDATES.firstOrNull { File(it).exists() }

    fun isRootAvailable(refresh: Boolean = false): Boolean {
        if (!refresh) cached?.let { return it }
        val result = try {
            val su = suBinary() ?: return run { cached = false; false }
            val proc = Runtime.getRuntime().exec(arrayOf(su, "-c", "id -u"))
            val finished = proc.waitFor() == 0
            val output = proc.inputStream.bufferedReader().readText().trim()
            finished && output == "0"
        } catch (e: Exception) {
            false
        }
        cached = result
        return result
    }
}

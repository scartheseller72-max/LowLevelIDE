package com.example.lowlevelide.util

import android.util.Log
import com.example.lowlevelide.BuildConfig

/**
 * Tiny wrapper over android.util.Log so we have one place to enable/disable logging
 * (useful for release builds).
 */
object Logger {
    private const val GLOBAL_TAG = "LowLevelIDE"
    private val verbose = BuildConfig.DEBUG

    fun d(tag: String, msg: String) { if (verbose) Log.d("$GLOBAL_TAG/$tag", msg) }
    fun i(tag: String, msg: String) = Log.i("$GLOBAL_TAG/$tag", msg)
    fun w(tag: String, msg: String, t: Throwable? = null) = Log.w("$GLOBAL_TAG/$tag", msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = Log.e("$GLOBAL_TAG/$tag", msg, t)
}

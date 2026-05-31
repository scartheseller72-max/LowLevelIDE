package com.example.lowlevelide.util

import android.content.Context
import android.os.Build
import com.example.lowlevelide.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught-exception handler.
 *
 * On any unhandled throwable we persist a timestamped crash report to
 * `<filesDir>/crash/` (device + build metadata + full stack trace) and then
 * delegate to the previously installed handler so the platform still shows its
 * normal "app stopped" dialog and the process is torn down cleanly.
 *
 * The Settings screen surfaces the latest report and a share action, so users can
 * forward a crash to the issue tracker without adb.
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val DIR = "crash"
    private const val MAX_REPORTS = 20

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, thread, throwable) }
                .onFailure { Logger.e(TAG, "Failed to persist crash report", it) }
            // Chain to the platform/default handler so the OS still does its thing.
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                }
        }
    }

    fun crashDir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun reports(context: Context): List<File> =
        crashDir(context).listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun latestReport(context: Context): File? = reports(context).firstOrNull()

    fun clear(context: Context) {
        reports(context).forEach { it.delete() }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir(context)
        trim(dir)
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(dir, "crash-$ts.log")
        val stack = StringWriter().also { sw -> throwable.printStackTrace(PrintWriter(sw)) }.toString()
        file.writeText(buildString {
            appendLine("LowLevelIDE crash report")
            appendLine("========================")
            appendLine("Time:        ${Date()}")
            appendLine("Thread:      ${thread.name} (id=${thread.id})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type:  ${BuildConfig.BUILD_TYPE}")
            appendLine("Android:     ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device:      ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABIs:        ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            appendLine(stack)
        })
        Logger.e(TAG, "Crash captured -> ${file.absolutePath}")
    }

    /** Keep only the most recent [MAX_REPORTS] logs. */
    private fun trim(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: return
        if (files.size < MAX_REPORTS) return
        files.sortedByDescending { it.lastModified() }
            .drop(MAX_REPORTS - 1)
            .forEach { it.delete() }
    }
}

package com.example.lowlevelide.terminal

import android.content.Context
import com.example.lowlevelide.bootstrap.BootstrapInstaller
import com.example.lowlevelide.bootstrap.PRootInstaller
import com.example.lowlevelide.system.RootDetect
import com.example.lowlevelide.util.Logger
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

/**
 * Resolves how to launch a shell given runtime conditions and user prefs:
 *
 *   1. Root preferred + available -> `su -c <bootstrap>/bin/bash`
 *   2. PRoot preferred + binary present -> `proot -r <bootstrap> -b /proc -b /dev -b /sys ...`
 *   3. Fallback -> `/system/bin/sh -i` with PATH/LD_LIBRARY_PATH augmented.
 *
 * Caller passes prefs in [Options]. Provider is stateless beyond detection caches.
 */
class TerminalSessionProvider(private val context: Context) {

    data class Options(
        val preferRoot: Boolean = true,
        val preferProot: Boolean = true,
        val initialCommand: String? = null
    )

    fun createSession(client: TerminalSessionClient, options: Options = Options()): TerminalSession {
        val bootstrapDir = BootstrapInstaller.bootstrapDir(context)
        val homeDir = BootstrapInstaller.homeDir(context).apply { mkdirs() }
        val rooted = options.preferRoot && RootDetect.isRootAvailable()
        val prootBin = PRootInstaller(context).prootBinary()
        val prootAvailable = options.preferProot && prootBin.canExecute()
        val (mode, exec, args) = pickShell(rooted, prootAvailable, bootstrapDir, prootBin, options.initialCommand)
        Logger.i(TAG, "Spawning terminal in mode=$mode exec=$exec args=${args.toList()}")

        val env = ShellEnvironment.build(context, mapOf("LOWLEVEL_MODE" to mode))
        return TerminalSession(
            exec,
            homeDir.absolutePath,
            args,
            env,
            /* terminalTranscriptRows = */ 5_000,
            client
        )
    }

    private fun pickShell(
        rooted: Boolean,
        prootAvailable: Boolean,
        bootstrap: File,
        prootBin: File,
        initialCommand: String?
    ): Triple<String, String, Array<String>> {
        val bootstrapBash = File(bootstrap, "bin/bash")
        val bootstrapSh = File(bootstrap, "bin/sh")
        val targetShell = when {
            bootstrapBash.canExecute() -> bootstrapBash.absolutePath
            bootstrapSh.canExecute() -> bootstrapSh.absolutePath
            else -> "/system/bin/sh"
        }

        return when {
            rooted -> {
                val su = RootDetect.suBinary() ?: "/system/bin/sh"
                // `su -c` takes a single shell string, so quote both the shell path and the
                // (currently always-null) initial command. Without this, any future caller that
                // wires user- or file-derived text into initialCommand could inject commands via
                // embedded quotes / $(...) / backticks into the elevated shell.
                val script = if (initialCommand != null)
                    "${shQuote(targetShell)} -c ${shQuote(initialCommand)}"
                else shQuote(targetShell)
                Triple("root", su, arrayOf("-c", script))
            }
            prootAvailable -> {
                // proot -r <bootstrap> -0 (fake-root) -b /proc -b /dev -b /sys -b /sdcard -w /root /bin/sh
                val argsList = mutableListOf(
                    "-r", bootstrap.absolutePath,
                    "-0",
                    "-b", "/proc",
                    "-b", "/dev",
                    "-b", "/sys",
                    "-b", "/sdcard",
                    "-w", "/root",
                    "/bin/sh"
                )
                if (initialCommand != null) {
                    argsList.add("-c"); argsList.add(initialCommand)
                } else {
                    argsList.add("-i")
                }
                Triple("proot", prootBin.absolutePath, argsList.toTypedArray())
            }
            else -> {
                // Direct execution: shell with augmented env, no chroot.
                val argsList = if (initialCommand != null) arrayOf("-c", initialCommand)
                else arrayOf("-i")
                Triple("direct", "/system/bin/sh", argsList)
            }
        }
    }

    /** Single-quote a value for safe interpolation into a `sh -c` string. */
    private fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    companion object { private const val TAG = "TerminalSessionProvider" }
}

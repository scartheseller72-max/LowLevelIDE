package com.example.lowlevelide.terminal

import android.content.Context
import android.os.Build
import com.example.lowlevelide.bootstrap.BootstrapInstaller
import java.io.File

/**
 * Builds the env-var array passed to the shell process. Centralised because the
 * exact PATH/LD_LIBRARY_PATH details matter to whether GCC etc. resolve.
 */
object ShellEnvironment {

    fun build(context: Context, extra: Map<String, String> = emptyMap()): Array<String> {
        val bootstrap = BootstrapInstaller.bootstrapDir(context)
        val home = BootstrapInstaller.homeDir(context)
        val tmp = BootstrapInstaller.tmpDir(context).apply { mkdirs() }

        val bin = File(bootstrap, "bin").absolutePath
        val sbin = File(bootstrap, "sbin").absolutePath
        val usrBin = File(bootstrap, "usr/bin").absolutePath
        val usrSbin = File(bootstrap, "usr/sbin").absolutePath
        val lib = File(bootstrap, "lib").absolutePath
        val usrLib = File(bootstrap, "usr/lib").absolutePath

        val map = LinkedHashMap<String, String>()
        map["HOME"] = home.absolutePath
        map["PWD"] = home.absolutePath
        map["TERM"] = "xterm-256color"
        map["TMPDIR"] = tmp.absolutePath
        map["LANG"] = "en_US.UTF-8"
        map["LC_ALL"] = "en_US.UTF-8"
        map["PATH"] = "$bin:$sbin:$usrBin:$usrSbin:/system/bin:/system/xbin"
        map["LD_LIBRARY_PATH"] = "$lib:$usrLib"
        map["ABI"] = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        map["BOOTSTRAP"] = bootstrap.absolutePath
        map["PS1"] = "lowlevel:\\w\\$ "
        map.putAll(extra)
        return map.entries.map { "${it.key}=${it.value}" }.toTypedArray()
    }
}

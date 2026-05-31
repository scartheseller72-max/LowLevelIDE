package com.example.lowlevelide.system

import android.os.Build

/**
 * Tiny helper for the few places we need to make ABI-aware decisions
 * (BootstrapInstaller, PRootInstaller). Kept in one file to avoid scattering
 * Build.SUPPORTED_ABIS calls.
 */
object DeviceInfo {

    enum class Abi(val tag: String) {
        ARM64("arm64"),
        ARMV7("armv7"),
        OTHER("other");
    }

    fun primaryAbi(): Abi {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.any { it == "arm64-v8a" } -> Abi.ARM64
            abis.any { it == "armeabi-v7a" } -> Abi.ARMV7
            else -> Abi.OTHER
        }
    }

    fun isArm64(): Boolean = primaryAbi() == Abi.ARM64
    fun isArmv7(): Boolean = primaryAbi() == Abi.ARMV7
}

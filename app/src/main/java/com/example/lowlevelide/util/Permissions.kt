package com.example.lowlevelide.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Handle POST_NOTIFICATIONS permission (required on Android 13+ for the foreground service
 * notification to actually display).
 *
 * The other declared permissions either don't need runtime requests (INTERNET) or are
 * deprecated paths the user can grant manually if they want full storage access.
 */
class NotificationPermissionHelper(private val activity: ComponentActivity) {

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result intentionally ignored — UX is informational either way */ }

    fun ensureGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val state = ContextCompat.checkSelfPermission(activity, permission)
        if (state != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(permission)
        }
    }
}

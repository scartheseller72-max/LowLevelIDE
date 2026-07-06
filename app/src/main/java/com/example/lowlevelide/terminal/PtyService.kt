package com.example.lowlevelide.terminal

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.lowlevelide.App
import com.example.lowlevelide.MainActivity
import com.example.lowlevelide.R

/**
 * Foreground service that owns the [TerminalSessionManager]. Keeping the manager out
 * of the Activity means terminals survive Activity recreation (rotation, themes,
 * background pressure). The notification is required by Android since API 26.
 */
class PtyService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): PtyService = this@PtyService
    }

    private val binder = LocalBinder()
    val sessionManager by lazy { TerminalSessionManager(applicationContext) }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(sessionManager.sessions.value.size)
        startForeground(NOTIFICATION_ID, notification)
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                sessionManager.finishAll()
                stopSelf()
            }
        }
        // Terminal sessions are backed by live child processes that cannot survive a process
        // kill, so resurrecting the service (START_STICKY, null intent) would only leave a stale
        // "0 sessions active" foreground notification. Don't auto-restart.
        return START_NOT_STICKY
    }

    fun refreshNotification() {
        val count = sessionManager.sessions.value.size
        if (count == 0) {
            stopSelf()
            return
        }
        val notif = buildNotification(count)
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notif)
    }

    override fun onDestroy() {
        sessionManager.finishAll()
        super.onDestroy()
    }

    private fun buildNotification(activeCount: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAll = PendingIntent.getService(
            this, 1,
            Intent(this, PtyService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_TERMINALS)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_running, activeCount))
            .setSmallIcon(R.drawable.ic_terminal)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_close, getString(R.string.notification_action_stop), stopAll)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_STOP_ALL = "com.example.lowlevelide.ACTION_STOP_ALL"
        private const val NOTIFICATION_ID = 0xC0DE

        fun start(context: Context) {
            val intent = Intent(context, PtyService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PtyService::class.java))
        }
    }
}

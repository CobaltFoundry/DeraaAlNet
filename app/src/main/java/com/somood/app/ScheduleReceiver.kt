package com.somood.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "com.somood.app.SCHEDULE_START"
        const val ACTION_STOP = "com.somood.app.SCHEDULE_STOP"
        const val ACTION_PRE_NOTIFY = "com.somood.app.PRE_NOTIFY"
        private const val PRE_CHANNEL = "somood_pre"
        private const val PRE_ID = 2
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_PRE_NOTIFY -> showPreNotification(context)
            ACTION_START -> {
                if (VpnService.prepare(context) == null) {
                    val i = Intent(context, BlockVpnService::class.java).apply {
                        action = BlockVpnService.ACTION_START
                    }
                    context.startForegroundService(i)
                }
                ScheduleHelper.scheduleDaily(context) // re-schedule next day
            }
            ACTION_STOP -> {
                val i = Intent(context, BlockVpnService::class.java).apply {
                    action = BlockVpnService.ACTION_STOP
                }
                context.startService(i)
                ScheduleHelper.scheduleDaily(context)
            }
        }
    }

    private fun showPreNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PRE_CHANNEL, "تنبيهات", NotificationManager.IMPORTANCE_HIGH
            )
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val pending = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, PRE_CHANNEL)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.pre_notify))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(PRE_ID, notif)
    }
}

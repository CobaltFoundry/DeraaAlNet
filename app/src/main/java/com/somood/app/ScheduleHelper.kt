package com.somood.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ScheduleHelper {

    private const val REQ_STOP = 201

    fun scheduleStopAfter(context: Context, minutes: Int) {
        if (minutes <= 0) {
            cancelStop(context)
            DurationHelper.clearSessionEnd(context)
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        DurationHelper.setSessionEndAt(context, endAt)

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = stopPending(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, endAt, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        }
    }

    fun cancelStop(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(stopPending(context))
        DurationHelper.clearSessionEnd(context)
    }

    private fun stopPending(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_STOP
        }
        return PendingIntent.getBroadcast(
            context, REQ_STOP, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleDaily(context: Context) {
    }

    fun canScheduleExact(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
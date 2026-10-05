package com.somood.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object ScheduleHelper {

    private const val TAG = "ScheduleHelper"
    private const val REQ_START = 100
    private const val REQ_STOP = 101
    private const val REQ_PRE = 102

    fun scheduleDaily(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelAll(context, am)

            scheduleExact(context, am, 17, 50, REQ_PRE, ScheduleReceiver.ACTION_PRE_NOTIFY)
            scheduleExact(context, am, 18, 0, REQ_START, ScheduleReceiver.ACTION_START)
            scheduleExact(context, am, 21, 0, REQ_STOP, ScheduleReceiver.ACTION_STOP)

            Log.d(TAG, "Daily schedule set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule", e)
        }
    }

    private fun cancelAll(context: Context, am: AlarmManager) {
        listOf(REQ_PRE, REQ_START, REQ_STOP).forEach { req ->
            val intent = Intent(context, ScheduleReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, req, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun scheduleExact(
        context: Context,
        am: AlarmManager,
        hour: Int,
        minute: Int,
        requestCode: Int,
        action: String
    ) {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            this.action = action
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            Log.w(TAG, "Exact alarm not allowed, used inexact", e)
        }
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
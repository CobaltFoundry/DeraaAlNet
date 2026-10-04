package com.somood.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ScheduleHelper {

    private const val REQ_START = 100
    private const val REQ_STOP = 101
    private const val REQ_PRE = 102

    fun scheduleDaily(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        scheduleExact(context, am, 17, 50, REQ_PRE, ScheduleReceiver.ACTION_PRE_NOTIFY)
        scheduleExact(context, am, 18, 0, REQ_START, ScheduleReceiver.ACTION_START)
        scheduleExact(context, am, 21, 0, REQ_STOP, ScheduleReceiver.ACTION_STOP)
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
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }
}

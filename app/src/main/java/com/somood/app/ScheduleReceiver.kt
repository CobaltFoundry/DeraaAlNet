package com.somood.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP = "com.somood.app.ACTION_STOP"
        const val ACTION_START = "com.somood.app.ACTION_START"
        const val ACTION_PRE_NOTIFY = "com.somood.app.ACTION_PRE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> {
                val i = Intent(context, BlockVpnService::class.java).apply {
                    action = BlockVpnService.ACTION_STOP
                }
                context.startService(i)
            }
            // START و PRE مش مستخدمين في الوضع الجديد
        }
    }
}
package com.somood.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * لو النت كان مقطوع وقت ما الجهاز اتقفل أو التطبيق اتحدّث، بنرجّع القطع تاني.
 * (القطع بيفضل لحد ما المستخدم هو اللي يوقفه.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!SessionState.isActive(context)) return

        // صلاحية الـ VPN اتسحبت → مينفعش نكمّل
        if (VpnService.prepare(context) != null) {
            SessionState.end(context)
            return
        }
        if (action == Intent.ACTION_BOOT_COMPLETED) SessionState.restartClock(context)

        try {
            BlockVpnService.start(context)
        } catch (_: Exception) {
            // لو النظام منع التشغيل من الخلفية، MainActivity هتستأنف عند فتح التطبيق
        }
    }
}

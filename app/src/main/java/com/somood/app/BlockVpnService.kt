package com.somood.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.FileInputStream
import kotlin.concurrent.thread

/**
 * بيقطع النت عن طريق VPN محلي: كل الترافيك (IPv4 + IPv6) بيتوجّه للخدمة وبيتم رميه.
 * التطبيقات في القائمة البيضاء + التطبيق نفسه مستثنيين.
 *
 * الحالة (شغال/واقف + وقت البداية) في [SessionState] عشان تفضل صح بعد قتل العملية أو الريبوت.
 */
class BlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.somood.app.START"
        const val ACTION_STOP = "com.somood.app.STOP"
        /** إعادة بناء الـ VPN بالقائمة البيضاء الجديدة من غير ما الجلسة تتقطع. */
        const val ACTION_RESTART = "com.somood.app.RESTART"

        const val CHANNEL_ID = "somood_vpn"
        const val NOTIF_ID = 42

        @Volatile
        var isRunning = false

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }

        fun restart(context: Context) {
            context.startService(intent(context, ACTION_RESTART))
        }

        private fun intent(context: Context, action: String) =
            Intent(context, BlockVpnService::class.java).setAction(action)
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBlock()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                enterForeground()
                SessionState.begin(this)
                applyBlock()
            }

            ACTION_RESTART -> {
                if (!SessionState.isActive(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                enterForeground()
                applyBlock()
            }

            // intent == null: النظام بيعيد تشغيل الخدمة بعد ما العملية اتقتلت
            else -> {
                if (!SessionState.isActive(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                enterForeground()
                applyBlock()
            }
        }
        return START_STICKY
    }

    /** بيبني الواجهة الجديدة الأول وبعدين يقفل القديمة، فمفيش لحظة النت بيفتح فيها. */
    private fun applyBlock() {
        val fresh = try {
            buildInterface()
        } catch (_: Exception) {
            null
        }
        if (fresh == null) {
            // الصلاحية اتسحبت أو النظام رفض — نقفل الجلسة بدل ما نبان شغالين وإحنا مش شغالين
            stopBlock()
            return
        }
        val old = vpnInterface
        vpnInterface = fresh
        try {
            old?.close()
        } catch (_: Exception) {
        }
        isRunning = true
        drain(fresh)
    }

    private fun buildInterface(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("درع النت")
            .setMtu(1500)
            .setBlocking(true)
            // IPv4
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            // IPv6 — من غيرها الترافيك ده بيعدّي من الـ VPN ومبيتقطعش
            .addAddress("fd00::2", 128)
            .addRoute("::", 0)

        for (pkg in WhitelistHelper.getPackages(this)) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {
                // التطبيق اتشال من الجهاز
            }
        }
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }
        return builder.establish()
    }

    /** بيقرا الـ packets ويرميها. بيقف لوحده لما الواجهة تتقفل. */
    private fun drain(pfd: ParcelFileDescriptor) {
        thread(name = "vpn-drain", isDaemon = true) {
            try {
                val input = FileInputStream(pfd.fileDescriptor)
                val buffer = ByteArray(32767)
                while (input.read(buffer) >= 0) {
                    // drop
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun stopBlock() {
        val startedAt = SessionState.end(this)
        if (startedAt > 0L) LogHelper.record(this, startedAt, System.currentTimeMillis())
        closeInterface()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeInterface() {
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
    }

    /** المستخدم فصل الـ VPN من إعدادات النظام أو تطبيق VPN تاني خده. */
    override fun onRevoke() {
        stopBlock()
    }

    override fun onDestroy() {
        // مش بنقفل الجلسة هنا: لو النظام هو اللي قتل الخدمة، لازم الحالة تفضل "شغالة" عشان تتستأنف.
        closeInterface()
        super.onDestroy()
    }

    private fun enterForeground() {
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "درع النت", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // الإيقاف من الإشعار بيفتح التطبيق وبيطلب نفس تأكيد القفل — مش بيوقف مباشرةً
        val stop = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_REQUEST_STOP, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val startedAt = SessionState.startedAt(this).takeIf { it > 0L } ?: System.currentTimeMillis()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("درع النت")
            .setContentText("النت مقطوع")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(0, "إيقاف", stop)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setOngoing(true)
            .build()
    }
}

package com.somood.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class BlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.somood.app.START"
        const val ACTION_STOP = "com.somood.app.STOP"
        const val CHANNEL_ID = "somood_vpn"
        const val NOTIF_ID = 42
        @Volatile var isRunning = false
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBlock()
                return START_NOT_STICKY
            }
            else -> startBlock()
        }
        return START_STICKY
    }

    private fun startBlock() {
        if (isRunning) return
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        val builder = Builder()
            .setSession("درع النت")
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)

        // التطبيقات المستثناة = تعدّي النت عادي (خارج الـ VPN)
        val whitelist = WhitelistHelper.getPackages(this)
        for (pkg in whitelist) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {
            }
        }
        // تطبيقنا نفسه برّه الـ VPN
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            stopSelf()
            return
        }

        isRunning = true
        running = true
        LogHelper.startSession(this)

        // قراءة ورمي الباكيتات (قطع النت)
        val fd = vpnInterface!!
        thread(name = "vpn-loop", isDaemon = true) {
            val input = FileInputStream(fd.fileDescriptor)
            val buffer = ByteBuffer.allocate(32767)
            try {
                while (running) {
                    buffer.clear()
                    val len = input.read(buffer.array())
                    if (len <= 0) break
                    // drop
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun stopBlock() {
        running = false
        isRunning = false
        LogHelper.endSession(this)
        DurationHelper.clearSessionEnd(this)
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "وضع الصمود", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("درع النت")
            .setContentText("وضع الصمود نشط — النت مقطوع")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopBlock()
        super.onDestroy()
    }
}
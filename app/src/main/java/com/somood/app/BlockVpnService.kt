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
import java.io.IOException
import java.nio.ByteBuffer

class BlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.somood.app.START"
        const val ACTION_STOP = "com.somood.app.STOP"
        const val CHANNEL_ID = "somood_vpn"
        const val NOTIF_ID = 1

        @Volatile
        var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var thread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBlocking()
            ACTION_STOP -> stopBlocking()
        }
        return START_STICKY
    }

    private fun startBlocking() {
        if (isRunning) return

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val builder = Builder()
            .setSession("درع النت")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setMtu(1500)
            .setBlocking(true)

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                stopSelf()
                return
            }
            isRunning = true
            LogHelper.startSession(this)

            thread = Thread {
                val buffer = ByteBuffer.allocate(32767)
                val input = FileInputStream(vpnInterface!!.fileDescriptor)
                try {
                    while (isRunning && !Thread.interrupted()) {
                        buffer.clear()
                        val length = input.read(buffer.array())
                        if (length > 0) {
                            // drop all packets → internet blocked
                        }
                    }
                } catch (e: IOException) {
                    // expected on close
                }
            }.also { it.start() }
        } catch (e: Exception) {
            stopBlocking()
        }
    }

    private fun stopBlocking() {
        isRunning = false
        thread?.interrupt()
        thread = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        LogHelper.endSession(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopBlocking()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "وضع الصمود",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}

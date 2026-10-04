[10/4/2026 4:39 PM] EMad ELdin: package com.somood.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.somood.app.databinding.ActivityMainBinding
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }

    private val VPN_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ScheduleHelper.scheduleDaily(this)
        LogHelper.init(this)

        binding.btnToggle.setOnClickListener { onToggleClicked() }
        binding.btnLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        binding.btnShare.setOnClickListener {
            val url = getString(R.string.telegram_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun onToggleClicked() {
        if (BlockVpnService.isRunning) {
            showStopConfirm()
        } else {
            requestVpnPermission()
        }
    }

    private fun showStopConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(R.string.confirm_message)
            .setPositiveButton(R.string.confirm_yes) { _, _ ->
                stopVpn()
            }
            .setNegativeButton(R.string.confirm_no, null)
            .show()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            startVpn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startVpn()
        } else if (requestCode == VPN_REQUEST_CODE) {
            Toast.makeText(this, R.string.vpn_permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    private fun startVpn() {
        val intent = Intent(this, BlockVpnService::class.java).apply {
            action = BlockVpnService.ACTION_START
        }
        startForegroundService(intent)
        updateUI()
    }

    private fun stopVpn() {
        val intent = Intent(this, BlockVpnService::class.java).apply {
            action = BlockVpnService.ACTION_STOP
        }
        startService(intent)
        updateUI()
    }

    private fun updateUI() {
        val isActive = BlockVpnService.isRunning
        val minutesLeft = getMinutesLeftInWindow()

        binding.statusText.text = if (isActive) getString(R.string.status_active) else getString(R.string.status_inactive)
        binding.statusText.setTextColor(
            getColor(if (isActive) R.color.green_active else R.color.text_secondary)
        )

        if (isActive && minutesLeft > 0) {
            binding.countdownText.text = toArabicDigits(minutesLeft.toString())
[10/4/2026 4:39 PM] EMad ELdin: binding.countdownLabel.text = getString(R.string.minutes_left)
            binding.countdownContainer.visibility = View.VISIBLE
        } else if (isActive) {
            binding.countdownText.text = toArabicDigits("0")
            binding.countdownLabel.text = getString(R.string.minutes_left)
            binding.countdownContainer.visibility = View.VISIBLE
        } else {
            binding.countdownContainer.visibility = View.GONE
        }

        binding.btnToggle.text = if (isActive) "إيقاف وضع الصمود" else "تفعيل وضع الصمود"
        binding.ringView.setBackgroundResource(
            if (isActive) R.drawable.ring_active else R.drawable.ring_inactive
        )
    }

    private fun getMinutesLeftInWindow(): Int {
        val now = Calendar.getInstance()
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (now.after(end)) return 0
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (now.before(start)) return 0
        val diffMs = end.timeInMillis - now.timeInMillis
        return (diffMs / 60000).toInt().coerceAtLeast(0)
    }

    private fun toArabicDigits(input: String): String {
        val western = "0123456789"
        val eastern = "٠١٢٣٤٥٦٧٨٩"
        return input.map { c ->
            val idx = western.indexOf(c)
            if (idx >= 0) eastern[idx] else c
        }.joinToString("")
    }
}

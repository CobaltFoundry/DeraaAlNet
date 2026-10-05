package com.somood.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.somood.app.databinding.ActivityMainBinding
import java.util.Calendar

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

        // جدولة يومية + حماية
        ScheduleHelper.scheduleDaily(this)
        LogHelper.init(this)
        requestBatteryOptimizationExemption()
        requestExactAlarmPermission()

        binding.btnLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        binding.btnShare.setOnClickListener {
            val url = getString(R.string.telegram_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        binding.btnMenu.setOnClickListener {
            // قائمة لاحقاً إن لزم
        }

        // تفعيل / إيقاف بالضغط على الحلقة
        binding.progressRing.setOnClickListener {
            onToggleClicked()
        }
    }

    override fun onResume() {
        super.onResume()
        // إعادة الجدولة كل ما التطبيق يرجع (حماية من ضياع المواعيد)
        ScheduleHelper.scheduleDaily(this)
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!ScheduleHelper.canScheduleExact(this)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
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
    val inScheduledWindow = isInScheduledWindow()

    // حالة النص والنقطة
    binding.statusText.text = if (isActive) {
        getString(R.string.status_active)
    } else {
        getString(R.string.status_inactive)
    }
    binding.statusText.setTextColor(
        getColor(if (isActive) R.color.green_active else R.color.text_secondary)
    )
    binding.statusDot.setBackgroundResource(
        if (isActive) R.drawable.dot_active else R.drawable.dot_inactive
    )

    when {
        // داخل المواعيد المحددة (٦–٩) والوضع شغال
        isActive && inScheduledWindow && minutesLeft > 0 -> {
            val hours = minutesLeft / 60
            val mins = minutesLeft % 60
            val timeStr = String.format("%d:%02d", hours, mins)
            binding.countdownText.text = toArabicDigits(timeStr)

            // الدائرة تتملي مع مرور الوقت
            val elapsed = (180 - minutesLeft).coerceIn(0, 180)
            binding.progressRing.progress = elapsed

            binding.remainingText.text =
                "متبقى ${toArabicDigits(minutesLeft.toString())} دقيقة على نهاية وضع الصمود"
            binding.remainingChip.visibility = View.VISIBLE
        }

        // خارج المواعيد + تفعيل يدوي → عداد تصاعدي
        isActive && !inScheduledWindow -> {
            val elapsed = LogHelper.getSessionElapsedMinutes(this)
            val hours = elapsed / 60
            val mins = elapsed % 60
            val timeStr = String.format("%d:%02d", hours, mins)
            binding.countdownText.text = toArabicDigits(timeStr)

            // الدائرة تتملي تدريجيًا مع الوقت (حد أقصى ١٨٠)
            binding.progressRing.progress = elapsed.coerceIn(0, 180)

            // الرسالة المطلوبة
            binding.remainingText.text =
                "${toArabicDigits(elapsed.toString())} دقيقة من غير نت، عاش💪🏻"
            binding.remainingChip.visibility = View.VISIBLE
        }

        // غير نشط
        else -> {
            binding.countdownText.text = toArabicDigits("٠:٠٠")
            binding.progressRing.progress = 0
            binding.remainingChip.visibility = View.GONE
        }
    }
}

    /** هل الآن داخل نافذة ٦ مساءً – ٩ مساءً؟ */
    private fun isInScheduledWindow(): Boolean {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val totalMinutes = hour * 60 + minute
        val start = 18 * 60      // ٦ مساءً
        val end = 21 * 60        // ٩ مساءً
        return totalMinutes in start until end
    }

    /** الدقايق المتبقية حتى ٩ مساءً (٠ لو خارج النافذة) */
    private fun getMinutesLeftInWindow(): Int {
        if (!isInScheduledWindow()) return 0

        val now = Calendar.getInstance()
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
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
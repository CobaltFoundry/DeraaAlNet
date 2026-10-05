package com.somood.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.somood.app.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private val VPN_REQUEST = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("somood_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVpn.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST)
            } else {
                Toast.makeText(this, "صلاحية VPN جاهزة ✓", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBattery.setOnClickListener {
            requestBatteryExemption()
        }

        binding.btnStart.setOnClickListener {
            prefs.edit().putBoolean("onboarding_done", true).apply()
            ScheduleHelper.scheduleDaily(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun requestBatteryExemption() {
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
                        Toast.makeText(this, "افتح إعدادات البطارية يدويًا", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "مستثنى من تحسين البطارية ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "تم تفعيل VPN ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "لازم توافق على VPN عشان يشتغل", Toast.LENGTH_LONG).show()
            }
        }
    }
}
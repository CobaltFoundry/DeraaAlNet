Looperpackage com.somood.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.somood.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }
    private val VPN_REQ = 1001
    private var selectedMinutes = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedMinutes = DurationHelper.getSelectedMinutes(this)
        setupChips()
        highlightChip()

        binding.btnToggle.setOnClickListener {
            if (BlockVpnService.isRunning) confirmStop() else requestVpn()
        }
        binding.btnApps.setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        binding.btnLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.btnApps.text = "التطبيقات المسموحة (${WhitelistHelper.count(this)})"
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun setupChips() {
        binding.chip30.setOnClickListener { selectDuration(30) }
        binding.chip60.setOnClickListener { selectDuration(60) }
        binding.chip120.setOnClickListener { selectDuration(120) }
        binding.chip180.setOnClickListener { selectDuration(180) }
        binding.chipUntil.setOnClickListener { selectDuration(0) }
    }

    private fun selectDuration(minutes: Int) {
        if (BlockVpnService.isRunning) {
            Toast.makeText(this, "أوقف وضع الصمود أولًا لتغيير المدة", Toast.LENGTH_SHORT).show()
            return
        }
        selectedMinutes = minutes
        DurationHelper.setSelectedMinutes(this, minutes)
        highlightChip()
        updateUI()
    }

    private fun highlightChip() {
        val chips = listOf(
            binding.chip30 to 30,
            binding.chip60 to 60,
            binding.chip120 to 120,
            binding.chip180 to 180,
            binding.chipUntil to 0
        )
        chips.forEach { (view, value) ->
            val selected = value == selectedMinutes
            view.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.primary else R.color.text_secondary)
            )
            view.alpha = if (selected) 1f else 0.7f
            view.setBackgroundResource(if (selected) R.drawable.chip_bg else R.drawable.chip_bg)
        }
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, VPN_REQ) else startVpn()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQ && resultCode == Activity.RESULT_OK) startVpn()
        else if (requestCode == VPN_REQ)
            Toast.makeText(this, R.string.vpn_permission_needed, Toast.LENGTH_LONG).show()
    }

    private fun startVpn() {
        val i = Intent(this, BlockVpnService::class.java).apply {
            action = BlockVpnService.ACTION_START
        }
        startForegroundService(i)
        ScheduleHelper.scheduleStopAfter(this, selectedMinutes)
        updateUI()
    }

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(R.string.confirm_message)
            .setPositiveButton(R.string.confirm_yes) { _, _ -> stopVpn() }
            .setNegativeButton(R.string.confirm_no, null)
            .show()
    }

    private fun stopVpn() {
        ScheduleHelper.cancelStop(this)
        val i = Intent(this, BlockVpnService::class.java).apply {
            action = BlockVpnService.ACTION_STOP
        }
        startService(i)
        updateUI()
    }

    private fun updateUI() {
        val active = BlockVpnService.isRunning
        binding.statusText.text = if (active) "نشط" else "غير نشط"
        binding.statusText.setTextColor(
            ContextCompat.getColor(this, if (active) R.color.green_active else R.color.text_secondary)
        )
        binding.statusDot.setBackgroundResource(
            if (active) R.drawable.dot_active else R.drawable.dot_inactive
        )
        binding.btnToggle.text = if (active) "إيقاف وضع الصمود" else "تشغيل وضع الصمود"

        if (active) {
            val remaining = DurationHelper.getRemainingMinutes(this)
            if (remaining >= 0) {
                // تنازلي
                val h = remaining / 60
                val m = remaining % 60
                binding.countdownText.text = toArabic("%d:%02d".format(h, m))
                binding.countdownLabel.text = "متبقي"
                val total = selectedMinutes.coerceAtLeast(1)
                val done = ((total - remaining) * 100 / total).coerceIn(0, 100)
                binding.progressRing.progress = done
            } else {
                // تصاعدي لحد ما يوقف
                val elapsed = LogHelper.getSessionElapsedMinutes(this)
                val h = elapsed / 60
                val m = elapsed % 60
                binding.countdownText.text = toArabic("%d:%02d".format(h, m))
                binding.countdownLabel.text = "من غير نت"
                binding.progressRing.progress = (elapsed % 100)
            }
        } else {
            if (selectedMinutes > 0) {
                val h = selectedMinutes / 60
                val m = selectedMinutes % 60
                binding.countdownText.text = toArabic("%d:%02d".format(h, m))
                binding.countdownLabel.text = "المدة المختارة"
            } else {
                binding.countdownText.text = toArabic("٠:٠٠")
                binding.countdownLabel.text = "لحد ما توقف"
            }
            binding.progressRing.progress = 0
        }
    }

    private fun toArabic(s: String): String {
        val w = "0123456789"
        val e = "٠١٢٣٤٥٦٧٨٩"
        return s.map { c -> val i = w.indexOf(c); if (i >= 0) e[i] else c }.joinToString("")
    }
}
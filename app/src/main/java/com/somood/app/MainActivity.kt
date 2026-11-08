package com.somood.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.somood.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        /** الإشعار بيفتح الشاشة بالـ extra ده عشان الإيقاف يعدّي على نفس خطوة التأكيد. */
        const val EXTRA_REQUEST_STOP = "request_stop"

        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var renderedActive: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startBlocking()
            } else {
                Toast.makeText(this, R.string.vpn_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    // إذن الإشعارات مش شرط للتشغيل — بنكمّل في الحالتين
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpn() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resumeIfNeeded()
        setupFooter()

        binding.btnToggle.setOnClickListener {
            if (SessionState.isActive(this)) requestStop() else begin()
        }
        binding.btnApps.setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        binding.btnLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateSubtitles()
        renderedActive = null
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_STOP, false) == true) {
            intent.removeExtra(EXTRA_REQUEST_STOP)
            if (SessionState.isActive(this)) requestStop()
        }
    }

    /** لو الحالة المحفوظة "شغال" بس الخدمة مش شغالة (النظام قتلها)، نرجّعها. */
    private fun resumeIfNeeded() {
        if (!SessionState.isActive(this) || BlockVpnService.isRunning) return
        if (VpnService.prepare(this) == null) {
            BlockVpnService.start(this)
        } else {
            SessionState.end(this) // الصلاحية اتسحبت
        }
    }

    // ───────────── التشغيل ─────────────

    private fun begin() {
        val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotifPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpn()
        }
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startBlocking()
    }

    private fun startBlocking() {
        SessionState.begin(this)
        BlockVpnService.start(this)
        updateUI()
    }

    // ───────────── الإيقاف (بتأكيد من قفل الجهاز) ─────────────

    /**
     * بيطلب البصمة أو PIN/النمط/كلمة السر بتاعة الموبايل نفسه.
     * لو الجهاز مفيهوش أي وسيلة حماية، الإيقاف بيتم مباشرةً.
     */
    private fun requestStop() {
        val canAuth = BiometricManager.from(this).canAuthenticate(AUTHENTICATORS)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            BlockVpnService.stop(this)
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    BlockVpnService.stop(this@MainActivity)
                }
                // الإلغاء أو الفشل: النت يفضل مقطوع
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.confirm_stop_title))
            .setSubtitle(getString(R.string.confirm_stop_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    // ───────────── الواجهة ─────────────

    private fun updateUI() {
        val active = SessionState.isActive(this)

        if (renderedActive != active) {
            renderedActive = active
            binding.statusPill.setBackgroundResource(if (active) R.drawable.pill_on else R.drawable.pill_off)
            binding.statusDot.setBackgroundResource(if (active) R.drawable.dot_on else R.drawable.dot_off)
            binding.statusText.text = if (active) "النت مقطوع" else "النت شغال"
            binding.statusText.setTextColor(
                ContextCompat.getColor(this, if (active) R.color.green_active else R.color.text_secondary)
            )
            binding.btnToggle.setBackgroundResource(if (active) R.drawable.power_bg_on else R.drawable.power_bg_off)
            binding.btnToggle.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, if (active) R.color.green_active else R.color.text_primary)
            )
            binding.btnToggle.contentDescription = if (active) "رجّع النت" else "اقطع النت"
            binding.countdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (active) 48f else 22f)
            binding.countdownLabel.text =
                if (active) "من غير نت — اضغط الزر عشان ترجّعه" else "التطبيقات المسموحة هتفضل شغالة"
        }

        if (active) {
            val seconds = (System.currentTimeMillis() - SessionState.startedAt(this)) / 1000
            binding.countdownText.text = Fmt.clock(seconds)
        } else {
            binding.countdownText.text = "اضغط عشان تقطع النت"
        }
    }

    private fun updateSubtitles() {
        val apps = WhitelistHelper.count(this)
        binding.appsSubtitle.text =
            if (apps == 0) "مفيش تطبيقات مسموحة" else "${Fmt.appsLabel(apps)} هتفضل شغالة"

        val today = LogHelper.todayMinutes(this)
        binding.logSubtitle.text =
            if (today == 0) "مفيش قطع النهارده" else "النهارده ${Fmt.minutesLabel(today)} من غير نت"
    }

    /** "صُنع بـ 💙 بواسطة me.app" — الاسم بيفتح قناة التليجرام. */
    private fun setupFooter() {
        val name = getString(R.string.made_by_name)
        val text = getString(R.string.made_by, name)
        val start = text.indexOf(name)
        val spannable = SpannableString(text)
        if (start >= 0) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = openChannel()
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = ContextCompat.getColor(this@MainActivity, R.color.green_active)
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            }, start, start + name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.footerText.text = spannable
        binding.footerText.movementMethod = LinkMovementMethod.getInstance()
        binding.footerText.highlightColor = Color.TRANSPARENT
    }

    private fun openChannel() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.made_by_url))))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.made_by_url), Toast.LENGTH_LONG).show()
        }
    }
}

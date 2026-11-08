package com.somood.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.somood.app.databinding.ActivityAppsBinding
import kotlin.concurrent.thread

class AppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppsBinding
    private lateinit var adapter: AppsAdapter
    private var allApps: List<AppItem> = emptyList()
    private var loaded = false

    data class AppItem(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?,
        var allowed: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = AppsAdapter(mutableListOf())
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        // تحميل التطبيقات والأيقونات تقيل — بنعمله بره الـ UI thread
        val whitelist = WhitelistHelper.getPackages(this)
        thread(name = "load-apps") {
            val apps = loadApps(whitelist)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                allApps = apps
                loaded = true
                applyFilter(binding.search.text?.toString().orEmpty())
            }
        }

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.btnSave.setOnClickListener {
            // لو القائمة لسه بتتحمّل، الحفظ هيمسح القائمة البيضاء بالغلط
            if (!loaded) return@setOnClickListener

            val selected = allApps.filter { it.allowed }.map { it.packageName }.toSet()
            WhitelistHelper.savePackages(this, selected)
            Toast.makeText(this, "تم الحفظ (${Fmt.arabic(selected.size)})", Toast.LENGTH_SHORT).show()

            // لو النت مقطوع دلوقتي، نطبّق القائمة الجديدة فورًا من غير ما نقفل الجلسة
            if (SessionState.isActive(this)) BlockVpnService.restart(this)
            finish()
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        adapter.update(
            if (q.isEmpty()) allApps
            else allApps.filter { it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        )
    }

    /** تطبيقات المستخدم اللي بتطلب إنترنت فقط (من غير تطبيقات النظام عشان محدش يقطع حاجة أساسية بالغلط). */
    private fun loadApps(whitelist: Set<String>): List<AppItem> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@filter false
                if (app.packageName == packageName) return@filter false
                try {
                    val info = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                    info.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
                } catch (_: Exception) {
                    true
                }
            }
            .map {
                AppItem(
                    name = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = pm.getApplicationIcon(it),
                    allowed = whitelist.contains(it.packageName)
                )
            }
            .sortedBy { it.name.lowercase() }
    }
}

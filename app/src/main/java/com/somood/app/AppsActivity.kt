package com.somood.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.somood.app.databinding.ActivityAppsBinding

class AppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppsBinding
    private lateinit var adapter: AppsAdapter
    private var allApps: List<AppItem> = emptyList()

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

        val whitelist = WhitelistHelper.getPackages(this).toMutableSet()
        allApps = loadApps(whitelist)
        adapter = AppsAdapter(allApps.toMutableList())
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                val filtered = if (q.isEmpty()) {
                    allApps
                } else {
                    allApps.filter {
                        it.name.lowercase().contains(q) ||
                            it.packageName.lowercase().contains(q)
                    }
                }
                adapter.update(filtered)
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.btnSave.setOnClickListener {
            val selected = allApps.filter { it.allowed }.map { it.packageName }.toSet()
            WhitelistHelper.savePackages(this, selected)
            Toast.makeText(this, "تم الحفظ (${selected.size})", Toast.LENGTH_SHORT).show()

            if (BlockVpnService.isRunning) {
                val stop = Intent(this, BlockVpnService::class.java).apply {
                    action = BlockVpnService.ACTION_STOP
                }
                startService(stop)
                binding.root.postDelayed({
                    val start = Intent(this, BlockVpnService::class.java).apply {
                        action = BlockVpnService.ACTION_START
                    }
                    startForegroundService(start)
                }, 400)
            }
            finish()
        }
    }

    private fun loadApps(whitelist: Set<String>): List<AppItem> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter { app ->
                // تطبيقات المستخدم فقط — بلاش تطبيقات النظام
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
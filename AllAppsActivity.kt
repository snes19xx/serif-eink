package com.snes19xx.einklauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.recyclerview.widget.GridLayoutManager
import com.snes19xx.einklauncher.databinding.ActivityAllAppsBinding

// Activity for displaying and launching all installed apps
class AllAppsActivity : Activity() {

    private lateinit var binding: ActivityAllAppsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.setWindowAnimations(0)

        binding = ActivityAllAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener {
            finish()
        }

        setupGrid()
    }

    private fun setupGrid() {
        binding.allAppsGrid.layoutManager = GridLayoutManager(this, 4)
        // Disable default RecyclerView animations for e-ink
        binding.allAppsGrid.itemAnimator = null

        val installedApps = getInstalledApps()
        val adapter = AppAdapter(this, installedApps)
        binding.allAppsGrid.adapter = adapter
    }

    private fun getInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName != applicationContext.packageName) {
                val label = resolveInfo.loadLabel(packageManager)
                val icon = resolveInfo.activityInfo.loadIcon(packageManager)
                apps.add(AppInfo(label, packageName, icon))
            }
        }

        apps.sortBy { it.label.toString().lowercase() }
        return apps
    }
}
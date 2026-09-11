package com.example.blocker

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blocker.databinding.ActivityAppsListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppsListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.apps_list_title)

        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        loadApps()
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val apps = withContext(Dispatchers.IO) { fetchInstalledApps() }
            binding.progressBar.visibility = View.GONE
            val adapter = AppsAdapter(apps.toMutableList()) { app, checked ->
                PrefsManager.toggleBlockedApp(this@AppsListActivity, app.packageName, checked)
            }
            binding.recyclerApps.adapter = adapter
        }
    }

    private fun fetchInstalledApps(): List<AppInfo> {
        val pm: PackageManager = packageManager
        val blocked = PrefsManager.getBlockedApps(this)
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        return resolveInfos
            .mapNotNull { it.activityInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName } // خود بلاکر را در لیست نشان نده
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm),
                    blocked = blocked.contains(info.packageName)
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}

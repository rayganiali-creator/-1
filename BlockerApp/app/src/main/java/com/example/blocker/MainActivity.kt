package com.example.blocker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var websitesAdapter: ArrayAdapter<String>
    private lateinit var keywordsAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebsiteSection()
        setupKeywordSection()

        binding.btnManageApps.setOnClickListener {
            startActivity(Intent(this, AppsListActivity::class.java))
        }

        binding.btnEnableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "«بلاکر» را در لیست پیدا کرده و روشن کنید",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLists()
    }

    private fun setupWebsiteSection() {
        websitesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.listWebsites.adapter = websitesAdapter

        binding.btnAddWebsite.setOnClickListener {
            val value = binding.etWebsite.text.toString().trim()
            if (TextUtils.isEmpty(value)) return@setOnClickListener
            PrefsManager.addBlockedWebsite(this, value)
            binding.etWebsite.text.clear()
            refreshLists()
        }

        binding.listWebsites.setOnItemClickListener { _, _, position, _ ->
            val value = websitesAdapter.getItem(position) ?: return@setOnItemClickListener
            PrefsManager.removeBlockedWebsite(this, value)
            Toast.makeText(this, "حذف شد: $value", Toast.LENGTH_SHORT).show()
            refreshLists()
        }
    }

    private fun setupKeywordSection() {
        keywordsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.listKeywords.adapter = keywordsAdapter

        binding.btnAddKeyword.setOnClickListener {
            val value = binding.etKeyword.text.toString().trim()
            if (TextUtils.isEmpty(value)) return@setOnClickListener
            PrefsManager.addBlockedKeyword(this, value)
            binding.etKeyword.text.clear()
            refreshLists()
        }

        binding.listKeywords.setOnItemClickListener { _, _, position, _ ->
            val value = keywordsAdapter.getItem(position) ?: return@setOnItemClickListener
            PrefsManager.removeBlockedKeyword(this, value)
            Toast.makeText(this, "حذف شد: $value", Toast.LENGTH_SHORT).show()
            refreshLists()
        }
    }

    private fun refreshLists() {
        websitesAdapter.clear()
        websitesAdapter.addAll(PrefsManager.getBlockedWebsites(this))
        websitesAdapter.notifyDataSetChanged()

        keywordsAdapter.clear()
        keywordsAdapter.addAll(PrefsManager.getBlockedKeywords(this))
        keywordsAdapter.notifyDataSetChanged()
    }
}

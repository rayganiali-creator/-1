package com.example.blocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.databinding.ActivityBlockedScreenBinding
import com.example.blocker.session.SessionManager
import com.example.blocker.ui.UiKit

/** صفحه‌ای که وقتی چیزی مسدود شد نشان داده می‌شود. */
class BlockedScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
    }

    private lateinit var binding: ActivityBlockedScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val message = intent.getStringExtra(EXTRA_MESSAGE)
        if (!message.isNullOrBlank()) {
            binding.txtMessage.text = message
        }

        val session = SessionManager.activeSession(this)
        if (session != null) {
            binding.txtSessionInfo.text = "سشن فوکوس فعال است — زمان باقی‌مانده: " +
                UiKit.formatDuration(SessionManager.remainingMillis(this, session))
            binding.txtSessionInfo.visibility = android.view.View.VISIBLE
        }

        binding.btnBackHome.setOnClickListener { goHome() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }
}

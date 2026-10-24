package com.example.blocker

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.OvershootInterpolator
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

        // انیمیشن سبک خرس ایجنت: ورود با جهش نرم، بعد تکان ملایم و همیشگی.
        // بدون هیچ کتابخانه‌ای؛ فقط دو سه انیماتور روی یک ImageView.
        // (findViewById به‌جای بایندینگ: گیت محلی R.id را از لی‌آوت می‌سازد ولی
        // کلاس‌های بایندینگِ تولیدیِ Gradle را ندارد.)
        val bear = findViewById<android.widget.ImageView>(R.id.imgBear)
        bear.scaleX = 0.6f; bear.scaleY = 0.6f; bear.alpha = 0f
        ObjectAnimator.ofFloat(bear, "scaleX", 0.6f, 1f).apply {
            duration = 500; interpolator = OvershootInterpolator(2f); start()
        }
        ObjectAnimator.ofFloat(bear, "scaleY", 0.6f, 1f).apply {
            duration = 500; interpolator = OvershootInterpolator(2f); start()
        }
        ObjectAnimator.ofFloat(bear, "alpha", 0f, 1f).apply { duration = 350; start() }
        ObjectAnimator.ofFloat(bear, "translationY", 0f, -12f, 0f).apply {
            duration = 1800; repeatCount = ObjectAnimator.INFINITE; startDelay = 550; start()
        }

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

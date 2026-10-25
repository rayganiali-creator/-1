package com.example.blocker.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.backup.BackupManager
import com.example.blocker.session.SessionManager

/** بکاپ/ریستور کانفیگ با Storage Access Framework؛ اعتبارسنجی کامل قبل از اعمال. */
class BackupActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(BackupManager.exportJson(this).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "بکاپ ذخیره شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره‌ی بکاپ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /** سقف حجم فایل بکاپ: بکاپ واقعی JSON در حد چند ده کیلوبایت است؛ فایل بزرگ‌تر، خراب یا مخرب تلقی می‌شود. */
        const val MAX_BACKUP_BYTES = 1024 * 1024
    }

    /** خواندن فایل با سقف حجم؛ بیش از سقف → null (بدون اینکه کل فایل به حافظه بیاید). */
    private fun readBackupTextCapped(uri: android.net.Uri): String? {
        val stream = contentResolver.openInputStream(uri) ?: return null
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BACKUP_BYTES) return null
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val text = try {
            readBackupTextCapped(uri)
        } catch (e: Exception) { null }
        if (text == null) { Toast.makeText(this, "خواندن فایل ممکن نشد یا حجم آن بیش از حد مجاز است (حداکثر ۱ مگابایت)", Toast.LENGTH_LONG).show(); return@registerForActivityResult }
        val error = BackupManager.validateBackup(text)
        if (error != null) {
            AlertDialog.Builder(this).setTitle("بکاپ نامعتبر است").setMessage(error)
                .setPositiveButton("باشه", null).show()
            return@registerForActivityResult
        }
        AlertDialog.Builder(this)
            .setTitle("جایگزینی کانفیگ فعلی؟")
            .setMessage("قوانین، دامنه‌ها، اپ‌های هدف و دیلی از فایل جایگزین می‌شوند. سشن فعال و مجوزها دست نمی‌خورند و بکاپ هیچ مجوزی برنمی‌گرداند.")
            .setPositiveButton("جایگزین کن") { _, _ ->
                val ok = BackupManager.applyBackup(this, text)
                Toast.makeText(this, if (ok) "بکاپ اعمال شد" else "اعمال بکاپ ناموفق بود؛ کانفیگ قبلی حفظ شد", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "بکاپ و بازیابی"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        column.addView(UiKit.body(this, "بکاپ شامل قوانین، دسته‌ها، دامنه‌ها، اپ‌های هدف، پروفایل‌های شخصی و دیلی است (فرمت JSON، schema نسخه ۱). سشن فعال، مجوزها و وضعیت Device Owner هرگز داخل بکاپ نیستند."))
        if (SessionManager.isStrictActive(this)) {
            column.addView(UiKit.body(this, "نکته: در سشن Strict، ریستور کانفیگ سشن جاری را عوض نمی‌کند (از snapshot قفل‌شده استفاده می‌شود)."))
        }
        column.addView(UiKit.primaryButton(this, "خروجی گرفتن بکاپ (JSON)") {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "blocker-backup.json")
            }
            exportLauncher.launch(intent)
        })
        column.addView(UiKit.tonalButton(this, "بازیابی از فایل بکاپ") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            importLauncher.launch(intent)
        })
    }
}

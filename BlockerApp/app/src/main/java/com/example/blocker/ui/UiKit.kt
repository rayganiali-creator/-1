package com.example.blocker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.example.blocker.PrefsManager
import com.example.blocker.R
import com.google.android.material.card.MaterialCardView

/** پالت رنگی برنامه بر اساس انتخاب کاربر برای رنگ پس‌زمینه (تنظیمات > رنگ پس‌زمینه).
 *  زبان بازطراحی تیره‌ی مدرن (موکاپ‌های تأییدشده‌ی ۲۰۲۶-۱۰-۲۴): پس‌زمینه‌ی زغالی،
 *  سطح کارت زغالیِ روشن‌تر، لهجه‌ی نعنایی. پیش‌فرض (خودکار) حالا تیره است تا
 *  ظاهر برنامه همان موکاپ‌های تأییدشده باشد؛ روشن/نعنایی/آبی/کرمی همچنان از
 *  تنظیمات قابل انتخاب‌اند و لهجه‌شان با همان تم هماهنگ می‌شود. */
object ThemePalette {
    data class Colors(
        val background: Int,
        val surface: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outline: Int,
        /** خاکستریِ ملایمِ کاشی‌ها و فیلدهای ورودی. */
        val tile: Int,
        /** لهجه‌ی اصلی (آیکن‌ها، حلقه، دکمه‌ی اصلی). */
        val accent: Int,
        /** نسخه‌ی ملایم لهجه برای پس‌زمینه‌ی دکمه‌ی تونال و چیپ انتخاب‌شده. */
        val accentSoft: Int,
        /** متن روی لهجه‌ی اصلی. */
        val onAccent: Int
    )

    fun resolve(context: Context): Colors {
        // طرح تأییدشده فقط تیره است؛ تم روشن/کرمی از برنامه حذف شده است.
        // «رنگ پس‌زمینه» در تنظیمات حالا رنگ صفحه‌ی بلاک است، نه تم برنامه.
        return dark()
    }

    private fun light() = Colors(0xFFF4F6F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF14181D.toInt(), 0xFF5A6363.toInt(), 0xFFE2E8E6.toInt(), 0xFFEEF2F0.toInt(), 0xFF0E9F76.toInt(), 0xFFD9F5EA.toInt(), 0xFFFFFFFF.toInt())
    private fun dark() = Colors(0xFF14181D.toInt(), 0xFF232A32.toInt(), 0xFFFFFFFF.toInt(), 0xFF9AA7B0.toInt(), 0xFF2E3A40.toInt(), 0xFF1E242B.toInt(), 0xFF7CFFD9.toInt(), 0xFF1E3A32.toInt(), 0xFF14181D.toInt())
}

/** سازنده‌های مشترک UI برنامه‌ای؛ زبان بصری «سد» در بازطراحی تیره‌ی مدرن:
 *  کارت‌های گرد ۲۸ و تخت (بدون سایه)، دکمه‌های قرصی نعنایی، حلقه‌ی درشت سشن. */
object UiKit {

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    fun screen(context: Context): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(context).apply {
            setBackgroundColor(ThemePalette.resolve(context).background)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(context, 20)
            setPadding(pad, pad, pad, dp(context, 28))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(column)
        return scroll to column
    }

    fun title(context: Context, text: String, sizeSp: Float = 22f): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ThemePalette.resolve(context).onSurface)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(context, 8) }
    }

    fun body(context: Context, text: String, sizeSp: Float = 14f): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(ThemePalette.resolve(context).onSurfaceVariant)
        setLineSpacing(0f, 1.25f)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(context, 8) }
    }

    fun card(context: Context, build: (LinearLayout) -> Unit): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 28).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ThemePalette.resolve(context).surface)
            setStrokeColor(android.content.res.ColorStateList.valueOf(ThemePalette.resolve(context).outline))
            strokeWidth = dp(context, 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 12) }
            useCompatPadding = true
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(context, 16)
            setPadding(pad, pad, pad, pad)
        }
        build(inner)
        card.addView(inner)
        return card
    }

    /** حلقه‌ی زنده‌ی پیشرفت سشن: کمان + متن وسط، با update() برای تیک هر ثانیه. */
    class LiveRingView(context: Context) : FrameLayout(context) {
        private val ringView: RingView
        private val remainingView: TextView
        init {
            val size = dp(context, 240)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 8); bottomMargin = dp(context, 8)
            }
            ringView = RingView(context, 1f)
            addView(ringView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val center = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            remainingView = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ThemePalette.resolve(context).onSurface)
                gravity = Gravity.CENTER
            }
            center.addView(remainingView)
            center.addView(TextView(context).apply {
                text = "زمان باقی‌مانده"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ThemePalette.resolve(context).onSurfaceVariant)
                gravity = Gravity.CENTER
            })
            addView(center)
        }
        fun update(fraction: Float, remaining: String) {
            ringView.fraction = fraction
            remainingView.text = remaining
        }
    }

    fun sessionRing(context: Context, remainingText: String, fraction: Float): LiveRingView =
        LiveRingView(context).apply { update(fraction, remainingText) }

    /** کارت آمار شیشه‌ای موکاپ: آیکون + برچسب + عدد درشت، با خط نازک دور. */
    fun statCard(context: Context, iconRes: Int, label: String, value: String): MaterialCardView {
        val palette = ThemePalette.resolve(context)
        val card = MaterialCardView(context).apply {
            radius = dp(context, 24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(palette.surface)
            setStrokeColor(android.content.res.ColorStateList.valueOf(palette.outline))
            strokeWidth = dp(context, 1)
            useCompatPadding = true
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(context, 16)
            setPadding(pad, pad, pad, pad)
        }
        inner.addView(ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            layoutParams = LinearLayout.LayoutParams(dp(context, 28), dp(context, 28))
        })
        inner.addView(TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.onSurfaceVariant)
            gravity = Gravity.CENTER
        })
        inner.addView(TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.onSurface)
            gravity = Gravity.CENTER
        })
        card.addView(inner)
        return card
    }

    /** چیپ کوچکِ اطلاعات (دسته، تأخیر و...) برای ردیف‌های قوانین: خاکستری ملایم، متن ریز. */
    fun miniChip(context: Context, label: String): TextView = TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(ThemePalette.resolve(context).onSurfaceVariant)
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 12).toFloat()
            setColor(ThemePalette.resolve(context).tile)
        }
        val hp = dp(context, 8); val vp = dp(context, 4)
        setPadding(hp, vp, hp, vp)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            val m = dp(context, 3)
            setMargins(m, m, m, m)
        }
    }

    private class RingView(context: Context, fraction: Float) : View(context) {
        var fraction: Float = fraction
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePalette.resolve(context).outline
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 8
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePalette.resolve(context).accent
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 8
            strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = progressPaint.strokeWidth / 2 + 2
            val rect = RectF(inset, inset, width - inset, height - inset)
            canvas.drawArc(rect, 0f, 360f, false, trackPaint)
            if (fraction > 0f) canvas.drawArc(rect, -90f, 360f * fraction, false, progressPaint)
        }
    }

    /** ردیف آمارِ ساده: عددهای بزرگ با برچسب، جدا شده با خط‌های نازک — بدون جعبه. */
    fun statsPlainRow(context: Context, items: List<Pair<String, String>>): LinearLayout {
        val palette = ThemePalette.resolve(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(context, 12); bottomMargin = dp(context, 12) }
        }
        items.forEachIndexed { i, (value, label) ->
            if (i > 0) {
                row.addView(View(context).apply {
                    setBackgroundColor(palette.outline)
                    layoutParams = LinearLayout.LayoutParams(dp(context, 1), dp(context, 48)).apply {
                        val m = dp(context, 10)
                        setMargins(m, 0, m, 0)
                    }
                })
            }
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(context).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.onSurface)
                gravity = Gravity.CENTER
            })
            col.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(palette.onSurfaceVariant)
                gravity = Gravity.CENTER
            })
            row.addView(col)
        }
        return row
    }

    /** کاشیِ مدرن: سطح ملایم، بدون خط و سایه، آیکونِ خطیِ لهجه‌دار مستقیم روی کاشی. */
    fun tile(context: Context, iconRes: Int, label: String, onClick: () -> Unit): MaterialCardView {
        val palette = ThemePalette.resolve(context)
        val card = MaterialCardView(context).apply {
            radius = dp(context, 24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(palette.tile)
            setStrokeColor(android.content.res.ColorStateList.valueOf(palette.outline))
            strokeWidth = dp(context, 1)
            isClickable = true
            isFocusable = true
            rippleColor = ColorStateList.valueOf(palette.accent)
            useCompatPadding = true
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(context, 16)
            setPadding(pad, pad, pad, pad)
        }
        inner.addView(ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(palette.accent)
            layoutParams = LinearLayout.LayoutParams(dp(context, 28), dp(context, 28))
        })
        inner.addView(TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.onSurface)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(context, 10) }
        })
        card.addView(inner)
        card.setOnClickListener { onClick() }
        return card
    }

    /** دکمه‌ی اصلی: یکدستِ لهجه‌ای، قرصی‌شکل، متن هماهنگ با لهجه. */
    fun primaryButton(context: Context, text: String, onClick: () -> Unit): AppCompatButton {
        val palette = ThemePalette.resolve(context)
        return AppCompatButton(context).apply {
            this.text = text
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 28).toFloat()
                setColor(palette.accent)
            }
            setTextColor(palette.onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(context, 56)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }
    }

    fun tonalButton(context: Context, text: String, onClick: () -> Unit): AppCompatButton {
        val palette = ThemePalette.resolve(context)
        return AppCompatButton(context).apply {
            this.text = text
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 24).toFloat()
                setColor(palette.accentSoft)
            }
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(context, 52)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }
    }

    fun textButton(context: Context, text: String, onClick: () -> Unit): AppCompatButton =
        AppCompatButton(context).apply {
            this.text = text
            background = null
            setTextColor(ThemePalette.resolve(context).accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(context, 48)
            setOnClickListener { onClick() }
        }

    fun outlinedButton(context: Context, text: String, onClick: () -> Unit): AppCompatButton =
        AppCompatButton(context).apply {
            this.text = text
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 24).toFloat()
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(dp(context, 1), ThemePalette.resolve(context).outline)
            }
            setTextColor(ThemePalette.resolve(context).onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            minimumHeight = dp(context, 52)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }

    /**
     * ردیف افقی: چپ منبسط می‌شود، راست فشرده می‌ماند.
     * قرارداد مهم: عرض فرزند راست همیشه WRAP_CONTENT می‌شود؛ اگر فرزند راست
     * MATCH_PARENT باشد (مثل UiKit.body)، در LinearLayout افقی تمام عرض را
     * می‌گیرد و فرزند چپ به عرض صفر له می‌شود (باگی که عنوان‌های فهرست
     * خلاصه‌ی آنبوردینگ را نامرئی می‌کرد).
     */
    fun row(context: Context, left: View, right: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(context, 6) }
        left.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        right.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(left)
        addView(right)
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(ThemePalette.resolve(context).outline)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1))
            .apply { topMargin = dp(context, 6); bottomMargin = dp(context, 10) }
    }

    fun statusDot(context: Context, ok: Boolean?): TextView = TextView(context).apply {
        text = "●"
        setTextColor(ContextCompat.getColor(context, when (ok) {
            true -> R.color.blocker_success
            false -> R.color.blocker_error
            null -> R.color.blocker_warning
        }))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    }

    /** قرص وضعیت محافظت: نقطه‌ی لهجه‌ای + عنوان + زیرنویس، روی پس‌زمینه‌ی
     *  ملایمِ هماهنگ با تم (فعال: تینت لهجه؛ خاموش: سطح ملایم با نقطه‌ی خطا). */
    fun statusPill(context: Context, active: Boolean, titleText: String, subtitleText: String): LinearLayout {
        val palette = ThemePalette.resolve(context)
        val bg = if (active) palette.accentSoft else palette.tile
        val dot = if (active) palette.accent else ContextCompat.getColor(context, R.color.blocker_error)
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 24).toFloat()
                setColor(bg)
            }
            val p = dp(context, 16)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 12) }
        }
        val dotView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dot)
            }
            layoutParams = LinearLayout.LayoutParams(dp(context, 8), dp(context, 8)).apply {
                marginEnd = dp(context, 8)
            }
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = titleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.onSurface)
        })
        texts.addView(TextView(context).apply {
            text = subtitleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.onSurfaceVariant)
        })
        pill.addView(dotView)
        pill.addView(texts)
        return pill
    }

    /** چیپ انتخاب (بدون شنونده؛ فراخواننده خودش setOnClickListener می‌گذارد). */
    fun chipView(context: Context, label: String, selected: Boolean): TextView {
        val chip = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            val hp = dp(context, 12)
            val vp = dp(context, 10)
            setPadding(hp, vp, hp, vp)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                val m = dp(context, 4)
                setMargins(m, m, m, m)
            }
        }
        setChipSelected(context, chip, selected)
        return chip
    }

    /** چیپ انتخاب با شنونده‌ی آماده. */
    fun chip(context: Context, label: String, selected: Boolean, onClick: () -> Unit): TextView =
        chipView(context, label, selected).apply { setOnClickListener { onClick() } }

    /** رنگ چیپ را با وضعیت انتخاب همگام می‌کند (انتخابی: تینت لهجه + متن لهجه‌ای). */
    fun setChipSelected(context: Context, chip: TextView, selected: Boolean) {
        val palette = ThemePalette.resolve(context)
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(context, 24).toFloat()
            setColor(
                if (selected) palette.accentSoft
                else palette.tile
            )
        }
        chip.setTextColor(
            if (selected) palette.accent
            else palette.onSurface
        )
        chip.setTypeface(chip.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    /** فیلد ورودیِ مدرن: پس‌زمینه‌ی سطح ملایم، گوشه‌های گرد ۲۰، بدون خط زیرین. */
    fun textField(context: Context, hint: String): EditText =
        EditText(context).apply {
            this.hint = hint
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(ThemePalette.resolve(context).tile)
            }
            val hp = dp(context, 16)
            val vp = dp(context, 14)
            setPadding(hp, vp, hp, vp)
            setTextColor(ThemePalette.resolve(context).onSurface)
            setHintTextColor(ThemePalette.resolve(context).onSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 8) }
        }

    fun formatDuration(millis: Long): String {
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    fun formatMinutes(mins: Long): String =
        if (mins >= 60) "${mins / 60} ساعت و ${mins % 60} دقیقه" else "$mins دقیقه"
}

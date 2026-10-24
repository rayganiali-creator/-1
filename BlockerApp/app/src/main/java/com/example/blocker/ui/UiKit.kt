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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.blocker.PrefsManager
import com.example.blocker.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** پالت رنگی برنامه بر اساس انتخاب کاربر برای رنگ پس‌زمینه (تنظیمات > رنگ پس‌زمینه).
 *  رنگ‌های تأکیدی (primary و...) همان توکن‌های DayNight می‌مانند؛ فقط پس‌زمینه،
 *  سطح کارت، رنگ متن و خط دور با تم عوض می‌شوند تا خوانایی در هر تم حفظ شود. */
object ThemePalette {
    data class Colors(
        val background: Int,
        val surface: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outline: Int
    )

    fun resolve(context: Context): Colors {
        val night = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return when (PrefsManager.getTheme(context)) {
            "dark" -> dark()
            "light" -> light()
            "mint" -> Colors(0xFFE7F5EE.toInt(), 0xFFFFFFFF.toInt(), 0xFF1B1D1E.toInt(), 0xFF5A6363.toInt(), 0xFFD5E8DD.toInt())
            "blue" -> Colors(0xFFEAF1FB.toInt(), 0xFFFFFFFF.toInt(), 0xFF1B1D1E.toInt(), 0xFF5A6363.toInt(), 0xFFD5E0F0.toInt())
            "cream" -> Colors(0xFFFDF6EC.toInt(), 0xFFFFFFFF.toInt(), 0xFF1B1D1E.toInt(), 0xFF5A6363.toInt(), 0xFFEADFC8.toInt())
            else -> if (night) dark() else light()
        }
    }

    private fun light() = Colors(0xFFF7F8F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF1B1D1E.toInt(), 0xFF5A6363.toInt(), 0xFFDCE3E1.toInt())
    private fun dark() = Colors(0xFF101314.toInt(), 0xFF181D1E.toInt(), 0xFFE8EAEB.toInt(), 0xFFA8B0B1.toInt(), 0xFF2A3234.toInt())
}

/** سازنده‌های مشترک UI برنامه‌ای؛ زبان بصری «سد»: کارت‌های گرد ۲۸، گرادیان قهرمان، دکمه‌های قرصی. */
object UiKit {

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    fun heroGradient(context: Context): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            ContextCompat.getColor(context, R.color.blocker_hero_start),
            ContextCompat.getColor(context, R.color.blocker_hero_end)
        )).apply { cornerRadius = dp(context, 28).toFloat() }

    fun screen(context: Context): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(context).apply {
            setBackgroundColor(ThemePalette.resolve(context).background)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(context, 16)
            setPadding(pad, pad, pad, dp(context, 24))
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
            cardElevation = dp(context, 1).toFloat()
            setCardBackgroundColor(ThemePalette.resolve(context).surface)
            strokeColor = ThemePalette.resolve(context).outline
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

    /** کارت قهرمان گرادیانی داشبورد: سپر + تیتر + زیرنویس + دکمه‌ی سفید اختیاری. */
    fun heroCard(
        context: Context,
        titleText: String,
        subtitleText: String,
        buttonText: String? = null,
        onButtonClick: (() -> Unit)? = null
    ): LinearLayout {
        val hero = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = heroGradient(context)
            val pad = dp(context, 20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 12) }
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(context).apply {
            text = titleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.blocker_on_hero))
        })
        texts.addView(TextView(context).apply {
            text = subtitleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.blocker_on_hero))
            alpha = 0.9f
        })
        val shield = ImageView(context).apply {
            setImageResource(R.drawable.ic_shield)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blocker_on_hero))
            layoutParams = LinearLayout.LayoutParams(dp(context, 56), dp(context, 56))
                .apply { marginStart = dp(context, 12) }
        }
        row.addView(texts)
        row.addView(shield)
        hero.addView(row)
        if (buttonText != null && onButtonClick != null) {
            hero.addView(MaterialButton(context).apply {
                text = buttonText
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blocker_on_hero))
                setTextColor(ContextCompat.getColor(context, R.color.blocker_primary))
                cornerRadius = dp(context, 24)
                minHeight = dp(context, 48)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(context, 12) }
                setOnClickListener { onButtonClick() }
            })
        }
        return hero
    }

    /** حلقه‌ی پیشرفت سشن: کمان + متن وسط (زمان باقی‌مانده و زیرنویس). */
    fun sessionRing(context: Context, remainingText: String, subText: String, fraction: Float): FrameLayout {
        val size = dp(context, 168)
        val frame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 8); bottomMargin = dp(context, 8)
            }
        }
        frame.addView(RingView(context, fraction.coerceIn(0f, 1f)))
        val center = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        center.addView(TextView(context).apply {
            text = remainingText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePalette.resolve(context).onSurface)
            gravity = Gravity.CENTER
        })
        center.addView(TextView(context).apply {
            text = subText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ThemePalette.resolve(context).onSurfaceVariant)
            gravity = Gravity.CENTER
        })
        frame.addView(center)
        return frame
    }

    private class RingView(context: Context, private val fraction: Float) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePalette.resolve(context).outline
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 10
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.blocker_primary)
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 10
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

    /** ردیف کارت‌های آمار با عرض مساوی. */
    fun statsRow(context: Context, items: List<Pair<String, String>>): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 12) }
        }
        items.forEachIndexed { i, (value, label) ->
            val card = MaterialCardView(context).apply {
                radius = dp(context, 20).toFloat()
                cardElevation = dp(context, 1).toFloat()
                setCardBackgroundColor(ThemePalette.resolve(context).surface)
                strokeColor = ThemePalette.resolve(context).outline
                strokeWidth = dp(context, 1)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(context, 8)
                }
                useCompatPadding = true
            }
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val pad = dp(context, 12)
                setPadding(pad, pad, pad, pad)
            }
            inner.addView(TextView(context).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.blocker_primary))
                gravity = Gravity.CENTER
            })
            inner.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ThemePalette.resolve(context).onSurfaceVariant)
                gravity = Gravity.CENTER
            })
            card.addView(inner)
            row.addView(card)
        }
        return row
    }

    /** کاشی بخش‌ها: آیکون در قاب رنگی + برچسب. */
    fun tile(context: Context, iconRes: Int, label: String, onClick: () -> Unit): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 20).toFloat()
            cardElevation = dp(context, 1).toFloat()
            setCardBackgroundColor(ThemePalette.resolve(context).surface)
            strokeColor = ThemePalette.resolve(context).outline
            strokeWidth = dp(context, 1)
            isClickable = true
            isFocusable = true
            rippleColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blocker_primary))
            useCompatPadding = true
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = dp(context, 12)
            setPadding(pad, pad, pad, pad)
        }
        val iconBox = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.blocker_primary_container))
                cornerRadius = dp(context, 16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
        }
        iconBox.addView(ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blocker_primary))
            layoutParams = LinearLayout.LayoutParams(dp(context, 24), dp(context, 24))
        })
        inner.addView(iconBox)
        inner.addView(TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ThemePalette.resolve(context).onSurface)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(context, 8) }
        })
        card.addView(inner)
        card.setOnClickListener { onClick() }
        return card
    }

    fun primaryButton(context: Context, text: String, onClick: () -> Unit): androidx.appcompat.widget.AppCompatButton =
        androidx.appcompat.widget.AppCompatButton(context).apply {
            this.text = text
            // AppCompatButton (not MaterialButton) so the gradient drawable is never
            // replaced by MaterialButton's internal shape background.
            background = heroGradient(context)
            setTextColor(ContextCompat.getColor(context, R.color.blocker_on_hero))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            minHeight = dp(context, 56)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }

    fun tonalButton(context: Context, text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context).apply {
            // Tonal appearance via the app's own M3 tokens: the Material library has no
            // materialButtonTonalStyle theme attr, so the tint is applied explicitly.
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.blocker_primary_container)
            )
            setTextColor(ContextCompat.getColor(context, R.color.blocker_primary))
            this.text = text
            cornerRadius = dp(context, 24)
            minHeight = dp(context, 52)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }

    fun textButton(context: Context, text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context).apply {
            // Text-button appearance explicitly (the Material library has no
            // materialButtonTextStyle theme attr): transparent background, primary label.
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(context, R.color.blocker_primary))
            strokeWidth = 0
            elevation = 0f
            this.text = text
            setOnClickListener { onClick() }
        }

    fun outlinedButton(context: Context, text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            cornerRadius = dp(context, 24)
            minHeight = dp(context, 52)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }

    fun row(context: Context, left: View, right: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(context, 6) }
        left.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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

    fun formatDuration(millis: Long): String {
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    fun formatMinutes(mins: Long): String =
        if (mins >= 60) "${mins / 60} ساعت و ${mins % 60} دقیقه" else "$mins دقیقه"
}

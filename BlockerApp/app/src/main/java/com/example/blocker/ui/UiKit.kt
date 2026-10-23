package com.example.blocker.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.blocker.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** سازنده‌های مشترک UI برنامه‌ای؛ یک زبان بصری یکدست برای همه‌ی صفحه‌ها (Material 3). */
object UiKit {

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    fun screen(context: Context): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.blocker_background))
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
        setTextColor(ContextCompat.getColor(context, R.color.blocker_on_surface))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(context, 8) }
    }

    fun body(context: Context, text: String, sizeSp: Float = 14f): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(ContextCompat.getColor(context, R.color.blocker_on_surface_variant))
        setLineSpacing(0f, 1.25f)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(context, 8) }
    }

    fun card(context: Context, build: (LinearLayout) -> Unit): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 16).toFloat()
            cardElevation = dp(context, 1).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.blocker_surface))
            strokeColor = ContextCompat.getColor(context, R.color.blocker_outline)
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

    fun primaryButton(context: Context, text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context).apply {
            this.text = text
            cornerRadius = dp(context, 12)
            minHeight = dp(context, 48)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }

    fun tonalButton(context: Context, text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            this.text = text
            cornerRadius = dp(context, 12)
            minHeight = dp(context, 48)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }

    fun outlinedButton(context: Context, text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            cornerRadius = dp(context, 12)
            minHeight = dp(context, 48)
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
        setBackgroundColor(ContextCompat.getColor(context, R.color.blocker_outline))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1))
            .apply { topMargin = dp(context, 6); bottomMargin = dp(context, 10) }
    }

    fun statusDot(context: Context, ok: Boolean?): TextView = TextView(context).apply {
        text = when (ok) { true -> "●"; false -> "●"; null -> "●" }
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

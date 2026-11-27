package com.example.execbin

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.TypedValue

/**
 * Replaces emoji characters in a string with monochrome line icons (ImageSpans)
 * built from LineIconDrawable. Reaction emojis (😀😂❤👍🔥🙏🎉😮😢) are skipped
 * and remain as expressive emoji glyphs.
 */
object IconHelper {

    /** Set of emojis we know how to draw as line icons. */
    private val SUPPORTED = setOf(
        "🔒", "🔐", "🛡", "🎤", "📱", "📷", "🔑", "👤", "👥",
        "⚠", "✓", "✕", "🟢", "🟡", "🟠", "🔴", "⚫",
        "💬", "📋", "🔔", "🚫", "✏", "🔍", "🌐", "➤", "➕",
        "🗑", "📧", "⚙", "🌙", "☀", "🔄", "🚪", "📎", "👋", "✈", "↩"
    )

    /** Reaction emojis we deliberately keep colorful. */
    private val REACTIONS = setOf(
        "❤", "👍", "😀", "😂", "😮", "😢", "🔥", "🙏", "🎉"
    )

    /**
     * Scans the text for known emojis and replaces each with an ImageSpan
     * containing a LineIconDrawable in the requested tint color.
     * Multi-codepoint emoji clusters (with VARIATION_SELECTOR FE0F) are
     * matched and replaced as one span.
     */
    fun iconize(context: Context, text: CharSequence?, color: Int, sizePx: Int = dp(context, 16)): CharSequence {
        if (text.isNullOrEmpty()) return text ?: ""
        val s = text.toString()
        val builder = SpannableString(s)

        var i = 0
        val len = s.length
        while (i < len) {
            val cp = s.codePointAt(i)
            val cpLen = Character.charCount(cp)
            // Build the candidate emoji cluster: codepoint + optional FE0F + optional ZWJ sequences
            var endIdx = i + cpLen
            // Consume variation selector
            if (endIdx < len && s[endIdx] == '\uFE0F') endIdx++

            val candidate = s.substring(i, endIdx)
            val normalized = candidate.replace("\uFE0F", "")

            if (SUPPORTED.contains(normalized)) {
                val drawable = LineIconDrawable(normalized, color, sizePx)
                drawable.setBounds(0, 0, sizePx, sizePx)
                val span = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
                builder.setSpan(span, i, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            i = endIdx
        }
        return builder
    }

    /** Returns true if the text contains any emoji we know how to icon-replace. */
    fun hasIcon(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return false
        val s = text.toString()
        return SUPPORTED.any { s.contains(it) }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}

/** Convenience extension: "🔒 Locked".asLineIcons(this, Color.WHITE) */
fun CharSequence.asLineIcons(context: Context, color: Int, sizeDp: Int = 16): CharSequence {
    val sizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat(), context.resources.displayMetrics
    ).toInt()
    return IconHelper.iconize(context, this, color, sizePx)
}

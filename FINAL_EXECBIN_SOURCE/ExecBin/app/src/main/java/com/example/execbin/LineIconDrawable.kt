package com.example.execbin

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Telegram-style monochrome line icon drawable.
 * Draws hand-drawn-look icons procedurally on a 100x100 canvas, then scales.
 *
 * Pass an emoji character + tint color, get a vector-style icon drawable.
 * Reaction emojis (😀😂❤👍 etc) are intentionally not handled — they remain
 * as expressive emoji glyphs.
 */
class LineIconDrawable(
    emoji: String,
    private val color: Int,
    private val sizePx: Int
) : Drawable() {

    // Strip variation selector + zero-width joiners so "🛡" matches "🛡️"
    private val key = emoji.replace("\uFE0F", "").replace("\u200D", "").trim()

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@LineIconDrawable.color
        strokeWidth = 8.5f  // on the 100x100 canvas
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@LineIconDrawable.color
        style = Paint.Style.FILL
    }

    init { setBounds(0, 0, sizePx, sizePx) }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
    override fun setAlpha(alpha: Int) { stroke.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { stroke.colorFilter = filter; fill.colorFilter = filter }
    @Deprecated("required override")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val s = minOf(w, h)
        val sx = s / 100f
        val ox = bounds.left + (w - s) / 2
        val oy = bounds.top + (h - s) / 2
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(sx, sx)
        when (key) {
            "🔒" -> drawLock(canvas)
            "🔐" -> drawLockKey(canvas)
            "🛡" -> drawShield(canvas)
            "🎤" -> drawMic(canvas)
            "📱" -> drawPhone(canvas)
            "📷" -> drawCamera(canvas)
            "🔑" -> drawKey(canvas)
            "👤" -> drawPerson(canvas)
            "👥" -> drawPeople(canvas)
            "⚠" -> drawWarning(canvas)
            "✓" -> drawCheck(canvas)
            "✕" -> drawX(canvas)
            "🟢" -> drawDot(canvas, Color.parseColor("#22C55E"))
            "🟡" -> drawDot(canvas, Color.parseColor("#FACC15"))
            "🟠" -> drawDot(canvas, Color.parseColor("#FB923C"))
            "🔴" -> drawDot(canvas, Color.parseColor("#EF4444"))
            "⚫" -> drawDot(canvas, Color.parseColor("#64748B"))
            "💬" -> drawChat(canvas)
            "📋" -> drawClipboard(canvas)
            "🔔" -> drawBell(canvas)
            "🚫" -> drawNoEntry(canvas)
            "✏" -> drawPencil(canvas)
            "🔍" -> drawSearch(canvas)
            "🌐" -> drawGlobe(canvas)
            "➤" -> drawArrowRight(canvas)
            "➕" -> drawPlus(canvas)
            "🗑" -> drawTrash(canvas)
            "📧" -> drawEnvelope(canvas)
            "⚙" -> drawGear(canvas)
            "🌙" -> drawMoon(canvas)
            "☀" -> drawSun(canvas)
            "🔄" -> drawRefresh(canvas)
            "🚪" -> drawDoor(canvas)
            "📎" -> drawClip(canvas)
            "👋" -> drawWave(canvas)
            "✈" -> drawPlane(canvas)
            "↩" -> drawReply(canvas)
            "⏹" -> drawStop(canvas)
        }
        canvas.restore()
    }

    private fun drawLock(c: Canvas) {
        c.drawRoundRect(RectF(28f, 45f, 72f, 88f), 7f, 7f, stroke)
        val shackle = Path().apply {
            moveTo(36f, 45f)
            lineTo(36f, 30f)
            arcTo(RectF(36f, 12f, 64f, 40f), 180f, 180f, false)
            lineTo(64f, 45f)
        }
        c.drawPath(shackle, stroke)
        c.drawCircle(50f, 62f, 3.5f, stroke)
        c.drawLine(50f, 65f, 50f, 75f, stroke)
    }

    private fun drawLockKey(c: Canvas) {
        // Same as lock but with a key shape inside instead of keyhole
        c.drawRoundRect(RectF(28f, 45f, 72f, 88f), 7f, 7f, stroke)
        val shackle = Path().apply {
            moveTo(36f, 45f)
            lineTo(36f, 30f)
            arcTo(RectF(36f, 12f, 64f, 40f), 180f, 180f, false)
            lineTo(64f, 45f)
        }
        c.drawPath(shackle, stroke)
        // Asterisk inside body to indicate locked content
        c.drawLine(44f, 60f, 56f, 75f, stroke)
        c.drawLine(56f, 60f, 44f, 75f, stroke)
        c.drawLine(50f, 58f, 50f, 78f, stroke)
    }

    private fun drawShield(c: Canvas) {
        val p = Path().apply {
            moveTo(50f, 12f)
            lineTo(82f, 25f)
            lineTo(82f, 52f)
            cubicTo(82f, 75f, 70f, 84f, 50f, 92f)
            cubicTo(30f, 84f, 18f, 75f, 18f, 52f)
            lineTo(18f, 25f)
            close()
        }
        c.drawPath(p, stroke)
        // Inner check
        val ck = Path().apply {
            moveTo(36f, 50f)
            lineTo(46f, 62f)
            lineTo(64f, 40f)
        }
        c.drawPath(ck, stroke)
    }

    private fun drawMic(c: Canvas) {
        c.drawRoundRect(RectF(38f, 12f, 62f, 60f), 12f, 12f, stroke)
        // U arc base
        val arc = RectF(28f, 40f, 72f, 75f)
        c.drawArc(arc, 0f, 180f, false, stroke)
        // Stand
        c.drawLine(50f, 75f, 50f, 88f, stroke)
        // Foot
        c.drawLine(36f, 88f, 64f, 88f, stroke)
    }

    private fun drawPhone(c: Canvas) {
        c.drawRoundRect(RectF(28f, 8f, 72f, 92f), 9f, 9f, stroke)
        c.drawLine(44f, 18f, 56f, 18f, stroke)
        c.drawCircle(50f, 82f, 3f, stroke)
    }

    private fun drawCamera(c: Canvas) {
        // Top tab
        val tab = Path().apply {
            moveTo(38f, 30f)
            lineTo(44f, 22f)
            lineTo(56f, 22f)
            lineTo(62f, 30f)
        }
        c.drawPath(tab, stroke)
        // Body
        c.drawRoundRect(RectF(15f, 30f, 85f, 82f), 6f, 6f, stroke)
        // Lens
        c.drawCircle(50f, 56f, 16f, stroke)
        c.drawCircle(50f, 56f, 8f, stroke)
    }

    private fun drawKey(c: Canvas) {
        c.drawCircle(28f, 50f, 14f, stroke)
        c.drawLine(42f, 50f, 88f, 50f, stroke)
        // notches
        c.drawLine(72f, 50f, 72f, 60f, stroke)
        c.drawLine(82f, 50f, 82f, 62f, stroke)
        // hole in head
        c.drawCircle(28f, 50f, 5f, stroke)
    }

    private fun drawPerson(c: Canvas) {
        c.drawCircle(50f, 32f, 14f, stroke)
        // Body — half-arc
        val arc = Path().apply {
            moveTo(20f, 90f)
            cubicTo(20f, 60f, 80f, 60f, 80f, 90f)
        }
        c.drawPath(arc, stroke)
    }

    private fun drawPeople(c: Canvas) {
        // Person 1 (left)
        c.drawCircle(38f, 36f, 11f, stroke)
        val arc1 = Path().apply {
            moveTo(15f, 88f)
            cubicTo(15f, 62f, 60f, 62f, 60f, 88f)
        }
        c.drawPath(arc1, stroke)
        // Person 2 (right, smaller, behind)
        c.drawCircle(66f, 30f, 9f, stroke)
        val arc2 = Path().apply {
            moveTo(56f, 80f)
            cubicTo(56f, 60f, 88f, 60f, 88f, 80f)
        }
        c.drawPath(arc2, stroke)
    }

    private fun drawWarning(c: Canvas) {
        val p = Path().apply {
            moveTo(50f, 12f)
            lineTo(90f, 84f)
            lineTo(10f, 84f)
            close()
        }
        c.drawPath(p, stroke)
        c.drawLine(50f, 38f, 50f, 64f, stroke)
        c.drawCircle(50f, 74f, 2.5f, fill)
    }

    private fun drawCheck(c: Canvas) {
        val p = Path().apply {
            moveTo(18f, 52f)
            lineTo(40f, 76f)
            lineTo(84f, 28f)
        }
        c.drawPath(p, stroke)
    }

    private fun drawX(c: Canvas) {
        c.drawLine(22f, 22f, 78f, 78f, stroke)
        c.drawLine(78f, 22f, 22f, 78f, stroke)
    }

    private fun drawDot(c: Canvas, dotColor: Int) {
        val saved = fill.color
        fill.color = dotColor
        c.drawCircle(50f, 50f, 28f, fill)
        fill.color = saved
    }

    private fun drawChat(c: Canvas) {
        val body = RectF(15f, 18f, 85f, 68f)
        c.drawRoundRect(body, 10f, 10f, stroke)
        // Tail
        val tail = Path().apply {
            moveTo(28f, 68f)
            lineTo(28f, 84f)
            lineTo(46f, 68f)
        }
        c.drawPath(tail, stroke)
        // Inner dots
        c.drawCircle(35f, 43f, 3f, fill)
        c.drawCircle(50f, 43f, 3f, fill)
        c.drawCircle(65f, 43f, 3f, fill)
    }

    private fun drawClipboard(c: Canvas) {
        c.drawRoundRect(RectF(22f, 18f, 78f, 92f), 6f, 6f, stroke)
        c.drawRoundRect(RectF(36f, 10f, 64f, 24f), 3f, 3f, stroke)
        c.drawLine(34f, 42f, 66f, 42f, stroke)
        c.drawLine(34f, 56f, 66f, 56f, stroke)
        c.drawLine(34f, 70f, 60f, 70f, stroke)
    }

    private fun drawBell(c: Canvas) {
        val bell = Path().apply {
            moveTo(28f, 68f)
            cubicTo(28f, 28f, 72f, 28f, 72f, 68f)
            lineTo(78f, 76f)
            lineTo(22f, 76f)
            close()
        }
        c.drawPath(bell, stroke)
        // Top knob
        c.drawCircle(50f, 22f, 4f, stroke)
        // Clapper
        val clapper = Path().apply {
            moveTo(44f, 80f)
            cubicTo(44f, 90f, 56f, 90f, 56f, 80f)
        }
        c.drawPath(clapper, stroke)
    }

    private fun drawNoEntry(c: Canvas) {
        c.drawCircle(50f, 50f, 36f, stroke)
        c.drawLine(25f, 25f, 75f, 75f, stroke)
    }

    private fun drawPencil(c: Canvas) {
        // Diagonal pencil (top-right to bottom-left)
        val tip = Path().apply {
            moveTo(78f, 14f)
            lineTo(86f, 22f)
            lineTo(32f, 76f)
            lineTo(20f, 80f)
            lineTo(24f, 68f)
            close()
        }
        c.drawPath(tip, stroke)
        // Tip point
        c.drawLine(20f, 80f, 32f, 76f, stroke)
    }

    private fun drawSearch(c: Canvas) {
        c.drawCircle(40f, 40f, 22f, stroke)
        c.drawLine(56f, 56f, 84f, 84f, stroke)
    }

    private fun drawGlobe(c: Canvas) {
        c.drawCircle(50f, 50f, 36f, stroke)
        // Vertical meridian
        c.drawLine(50f, 14f, 50f, 86f, stroke)
        // Horizontal equator
        c.drawLine(14f, 50f, 86f, 50f, stroke)
        // Curved meridians
        val left = Path().apply {
            moveTo(50f, 14f)
            cubicTo(20f, 35f, 20f, 65f, 50f, 86f)
        }
        val right = Path().apply {
            moveTo(50f, 14f)
            cubicTo(80f, 35f, 80f, 65f, 50f, 86f)
        }
        c.drawPath(left, stroke)
        c.drawPath(right, stroke)
    }

    private fun drawArrowRight(c: Canvas) {
        c.drawLine(20f, 50f, 78f, 50f, stroke)
        c.drawLine(60f, 32f, 80f, 50f, stroke)
        c.drawLine(60f, 68f, 80f, 50f, stroke)
    }

    private fun drawPlus(c: Canvas) {
        c.drawLine(20f, 50f, 80f, 50f, stroke)
        c.drawLine(50f, 20f, 50f, 80f, stroke)
    }

    private fun drawTrash(c: Canvas) {
        // Lid handle
        c.drawLine(38f, 18f, 62f, 18f, stroke)
        // Lid
        c.drawLine(20f, 26f, 80f, 26f, stroke)
        // Body
        val body = Path().apply {
            moveTo(28f, 26f)
            lineTo(32f, 88f)
            lineTo(68f, 88f)
            lineTo(72f, 26f)
        }
        c.drawPath(body, stroke)
        // Vertical lines inside
        c.drawLine(42f, 38f, 42f, 78f, stroke)
        c.drawLine(50f, 38f, 50f, 78f, stroke)
        c.drawLine(58f, 38f, 58f, 78f, stroke)
    }

    private fun drawEnvelope(c: Canvas) {
        c.drawRoundRect(RectF(15f, 28f, 85f, 76f), 4f, 4f, stroke)
        val flap = Path().apply {
            moveTo(15f, 32f)
            lineTo(50f, 58f)
            lineTo(85f, 32f)
        }
        c.drawPath(flap, stroke)
    }

    private fun drawGear(c: Canvas) {
        // Center circle
        c.drawCircle(50f, 50f, 12f, stroke)
        // 8 teeth via lines
        val teeth = 8
        for (i in 0 until teeth) {
            val a = Math.PI * 2 * i / teeth
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            val x1 = 50f + 22f * cos
            val y1 = 50f + 22f * sin
            val x2 = 50f + 36f * cos
            val y2 = 50f + 36f * sin
            c.drawLine(x1, y1, x2, y2, stroke)
        }
    }

    private fun drawMoon(c: Canvas) {
        // Crescent via path
        val p = Path().apply {
            addCircle(50f, 50f, 36f, Path.Direction.CW)
            val cut = Path().apply {
                addCircle(64f, 42f, 32f, Path.Direction.CW)
            }
            op(cut, Path.Op.DIFFERENCE)
        }
        c.drawPath(p, stroke)
    }

    private fun drawSun(c: Canvas) {
        c.drawCircle(50f, 50f, 16f, stroke)
        val rays = 8
        for (i in 0 until rays) {
            val a = Math.PI * 2 * i / rays
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            val x1 = 50f + 24f * cos
            val y1 = 50f + 24f * sin
            val x2 = 50f + 36f * cos
            val y2 = 50f + 36f * sin
            c.drawLine(x1, y1, x2, y2, stroke)
        }
    }

    private fun drawRefresh(c: Canvas) {
        // 3/4 circle arc
        val arc = RectF(20f, 20f, 80f, 80f)
        c.drawArc(arc, -45f, 270f, false, stroke)
        // Arrowhead pointing up at top-right
        c.drawLine(80f, 30f, 80f, 14f, stroke)
        c.drawLine(80f, 14f, 66f, 14f, stroke)
    }

    private fun drawDoor(c: Canvas) {
        c.drawRoundRect(RectF(22f, 12f, 78f, 92f), 4f, 4f, stroke)
        c.drawCircle(66f, 54f, 3f, fill)
    }

    private fun drawClip(c: Canvas) {
        // Paperclip shape
        val p = Path().apply {
            moveTo(70f, 30f)
            cubicTo(70f, 16f, 50f, 16f, 50f, 30f)
            lineTo(50f, 76f)
            cubicTo(50f, 86f, 36f, 86f, 36f, 76f)
            lineTo(36f, 36f)
            cubicTo(36f, 28f, 50f, 28f, 50f, 36f)
            lineTo(50f, 70f)
        }
        c.drawPath(p, stroke)
    }

    private fun drawWave(c: Canvas) {
        // Hand outline (4 fingers + thumb)
        for (i in 0..3) {
            val x = 32f + i * 10f
            c.drawLine(x, 28f + i * 2f, x, 64f, stroke)
        }
        // Palm bottom
        val palm = Path().apply {
            moveTo(28f, 64f)
            cubicTo(28f, 88f, 72f, 88f, 72f, 64f)
        }
        c.drawPath(palm, stroke)
        // Thumb
        c.drawLine(72f, 50f, 80f, 60f, stroke)
    }

    private fun drawPlane(c: Canvas) {
        // Paper-plane / send icon
        val p = Path().apply {
            moveTo(14f, 50f)
            lineTo(86f, 18f)
            lineTo(60f, 86f)
            lineTo(48f, 56f)
            close()
        }
        c.drawPath(p, stroke)
        c.drawLine(48f, 56f, 86f, 18f, stroke)
    }

    private fun drawStop(c: Canvas) {
        c.drawRoundRect(RectF(24f, 24f, 76f, 76f), 6f, 6f, fill)
    }

    private fun drawReply(c: Canvas) {
        // Curved arrow pointing left and down
        c.drawLine(20f, 50f, 50f, 50f, stroke)
        c.drawLine(20f, 50f, 32f, 38f, stroke)
        c.drawLine(20f, 50f, 32f, 62f, stroke)
        val curve = Path().apply {
            moveTo(50f, 50f)
            cubicTo(78f, 50f, 80f, 86f, 50f, 86f)
        }
        c.drawPath(curve, stroke)
    }
}

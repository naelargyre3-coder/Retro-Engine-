package com.retroengine.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.hypot
import kotlin.math.min

private val DARK_OFF = 0xAA30303A.toInt()
private val DARK_ON = 0xEE62627A.toInt()
private val RIM = 0xAAFFFFFF.toInt()
private val ARROW = 0xCCFFFFFF.toInt()
private val RED_LIGHT = 0xFFFF7A7A.toInt()
private val RED_MID = 0xFFD32F2F.toInt()
private val RED_DARK = 0xFF7F1414.toInt()

class TouchOverlay(
    context: Context,
    private val onKey: (keyCode: Int, pressed: Boolean) -> Unit
) : View(context) {

    private class RoundButton(val key: Int, val label: String) {
        var cx = 0f
        var cy = 0f
        var r = 0f
        var shader: Shader? = null
    }

    private class PillButton(val key: Int, val label: String) {
        val rect = RectF()
    }

    private val buttonA = RoundButton(KeyEvent.KEYCODE_BUTTON_A, "A")
    private val buttonB = RoundButton(KeyEvent.KEYCODE_BUTTON_B, "B")
    private val select = PillButton(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT")
    private val start = PillButton(KeyEvent.KEYCODE_BUTTON_START, "START")

    private var padX = 0f
    private var padY = 0f
    private var padR = 0f
    private var insetL = 0f
    private var insetR = 0f
    private var insetB = 0f
    private var pressedKeys: Set<Int> = emptySet()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ARROW }
    private val path = Path()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            insetL = bars.left.toFloat()
            insetR = bars.right.toFloat()
            insetB = bars.bottom.toFloat()
            layoutControls()
            invalidate()
            insets
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutControls()
    }

    private fun makeShader(b: RoundButton): Shader =
        RadialGradient(
            b.cx - b.r * 0.3f, b.cy - b.r * 0.3f, b.r * 1.3f,
            intArrayOf(RED_LIGHT, RED_MID, RED_DARK),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

    private fun layoutControls() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val landscape = w > h
        val left = insetL
        val right = w - insetR
        val bottom = h - insetB
        val u = min(w, h)
        val margin = if (landscape) 0.06f * u else 0.14f * u
        val side = 0.05f * u

        padR = 0.18f * u
        padX = left + side + padR
        padY = bottom - margin - padR

        val r = 0.095f * u
        buttonA.r = r
        buttonA.cx = right - side - r
        buttonA.cy = bottom - margin - 2.3f * r
        buttonB.r = r
        buttonB.cx = buttonA.cx - 2.3f * r
        buttonB.cy = bottom - margin - r
        buttonA.shader = makeShader(buttonA)
        buttonB.shader = makeShader(buttonB)

        val pillW = 0.2f * u
        val pillH = 0.07f * u
        if (landscape) {
            val selY = padY - padR - 0.1f * u
            select.rect.set(padX - pillW / 2, selY - pillH / 2, padX + pillW / 2, selY + pillH / 2)
            val startX = (buttonA.cx + buttonB.cx) / 2
            val startY = buttonA.cy - r - 0.1f * u
            start.rect.set(startX - pillW / 2, startY - pillH / 2, startX + pillW / 2, startY + pillH / 2)
        } else {
            val pillY = bottom - 0.06f * u - pillH / 2
            val gap = 0.03f * u
            val cx = (left + right) / 2
            select.rect.set(cx - gap - pillW, pillY - pillH / 2, cx - gap, pillY + pillH / 2)
            start.rect.set(cx + gap, pillY - pillH / 2, cx + gap + pillW, pillY + pillH / 2)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ending = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        val skipIndex =
            if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        val now = HashSet<Int>()
        if (!ending) {
            for (i in 0 until event.pointerCount) {
                if (i != skipIndex) collect(event.getX(i), event.getY(i), now)
            }
        }
        update(now)
        return true
    }

    private fun collect(x: Float, y: Float, out: MutableSet<Int>) {
        val dx = x - padX
        val dy = y - padY
        val dist = hypot(dx, dy)
        if (dist <= padR * 1.3f && dist > padR * 0.12f) {
            val t = padR * 0.3f
            if (dx < -t) out.add(KeyEvent.KEYCODE_DPAD_LEFT)
            if (dx > t) out.add(KeyEvent.KEYCODE_DPAD_RIGHT)
            if (dy < -t) out.add(KeyEvent.KEYCODE_DPAD_UP)
            if (dy > t) out.add(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        var best: RoundButton? = null
        var bestDist = Float.MAX_VALUE
        for (b in listOf(buttonA, buttonB)) {
            val d = hypot(x - b.cx, y - b.cy)
            if (d <= b.r * 1.35f && d < bestDist) {
                best = b
                bestDist = d
            }
        }
        if (best != null) out.add(best.key)
        for (p in listOf(select, start)) {
            val zone = RectF(p.rect)
            zone.inset(-p.rect.width() * 0.1f, -p.rect.height() * 0.35f)
            if (zone.contains(x, y)) out.add(p.key)
        }
    }

    private fun update(now: Set<Int>) {
        for (k in now) if (k !in pressedKeys) onKey(k, true)
        for (k in pressedKeys) if (k !in now) onKey(k, false)
        if (now != pressedKeys) {
            pressedKeys = now
            invalidate()
        }
    }

    private fun arrow(canvas: Canvas, cx: Float, cy: Float, size: Float, dx: Float, dy: Float) {
        val px = -dy
        val py = dx
        path.reset()
        path.moveTo(cx + dx * size, cy + dy * size)
        path.lineTo(cx - dx * size * 0.6f + px * size, cy - dy * size * 0.6f + py * size)
        path.lineTo(cx - dx * size * 0.6f - px * size, cy - dy * size * 0.6f - py * size)
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val u = min(width, height).toFloat()
        stroke.strokeWidth = 0.006f * u
        stroke.color = RIM
        val th = padR * 0.62f
        val corner = th * 0.22f
        val mid = (th / 2 + padR) / 2

        fun arm(key: Int, l: Float, t: Float, r: Float, b: Float) {
            fill.color = if (key in pressedKeys) DARK_ON else DARK_OFF
            canvas.drawRoundRect(l, t, r, b, corner, corner, fill)
            canvas.drawRoundRect(l, t, r, b, corner, corner, stroke)
        }
        arm(KeyEvent.KEYCODE_DPAD_UP, padX - th / 2, padY - padR, padX + th / 2, padY - th / 2)
        arm(KeyEvent.KEYCODE_DPAD_DOWN, padX - th / 2, padY + th / 2, padX + th / 2, padY + padR)
        arm(KeyEvent.KEYCODE_DPAD_LEFT, padX - padR, padY - th / 2, padX - th / 2, padY + th / 2)
        arm(KeyEvent.KEYCODE_DPAD_RIGHT, padX + th / 2, padY - th / 2, padX + padR, padY + th / 2)
        arm(-1, padX - th / 2, padY - th / 2, padX + th / 2, padY + th / 2)
        arrow(canvas, padX, padY - mid, th * 0.22f, 0f, -1f)
        arrow(canvas, padX, padY + mid, th * 0.22f, 0f, 1f)
        arrow(canvas, padX - mid, padY, th * 0.22f, -1f, 0f)
        arrow(canvas, padX + mid, padY, th * 0.22f, 1f, 0f)

        text.textSize = 0.06f * u
        for (b in listOf(buttonA, buttonB)) {
            val on = b.key in pressedKeys
            canvas.save()
            if (on) canvas.scale(0.94f, 0.94f, b.cx, b.cy)
            fill.shader = b.shader
            fill.alpha = if (on) 255 else 200
            canvas.drawCircle(b.cx, b.cy, b.r, fill)
            fill.shader = null
            canvas.drawCircle(b.cx, b.cy, b.r, stroke)
            canvas.drawText(b.label, b.cx, b.cy + text.textSize / 3, text)
            canvas.restore()
        }

        text.textSize = 0.032f * u
        for (p in listOf(select, start)) {
            fill.color = if (p.key in pressedKeys) DARK_ON else DARK_OFF
            val radius = p.rect.height() / 2
            canvas.drawRoundRect(p.rect, radius, radius, fill)
            canvas.drawRoundRect(p.rect, radius, radius, stroke)
            canvas.drawText(p.label, p.rect.centerX(), p.rect.centerY() + text.textSize / 3, text)
        }
    }
}

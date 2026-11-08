package com.retroengine.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

private val WHITE_OFF = 0x55FFFFFF
private val WHITE_ON = 0xAAFFFFFF.toInt()
private val RED_OFF = 0x99D32F2F.toInt()
private val RED_ON = 0xDDFF5252.toInt()
private val GRAY_OFF = 0x88444444.toInt()
private val GRAY_ON = 0xCC888888.toInt()

class TouchOverlay(
    context: Context,
    private val onKey: (keyCode: Int, pressed: Boolean) -> Unit
) : View(context) {

    private class RoundButton(val key: Int, val label: String) {
        var cx = 0f
        var cy = 0f
        var r = 0f
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
    private var pressedKeys: Set<Int> = emptySet()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w.toFloat()
        val height = h.toFloat()
        val u = min(width, height)
        val margin = 0.14f * u
        val side = 0.05f * u

        padR = 0.18f * u
        padX = side + padR
        padY = height - margin - padR

        val r = 0.095f * u
        buttonA.r = r
        buttonA.cx = width - side - r
        buttonA.cy = height - margin - 2.3f * r
        buttonB.r = r
        buttonB.cx = buttonA.cx - 2.3f * r
        buttonB.cy = height - margin - r

        val pillW = 0.2f * u
        val pillH = 0.07f * u
        val pillY = height - 0.06f * u - pillH / 2
        val gap = 0.03f * u
        select.rect.set(width / 2 - gap - pillW, pillY - pillH / 2, width / 2 - gap, pillY + pillH / 2)
        start.rect.set(width / 2 + gap, pillY - pillH / 2, width / 2 + gap + pillW, pillY + pillH / 2)
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

    override fun onDraw(canvas: Canvas) {
        val u = min(width, height).toFloat()
        val th = padR * 0.62f
        val corner = th * 0.18f

        fun arm(key: Int, l: Float, t: Float, r: Float, b: Float) {
            fill.color = if (key in pressedKeys) WHITE_ON else WHITE_OFF
            canvas.drawRoundRect(l, t, r, b, corner, corner, fill)
        }
        arm(KeyEvent.KEYCODE_DPAD_UP, padX - th / 2, padY - padR, padX + th / 2, padY - th / 2)
        arm(KeyEvent.KEYCODE_DPAD_DOWN, padX - th / 2, padY + th / 2, padX + th / 2, padY + padR)
        arm(KeyEvent.KEYCODE_DPAD_LEFT, padX - padR, padY - th / 2, padX - th / 2, padY + th / 2)
        arm(KeyEvent.KEYCODE_DPAD_RIGHT, padX + th / 2, padY - th / 2, padX + padR, padY + th / 2)
        arm(-1, padX - th / 2, padY - th / 2, padX + th / 2, padY + th / 2)

        text.textSize = 0.06f * u
        for (b in listOf(buttonA, buttonB)) {
            fill.color = if (b.key in pressedKeys) RED_ON else RED_OFF
            canvas.drawCircle(b.cx, b.cy, b.r, fill)
            canvas.drawText(b.label, b.cx, b.cy + text.textSize / 3, text)
        }

        text.textSize = 0.032f * u
        for (p in listOf(select, start)) {
            fill.color = if (p.key in pressedKeys) GRAY_ON else GRAY_OFF
            val radius = p.rect.height() / 2
            canvas.drawRoundRect(p.rect, radius, radius, fill)
            canvas.drawText(p.label, p.rect.centerX(), p.rect.centerY() + text.textSize / 3, text)
        }
    }
}

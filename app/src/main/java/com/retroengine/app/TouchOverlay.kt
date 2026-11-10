package com.retroengine.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.hypot
import kotlin.math.min

private val DARK_OFF = 0xAA30303A.toInt()
private val DARK_ON = 0xEE62627A.toInt()
private val RIM = 0xBBFFFFFF.toInt()
private val ARROW_OFF = 0xDD202128.toInt()
private val ARROW_ON = 0xFFFFFFFF.toInt()
private val SHADOW = 0x66000000
private val HIGHLIGHT = 0x44FFFFFF
private val PRESS_OVERLAY = 0x66FFFFFF
private val RED_LIGHT = 0xFFFF7A7A.toInt()
private val RED_MID = 0xFFD32F2F.toInt()
private val RED_DARK = 0xFF7F1414.toInt()
private val PAD_OUT_TOP = 0xE09A9CA8.toInt()
private val PAD_OUT_BOTTOM = 0xE04A4C56.toInt()
private val PAD_IN_TOP = 0xE07F818D.toInt()
private val PAD_IN_BOTTOM = 0xE05B5D69.toInt()
private val CENTER_IN = 0xFF1A1B21.toInt()
private val CENTER_OUT = 0xFF4A4B55.toInt()

class TouchOverlay(
    context: Context,
    private val skin: ConsoleSkin = GameConsole.NES.skin,
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

    private val armKeys = intArrayOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT
    )
    private val armRects = Array(4) { RectF() }
    private var plusOuter = Path()
    private var plusInner = Path()
    private var outerShader: Shader? = null
    private var innerShader: Shader? = null
    private var centerShader: Shader? = null

    private var padX = 0f
    private var padY = 0f
    private var padR = 0f
    private var insetL = 0f
    private var insetR = 0f
    private var insetB = 0f
    private var pressedKeys: Set<Int> = emptySet()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            insetL = cut.left.toFloat()
            insetR = cut.right.toFloat()
            insetB = cut.bottom.toFloat()
            layoutControls()
            invalidate()
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val window = (context as? Activity)?.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, this)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutControls()
    }
    private fun colorsFor(b: RoundButton): ButtonColors =
        if (b.key == KeyEvent.KEYCODE_BUTTON_A) skin.a else skin.b
   
    private fun makeShader(b: RoundButton): Shader =
        RadialGradient(
            b.cx - b.r * 0.3f, b.cy - b.r * 0.3f, b.r * 1.3f,
            intArrayOf(colorsFor(b).light, colorsFor(b).mid, colorsFor(b).dark),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

    private fun plusPath(cx: Float, cy: Float, half: Float, t: Float, corner: Float): Path {
        val vert = Path()
        vert.addRoundRect(
            RectF(cx - t / 2, cy - half, cx + t / 2, cy + half),
            corner, corner, Path.Direction.CW
        )
        val horiz = Path()
        horiz.addRoundRect(
            RectF(cx - half, cy - t / 2, cx + half, cy + t / 2),
            corner, corner, Path.Direction.CW
        )
        vert.op(horiz, Path.Op.UNION)
        return vert
    }

    private fun layoutControls() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val landscape = w > h
        val left = insetL
        val right = w - insetR
        val bottom = h - insetB
        val u = min(w, h)
        val margin = if (landscape) 0.07f * u else 0.14f * u
        val side = 0.05f * u

        padR = (if (landscape) 0.21f else 0.19f) * u
        padX = left + side + padR
        padY = bottom - margin - padR

        val th = padR * 0.62f
        armRects[0].set(padX - th / 2, padY - padR, padX + th / 2, padY - th / 2)
        armRects[1].set(padX - th / 2, padY + th / 2, padX + th / 2, padY + padR)
        armRects[2].set(padX - padR, padY - th / 2, padX - th / 2, padY + th / 2)
        armRects[3].set(padX + th / 2, padY - th / 2, padX + padR, padY + th / 2)

        plusOuter = plusPath(padX, padY, padR, th, th * 0.3f)
        plusInner = plusPath(padX, padY, padR * 0.93f, th * 0.78f, th * 0.24f)
        outerShader = LinearGradient(
            0f, padY - padR, 0f, padY + padR,
            PAD_OUT_TOP, PAD_OUT_BOTTOM, Shader.TileMode.CLAMP
        )
        innerShader = LinearGradient(
            0f, padY - padR, 0f, padY + padR,
            PAD_IN_TOP, PAD_IN_BOTTOM, Shader.TileMode.CLAMP
        )
        centerShader = RadialGradient(
            padX, padY, th * 0.3f,
            CENTER_IN, CENTER_OUT, Shader.TileMode.CLAMP
        )

        val r = 0.09f * u
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
        if (false) {
            val rowY = min(padY - padR, buttonA.cy - r) - 0.1f * u
            select.rect.set(padX - pillW / 2, rowY - pillH / 2, padX + pillW / 2, rowY + pillH / 2)
            val startX = (buttonA.cx + buttonB.cx) / 2
            start.rect.set(startX - pillW / 2, rowY - pillH / 2, startX + pillW / 2, rowY + pillH / 2)
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
        if (dist <= padR * 1.05f && dist > padR * 0.12f) {
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
    fun releaseAll() {
        update(emptySet())
    }
    private fun update(now: Set<Int>) {
        for (k in now) if (k !in pressedKeys) onKey(k, true)
        for (k in pressedKeys) if (k !in now) onKey(k, false)
        if (now != pressedKeys) {
            pressedKeys = now
            invalidate()
        }
    }

    private fun arrow(
        canvas: Canvas, cx: Float, cy: Float, size: Float,
        dx: Float, dy: Float, key: Int
    ) {
        arrowPaint.color = if (key in pressedKeys) ARROW_ON else ARROW_OFF
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
        val mid = (th / 2 + padR) / 2

        // Croix directionnelle : ombre, métal, biseau, creux central, flèches
        fill.shader = null
        fill.color = SHADOW
        canvas.save()
        canvas.translate(0f, 0.012f * u)
        canvas.drawPath(plusOuter, fill)
        canvas.restore()

        fill.shader = outerShader
        canvas.drawPath(plusOuter, fill)
        fill.shader = innerShader
        canvas.drawPath(plusInner, fill)
        fill.shader = null
        canvas.drawPath(plusOuter, stroke)

        for (i in 0 until 4) {
            if (armKeys[i] in pressedKeys) {
                canvas.save()
                canvas.clipPath(plusOuter)
                fill.color = PRESS_OVERLAY
                canvas.drawRect(armRects[i], fill)
                canvas.restore()
            }
        }

        fill.shader = centerShader
        canvas.drawCircle(padX, padY, th * 0.3f, fill)
        fill.shader = null

        arrow(canvas, padX, padY - mid, th * 0.2f, 0f, -1f, KeyEvent.KEYCODE_DPAD_UP)
        arrow(canvas, padX, padY + mid, th * 0.2f, 0f, 1f, KeyEvent.KEYCODE_DPAD_DOWN)
        arrow(canvas, padX - mid, padY, th * 0.2f, -1f, 0f, KeyEvent.KEYCODE_DPAD_LEFT)
        arrow(canvas, padX + mid, padY, th * 0.2f, 1f, 0f, KeyEvent.KEYCODE_DPAD_RIGHT)

        // Boutons A et B
        text.textSize = 0.065f * u
        for (b in listOf(buttonA, buttonB)) {
            val on = b.key in pressedKeys
            canvas.save()
            if (on) canvas.scale(0.94f, 0.94f, b.cx, b.cy)
            fill.shader = null
            fill.color = SHADOW
            canvas.drawCircle(b.cx, b.cy + b.r * 0.1f, b.r, fill)
            fill.shader = b.shader
            fill.alpha = if (on) 255 else 215
            canvas.drawCircle(b.cx, b.cy, b.r, fill)
            fill.shader = null
            canvas.drawCircle(b.cx, b.cy, b.r, stroke)
            fill.color = HIGHLIGHT
            canvas.drawOval(
                b.cx - b.r * 0.62f, b.cy - b.r * 0.9f,
                b.cx + b.r * 0.62f, b.cy - b.r * 0.25f, fill
            )
            canvas.drawText(b.label, b.cx, b.cy + text.textSize / 3, text)
            canvas.restore()
        }

        // Select et Start
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

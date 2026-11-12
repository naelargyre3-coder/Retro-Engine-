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

interface PadOverlay {
    fun releaseAll()
}

private const val C_UP = 2001
private const val C_DOWN = 2002
private const val C_LEFT = 2003
private const val C_RIGHT = 2004

private val N_DARK_OFF = 0xAA30303A.toInt()
private val N_DARK_ON = 0xEE62627A.toInt()
private val N_RIM = 0xBBFFFFFF.toInt()
private val N_SHADOW = 0x66000000
private val N_HIGHLIGHT = 0x44FFFFFF
private val N_PAD_TOP = 0xE09A9CA8.toInt()
private val N_PAD_BOTTOM = 0xE04A4C56.toInt()
private val N_ARROW_OFF = 0xDD202128.toInt()
private val N_ARROW_ON = 0xFFFFFFFF.toInt()
private val N_PRESS = 0x66FFFFFF
private val N_BASE_TOP = 0xCC3A3B45.toInt()
private val N_BASE_BOTTOM = 0xCC15151A.toInt()
private val N_KNOB_LIGHT = 0xFFB0B0BC.toInt()
private val N_KNOB_DARK = 0xFF3A3A44.toInt()

class N64TouchOverlay(
    context: Context,
    private val skin: ConsoleSkin,
    private val onKey: (keyCode: Int, pressed: Boolean) -> Unit,
    private val onStick: (right: Boolean, x: Float, y: Float) -> Unit
) : View(context), PadOverlay, EditableOverlay {

    private class Round(val key: Int, val label: String, val colors: ButtonColors) {
        var cx = 0f
        var cy = 0f
        var r = 0f
        var shader: Shader? = null
    }

    private class Pill(val key: Int, val label: String) {
        val rect = RectF()
    }

    // Touches envoyees au moteur : A = B libretro, B = Y libretro, Z = L2
    private val buttonA = Round(KeyEvent.KEYCODE_BUTTON_B, "A", skin.a)
    private val buttonB = Round(KeyEvent.KEYCODE_BUTTON_Y, "B", skin.b)
    private val faces = listOf(buttonA, buttonB)
    private val cUp = Round(C_UP, "", skin.x)
    private val cDown = Round(C_DOWN, "", skin.x)
    private val cLeft = Round(C_LEFT, "", skin.x)
    private val cRight = Round(C_RIGHT, "", skin.x)
    private val cButtons = listOf(cUp, cDown, cLeft, cRight)
    private val start = Pill(KeyEvent.KEYCODE_BUTTON_START, "START")
    private val lBtn = Pill(KeyEvent.KEYCODE_BUTTON_L1, "L")
    private val zBtn = Pill(KeyEvent.KEYCODE_BUTTON_L2, "Z")
    private val rBtn = Pill(KeyEvent.KEYCODE_BUTTON_R1, "R")
    private val pills = listOf(start, lBtn, zBtn, rBtn)

    private val armKeys = intArrayOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
    )
    private val armRects = Array(4) { RectF() }
    private var plus = Path()
    private var plusShader: Shader? = null

    private var dX = 0f
    private var dY = 0f
    private var dR = 0f

    private var sx = 0f
    private var sy = 0f
    private var sr = 0f
    private var baseShader: Shader? = null
    private var knobShader: Shader? = null
    private var stickId = -1
    private var stickX = 0f
    private var stickY = 0f

    private var insetL = 0f
    private var insetR = 0f
    private var insetB = 0f
    private var pressedKeys: Set<Int> = emptySet()

    private var adjust: ControlAdjust? = null
    private var adjustLandscape = false
    private val editor = OverlayEditor(this) {
        layoutControls()
        invalidate()
    }

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

    // ---------- Edition ----------

    override fun setEditMode(on: Boolean) {
        editor.active = on
        if (on) releaseAll()
        invalidate()
    }

    override fun scaleSelected(factor: Float) {
        editor.scaleSelected(factor)
    }

    override fun resetAdjust() {
        editor.reset()
    }

    override fun setSelectionListener(listener: (String?) -> Unit) {
        editor.listener = listener
    }

    private fun adjustFor(landscape: Boolean): ControlAdjust {
        val current = adjust
        if (current != null && adjustLandscape == landscape) return current
        val created = ControlAdjust(context, ButtonSet.N64, landscape)
        adjust = created
        adjustLandscape = landscape
        editor.adjust = created
        return created
    }

    private fun adjustRounds(list: List<Round>, g: ControlAdjust.G, u: Float) {
        val mx = list.map { it.cx }.average().toFloat()
        val my = list.map { it.cy }.average().toFloat()
        for (b in list) {
            b.cx = mx + (b.cx - mx) * g.scale + g.dx * u
            b.cy = my + (b.cy - my) * g.scale + g.dy * u
            b.r *= g.scale
        }
    }

    private fun adjustPill(p: Pill, g: ControlAdjust.G, u: Float) {
        val cx = p.rect.centerX() + g.dx * u
        val cy = p.rect.centerY() + g.dy * u
        val w = p.rect.width() * g.scale
        val h = p.rect.height() * g.scale
        p.rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    // ---------- Disposition ----------

    private fun makeShader(b: Round): Shader =
        RadialGradient(
            b.cx - b.r * 0.3f, b.cy - b.r * 0.3f, b.r * 1.3f,
            intArrayOf(b.colors.light, b.colors.mid, b.colors.dark),
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
        val side = 0.04f * u
        val margin = if (landscape) 0.06f * u else 0.10f * u
        editor.unit = u
        val adj = adjustFor(landscape)

        // Positions par defaut : stick et croix
        val sr0 = (if (landscape) 0.15f else 0.13f) * u
        val sx0 = left + side + sr0
        val sy0 = bottom - margin - sr0
        val dR0 = (if (landscape) 0.13f else 0.105f) * u
        val dX0 = sx0
        val dY0 = sy0 - sr0 - 0.04f * u - dR0

        // A et B
        val rA = (if (landscape) 0.095f else 0.085f) * u
        val rightMargin = (if (landscape) 0.12f else 0.14f) * u
        buttonA.r = rA
        buttonA.cx = right - side - rA
        buttonA.cy = bottom - rightMargin - rA
        buttonB.r = rA
        buttonB.cx = buttonA.cx - 2.2f * rA
        buttonB.cy = buttonA.cy - 0.9f * rA

        // Les quatre boutons C en losange
        val rC = (if (landscape) 0.058f else 0.05f) * u
        val offC = 1.7f * rC
        val ccx = right - side - offC - rC
        val ccy = buttonA.cy - rA - 0.09f * u - offC - rC
        cUp.cx = ccx; cUp.cy = ccy - offC
        cDown.cx = ccx; cDown.cy = ccy + offC
        cLeft.cx = ccx - offC; cLeft.cy = ccy
        cRight.cx = ccx + offC; cRight.cy = ccy
        for (b in cButtons) b.r = rC

        // Start en bas au centre
        val pillW = 0.2f * u
        val pillH = 0.07f * u
        val pillY = bottom - 0.05f * u - pillH / 2
        val cx = (left + right) / 2
        start.rect.set(cx - pillW / 2, pillY - pillH / 2, cx + pillW / 2, pillY + pillH / 2)

        // L, Z et R
        val shW = 0.2f * u
        val shH = 0.1f * u
        if (landscape) {
            val y = 0.05f * u
            lBtn.rect.set(left + side, y, left + side + shW, y + shH)
            zBtn.rect.set(left + side, y + shH + 0.02f * u, left + side + shW, y + 2 * shH + 0.02f * u)
            rBtn.rect.set(right - side - shW, y, right - side, y + shH)
        } else {
            val top = min(dY0 - dR0, ccy - offC - rC) - 0.03f * u - shH
            lBtn.rect.set(left + side, top, left + side + shW, top + shH)
            zBtn.rect.set(left + side + shW + 0.02f * u, top, left + side + 2 * shW + 0.02f * u, top + shH)
            rBtn.rect.set(right - side - shW, top, right - side, top + shH)
        }

        // Reglages de l'utilisateur
        val gs = adj.get("stick")
        sr = sr0 * gs.scale
        sx = sx0 + gs.dx * u
        sy = sy0 + gs.dy * u
        val gd = adj.get("pad")
        dR = dR0 * gd.scale
        dX = dX0 + gd.dx * u
        dY = dY0 + gd.dy * u
        adjustRounds(faces, adj.get("face"), u)
        adjustRounds(cButtons, adj.get("c"), u)
        adjustPill(start, adj.get("start"), u)
        adjustPill(lBtn, adj.get("l"), u)
        adjustPill(zBtn, adj.get("z"), u)
        adjustPill(rBtn, adj.get("r"), u)

        // Formes et degrades
        baseShader = LinearGradient(
            0f, sy - sr, 0f, sy + sr, N_BASE_TOP, N_BASE_BOTTOM, Shader.TileMode.CLAMP
        )
        val kr = sr * 0.5f
        knobShader = RadialGradient(
            -kr * 0.3f, -kr * 0.3f, kr * 1.3f,
            N_KNOB_LIGHT, N_KNOB_DARK, Shader.TileMode.CLAMP
        )
        val th = dR * 0.62f
        armRects[0].set(dX - th / 2, dY - dR, dX + th / 2, dY - th / 2)
        armRects[1].set(dX - th / 2, dY + th / 2, dX + th / 2, dY + dR)
        armRects[2].set(dX - dR, dY - th / 2, dX - th / 2, dY + th / 2)
        armRects[3].set(dX + th / 2, dY - th / 2, dX + dR, dY + th / 2)
        plus = plusPath(dX, dY, dR, th, th * 0.3f)
        plusShader = LinearGradient(
            0f, dY - dR, 0f, dY + dR, N_PAD_TOP, N_PAD_BOTTOM, Shader.TileMode.CLAMP
        )
        for (b in faces) b.shader = makeShader(b)
        for (b in cButtons) b.shader = makeShader(b)

        // Zones de selection pour l'edition
        editor.bounds.clear()
        editor.bounds["stick"] = RectF(sx - sr, sy - sr, sx + sr, sy + sr)
        editor.bounds["pad"] = RectF(dX - dR, dY - dR, dX + dR, dY + dR)
        editor.bounds["face"] = RectF(
            faces.minOf { it.cx - it.r }, faces.minOf { it.cy - it.r },
            faces.maxOf { it.cx + it.r }, faces.maxOf { it.cy + it.r }
        )
        editor.bounds["c"] = RectF(
            cButtons.minOf { it.cx - it.r }, cButtons.minOf { it.cy - it.r },
            cButtons.maxOf { it.cx + it.r }, cButtons.maxOf { it.cy + it.r }
        )
        editor.bounds["start"] = RectF(start.rect)
        editor.bounds["l"] = RectF(lBtn.rect)
        editor.bounds["z"] = RectF(zBtn.rect)
        editor.bounds["r"] = RectF(rBtn.rect)
    }

    // ---------- Touches ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (editor.active) return editor.onTouch(e)
        val ending = e.actionMasked == MotionEvent.ACTION_UP ||
            e.actionMasked == MotionEvent.ACTION_CANCEL
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val now = HashSet<Int>()
        var stickSeen = false
        if (!ending) {
            for (i in 0 until e.pointerCount) {
                if (i == skip) continue
                val id = e.getPointerId(i)
                val x = e.getX(i)
                val y = e.getY(i)
                if (id == stickId || (stickId == -1 && hypot(x - sx, y - sy) <= sr * 1.5f)) {
                    stickId = id
                    stickSeen = true
                    moveStick(x, y)
                } else {
                    collect(x, y, now)
                }
            }
        }
        if (!stickSeen && stickId != -1) {
            stickId = -1
            setStick(0f, 0f)
        }
        update(now)
        return true
    }

    private fun moveStick(x: Float, y: Float) {
        var dx = (x - sx) / sr
        var dy = (y - sy) / sr
        val len = hypot(dx, dy)
        if (len > 1f) {
            dx /= len
            dy /= len
        }
        setStick(dx, dy)
    }

    private fun setStick(nx: Float, ny: Float) {
        if (nx == stickX && ny == stickY) return
        stickX = nx
        stickY = ny
        onStick(false, nx, ny)
        invalidate()
    }

    private fun collect(x: Float, y: Float, out: MutableSet<Int>) {
        val dx = x - dX
        val dy = y - dY
        val dist = hypot(dx, dy)
        if (dist <= dR * 1.1f && dist > dR * 0.12f) {
            val t = dR * 0.3f
            if (dx < -t) out.add(KeyEvent.KEYCODE_DPAD_LEFT)
            if (dx > t) out.add(KeyEvent.KEYCODE_DPAD_RIGHT)
            if (dy < -t) out.add(KeyEvent.KEYCODE_DPAD_UP)
            if (dy > t) out.add(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        var best: Round? = null
        var bestDist = Float.MAX_VALUE
        for (b in faces + cButtons) {
            val d = hypot(x - b.cx, y - b.cy)
            if (d <= b.r * 1.3f && d < bestDist) {
                best = b
                bestDist = d
            }
        }
        if (best != null) out.add(best.key)
        for (p in pills) {
            val zone = RectF(p.rect)
            zone.inset(-p.rect.width() * 0.08f, -p.rect.height() * 0.2f)
            if (zone.contains(x, y)) out.add(p.key)
        }
    }

    private fun isC(key: Int): Boolean = key >= C_UP && key <= C_RIGHT

    private fun update(now: Set<Int>) {
        for (k in now) if (k !in pressedKeys && !isC(k)) onKey(k, true)
        for (k in pressedKeys) if (k !in now && !isC(k)) onKey(k, false)
        val cNow = now.filter { isC(it) }.toSet()
        val cOld = pressedKeys.filter { isC(it) }.toSet()
        if (cNow != cOld) {
            val x = (if (C_RIGHT in cNow) 1f else 0f) - (if (C_LEFT in cNow) 1f else 0f)
            val y = (if (C_DOWN in cNow) 1f else 0f) - (if (C_UP in cNow) 1f else 0f)
            onStick(true, x, y)
        }
        if (now != pressedKeys) {
            pressedKeys = now
            invalidate()
        }
    }

    override fun releaseAll() {
        stickId = -1
        setStick(0f, 0f)
        update(emptySet())
    }

    // ---------- Dessin ----------

    private fun arrow(
        canvas: Canvas, cx: Float, cy: Float, size: Float,
        dx: Float, dy: Float, color: Int
    ) {
        arrowPaint.color = color
        val px = -dy
        val py = dx
        path.reset()
        path.moveTo(cx + dx * size, cy + dy * size)
        path.lineTo(cx - dx * size * 0.6f + px * size, cy - dy * size * 0.6f + py * size)
        path.lineTo(cx - dx * size * 0.6f - px * size, cy - dy * size * 0.6f - py * size)
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    private fun drawRound(canvas: Canvas, b: Round, showLabel: Boolean) {
        val on = b.key in pressedKeys
        canvas.save()
        if (on) canvas.scale(0.94f, 0.94f, b.cx, b.cy)
        fill.shader = null
        fill.color = N_SHADOW
        canvas.drawCircle(b.cx, b.cy + b.r * 0.1f, b.r, fill)
        fill.shader = b.shader
        fill.alpha = if (on) 255 else 215
        canvas.drawCircle(b.cx, b.cy, b.r, fill)
        fill.shader = null
        canvas.drawCircle(b.cx, b.cy, b.r, stroke)
        fill.color = N_HIGHLIGHT
        canvas.drawOval(
            b.cx - b.r * 0.62f, b.cy - b.r * 0.9f,
            b.cx + b.r * 0.62f, b.cy - b.r * 0.25f, fill
        )
        if (showLabel) {
            text.textSize = b.r * 0.72f
            canvas.drawText(b.label, b.cx, b.cy + text.textSize / 3, text)
        }
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val u = min(width, height).toFloat()
        stroke.strokeWidth = 0.006f * u
        stroke.color = N_RIM

        // Stick analogique
        fill.shader = baseShader
        canvas.drawCircle(sx, sy, sr, fill)
        fill.shader = null
        canvas.drawCircle(sx, sy, sr, stroke)
        val kr = sr * 0.5f
        val kx = sx + stickX * sr * 0.5f
        val ky = sy + stickY * sr * 0.5f
        canvas.save()
        canvas.translate(kx, ky)
        fill.shader = null
        fill.color = N_SHADOW
        canvas.drawCircle(0f, kr * 0.1f, kr, fill)
        fill.shader = knobShader
        canvas.drawCircle(0f, 0f, kr, fill)
        fill.shader = null
        canvas.drawCircle(0f, 0f, kr, stroke)
        canvas.restore()

        // Croix directionnelle
        fill.shader = null
        fill.color = N_SHADOW
        canvas.save()
        canvas.translate(0f, 0.01f * u)
        canvas.drawPath(plus, fill)
        canvas.restore()
        fill.shader = plusShader
        canvas.drawPath(plus, fill)
        fill.shader = null
        canvas.drawPath(plus, stroke)
        for (i in 0 until 4) {
            if (armKeys[i] in pressedKeys) {
                canvas.save()
                canvas.clipPath(plus)
                fill.color = N_PRESS
                canvas.drawRect(armRects[i], fill)
                canvas.restore()
            }
        }
        val th = dR * 0.62f
        val mid = (th / 2 + dR) / 2
        arrow(canvas, dX, dY - mid, th * 0.2f, 0f, -1f,
            if (KeyEvent.KEYCODE_DPAD_UP in pressedKeys) N_ARROW_ON else N_ARROW_OFF)
        arrow(canvas, dX, dY + mid, th * 0.2f, 0f, 1f,
            if (KeyEvent.KEYCODE_DPAD_DOWN in pressedKeys) N_ARROW_ON else N_ARROW_OFF)
        arrow(canvas, dX - mid, dY, th * 0.2f, -1f, 0f,
            if (KeyEvent.KEYCODE_DPAD_LEFT in pressedKeys) N_ARROW_ON else N_ARROW_OFF)
        arrow(canvas, dX + mid, dY, th * 0.2f, 1f, 0f,
            if (KeyEvent.KEYCODE_DPAD_RIGHT in pressedKeys) N_ARROW_ON else N_ARROW_OFF)

        // A, B et les quatre boutons C
        for (b in faces) drawRound(canvas, b, true)
        for (b in cButtons) drawRound(canvas, b, false)
        val cs = cUp.r * 0.45f
        arrow(canvas, cUp.cx, cUp.cy, cs, 0f, -1f, N_ARROW_OFF)
        arrow(canvas, cDown.cx, cDown.cy, cs, 0f, 1f, N_ARROW_OFF)
        arrow(canvas, cLeft.cx, cLeft.cy, cs, -1f, 0f, N_ARROW_OFF)
        arrow(canvas, cRight.cx, cRight.cy, cs, 1f, 0f, N_ARROW_OFF)

        // Start, L, Z, R
        text.textSize = 0.032f * u
        for (p in pills) {
            fill.color = if (p.key in pressedKeys) N_DARK_ON else N_DARK_OFF
            val radius = p.rect.height() / 2
            canvas.drawRoundRect(p.rect, radius, radius, fill)
            canvas.drawRoundRect(p.rect, radius, radius, stroke)
            canvas.drawText(p.label, p.rect.centerX(), p.rect.centerY() + text.textSize / 3, text)
        }

        editor.draw(canvas)
    }
}

package fr.retroengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Overlay NES : chaque touche est dessinée nativement en Canvas.
 * Aucun pixel du screenshot de référence n'est utilisé comme texture.
 */
class VirtualControllerView(
    context: Context,
    private val onInputChanged: (Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerMasks = mutableMapOf<Int, Int>()
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val prefs = context.getSharedPreferences("retro_engine_controller", Context.MODE_PRIVATE)
    private var opacity = prefs.getFloat("opacity", 0.5f).coerceIn(0.15f, 1f)
    private var vibrationEnabled = prefs.getBoolean("vibration", true)

    init {
        isClickable = true
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setOpacity(value: Float) {
        opacity = value.coerceIn(0.15f, 1f)
        prefs.edit().putFloat("opacity", opacity).apply()
        invalidate()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
        prefs.edit().putBoolean("vibration", enabled).apply()
    }

    private fun isPortrait() = height > width
    private fun buttons() = ControllerLayout.nes(isPortrait())
    private fun alpha(base: Int, factor: Float) = (base * factor).toInt().coerceIn(0, 255)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height).toFloat()
        val baseAlpha = (opacity * 255).toInt()
        drawDpad(canvas, scale, baseAlpha)
        buttons().forEach { button ->
            val cx = button.x * width
            val cy = button.y * height
            val radius = button.radius * scale
            when (button.inputMask) {
                ControllerLayout.NES_A, ControllerLayout.NES_B ->
                    drawActionButton(canvas, cx, cy, radius, button.label, baseAlpha)
                ControllerLayout.NES_SELECT, ControllerLayout.NES_START ->
                    drawSystemButton(canvas, cx, cy, radius, button.label, baseAlpha)
            }
        }
        paint.shader = null
        paint.clearShadowLayer()
        paint.alpha = 255
    }

    /** Boutons A/B : coque noire, bague biseautée et dôme rouge laqué. */
    private fun drawActionButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, baseAlpha: Int) {
        val outer = radius * 1.08f
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0xFF05070B.toInt()
        paint.alpha = alpha(baseAlpha, 0.96f)
        canvas.drawCircle(cx, cy + radius * 0.10f, outer, paint)

        paint.shader = LinearGradient(
            cx, cy - outer, cx, cy + outer,
            intArrayOf(0xFFE1E7EB.toInt(), 0xFF77818B.toInt(), 0xFF252C34.toInt(), 0xFF10151B.toInt()),
            floatArrayOf(0f, 0.28f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, outer, paint)
        paint.shader = null

        paint.color = 0xFF5C101B.toInt()
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, radius * 0.965f, paint)

        paint.shader = LinearGradient(
            cx - radius * 0.42f, cy - radius, cx + radius * 0.34f, cy + radius,
            intArrayOf(0xFFFF7B82.toInt(), 0xFFE93443.toInt(), 0xFFB51026.toInt(), 0xFF690B1B.toInt()),
            floatArrayOf(0f, 0.34f, 0.76f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, radius * 0.91f, paint)
        paint.shader = null

        paint.shader = RadialGradient(
            cx - radius * 0.30f, cy - radius * 0.42f, radius * 1.05f,
            intArrayOf(alpha(baseAlpha, 0.40f) shl 24 or 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 0.88f, paint)
        paint.shader = null

        paint.color = Color.WHITE
        paint.alpha = alpha(baseAlpha, 0.34f)
        canvas.drawOval(RectF(cx - radius * 0.57f, cy - radius * 0.70f, cx + radius * 0.20f, cy - radius * 0.34f), paint)
        paint.alpha = alpha(baseAlpha, 0.18f)
        canvas.drawOval(RectF(cx - radius * 0.43f, cy - radius * 0.76f, cx + radius * 0.02f, cy - radius * 0.61f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (radius * 0.035f).coerceAtLeast(1f)
        paint.color = 0xFFFFB8BE.toInt()
        paint.alpha = alpha(baseAlpha, 0.55f)
        canvas.drawCircle(cx, cy, radius * 0.86f, paint)
        paint.style = Paint.Style.FILL

        paint.shader = null
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = radius * 0.76f
        paint.color = 0xFF5A0C18.toInt()
        paint.alpha = alpha(baseAlpha, 0.75f)
        canvas.drawText(label, cx + radius * 0.012f, cy + paint.textSize * 0.36f + radius * 0.035f, paint)
        paint.color = Color.WHITE
        paint.alpha = baseAlpha
        canvas.drawText(label, cx, cy + paint.textSize * 0.34f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    /** SELECT/START : capsules gris acier, tranche sombre et surface satinée. */
    private fun drawSystemButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, baseAlpha: Int) {
        val rect = RectF(cx - radius * 1.48f, cy - radius * 0.55f, cx + radius * 1.48f, cy + radius * 0.55f)
        val corner = radius * 0.48f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF05070A.toInt()
        paint.alpha = alpha(baseAlpha, 0.95f)
        canvas.drawRoundRect(RectF(rect.left, rect.top + radius * 0.17f, rect.right, rect.bottom + radius * 0.17f), corner, corner, paint)

        paint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(0xFFE5E9EC.toInt(), 0xFF929CA6.toInt(), 0xFF424B56.toInt(), 0xFF202832.toInt()),
            floatArrayOf(0f, 0.30f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawRoundRect(rect, corner, corner, paint)
        paint.shader = null

        val inner = RectF(rect.left + radius * 0.10f, rect.top + radius * 0.075f, rect.right - radius * 0.10f, rect.bottom - radius * 0.09f)
        paint.shader = LinearGradient(
            inner.left, inner.top, inner.left, inner.bottom,
            intArrayOf(0xFF8C97A2.toInt(), 0xFF5C6672.toInt(), 0xFF303944.toInt()),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawRoundRect(inner, corner * 0.82f, corner * 0.82f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (radius * 0.045f).coerceAtLeast(1f)
        paint.color = 0xFFEAF0F5.toInt()
        paint.alpha = alpha(baseAlpha, 0.58f)
        canvas.drawRoundRect(inner, corner * 0.82f, corner * 0.82f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = alpha(baseAlpha, 0.17f)
        canvas.drawRoundRect(RectF(inner.left + radius * 0.12f, inner.top + radius * 0.06f, inner.right - radius * 0.12f, inner.top + radius * 0.18f), corner * 0.35f, corner * 0.35f, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = radius * 0.43f
        paint.color = 0xFFE6EBF0.toInt()
        paint.alpha = alpha(baseAlpha, 0.96f)
        canvas.drawText(label, cx, cy + paint.textSize * 0.35f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    /** Croix directionnelle à quatre branches : base moulée, biseaux et flèches gravées. */
    private fun drawDpad(canvas: Canvas, scale: Float, baseAlpha: Int) {
        val cx = (if (isPortrait()) 0.22f else 0.16f) * width
        val cy = (if (isPortrait()) 0.70f else 0.75f) * height
        val arm = scale * 0.145f
        val half = scale * 0.052f
        val corner = scale * 0.016f
        val path = Path().apply {
            moveTo(cx - half, cy - arm)
            lineTo(cx + half, cy - arm)
            lineTo(cx + half, cy - half)
            lineTo(cx + arm, cy - half)
            lineTo(cx + arm, cy + half)
            lineTo(cx + half, cy + half)
            lineTo(cx + half, cy + arm)
            lineTo(cx - half, cy + arm)
            lineTo(cx - half, cy + half)
            lineTo(cx - arm, cy + half)
            lineTo(cx - arm, cy - half)
            lineTo(cx - half, cy - half)
            close()
        }
        val bounds = RectF(cx - arm, cy - arm, cx + arm, cy + arm)
        canvas.save()
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0xFF03060A.toInt()
        paint.alpha = alpha(baseAlpha, 0.95f)
        canvas.save()
        canvas.translate(0f, scale * 0.012f)
        canvas.drawPath(path, paint)
        canvas.restore()

        paint.shader = LinearGradient(cx - arm, cy - arm, cx + arm, cy + arm,
            intArrayOf(0xFF778391.toInt(), 0xFF394451.toInt(), 0xFF151C25.toInt()),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = baseAlpha
        canvas.drawPath(path, paint)
        paint.shader = null

        val inset = Path().apply {
            moveTo(cx - half * 0.84f, cy - arm * 0.91f)
            lineTo(cx + half * 0.84f, cy - arm * 0.91f)
            lineTo(cx + half * 0.84f, cy - half * 0.82f)
            lineTo(cx + arm * 0.91f, cy - half * 0.82f)
            lineTo(cx + arm * 0.91f, cy + half * 0.82f)
            lineTo(cx + half * 0.84f, cy + half * 0.82f)
            lineTo(cx + half * 0.84f, cy + arm * 0.91f)
            lineTo(cx - half * 0.84f, cy + arm * 0.91f)
            lineTo(cx - half * 0.84f, cy + half * 0.82f)
            lineTo(cx - arm * 0.91f, cy + half * 0.82f)
            lineTo(cx - arm * 0.91f, cy - half * 0.82f)
            lineTo(cx - half * 0.84f, cy - half * 0.82f)
            close()
        }
        paint.shader = LinearGradient(cx, cy - arm, cx, cy + arm,
            intArrayOf(0xFF586574.toInt(), 0xFF303B48.toInt(), 0xFF1B232D.toInt()),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = baseAlpha
        canvas.drawPath(inset, paint)
        paint.shader = null

        // Séparations discrètes entre les quatre branches et le moyeu.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (scale * 0.0045f).coerceAtLeast(1f)
        paint.color = 0xFF0B1119.toInt()
        paint.alpha = alpha(baseAlpha, 0.78f)
        canvas.drawLine(cx - half * 0.78f, cy - half, cx + half * 0.78f, cy - half, paint)
        canvas.drawLine(cx - half * 0.78f, cy + half, cx + half * 0.78f, cy + half, paint)
        canvas.drawLine(cx - half, cy - half * 0.78f, cx - half, cy + half * 0.78f, paint)
        canvas.drawLine(cx + half, cy - half * 0.78f, cx + half, cy + half * 0.78f, paint)

        // Flèches gravées, volontairement sobres comme sur une manette NES.
        paint.style = Paint.Style.FILL
        paint.color = 0xFFC4CED8.toInt()
        paint.alpha = alpha(baseAlpha, 0.48f)
        val arrow = scale * 0.017f
        val offset = arm * 0.57f
        val arrows = arrayOf(
            floatArrayOf(cx, cy - arm * 0.76f, cx - arrow, cy - offset, cx + arrow, cy - offset),
            floatArrayOf(cx, cy + arm * 0.76f, cx - arrow, cy + offset, cx + arrow, cy + offset),
            floatArrayOf(cx - arm * 0.76f, cy, cx - offset, cy - arrow, cx - offset, cy + arrow),
            floatArrayOf(cx + arm * 0.76f, cy, cx + offset, cy - arrow, cx + offset, cy + arrow)
        )
        arrows.forEach { p ->
            val tri = Path().apply { moveTo(p[0], p[1]); lineTo(p[2], p[3]); lineTo(p[4], p[5]); close() }
            canvas.drawPath(tri, paint)
        }
        canvas.restore()
    }

    private fun hitTest(x: Float, y: Float): Int {
        val scale = min(width, height).toFloat()
        val dpadX = (if (isPortrait()) 0.22f else 0.16f) * width
        val dpadY = (if (isPortrait()) 0.70f else 0.75f) * height
        val dx = x - dpadX
        val dy = y - dpadY
        val reach = scale * 0.19f
        if (abs(dx) <= reach && abs(dy) <= reach) {
            var mask = 0
            if (dx < -reach * 0.22f) mask = mask or ControllerLayout.LEFT
            if (dx > reach * 0.22f) mask = mask or ControllerLayout.RIGHT
            if (dy < -reach * 0.22f) mask = mask or ControllerLayout.UP
            if (dy > reach * 0.22f) mask = mask or ControllerLayout.DOWN
            return mask
        }
        buttons().forEach { button ->
            val bx = button.x * width
            val by = button.y * height
            val dxButton = x - bx
            val dyButton = y - by
            val hitRadius = button.radius * scale * 1.65f
            if (sqrt(dxButton * dxButton + dyButton * dyButton) <= hitRadius) return button.inputMask
        }
        return 0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val mask = hitTest(event.getX(index), event.getY(index))
                pointerMasks[id] = mask
                if (mask != 0) vibrate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    pointerMasks[id] = hitTest(event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                pointerMasks.remove(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> pointerMasks.clear()
        }
        var combined = 0
        pointerMasks.values.forEach { combined = combined or it }
        onInputChanged(combined)
        return true
    }

    private fun vibrate() {
        if (!vibrationEnabled || vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(12, 45))
            else @Suppress("DEPRECATION") vibrator.vibrate(12)
        } catch (_: SecurityException) { }
    }
}

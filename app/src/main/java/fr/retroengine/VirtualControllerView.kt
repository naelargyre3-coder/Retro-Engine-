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
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Overlay NES dessiné nativement : matériaux lisses, reflets et biseaux.
 * Aucun pixel ni atlas bitmap de la maquette n'est réutilisé.
 */
class VirtualControllerView(
    context: Context,
    private val onInputChanged: (Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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

    private fun fill(color: Int, a: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.clearShadowLayer()
        paint.color = color
        paint.alpha = a
        paint.strokeWidth = 1f
    }

    private fun gradient(
        bounds: RectF,
        top: Int,
        middle: Int,
        bottom: Int,
        a: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.clearShadowLayer()
        paint.shader = LinearGradient(
            bounds.left, bounds.top, bounds.left, bounds.bottom,
            intArrayOf(top, middle, bottom), floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = a
    }

    private fun rounded(canvas: Canvas, rect: RectF, radius: Float, color: Int, a: Int) {
        fill(color, a)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

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

    /** Bouton A/B rouge bombé, bague acier et reflet spéculaire. */
    private fun drawActionButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, a: Int) {
        val outer = radius * 1.10f
        val shadow = RectF(cx - outer, cy - outer * 0.88f + radius * 0.13f,
            cx + outer, cy + outer * 1.02f + radius * 0.13f)
        fill(Color.BLACK, alpha(a, 0.82f))
        paint.setShadowLayer(radius * 0.16f, 0f, radius * 0.10f, Color.BLACK)
        canvas.drawOval(shadow, paint)
        paint.clearShadowLayer()

        // Bague extérieure métallique.
        val ring = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
        paint.shader = RadialGradient(cx - radius * 0.28f, cy - radius * 0.40f, outer * 1.65f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFBFC7CE.toInt(), 0xFF59636E.toInt(), 0xFF202832.toInt()),
            floatArrayOf(0f, 0.38f, 0.78f, 1f), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        paint.alpha = a
        canvas.drawOval(ring, paint)
        paint.shader = null

        val inset = radius * 0.13f
        val face = RectF(cx - radius * 0.91f, cy - radius * 0.91f,
            cx + radius * 0.91f, cy + radius * 0.91f)
        fill(0xFF171B21.toInt(), a)
        canvas.drawOval(face, paint)

        val red = RectF(cx - radius * 0.80f, cy - radius * 0.80f,
            cx + radius * 0.80f, cy + radius * 0.80f)
        paint.shader = RadialGradient(cx - radius * 0.30f, cy - radius * 0.42f, radius * 1.65f,
            intArrayOf(0xFFFF8A8F.toInt(), 0xFFEF3548.toInt(), 0xFFC71931.toInt(), 0xFF790D20.toInt()),
            floatArrayOf(0f, 0.34f, 0.76f, 1f), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        paint.alpha = a
        canvas.drawOval(red, paint)
        paint.shader = null

        // Fin liseré intérieur et éclat en haut à gauche.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1.2f, radius * 0.035f)
        paint.color = 0xFFFFA0A4.toInt()
        paint.alpha = alpha(a, 0.72f)
        canvas.drawArc(red, 205f, 125f, false, paint)
        paint.style = Paint.Style.FILL
        val shine = RectF(cx - radius * 0.49f, cy - radius * 0.60f,
            cx + radius * 0.10f, cy - radius * 0.28f)
        paint.shader = RadialGradient(shine.centerX(), shine.centerY(), radius * 0.58f,
            intArrayOf(0xDFFFFFFF.toInt(), 0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawOval(shine, paint)
        paint.shader = null

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = radius * 0.78f
        paint.color = 0xFF650D1A.toInt()
        paint.alpha = alpha(a, 0.78f)
        canvas.drawText(label, cx + radius * 0.018f, cy + paint.textSize * 0.36f + radius * 0.025f, paint)
        paint.color = Color.WHITE
        paint.alpha = a
        canvas.drawText(label, cx, cy + paint.textSize * 0.36f, paint)
        paint.typeface = Typeface.DEFAULT
    }

    /** SELECT/START : capsules gris foncé à double biseau métallique. */
    private fun drawSystemButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, a: Int) {
        val halfW = radius * 1.62f
        val halfH = radius * 0.57f
        val outer = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        fill(Color.BLACK, alpha(a, 0.88f))
        paint.setShadowLayer(radius * 0.16f, 0f, radius * 0.10f, Color.BLACK)
        canvas.drawRoundRect(RectF(outer).apply { offset(0f, radius * 0.12f) },
            halfH, halfH, paint)
        paint.clearShadowLayer()

        gradient(outer, 0xFFF1F4F6.toInt(), 0xFF7D8995.toInt(), 0xFF252D37.toInt(), a)
        canvas.drawRoundRect(outer, halfH, halfH, paint)
        paint.shader = null

        val inner = RectF(outer).apply { inset(radius * 0.095f, radius * 0.095f) }
        gradient(inner, 0xFF687481.toInt(), 0xFF303A46.toInt(), 0xFF111820.toInt(), a)
        canvas.drawRoundRect(inner, halfH * 0.78f, halfH * 0.78f, paint)
        paint.shader = null

        val face = RectF(inner).apply { inset(radius * 0.055f, radius * 0.045f) }
        gradient(face, 0xFF515D69.toInt(), 0xFF303A45.toInt(), 0xFF202832.toInt(), a)
        canvas.drawRoundRect(face, halfH * 0.70f, halfH * 0.70f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, radius * 0.025f)
        paint.color = 0xFFDCE2E8.toInt()
        paint.alpha = alpha(a, 0.66f)
        canvas.drawRoundRect(RectF(face).apply { inset(radius * 0.035f, radius * 0.035f) },
            halfH * 0.60f, halfH * 0.60f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = radius * 0.39f
        paint.color = 0xFF080D13.toInt()
        paint.alpha = alpha(a, 0.9f)
        canvas.drawText(label, cx, cy + paint.textSize * 0.37f + radius * 0.025f, paint)
        paint.color = 0xFFF1F4F7.toInt()
        paint.alpha = a
        canvas.drawText(label, cx, cy + paint.textSize * 0.37f, paint)
        paint.typeface = Typeface.DEFAULT
    }

    /** Croix directionnelle grise en relief, avec branches arrondies et centre circulaire. */
    private fun drawDpad(canvas: Canvas, scale: Float, a: Int) {
        val cx = 0.22f * width
        val cy = (if (isPortrait()) 0.70f else 0.67f) * height
        val arm = scale * (if (isPortrait()) 0.145f else 0.224f)
        val half = scale * (if (isPortrait()) 0.052f else 0.078f)
        val radius = half * 0.34f

        val shadow = Path().apply {
            addRoundRect(RectF(cx - half, cy - arm, cx + half, cy + arm), radius, radius, Path.Direction.CW)
            addRoundRect(RectF(cx - arm, cy - half, cx + arm, cy + half), radius, radius, Path.Direction.CW)
        }
        fill(0xFF05080C.toInt(), alpha(a, 0.95f))
        paint.setShadowLayer(scale * 0.018f, 0f, scale * 0.012f, Color.BLACK)
        canvas.save()
        canvas.translate(0f, scale * 0.012f)
        canvas.drawPath(shadow, paint)
        canvas.restore()
        paint.clearShadowLayer()

        // Corps acier sombre.
        fill(0xFF26313D.toInt(), a)
        canvas.drawPath(shadow, paint)

        // Dégradés de lumière séparés sur les quatre branches.
        val up = RectF(cx - half * 0.82f, cy - arm * 0.96f, cx + half * 0.82f, cy + half * 0.10f)
        gradient(up, 0xFFB5C0CA.toInt(), 0xFF667381.toInt(), 0xFF303B47.toInt(), a)
        canvas.drawRoundRect(up, radius, radius, paint)
        val down = RectF(cx - half * 0.82f, cy - half * 0.08f, cx + half * 0.82f, cy + arm * 0.96f)
        gradient(down, 0xFF566270.toInt(), 0xFF35414E.toInt(), 0xFF151D27.toInt(), a)
        canvas.drawRoundRect(down, radius, radius, paint)
        val left = RectF(cx - arm * 0.96f, cy - half * 0.82f, cx + half * 0.10f, cy + half * 0.82f)
        gradient(left, 0xFFAAB5C0.toInt(), 0xFF64717F.toInt(), 0xFF303B47.toInt(), a)
        canvas.drawRoundRect(left, radius, radius, paint)
        val right = RectF(cx - half * 0.10f, cy - half * 0.82f, cx + arm * 0.96f, cy + half * 0.82f)
        gradient(right, 0xFF566270.toInt(), 0xFF35414E.toInt(), 0xFF151D27.toInt(), a)
        canvas.drawRoundRect(right, radius, radius, paint)
        paint.shader = null

        // Moyeu central sombre, légèrement bombé.
        val hub = RectF(cx - half * 0.80f, cy - half * 0.80f, cx + half * 0.80f, cy + half * 0.80f)
        paint.shader = RadialGradient(cx - half * 0.22f, cy - half * 0.28f, half * 1.55f,
            intArrayOf(0xFF687583.toInt(), 0xFF303B47.toInt(), 0xFF151D26.toInt()),
            null, Shader.TileMode.CLAMP)
        paint.alpha = a
        canvas.drawOval(hub, paint)
        paint.shader = null

        // Flèches gravées, discrètes et centrées sur les bras.
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF202A34.toInt()
            alpha = alpha(a, 0.62f)
            style = Paint.Style.FILL
        }
        val arrow = scale * (if (isPortrait()) 0.018f else 0.022f)
        fun triangle(points: FloatArray) {
            val path = Path().apply {
                moveTo(points[0], points[1])
                lineTo(points[2], points[3])
                lineTo(points[4], points[5])
                close()
            }
            canvas.drawPath(path, arrowPaint)
        }
        triangle(floatArrayOf(cx, cy - arm * 0.77f, cx - arrow, cy - arm * 0.58f, cx + arrow, cy - arm * 0.58f))
        triangle(floatArrayOf(cx, cy + arm * 0.77f, cx - arrow, cy + arm * 0.58f, cx + arrow, cy + arm * 0.58f))
        triangle(floatArrayOf(cx - arm * 0.77f, cy, cx - arm * 0.58f, cy - arrow, cx - arm * 0.58f, cy + arrow))
        triangle(floatArrayOf(cx + arm * 0.77f, cy, cx + arm * 0.58f, cy - arrow, cx + arm * 0.58f, cy + arrow))
        canvas.restore()
    }

    private fun hitTest(x: Float, y: Float): Int {
        val scale = min(width, height).toFloat()
        val dpadX = 0.22f * width
        val dpadY = (if (isPortrait()) 0.70f else 0.67f) * height
        val dx = x - dpadX
        val dy = y - dpadY
        val reach = scale * (if (isPortrait()) 0.19f else 0.25f)
        val horizontalReach = reach * if (isPortrait()) 1f else (width.toFloat() / height) / (530f / 290f)
        if (abs(dx) <= horizontalReach && abs(dy) <= reach) {
            var mask = 0
            if (dx < -horizontalReach * 0.22f) mask = mask or ControllerLayout.LEFT
            if (dx > horizontalReach * 0.22f) mask = mask or ControllerLayout.RIGHT
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

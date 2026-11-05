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
 * Overlay tactile NES : textures rétro en relief, zones tactiles étendues
 * et suivi multitouch indépendant. Les dimensions et positions proviennent
 * de ControllerLayout et ne sont pas modifiées par le rendu graphique.
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
            if (button.inputMask == ControllerLayout.NES_A ||
                button.inputMask == ControllerLayout.NES_B) {
                drawActionButton(canvas, cx, cy, radius, button.color, button.label, baseAlpha)
            } else {
                drawSystemButton(canvas, cx, cy, radius, button.label, baseAlpha)
            }
        }
        paint.shader = null
        paint.clearShadowLayer()
    }

    /** Bouton rouge bombé : coque sombre, anneau biseauté, face dégradée et reflets. */
    private fun drawActionButton(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        color: Int, label: String, baseAlpha: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.rgb(7, 9, 15)
        paint.alpha = alpha(baseAlpha, 0.92f)
        canvas.drawCircle(cx, cy + radius * 0.13f, radius * 1.12f, paint)

        // Bague extérieure façon métal sombre.
        paint.shader = LinearGradient(
            cx, cy - radius, cx, cy + radius,
            intArrayOf(0xFFE5F1F7.toInt(), 0xFF697684.toInt(), 0xFF202832.toInt()),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, radius * 1.075f, paint)
        paint.shader = null

        // Liseré rouge foncé qui sépare la coque de la face.
        paint.color = 0xFF641923.toInt()
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, radius * 0.99f, paint)

        val face = RectF(cx - radius * 0.94f, cy - radius * 0.94f,
            cx + radius * 0.94f, cy + radius * 0.94f)
        paint.shader = LinearGradient(
            cx - radius * 0.35f, cy - radius,
            cx + radius * 0.35f, cy + radius,
            intArrayOf(
                lighten(color, 58), color,
                darken(color, 42), darken(color, 78)
            ),
            floatArrayOf(0f, 0.38f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, radius * 0.94f, paint)
        paint.shader = null

        // Reflet radial doux en haut à gauche pour simuler une surface laquée.
        paint.shader = RadialGradient(
            cx - radius * 0.28f, cy - radius * 0.43f, radius * 0.88f,
            intArrayOf(alpha(baseAlpha, 0.48f) shl 24 or 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, radius * 0.9f, paint)
        paint.shader = null

        // Bande de reflet nette et petit éclat.
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = alpha(baseAlpha, 0.30f)
        canvas.drawOval(
            RectF(cx - radius * 0.58f, cy - radius * 0.68f,
                cx + radius * 0.28f, cy - radius * 0.27f), paint
        )
        paint.alpha = alpha(baseAlpha, 0.16f)
        canvas.drawOval(
            RectF(cx - radius * 0.48f, cy - radius * 0.72f,
                cx + radius * 0.05f, cy - radius * 0.56f), paint
        )

        // Fin contour intérieur et libellé net.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (radius * 0.035f).coerceAtLeast(1f)
        paint.color = 0xFFFFB7B9.toInt()
        paint.alpha = alpha(baseAlpha, 0.62f)
        canvas.drawCircle(cx, cy, radius * 0.88f, paint)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = radius * 0.78f
        paint.color = 0xFF3B1016.toInt()
        paint.alpha = alpha(baseAlpha, 0.8f)
        canvas.drawText(label, cx + radius * 0.015f, cy + paint.textSize * 0.37f + radius * 0.035f, paint)
        paint.color = Color.WHITE
        paint.alpha = baseAlpha
        canvas.drawText(label, cx, cy + paint.textSize * 0.34f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    /** SELECT/START : capsule compacte avec coque chrome et surface gris satin. */
    private fun drawSystemButton(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        label: String, baseAlpha: Int
    ) {
        val rect = RectF(cx - radius * 1.42f, cy - radius * 0.52f,
            cx + radius * 1.42f, cy + radius * 0.52f)
        val corner = radius * 0.48f

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF05070B.toInt()
        paint.alpha = alpha(baseAlpha, 0.9f)
        canvas.drawRoundRect(RectF(rect.left, rect.top + radius * 0.16f,
            rect.right, rect.bottom + radius * 0.16f), corner, corner, paint)

        // Coque métallique à plusieurs nuances.
        paint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(0xFFE0E7ED.toInt(), 0xFF7D8997.toInt(), 0xFF303945.toInt()),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawRoundRect(rect, corner, corner, paint)
        paint.shader = null

        val inner = RectF(rect.left + radius * 0.10f, rect.top + radius * 0.075f,
            rect.right - radius * 0.10f, rect.bottom - radius * 0.075f)
        paint.shader = LinearGradient(
            inner.left, inner.top, inner.left, inner.bottom,
            intArrayOf(0xFF737F8D.toInt(), 0xFF414B59.toInt(), 0xFF252D38.toInt()),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawRoundRect(inner, corner * 0.82f, corner * 0.82f, paint)
        paint.shader = null

        // Reflet fin sur la tranche supérieure.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (radius * 0.045f).coerceAtLeast(1f)
        paint.color = 0xFFE6F1FA.toInt()
        paint.alpha = alpha(baseAlpha, 0.65f)
        canvas.drawRoundRect(inner, corner * 0.82f, corner * 0.82f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = radius * 0.43f
        paint.color = 0xFF111923.toInt()
        paint.alpha = baseAlpha
        canvas.drawText(label, cx, cy + paint.textSize * 0.35f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    /** Croix anthracite texturée : biseaux, facettes et séparations directionnelles. */
    private fun drawDpad(canvas: Canvas, scale: Float, baseAlpha: Int) {
        val cx = (if (isPortrait()) 0.22f else 0.16f) * width
        val cy = (if (isPortrait()) 0.70f else 0.75f) * height
        val arm = scale * 0.145f
        val half = scale * 0.052f
        val corner = scale * 0.018f
        val shadow = scale * 0.012f

        val vertical = RectF(cx - half, cy - arm, cx + half, cy + arm)
        val horizontal = RectF(cx - arm, cy - half, cx + arm, cy + half)

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF05080D.toInt()
        paint.alpha = alpha(baseAlpha, 0.95f)
        canvas.drawRoundRect(RectF(vertical.left, vertical.top + shadow,
            vertical.right, vertical.bottom + shadow), corner, corner, paint)
        canvas.drawRoundRect(RectF(horizontal.left, horizontal.top + shadow,
            horizontal.right, horizontal.bottom + shadow), corner, corner, paint)

        // Base en métal sombre, avec dégradés verticaux et horizontaux.
        paint.shader = LinearGradient(
            vertical.left, vertical.top, vertical.right, vertical.bottom,
            intArrayOf(0xFF667382.toInt(), 0xFF303A47.toInt(), 0xFF171E28.toInt()),
            floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawRoundRect(vertical, corner, corner, paint)
        paint.shader = LinearGradient(
            horizontal.left, horizontal.top, horizontal.right, horizontal.bottom,
            intArrayOf(0xFF687585.toInt(), 0xFF343E4B.toInt(), 0xFF171E28.toInt()),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = baseAlpha
        canvas.drawRoundRect(horizontal, corner, corner, paint)
        paint.shader = null

        // Facettes lumineuses sur les bords supérieurs de chaque branche.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (scale * 0.006f).coerceAtLeast(1.5f)
        paint.color = 0xFFC4D0DD.toInt()
        paint.alpha = alpha(baseAlpha, 0.62f)
        canvas.drawRoundRect(vertical, corner, corner, paint)
        canvas.drawRoundRect(horizontal, corner, corner, paint)

        // Ligne d'ombre interne pour accentuer le creux central.
        paint.style = Paint.Style.FILL
        paint.color = 0xFF101720.toInt()
        paint.alpha = alpha(baseAlpha, 0.82f)
        canvas.drawRoundRect(
            RectF(cx - half * 0.52f, cy - half * 0.52f,
                cx + half * 0.52f, cy + half * 0.52f),
            corner * 0.5f, corner * 0.5f, paint
        )

        // Petites gravures directionnelles en gris clair.
        paint.color = 0xFFD4DEE8.toInt()
        paint.alpha = alpha(baseAlpha, 0.58f)
        paint.strokeWidth = (scale * 0.0065f).coerceAtLeast(1.5f)
        paint.strokeCap = Paint.Cap.ROUND
        val arrow = scale * 0.018f
        val inset = arm * 0.58f
        canvas.drawLine(cx, cy - arm * 0.76f, cx - arrow, cy - inset, paint)
        canvas.drawLine(cx, cy - arm * 0.76f, cx + arrow, cy - inset, paint)
        canvas.drawLine(cx, cy + arm * 0.76f, cx - arrow, cy + inset, paint)
        canvas.drawLine(cx, cy + arm * 0.76f, cx + arrow, cy + inset, paint)
        canvas.drawLine(cx - arm * 0.76f, cy, cx - inset, cy - arrow, paint)
        canvas.drawLine(cx - arm * 0.76f, cy, cx - inset, cy + arrow, paint)
        canvas.drawLine(cx + arm * 0.76f, cy, cx + inset, cy - arrow, paint)
        canvas.drawLine(cx + arm * 0.76f, cy, cx + inset, cy + arrow, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun lighten(color: Int, amount: Int): Int {
        val r = (Color.red(color) + amount).coerceAtMost(255)
        val g = (Color.green(color) + amount).coerceAtMost(255)
        val b = (Color.blue(color) + amount).coerceAtMost(255)
        return Color.rgb(r, g, b)
    }

    private fun darken(color: Int, amount: Int): Int {
        val r = (Color.red(color) - amount).coerceAtLeast(0)
        val g = (Color.green(color) - amount).coerceAtLeast(0)
        val b = (Color.blue(color) - amount).coerceAtLeast(0)
        return Color.rgb(r, g, b)
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

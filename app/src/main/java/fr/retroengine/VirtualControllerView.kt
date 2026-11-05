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
    private val paint = Paint(0)
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

    /** Dessine une pastille en bandes de pixels, sans lissage ni dégradé continu. */
    private fun drawPixelDisc(canvas: Canvas, cx: Float, cy: Float, radius: Float, baseAlpha: Int) {
        val cell = (radius / 10f).coerceAtLeast(1f)
        val widths = intArrayOf(4, 8, 12, 14, 16, 18, 18, 20, 20, 20, 20, 18, 18, 16, 14, 12, 8, 4)
        widths.forEachIndexed { row, cells ->
            val y = cy - 9f * cell + row * cell
            val color = when {
                row <= 2 -> 0xFFFF777F.toInt()
                row <= 6 -> 0xFFEF3948.toInt()
                row <= 11 -> 0xFFD51E32.toInt()
                else -> 0xFF8D1022.toInt()
            }
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = color
            paint.alpha = baseAlpha
            val half = cells * cell / 2f
            canvas.drawRect(cx - half, y, cx + half, y + cell, paint)
        }
    }

    private fun drawPixelRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int, baseAlpha: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        paint.alpha = baseAlpha
        canvas.drawRect(left, top, right, bottom, paint)
    }

    /** A/B : boutons rouges brillants en pixels, avec bague et ombre en marches d'escalier. */
    private fun drawActionButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, baseAlpha: Int) {
        val cell = (radius / 10f).coerceAtLeast(1f)
        drawPixelRect(canvas, cx - radius * 0.82f, cy - radius * 0.72f + cell, cx + radius * 0.82f, cy + radius * 0.92f + cell, 0xFF030509.toInt(), alpha(baseAlpha, 0.98f))
        drawPixelDisc(canvas, cx, cy, radius * 1.04f, baseAlpha)
        // Bague sombre et liseré acier en blocs concentriques.
        drawPixelRect(canvas, cx - radius * 0.70f, cy - radius * 0.78f, cx + radius * 0.70f, cy + radius * 0.78f, 0xFF5C101B.toInt(), baseAlpha)
        drawPixelRect(canvas, cx - radius * 0.62f, cy - radius * 0.70f, cx + radius * 0.62f, cy + radius * 0.70f, 0xFFDA2638.toInt(), baseAlpha)
        // Reflets rectangulaires pour conserver le rendu pixel-art.
        drawPixelRect(canvas, cx - radius * 0.42f, cy - radius * 0.58f, cx + radius * 0.16f, cy - radius * 0.42f, 0xFFFFA0A5.toInt(), alpha(baseAlpha, 0.82f))
        drawPixelRect(canvas, cx - radius * 0.26f, cy - radius * 0.40f, cx + radius * 0.02f, cy - radius * 0.28f, 0xFFFFD0D2.toInt(), alpha(baseAlpha, 0.68f))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
        paint.textSize = radius * 0.76f
        paint.color = 0xFF5A0C18.toInt()
        paint.alpha = alpha(baseAlpha, 0.85f)
        canvas.drawText(label, cx + cell * 0.25f, cy + paint.textSize * 0.35f + cell * 0.35f, paint)
        paint.color = Color.WHITE
        paint.alpha = baseAlpha
        canvas.drawText(label, cx, cy + paint.textSize * 0.35f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    /** SELECT/START : capsules acier à coins en escalier et détails en pixels. */
    private fun drawSystemButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, baseAlpha: Int) {
        val halfW = radius * 1.48f
        val halfH = radius * 0.55f
        val step = (radius * 0.16f).coerceAtLeast(2f)
        val l = cx - halfW
        val r = cx + halfW
        val t = cy - halfH
        val b = cy + halfH
        drawPixelRect(canvas, l + step, t + step, r - step, b + step * 1.55f, 0xFF05070A.toInt(), alpha(baseAlpha, 0.98f))
        drawPixelRect(canvas, l, t + step, r, b - step, 0xFF929CA6.toInt(), baseAlpha)
        drawPixelRect(canvas, l + step, t, r - step, b, 0xFF929CA6.toInt(), baseAlpha)
        drawPixelRect(canvas, l + step * 1.4f, t + step * 1.4f, r - step * 1.4f, b - step * 1.4f, 0xFF3B4652.toInt(), baseAlpha)
        drawPixelRect(canvas, l + step * 2f, t + step * 1.8f, r - step * 2f, t + step * 2.6f, 0xFFBFC8D0.toInt(), alpha(baseAlpha, 0.72f))
        drawPixelRect(canvas, l + step * 2f, b - step * 2.2f, r - step * 2f, b - step * 1.5f, 0xFF202832.toInt(), baseAlpha)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
        paint.textSize = radius * 0.42f
        paint.color = 0xFFE6EBF0.toInt()
        paint.alpha = baseAlpha
        canvas.drawText(label, cx, cy + paint.textSize * 0.35f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    /** Croix directionnelle anthracite : croix en blocs, biseaux carrés et flèches pixelisées. */
    private fun drawDpad(canvas: Canvas, scale: Float, baseAlpha: Int) {
        val cx = (if (isPortrait()) 0.22f else 0.16f) * width
        val cy = (if (isPortrait()) 0.70f else 0.75f) * height
        val arm = scale * 0.145f
        val half = scale * 0.052f
        val cell = (scale * 0.012f).coerceAtLeast(1f)
        // Ombre décalée, puis croix principale composée de rectangles.
        drawPixelRect(canvas, cx - half, cy - arm + cell, cx + half, cy + arm + cell, 0xFF03060A.toInt(), alpha(baseAlpha, 0.95f))
        drawPixelRect(canvas, cx - arm, cy - half + cell, cx + arm, cy + half + cell, 0xFF03060A.toInt(), alpha(baseAlpha, 0.95f))
        drawPixelRect(canvas, cx - half, cy - arm, cx + half, cy + arm, 0xFF46515E.toInt(), baseAlpha)
        drawPixelRect(canvas, cx - arm, cy - half, cx + arm, cy + half, 0xFF46515E.toInt(), baseAlpha)
        // Faces éclairées en haut et à gauche, faces sombres en bas et à droite.
        drawPixelRect(canvas, cx - half + cell, cy - arm + cell, cx + half - cell, cy - half, 0xFF778391.toInt(), baseAlpha)
        drawPixelRect(canvas, cx - arm + cell, cy - half + cell, cx - half, cy + half - cell, 0xFF687584.toInt(), baseAlpha)
        drawPixelRect(canvas, cx - half + cell, cy + half, cx + half - cell, cy + arm - cell, 0xFF202A35.toInt(), baseAlpha)
        drawPixelRect(canvas, cx + half, cy - half + cell, cx + arm - cell, cy + half - cell, 0xFF1B232D.toInt(), baseAlpha)
        // Quatre flèches courtes dessinées en marches carrées.
        val a = cell * 1.35f
        val tip = cell * 3f
        drawPixelRect(canvas, cx - a, cy - arm * 0.78f, cx + a, cy - arm * 0.48f, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        drawPixelRect(canvas, cx - tip, cy - arm * 0.55f, cx + tip, cy - arm * 0.43f, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        drawPixelRect(canvas, cx - a, cy + arm * 0.48f, cx + a, cy + arm * 0.78f, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        drawPixelRect(canvas, cx - tip, cy + arm * 0.43f, cx + tip, cy + arm * 0.55f, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        drawPixelRect(canvas, cx - arm * 0.78f, cy - a, cx - arm * 0.48f, cy + a, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        drawPixelRect(canvas, cx - arm * 0.55f, cy - tip, cx - arm * 0.43f, cy + tip, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        drawPixelRect(canvas, cx + arm * 0.48f, cy - a, cx + arm * 0.78f, cy + a, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        drawPixelRect(canvas, cx + arm * 0.43f, cy - tip, cx + arm * 0.55f, cy + tip, 0xFFC4CED8.toInt(), alpha(baseAlpha, 0.78f))
        // Séparation centrale discrète.
        drawPixelRect(canvas, cx - half + cell, cy - cell * 0.5f, cx + half - cell, cy + cell * 0.5f, 0xFF111923.toInt(), alpha(baseAlpha, 0.7f))
        drawPixelRect(canvas, cx - cell * 0.5f, cy - half + cell, cx + cell * 0.5f, cy + half - cell, 0xFF111923.toInt(), alpha(baseAlpha, 0.7f))
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

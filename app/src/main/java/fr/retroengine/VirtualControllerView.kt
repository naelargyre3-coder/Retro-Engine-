package fr.retroengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
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
    private val textureAtlas: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.nes_controller_atlas) }
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
            drawReferenceButton(canvas, cx, cy, radius, button.inputMask, baseAlpha)
        }
        paint.shader = null
        paint.clearShadowLayer()
    }

    /** Dessine les éléments de la maquette à partir de leurs pixels d'origine. */
    private fun drawReferenceButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, inputMask: Int, baseAlpha: Int) {
        val source: Rect
        val destination: RectF
        when (inputMask) {
            ControllerLayout.NES_B -> {
                source = Rect(62, 0, 98, 35)
                val diameter = radius * 2.15f
                destination = RectF(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f)
            }
            ControllerLayout.NES_A -> {
                source = Rect(98, 0, 134, 35)
                val diameter = radius * 2.15f
                destination = RectF(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f)
            }
            ControllerLayout.NES_SELECT -> {
                source = Rect(0, 63, 43, 82)
                val buttonWidth = radius * 2.84f
                val buttonHeight = radius * 1.04f
                destination = RectF(cx - buttonWidth / 2f, cy - buttonHeight / 2f, cx + buttonWidth / 2f, cy + buttonHeight / 2f)
            }
            ControllerLayout.NES_START -> {
                source = Rect(44, 63, 87, 82)
                val buttonWidth = radius * 2.84f
                val buttonHeight = radius * 1.04f
                destination = RectF(cx - buttonWidth / 2f, cy - buttonHeight / 2f, cx + buttonWidth / 2f, cy + buttonHeight / 2f)
            }
            else -> return
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.alpha = baseAlpha
        paint.isFilterBitmap = true
        canvas.drawBitmap(textureAtlas, source, destination, paint)
    }

    /** D-pad en relief extrait de la maquette, avec la même emprise qu'avant. */
    private fun drawDpad(canvas: Canvas, scale: Float, baseAlpha: Int) {
        val cx = (if (isPortrait()) 0.22f else 0.16f) * width
        val cy = (if (isPortrait()) 0.70f else 0.75f) * height
        val side = scale * 0.29f
        val destination = RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.alpha = baseAlpha
        paint.isFilterBitmap = true
        canvas.drawBitmap(textureAtlas, Rect(0, 0, 60, 61), destination, paint)
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

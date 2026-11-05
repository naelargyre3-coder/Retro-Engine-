package fr.retroengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Overlay NES autonome : dessin semi-transparent, zones tactiles élargies,
 * diagonales et suivi indépendant de chaque pointeur tactile.
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height).toFloat()
        val alpha = (opacity * 255).toInt()
        drawDpad(canvas, scale, alpha)
        buttons().forEach { button ->
            val cx = button.x * width
            val cy = button.y * height
            val radius = button.radius * scale
            paint.color = button.color
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            val buttonRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val corner = radius * 0.42f
            // Contour clair et face arrondie, plus proches de la maquette.
            paint.color = 0xFFB9F7FF.toInt()
            paint.alpha = (alpha * 0.55f).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (scale * 0.018f).coerceAtLeast(4f)
            canvas.drawRoundRect(buttonRect, corner, corner, paint)
            paint.color = button.color
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(buttonRect, corner, corner, paint)
            paint.color = android.graphics.Color.WHITE
            paint.alpha = (alpha * 0.9f).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (scale * 0.007f).coerceAtLeast(2.5f)
            canvas.drawRoundRect(buttonRect, corner, corner, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = if (button.label.length > 2) radius * 0.42f else radius * 0.85f
            paint.color = android.graphics.Color.WHITE
            paint.alpha = alpha
            canvas.drawText(button.label, cx, cy + paint.textSize * 0.34f, paint)
        }
    }

    private fun drawDpad(canvas: Canvas, scale: Float, alpha: Int) {
        val cx = (if (isPortrait()) 0.22f else 0.16f) * width
        val cy = (if (isPortrait()) 0.70f else 0.76f) * height
        val arm = scale * 0.155f
        val thickness = scale * 0.105f
        paint.color = 0xFF303844.toInt()
        paint.alpha = alpha
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(cx - thickness/2, cy - arm, cx + thickness/2, cy + arm), thickness/4, thickness/4, paint)
        canvas.drawRoundRect(RectF(cx - arm, cy - thickness/2, cx + arm, cy + thickness/2), thickness/4, thickness/4, paint)
        paint.color = 0xFF9AA7B7.toInt()
        paint.alpha = (alpha * 0.65f).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (scale * 0.006f).coerceAtLeast(2f)
        canvas.drawRoundRect(RectF(cx - thickness/2, cy - arm, cx + thickness/2, cy + arm), thickness/4, thickness/4, paint)
        canvas.drawRoundRect(RectF(cx - arm, cy - thickness/2, cx + arm, cy + thickness/2), thickness/4, thickness/4, paint)
        paint.style = Paint.Style.FILL
    }

    private fun hitTest(x: Float, y: Float): Int {
        val scale = min(width, height).toFloat()
        val dpadX = (if (isPortrait()) 0.22f else 0.16f) * width
        val dpadY = (if (isPortrait()) 0.70f else 0.76f) * height
        val dx = x - dpadX
        val dy = y - dpadY
        // Zone carrée élargie : les diagonales combinent deux bits directionnels.
        val reach = scale * 0.195f
        if (abs(dx) <= reach && abs(dy) <= reach) {
            var mask = 0
            if (dx < -reach * 0.22f) mask = mask or ControllerLayout.LEFT
            if (dx > reach * 0.22f) mask = mask or ControllerLayout.RIGHT
            if (dy < -reach * 0.22f) mask = mask or ControllerLayout.UP
            if (dy > reach * 0.22f) mask = mask or ControllerLayout.DOWN
            return mask
        }
        buttons().forEach { b ->
            val bx = b.x * width
            val by = b.y * height
            val radius = b.radius * scale * 1.55f
            val distance = sqrt((x-bx)*(x-bx) + (y-by)*(y-by))
            if (distance <= radius) return b.inputMask
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerMasks.remove(event.getPointerId(event.actionIndex))
            }
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

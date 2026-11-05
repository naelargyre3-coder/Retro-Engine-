package fr.retroengine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
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
 * Overlay tactile NES : croix directionnelle en relief, boutons A/B circulaires,
 * zones tactiles étendues et suivi multitouch indépendant.
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
            if (button.inputMask == ControllerLayout.NES_A ||
                button.inputMask == ControllerLayout.NES_B) {
                drawActionButton(canvas, cx, cy, button.radius * scale, button.color, button.label, alpha)
            } else {
                drawSystemButton(canvas, cx, cy, button.radius * scale, button.label, alpha)
            }
        }
    }

    private fun drawActionButton(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        color: Int, label: String, alpha: Int
    ) {
        // Ombre et contour externe pour donner du relief sans transformer le bouton en carré.
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF11151D.toInt()
        paint.alpha = (alpha * 0.9f).toInt()
        canvas.drawCircle(cx, cy + radius * 0.12f, radius * 1.08f, paint)

        paint.color = 0xFFB9F7FF.toInt()
        paint.alpha = (alpha * 0.72f).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (radius * 0.12f).coerceAtLeast(2f)
        canvas.drawCircle(cx, cy, radius * 1.04f, paint)

        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, paint)

        // Reflet supérieur discret, face rouge brillante.
        paint.color = Color.WHITE
        paint.alpha = (alpha * 0.28f).toInt()
        canvas.drawOval(
            RectF(cx - radius * 0.58f, cy - radius * 0.68f,
                cx + radius * 0.42f, cy - radius * 0.18f), paint
        )
        paint.color = Color.WHITE
        paint.alpha = alpha
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = radius * 0.82f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText(label, cx, cy + paint.textSize * 0.34f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    private fun drawSystemButton(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, alpha: Int) {
        val rect = RectF(cx - radius * 1.42f, cy - radius * 0.52f,
            cx + radius * 1.42f, cy + radius * 0.52f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF151922.toInt()
        paint.alpha = alpha
        canvas.drawRoundRect(RectF(rect.left, rect.top + radius * 0.12f,
            rect.right, rect.bottom + radius * 0.12f), radius, radius, paint)
        paint.color = 0xFF59616E.toInt()
        paint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = 0xFFB9C2D0.toInt()
        paint.alpha = (alpha * 0.7f).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (radius * 0.09f).coerceAtLeast(1.5f)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = alpha
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = radius * 0.48f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText(label, cx, cy + paint.textSize * 0.34f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    private fun drawDpad(canvas: Canvas, scale: Float, alpha: Int) {
        val cx = (if (isPortrait()) 0.22f else 0.16f) * width
        val cy = (if (isPortrait()) 0.70f else 0.75f) * height
        val arm = scale * 0.145f
        val half = scale * 0.052f
        val corner = scale * 0.018f

        // Ombre portée commune.
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF080B10.toInt()
        paint.alpha = (alpha * 0.95f).toInt()
        val shadow = scale * 0.012f
        canvas.drawRoundRect(RectF(cx-half, cy-arm+shadow, cx+half, cy+arm+shadow), corner, corner, paint)
        canvas.drawRoundRect(RectF(cx-arm, cy-half+shadow, cx+arm, cy+half+shadow), corner, corner, paint)

        // Croix gris anthracite composée de quatre branches nettes.
        paint.color = 0xFF303844.toInt()
        paint.alpha = alpha
        canvas.drawRoundRect(RectF(cx-half, cy-arm, cx+half, cy+arm), corner, corner, paint)
        canvas.drawRoundRect(RectF(cx-arm, cy-half, cx+arm, cy+half), corner, corner, paint)

        // Biseaux clairs sur les bords supérieurs et gauches.
        paint.color = 0xFF9AA7B7.toInt()
        paint.alpha = (alpha * 0.75f).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (scale * 0.006f).coerceAtLeast(1.5f)
        canvas.drawRoundRect(RectF(cx-half, cy-arm, cx+half, cy+arm), corner, corner, paint)
        canvas.drawRoundRect(RectF(cx-arm, cy-half, cx+arm, cy+half), corner, corner, paint)
        paint.style = Paint.Style.FILL

        // Séparations discrètes qui rendent les quatre directions lisibles.
        paint.color = 0xFF202630.toInt()
        paint.alpha = (alpha * 0.8f).toInt()
        canvas.drawRect(cx-half * 0.55f, cy-half * 0.55f, cx+half * 0.55f, cy+half * 0.55f, paint)
        paint.color = 0xFFBBC5D1.toInt()
        paint.alpha = (alpha * 0.45f).toInt()
        val arrow = scale * 0.018f
        // Petits chevrons directionnels gravés.
        canvas.drawLine(cx, cy-arm*0.72f, cx-arrow, cy-arm*0.58f, paint)
        canvas.drawLine(cx, cy-arm*0.72f, cx+arrow, cy-arm*0.58f, paint)
        canvas.drawLine(cx, cy+arm*0.72f, cx-arrow, cy+arm*0.58f, paint)
        canvas.drawLine(cx, cy+arm*0.72f, cx+arrow, cy+arm*0.58f, paint)
        canvas.drawLine(cx-arm*0.72f, cy, cx-arm*0.58f, cy-arrow, paint)
        canvas.drawLine(cx-arm*0.72f, cy, cx-arm*0.58f, cy+arrow, paint)
        canvas.drawLine(cx+arm*0.72f, cy, cx+arm*0.58f, cy-arrow, paint)
        canvas.drawLine(cx+arm*0.72f, cy, cx+arm*0.58f, cy+arrow, paint)
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

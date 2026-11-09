package com.retroengine.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropView(
    context: Context,
    private val bitmap: Bitmap,
    private val onChanged: () -> Unit
) : View(context) {

    private val transform = Matrix()
    private val values = FloatArray(9)
    private val cropRect = RectF()
    private val mapped = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint().apply { color = 0xCC000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 4f
    }
    private var minScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                transform.getValues(values)
                val current = values[Matrix.MSCALE_X]
                val target = (current * detector.scaleFactor).coerceIn(minScale, minScale * 8f)
                val factor = target / current
                transform.postScale(factor, factor, detector.focusX, detector.focusY)
                fixBounds()
                invalidate()
                return true
            }
        }
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h) * 0.86f
        val cx = w / 2f
        val cy = h / 2f
        cropRect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        minScale = max(size / bw, size / bh)
        transform.reset()
        transform.postScale(minScale, minScale)
        transform.postTranslate(cx - bw * minScale / 2, cy - bh * minScale / 2)
        onChanged()
    }

    private fun fixBounds() {
        mapped.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        transform.mapRect(mapped)
        var dx = 0f
        var dy = 0f
        if (mapped.left > cropRect.left) dx = cropRect.left - mapped.left
        if (mapped.right < cropRect.right) dx = cropRect.right - mapped.right
        if (mapped.top > cropRect.top) dy = cropRect.top - mapped.top
        if (mapped.bottom < cropRect.bottom) dy = cropRect.bottom - mapped.bottom
        transform.postTranslate(dx, dy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sx += e.getX(i)
            sy += e.getY(i)
            n++
        }
        if (n > 0) {
            sx /= n
            sy /= n
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                lastX = sx
                lastY = sy
            }
            MotionEvent.ACTION_MOVE -> {
                transform.postTranslate(sx - lastX, sy - lastY)
                lastX = sx
                lastY = sy
                fixBounds()
                invalidate()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> onChanged()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawBitmap(bitmap, transform, paint)
        canvas.drawRect(0f, 0f, w, cropRect.top, shade)
        canvas.drawRect(0f, cropRect.bottom, w, h, shade)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, shade)
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, shade)
        canvas.drawRect(cropRect, border)
    }

    // Renvoie la zone cadree, en carre, a la taille demandee
    fun crop(size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val m = Matrix(transform)
        m.postTranslate(-cropRect.left, -cropRect.top)
        val k = size / cropRect.width()
        m.postScale(k, k)
        canvas.drawBitmap(bitmap, m, paint)
        return out
    }
}

package com.retroengine.app

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

interface EditableOverlay {
    fun setEditMode(on: Boolean)
    fun scaleSelected(factor: Float)
    fun resetAdjust()
    fun setSelectionListener(listener: (String?) -> Unit)
}

// Gere la selection, le deplacement et le redimensionnement des groupes de touches
class OverlayEditor(private val view: View, private val relayout: () -> Unit) {

    var active = false
    var adjust: ControlAdjust? = null
    var unit = 1f
    var listener: ((String?) -> Unit)? = null
    val bounds = LinkedHashMap<String, RectF>()
    var selected: String? = null
        private set

    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x88FFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = 0xFFFFD54F.toInt()
    }

    private val scaleDetector = ScaleGestureDetector(
        view.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleSelected(detector.scaleFactor)
                return true
            }
        }
    )

    private fun groupAt(x: Float, y: Float): String? {
        var best: String? = null
        var bestArea = Float.MAX_VALUE
        val margin = unit * 0.02f
        for ((name, r) in bounds) {
            val zone = RectF(r)
            zone.inset(-margin, -margin)
            if (zone.contains(x, y)) {
                val area = r.width() * r.height()
                if (area < bestArea) {
                    best = name
                    bestArea = area
                }
            }
        }
        return best
    }

    fun onTouch(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val g = groupAt(e.x, e.y)
                if (g != null) selected = g
                dragging = g != null
                lastX = e.x
                lastY = e.y
                listener?.invoke(selected)
                view.invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && e.pointerCount == 1 && !scaleDetector.isInProgress) {
                    moveSelected(e.x - lastX, e.y - lastY)
                }
                lastX = e.x
                lastY = e.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> dragging = false
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                adjust?.save()
            }
        }
        return true
    }

    private fun moveSelected(dxPx: Float, dyPx: Float) {
        val name = selected ?: return
        val a = adjust ?: return
        val g = a.get(name)
        g.dx += dxPx / unit
        g.dy += dyPx / unit
        relayout()
    }

    fun scaleSelected(factor: Float) {
        val name = selected ?: return
        val a = adjust ?: return
        val g = a.get(name)
        g.scale = (g.scale * factor).coerceIn(0.5f, 2.2f)
        a.save()
        relayout()
    }

    fun reset() {
        adjust?.reset()
        relayout()
    }

    fun draw(canvas: Canvas) {
        if (!active) return
        for ((name, r) in bounds) {
            canvas.drawRect(r, if (name == selected) selectedPaint else boxPaint)
        }
    }
}

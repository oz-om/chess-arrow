package com.gr.chessarrows

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator

class ArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintArrowHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Arrow data: list of (start, end) points
    private val arrows = mutableListOf<Pair<PointF, PointF>>()

    // Current drawing state
    private var isDrawing = false
    private var currentStart: PointF? = null
    private var currentEnd: PointF? = null

    // Style
    private var arrowColor: Int = 0xFF2E7D32
    private var arrowWidthDp: Int = 8
    private var arrowAlphaPercent: Int = 80

    // Touch handling for drawing
    private var touchSlop = 0

    // Callbacks
    var onArrowClick: (() -> Unit)? = null
    var onDrawingStateChanged: ((Boolean) -> Unit)? = null

    init {
        touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    }

    fun updateStyle(color: Int, widthDp: Int, alphaPercent: Int) {
        arrowColor = color
        arrowWidthDp = widthDp
        arrowAlphaPercent = alphaPercent
        invalidate()
    }

    fun setDrawingActive(active: Boolean) {
        isDrawing = active
        onDrawingStateChanged?.invoke(active)
        invalidate()
    }

    fun addArrow(start: PointF, end: PointF) {
        arrows.add(Pair(start, end))
        invalidate()
    }

    fun undoLastArrow() {
        if (arrows.isNotEmpty()) {
            arrows.removeAt(arrows.lastIndex)
            invalidate()
        }
    }

    fun clearArrows() {
        arrows.clear()
        invalidate()
    }

    fun getArrows(): List<Pair<PointF, PointF>> = arrows.toList()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val strokeWidth = (arrowWidthDp * resources.displayMetrics.density).toFloat()
        val alpha = (arrowAlphaPercent / 100f * 255).toInt()

        paintFill.color = arrowColor
        paintFill.alpha = alpha
        paintStroke.color = arrowColor
        paintStroke.alpha = alpha
        paintStroke.strokeWidth = strokeWidth
        paintArrowHead.color = arrowColor
        paintArrowHead.alpha = alpha

        // Draw completed arrows
        for ((start, end) in arrows) {
            drawArrow(canvas, start.x, start.y, end.x, end.y, strokeWidth)
        }

        // Draw current arrow being drawn
        if (isDrawing && currentStart != null && currentEnd != null) {
            drawArrow(canvas, currentStart!!.x, currentStart!!.y, currentEnd!!.x, currentEnd!!.y, strokeWidth)
        }
    }

    private fun drawArrow(
        canvas: Canvas,
        sx: Float, sy: Float,
        ex: Float, ey: Float,
        strokeWidth: Float
    ) {
        val dx = ex - sx
        val dy = ey - sy
        val length = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (length < 1f) return

        val ux = dx / length
        val uy = dy / length
        val headSize = strokeWidth * 3f

        // Main line
        canvas.drawLine(sx, sy, ex, ey, paintStroke)

        // Arrow head
        val hx = ex - ux * headSize
        val hy = ey - uy * headSize
        val px = -uy * headSize * 0.7f
        val py = ux * headSize * 0.7f

        val path = Path().apply {
            moveTo(ex, ey)
            lineTo(hx + px, hy + py)
            lineTo(hx - px, hy - py)
            close()
        }
        canvas.drawPath(path, paintArrowHead)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isDrawing) {
                    currentStart = PointF(event.x, event.y)
                    currentEnd = PointF(event.x, event.y)
                    invalidate()
                    return true
                } else {
                    // Not in drawing mode - could be a click to toggle
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing && currentStart != null) {
                    currentEnd = PointF(event.x, event.y)
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing && currentStart != null && currentEnd != null) {
                    // Complete the arrow
                    val dx = currentEnd!!.x - currentStart!!.x
                    val dy = currentEnd!!.y - currentStart!!.y
                    val distance = Math.hypot(dx.toDouble(), dy.toDouble())
                    
                    if (distance > touchSlop) {
                        // Valid arrow - add it
                        arrows.add(Pair(currentStart!!, currentEnd!!))
                    }
                    currentStart = null
                    currentEnd = null
                    invalidate()
                    return true
                } else if (!isDrawing) {
                    // Click on drawing surface when not drawing - toggle mode
                    performClick()
                    onArrowClick?.invoke()
                    return true
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }
}
package com.gr.chessarrows

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

class ArrowPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintArrowHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var arrowColor: Int = 0xFF2E7D32
    private var arrowWidthDp: Int = 8
    private var arrowAlphaPercent: Int = 80

    // Sample arrow path for preview (knight move L-shape)
    private val previewArrows = mutableListOf<Pair<PointF, PointF>>()

    init {
        // Create a sample knight move arrow for preview
        previewArrows.add(Pair(PointF(0.2f, 0.7f), PointF(0.4f, 0.3f)))
        previewArrows.add(Pair(PointF(0.4f, 0.3f), PointF(0.6f, 0.5f)))
    }

    fun updateStyle(color: Int, widthDp: Int, alphaPercent: Int) {
        arrowColor = color
        arrowWidthDp = widthDp
        arrowAlphaPercent = alphaPercent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val strokeWidth = (arrowWidthDp * resources.displayMetrics.density).toFloat()
        val alpha = (arrowAlphaPercent / 100f * 255).toInt()

        paintFill.color = arrowColor
        paintFill.alpha = alpha
        paintStroke.color = arrowColor
        paintStroke.alpha = alpha
        paintStroke.strokeWidth = strokeWidth
        paintArrowHead.color = arrowColor
        paintArrowHead.alpha = alpha

        // Draw preview arrows
        for ((start, end) in previewArrows) {
            val sx = start.x * width
            val sy = start.y * height
            val ex = end.x * width
            val ey = end.y * height

            drawArrow(canvas, sx, sy, ex, ey, strokeWidth)
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

        // Arrow head size proportional to stroke width
        val headSize = strokeWidth * 3f

        // Main line
        canvas.drawLine(sx, sy, ex, ey, paintStroke)

        // Arrow head (triangle at end)
        val hx = ex - ux * headSize
        val hy = ey - uy * headSize
        val px = -uy * headSize * 0.7f
        val py = ux * headSize * 0.7f

        val path = android.graphics.Path().apply {
            moveTo(ex, ey)
            lineTo(hx + px, hy + py)
            lineTo(hx - px, hy - py)
            close()
        }
        canvas.drawPath(path, paintArrowHead)
    }
}
package com.example.employeepos2

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class MeasurementOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var drawMode = 0 // 0 = Door, 1 = Concrete Right, 2 = Concrete Left

    var showWidthLine = false
    var showHeightLine = false
    var showHeadLine = false
    var showLeftLine = false
    var showRightLine = false

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.RED
        textSize = 55f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Dashed line for concrete
    private val concretePaint = Paint().apply {
        color = Color.parseColor("#CC0007") // Integrity Red
        strokeWidth = 8f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        isAntiAlias = true
    }

    // Little dot at the end of the line to point exactly to the feature
    private val dotPaint = Paint().apply {
        color = Color.parseColor("#CC0007")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val isEditor = isInEditMode

        if (drawMode == 0) {
            // --- DOOR MEASUREMENTS ---

            if (showWidthLine || isEditor) {
                val yPos = h * 0.67f
                drawLineWithArchitecturalTicks(canvas, w * 0.35f, yPos, w * 0.65f, yPos)
                canvas.drawText("W", w * 0.50f, yPos - 20f, labelPaint)
            }
            if (showHeightLine || isEditor) {
                val xPos = w * 0.38f
                drawLineWithArchitecturalTicks(canvas, xPos, h * 0.31f, xPos, h * 0.76f)
                canvas.drawText("H", xPos + 30f, h * 0.55f, labelPaint)
            }
            if (showHeadLine || isEditor) {
                val xPos = w * 0.47f
                drawLineWithArchitecturalTicks(canvas, xPos, h * 0.15f, xPos, h * 0.28f)
                canvas.drawText("HR", xPos - 50f, h * 0.21f, labelPaint)
            }
            if (showLeftLine || isEditor) {
                val yPos = h * 0.72f
                drawLineWithArchitecturalTicks(canvas, w * 0.16f, yPos, w * 0.32f, yPos)
                canvas.drawText("L", w * 0.23f, yPos - 20f, labelPaint)
            }
            if (showRightLine || isEditor) {
                val yPos = h * 0.72f
                drawLineWithArchitecturalTicks(canvas, w * 0.68f, yPos, w * 0.82f, yPos)
                canvas.drawText("R", w * 0.76f, yPos - 20f, labelPaint)
            }
        } else if (drawMode == 1) {
            // --- RIGHT CONCRETE (Un-Flipped Image) ---

            // WIDTH (Points down/left to the Yellow horizontal step)
            drawPointerLine(canvas, w * 0.60f, h * 0.30f, w * 0.58f, h * 0.40f)

            // LENGTH (Points up/left to the Blue long outer edge)
            drawPointerLine(canvas, w * 0.40f, h * 0.65f, w * 0.41f, h * 0.44f)

        } else if (drawMode == 2) {
            // --- LEFT CONCRETE (Mirrored Image) ---

            // Width now points to where Length used to end (0.59w, 0.44h)
            drawPointerLine(canvas, w * 0.60f, h * 0.30f, w * 0.58f, h * 0.40f)

            // Length now points to where Width used to end (0.42w, 0.40h)
            drawPointerLine(canvas, w * 0.40f, h * 0.65f, w * 0.41f, h * 0.44f)
        }
    }

    // Helper for Door lines
    private fun drawLineWithArchitecturalTicks(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint)
        val tickSize = 15f
        canvas.drawLine(x1 - tickSize, y1 + tickSize, x1 + tickSize, y1 - tickSize, paint)
        canvas.drawLine(x2 - tickSize, y2 + tickSize, x2 + tickSize, y2 - tickSize, paint)
    }

    // Helper for Concrete lines (Draws dashed line with a solid dot at the target)
    private fun drawPointerLine(canvas: Canvas, startX: Float, startY: Float, targetX: Float, targetY: Float) {
        canvas.drawLine(startX, startY, targetX, targetY, concretePaint)
        canvas.drawCircle(targetX, targetY, 12f, dotPaint)
    }
}
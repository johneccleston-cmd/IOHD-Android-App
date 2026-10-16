package com.example.employeepos2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Login background: horizontal slats drifting down like a sectional garage door, plus a soft red glow that
 * slowly wanders. Same motif as the office app's sign-in brand panel, on the dark slate panel.
 */
class SlatsBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val gap = 46f * density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * density }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val basePaint = Paint()
    private var t = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 18_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    private fun motionAllowed() =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (motionAllowed()) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        // Dark slate base, same as the office app's brand panel.
        basePaint.shader = LinearGradient(
            0f, 0f, w, h, Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, basePaint)
        val twoPi = (Math.PI * 2).toFloat()

        // Red glow, drifting slowly.
        val radius = min(w, h) * 0.75f
        val cx = w * (0.8f + 0.08f * sin(t * twoPi))
        val cy = h * (0.15f + 0.05f * cos(t * twoPi))
        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            Color.argb(82, 0xCC, 0x00, 0x07), Color.argb(0, 0xCC, 0x00, 0x07),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, glowPaint)

        // Slats drifting down, fading toward the bottom.
        val offset = (t * gap * 4) % gap
        var y = -gap + offset
        while (y < h) {
            val fade = (1f - y / h).coerceIn(0f, 1f)
            linePaint.color = Color.argb((255 * (0.10f * fade + 0.02f)).toInt(), 0xFF, 0xFF, 0xFF)
            canvas.drawLine(0f, y, w, y, linePaint)
            linePaint.color = Color.argb((255 * 0.12f * fade).toInt(), 0x00, 0x00, 0x00)
            canvas.drawLine(0f, y + 3f * density, w, y + 3f * density, linePaint)
            y += gap
        }
    }
}

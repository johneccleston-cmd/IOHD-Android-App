package com.example.employeepos2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class LiquidProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Configurable properties ──────────────────────────────────────────────
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var waveColor: Int = Color.parseColor("#CC0007")
        set(value) {
            field = value
            frontWavePaint.color = value
            backWavePaint.color = darken(value, 0.80f)
            gradientWavePaint.shader = createWaveGradientShader(value)
            invalidate()
        }

    // Animation cycle duration
    var waveDurationMs: Long = 1800L
        set(value) {
            field = value
            animator?.duration = value
        }

    // ── Paints ───────────────────────────────────────────────────────────────
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FCEAEA")
        style = Paint.Style.FILL
    }

    private val backWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = darken(waveColor, 0.80f)
        style = Paint.Style.FILL
        alpha = 160
    }

    private val frontWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = waveColor
        style = Paint.Style.FILL
    }

    // Gradient wave paint for gradient effect
    private val gradientWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = createWaveGradientShader(waveColor)
    }

    // Shimmer overlay for highlight
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Glowing effect paint
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = waveColor
        style = Paint.Style.STROKE
        strokeWidth = 20f
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }

    // Paths for wave and mask
    private val clipPath = Path()
    private val frontPath = Path()
    private val backPath = Path()

    // Animation variables
    private var wavePhase = 0f
    private var animator: ValueAnimator? = null

    init {
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = waveDurationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                wavePhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Pill-shaped clip
        clipPath.reset()
        clipPath.addRoundRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            h / 2f, h / 2f,
            Path.Direction.CW
        )

        // Shimmer gradient
        shimmerPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.argb(90, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // Create gradient shader for waves
        gradientWavePaint.shader = createWaveGradientShader(waveColor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.clipPath(clipPath)
        // Background track
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), trackPaint)

        if (progress <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()
        val fillX = w * progress

        // Dynamic amplitude based on progress (more lively at higher progress)
        val amplitude = h * (0.12f + 0.06f * progress)
        val waveLength = w * 0.55f
        val baseY = amplitude

        val twoPi = (2.0 * Math.PI).toFloat()

        // Back wave with gradient fill
        buildWavePath(backPath, fillX, h, amplitude * 0.7f, waveLength, baseY, wavePhase * twoPi + 0.9f)
        canvas.drawPath(backPath, gradientWavePaint)

        // Front wave with gradient fill
        buildWavePath(frontPath, fillX, h, amplitude, waveLength, baseY, wavePhase * twoPi)
        canvas.drawPath(frontPath, gradientWavePaint)

        // Add a glow overlay for more depth
        glowPaint.color = waveColor
        canvas.drawPath(frontPath, glowPaint)

        // Shimmer highlight
        canvas.drawRect(0f, 0f, fillX, h, shimmerPaint)
    }

    private fun buildWavePath(
        path: Path,
        fillX: Float,
        canvasH: Float,
        amplitude: Float,
        waveLength: Float,
        baseY: Float,
        phaseShift: Float
    ) {
        path.reset()
        path.moveTo(0f, canvasH)
        path.lineTo(0f, baseY)

        var x = 0f
        while (x <= fillX + 5f) {
            val y = baseY + sin((x / waveLength) + phaseShift) * amplitude
            path.lineTo(x, y.toFloat())
            x += 4f
        }

        path.lineTo(fillX, canvasH)
        path.close()
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun createWaveGradientShader(baseColor: Int): Shader {
        val gradientColors = intArrayOf(
            darken(baseColor, 0.8f),
            baseColor,
            darken(baseColor, 0.8f)
        )
        return LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            gradientColors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimation() else animator?.pause()
    }
}
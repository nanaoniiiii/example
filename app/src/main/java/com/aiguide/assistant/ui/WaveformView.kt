package com.aiguide.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 16
    private val paint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val amplitudes = FloatArray(barCount) { 0.3f + Random.nextFloat() * 0.7f }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    private var phase = 0f
    private var isAnimating = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimation()
        else stopAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        phase += 0.12f
        val barWidth = (width - (barCount - 1) * 4f) / barCount
        val maxBarHeight = height * 0.8f
        val baseY = height / 2f

        for (i in 0 until barCount) {
            val amp = amplitudes[i]
            val barHeight = (sin(phase + i * 0.6f) * amp * maxBarHeight / 2f).coerceIn(4f, maxBarHeight)
            val x = i * (barWidth + 4f)
            paint.strokeWidth = barWidth
            canvas.drawLine(
                x + barWidth / 2, baseY - barHeight,
                x + barWidth / 2, baseY + barHeight,
                paint
            )
        }
    }

    fun startAnimation() {
        if (isAnimating) return
        isAnimating = true
        animator.start()
    }

    fun stopAnimation() {
        isAnimating = false
        animator.cancel()
    }
}

package com.brouken.player.ui.subtitle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * Fun Pac-Man style loading animation for subtitle fetching
 * 
 * Features:
 * - Yellow Pac-Man that moves right eating dots
 * - Mouth opens and closes as it moves
 * - Dots disappear as Pac-Man reaches them
 * - Shows progress text below
 */
class PacmanLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val PAC_RADIUS = 24f // dp
        private const val DOT_RADIUS = 4f  // dp
        private const val DOT_SPACING = 20f // dp
        private const val DOT_COUNT = 6
        private const val ANIMATION_DURATION = 2500L // ms
        private const val MOUTH_ANIMATION_SPEED = 12f
    }
    
    // Paints
    private val pacPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    
    // State
    private var animationProgress = 0f // 0.0 to 1.0
    private var statusText = "Loading..."
    private var isError = false
    
    // Calculated dimensions (in pixels)
    private var pacRadius = PAC_RADIUS * resources.displayMetrics.density
    private var dotRadius = DOT_RADIUS * resources.displayMetrics.density
    private var dotSpacing = DOT_SPACING * resources.displayMetrics.density
    
    // Animation
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ANIMATION_DURATION
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            animationProgress = animation.animatedValue as Float
            invalidate()
        }
    }
    
    init {
        // Start animation by default
        startAnimation()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (DOT_COUNT * dotSpacing + pacRadius * 4).toInt()
        val desiredHeight = (pacRadius * 3 + textPaint.textSize * 2).toInt()
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (isError) {
            drawErrorState(canvas)
            return
        }
        
        val centerY = height / 2f - textPaint.textSize / 2
        
        // Calculate start and end positions
        val startX = pacRadius * 2
        val endX = width - pacRadius * 2
        val travelDistance = endX - startX
        
        // Current Pac-Man position
        val pacX = startX + (animationProgress * travelDistance)
        
        // Draw dots (only those ahead of Pac-Man)
        val dotsStartX = startX + pacRadius * 2
        for (i in 0 until DOT_COUNT) {
            val dotX = dotsStartX + (i * dotSpacing)
            
            // Only draw if Pac-Man hasn't reached this dot
            if (dotX > pacX + pacRadius * 0.5) {
                canvas.drawCircle(dotX, centerY, dotRadius, dotPaint)
            }
        }
        
        // Draw Pac-Man
        drawPacman(canvas, pacX, centerY)
        
        // Draw status text
        canvas.drawText(
            statusText,
            width / 2f,
            centerY + pacRadius + textPaint.textSize * 1.5f,
            textPaint
        )
    }
    
    private fun drawPacman(canvas: Canvas, x: Float, y: Float) {
        // Calculate mouth angle (oscillating)
        val mouthAngle = 45f * abs(sin(animationProgress * MOUTH_ANIMATION_SPEED * Math.PI)).toFloat()
        
        // Create arc bounds
        val rect = RectF(
            x - pacRadius,
            y - pacRadius,
            x + pacRadius,
            y + pacRadius
        )
        
        // Draw Pac-Man as an arc (open mouth facing right)
        canvas.drawArc(
            rect,
            mouthAngle,           // Start angle (top of mouth)
            360f - 2 * mouthAngle, // Sweep angle
            true,                  // Use center
            pacPaint
        )
        
        // Draw eye
        val eyeX = x + pacRadius * 0.2f
        val eyeY = y - pacRadius * 0.4f
        canvas.drawCircle(eyeX, eyeY, pacRadius * 0.1f, dotPaint.apply { color = Color.BLACK })
        dotPaint.color = Color.WHITE // Reset
    }
    
    private fun drawErrorState(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f - textPaint.textSize / 2
        
        // Draw sad Pac-Man (reversed, facing left)
        pacPaint.color = Color.YELLOW
        val rect = RectF(
            centerX - pacRadius,
            centerY - pacRadius,
            centerX + pacRadius,
            centerY + pacRadius
        )
        
        canvas.drawArc(rect, 180f + 30f, 300f, true, pacPaint)
        
        // Draw X eyes
        val eyeSize = pacRadius * 0.3f
        val eyeCenterX = centerX - pacRadius * 0.2f
        val eyeCenterY = centerY - pacRadius * 0.3f
        
        val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            strokeWidth = 3f * resources.displayMetrics.density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        canvas.drawLine(
            eyeCenterX - eyeSize, eyeCenterY - eyeSize,
            eyeCenterX + eyeSize, eyeCenterY + eyeSize,
            xPaint
        )
        canvas.drawLine(
            eyeCenterX - eyeSize, eyeCenterY + eyeSize,
            eyeCenterX + eyeSize, eyeCenterY - eyeSize,
            xPaint
        )
        
        // Draw error text
        textPaint.color = Color.parseColor("#FF6B6B")
        canvas.drawText(
            statusText,
            centerX,
            centerY + pacRadius + textPaint.textSize * 1.5f,
            textPaint
        )
        textPaint.color = Color.WHITE // Reset
    }
    
    // ========================================================================
    // PUBLIC API
    // ========================================================================
    
    fun startAnimation() {
        isError = false
        if (!animator.isRunning) {
            animator.start()
        }
    }
    
    fun stopAnimation() {
        animator.cancel()
    }
    
    fun setStatusText(text: String) {
        statusText = text
        invalidate()
    }
    
    fun showError(message: String) {
        isError = true
        statusText = message
        stopAnimation()
        invalidate()
    }
    
    fun showSuccess(message: String) {
        statusText = message
        // Could add success animation here
    }
    
    fun reset() {
        isError = false
        statusText = "Loading..."
        animationProgress = 0f
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isError) {
            startAnimation()
        }
    }
}

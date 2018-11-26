package com.example.android.waves

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class WavesView
@JvmOverloads
constructor(context: Context,
            attrs: AttributeSet? = null,
            defStyleAttr: Int = R.attr.wavesViewStyle
) : View(context, attrs, defStyleAttr), TiltListener {

    private val wavePaint: Paint
    private val waveGap: Float
    private val wavePath = Path()
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Highlight only the areas already touched on the canvas
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    // gradient colors
    private val green = Color.GREEN
    // solid green in the center, transparent green at the edges
    private val gradientColors =
            intArrayOf(green, modifyAlpha(green, 0.80f),
                    modifyAlpha(green, 0.05f))
    private val gradientMatrix = Matrix()
    val tiltSensor = WaveTiltSensor(context)

    private var maxRadius = 0f
    private var center = PointF(0f, 0f)
    private var initialRadius = 0f

    private var waveAnimator: ValueAnimator? = null
    private var waveRadiusOffset = 0f
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    init {
        val attrs = context.obtainStyledAttributes(attrs, R.styleable.WavesView, defStyleAttr, 0)

        //init paint with custom attrs
        wavePaint = Paint(ANTI_ALIAS_FLAG).apply {
            color = attrs.getColor(R.styleable.WavesView_waveColor, 0)
            strokeWidth = attrs.getDimension(R.styleable.WavesView_waveStrokeWidth, 0f)
            style = Paint.Style.STROKE
        }

        waveGap = attrs.getDimension(R.styleable.WavesView_waveGap, 50f)
        attrs.recycle()
    }

    override fun onTilt(pitchRollRad: Pair<Double, Double>) {
        val pitchRad = pitchRollRad.first
        val rollRad = pitchRollRad.second

        // Use half view height/width to calculate offset instead of full view/device measurement
        val maxYOffset = center.y.toDouble()
        val maxXOffset = center.x.toDouble()

        val yOffset = (Math.sin(pitchRad) * maxYOffset)
        val xOffset = (Math.sin(rollRad) * maxXOffset)

        updateGradient(xOffset.toFloat() + center.x, yOffset.toFloat() + center.y)
    }

    private fun updateGradient(x: Float, y: Float) {
        gradientMatrix.setTranslate(x - center.x, y - center.y)
        gradientPaint.shader.setLocalMatrix(gradientMatrix)
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        //set the center of all circles to be center of the view
        center.set(w / 2f, h / 2f)
        maxRadius = Math.hypot(center.x.toDouble(), center.y.toDouble()).toFloat() * 1.3f
        initialRadius = w / waveGap

        //Create gradient after getting sizing information
        gradientPaint.shader = RadialGradient(
                center.x, center.y, w / 2f,
                gradientColors, null, Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        waveAnimator = ValueAnimator.ofFloat(0f, waveGap).apply {
            addUpdateListener {
                waveRadiusOffset = it.animatedValue as Float
            }
            duration = 1500L
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        tiltSensor.addListener(this)
        tiltSensor.register()
    }

    override fun onDetachedFromWindow() {
        waveAnimator?.cancel()
        tiltSensor.unregister()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        //draw circles separated by a space the size of waveGap
        var currentRadius = initialRadius + waveRadiusOffset
        while (currentRadius < maxRadius) {
            val path = createStarPath(currentRadius, wavePath)
            canvas.drawPath(path, wavePaint)
            currentRadius += waveGap
        }

        canvas.drawPaint(gradientPaint)
    }

    private fun createStarPath(
            radius: Float,
            path: Path = Path(),
            points: Int = 20
    ): Path {
        path.reset()
        val pointDelta = 0.7f // difference between the "far" and "close" points from the center
        val angleInRadians = 2.0 * Math.PI / points // essentially 360/20 or 18 degrees, angle each line should be drawn
        val startAngleInRadians = 0.0 //starting to draw star at 0 degrees

        //move pointer to 0 degrees relative to the center of the screen
        path.moveTo(
                center.x + (radius * pointDelta * Math.cos(startAngleInRadians)).toFloat(),
                center.y + (radius * pointDelta * Math.sin(startAngleInRadians)).toFloat()
        )

        //create a line between all the points in the star
        for (i in 1 until points) {
            val hypotenuse = if (i % 2 == 0) {
                //by reducing the distance from the circle every other points, we create the "dip" in the star
                pointDelta * radius
            } else {
                radius
            }

            val nextPointX = center.x + (hypotenuse * Math.cos(startAngleInRadians - angleInRadians * i)).toFloat()
            val nextPointY = center.y + (hypotenuse * Math.sin(startAngleInRadians - angleInRadians * i)).toFloat()
            path.lineTo(nextPointX, nextPointY)
        }

        path.close()
        return path
    }

    private fun modifyAlpha(color: Int, alpha: Float): Int {
        return color and 0x00ffffff or ((alpha * 255).toInt() shl 24)
    }
}

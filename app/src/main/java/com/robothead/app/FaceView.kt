package com.robothead.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class FaceView(context: Context) : View(context) {

    private val backgroundPaint = Paint().apply { color = Color.BLACK }
    private val eyePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val pupilPaint = Paint().apply { color = Color.BLACK; isAntiAlias = true }
    private val lidPaint = Paint().apply {
        color = Color.CYAN
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }
    private val mouthFillPaint = Paint().apply { color = Color.CYAN; isAntiAlias = true }

    private var eyeOffsetX = 0f
    private var eyeOffsetY = 0f
    private var eyeOpenAmount = 1f
    private var mouthOpenAmount = 0f

    fun setEyeOffset(x: Float, y: Float) {
        eyeOffsetX = x.coerceIn(-1f, 1f)
        eyeOffsetY = y.coerceIn(-1f, 1f)
        postInvalidateOnAnimation()
    }

    fun setEyeOpenAmount(v: Float) {
        eyeOpenAmount = v.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setMouthOpenAmount(v: Float) {
        mouthOpenAmount = v.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerX = width / 2f
        val centerY = height / 2f
        val eyeRadius = minOf(width, height) * 0.09f
        val eyeOffsetXPx = width * 0.18f
        val eyeY = centerY - eyeRadius
        val maxPupilShift = eyeRadius * 0.45f

        for (side in floatArrayOf(-1f, 1f)) {
            val ex = centerX + side * eyeOffsetXPx
            if (eyeOpenAmount < 0.08f) {
                canvas.drawLine(ex - eyeRadius, eyeY, ex + eyeRadius, eyeY, lidPaint)
            } else {
                val halfHeight = eyeRadius * eyeOpenAmount
                canvas.drawOval(
                    RectF(ex - eyeRadius, eyeY - halfHeight, ex + eyeRadius, eyeY + halfHeight),
                    eyePaint
                )
                canvas.drawCircle(
                    ex + eyeOffsetX * maxPupilShift,
                    eyeY + eyeOffsetY * maxPupilShift * eyeOpenAmount,
                    eyeRadius * 0.4f,
                    pupilPaint
                )
            }
        }

        val mouthY = centerY + height * 0.15f
        val mouthHalfWidth = width * 0.1f
        if (mouthOpenAmount < 0.08f) {
            canvas.drawLine(centerX - mouthHalfWidth, mouthY, centerX + mouthHalfWidth, mouthY, lidPaint)
        } else {
            val mouthHeight = mouthHalfWidth * mouthOpenAmount
            canvas.drawOval(
                RectF(centerX - mouthHalfWidth, mouthY - mouthHeight, centerX + mouthHalfWidth, mouthY + mouthHeight),
                mouthFillPaint
            )
        }
    }
}

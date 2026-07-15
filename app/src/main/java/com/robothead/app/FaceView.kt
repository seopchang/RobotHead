package com.robothead.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class FaceView(context: Context) : View(context) {

    private val backgroundPaint = Paint().apply { color = Color.BLACK }
    private val eyePaint = Paint().apply {
        color = Color.CYAN
        isAntiAlias = true
    }
    private val mouthPaint = Paint().apply {
        color = Color.CYAN
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerX = width / 2f
        val centerY = height / 2f
        val eyeRadius = minOf(width, height) * 0.08f
        val eyeOffsetX = width * 0.18f
        val eyeY = centerY - eyeRadius

        canvas.drawCircle(centerX - eyeOffsetX, eyeY, eyeRadius, eyePaint)
        canvas.drawCircle(centerX + eyeOffsetX, eyeY, eyeRadius, eyePaint)

        val mouthY = centerY + height * 0.15f
        val mouthHalfWidth = width * 0.1f
        canvas.drawLine(centerX - mouthHalfWidth, mouthY, centerX + mouthHalfWidth, mouthY, mouthPaint)
    }
}

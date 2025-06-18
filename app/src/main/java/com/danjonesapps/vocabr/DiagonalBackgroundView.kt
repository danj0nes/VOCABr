package com.danjonesapps.vocabr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class DiagonalBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val leftPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.background_light_gray)   // Left side color
        style = Paint.Style.FILL
    }

    private val rightPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.background_gray)   // Right side color
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        val topSplitX = widthF * 0.55f   // 60% across top
        val bottomSplitX = widthF * 0.35f // 30% across bottom

        // LEFT SIDE (above the diagonal)
        val pathLeft = Path().apply {
            moveTo(0f, 0f)
            lineTo(topSplitX, 0f)
            lineTo(bottomSplitX, heightF)
            lineTo(0f, heightF)
            close()
        }

        // RIGHT SIDE (below the diagonal)
        val pathRight = Path().apply {
            moveTo(topSplitX, 0f)
            lineTo(widthF, 0f)
            lineTo(widthF, heightF)
            lineTo(bottomSplitX, heightF)
            close()
        }

        canvas.drawPath(pathLeft, leftPaint)
        canvas.drawPath(pathRight, rightPaint)
    }
}

package com.sahin.galerim

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class CheckCircleDrawable(private val bgColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun getIntrinsicWidth(): Int = 72
    override fun getIntrinsicHeight(): Int = 72

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.centerX().toFloat()
        val cy = b.centerY().toFloat()
        val radius = Math.min(b.width(), b.height()) / 2f

        paint.style = Paint.Style.FILL
        paint.color = bgColor
        canvas.drawCircle(cx, cy, radius, paint)

        val isBgWhite = bgColor == Color.WHITE || bgColor == Color.parseColor("#FFFFFF")
        if (isBgWhite) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = radius * 0.1f
            canvas.drawCircle(cx, cy, radius, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.color = if (isBgWhite) Color.BLACK else Color.WHITE
        paint.strokeWidth = radius * 0.15f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        path.reset()
        path.moveTo(cx - radius * 0.3f, cy)
        path.lineTo(cx - radius * 0.1f, cy + radius * 0.3f)
        path.lineTo(cx + radius * 0.45f, cy - radius * 0.35f)

        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { 
        paint.alpha = alpha 
    }
    
    override fun setColorFilter(colorFilter: ColorFilter?) { 
        paint.colorFilter = colorFilter 
    }
    
    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

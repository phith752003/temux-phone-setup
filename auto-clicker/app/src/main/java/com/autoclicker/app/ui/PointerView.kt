package com.autoclicker.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Custom floating pointer view representing click/swipe coordinates on screen.
 * Supports drag-and-drop to update layout coordinates.
 */
class PointerView(
    context: Context,
    private val windowManager: WindowManager,
    val label: String,
    initialX: Int,
    initialY: Int,
    private val onClicked: () -> Unit,
    private val onPositionChanged: (newX: Int, newY: Int) -> Unit
) : View(context) {

    private val sizePx = (44 * context.resources.displayMetrics.density).toInt() // 44dp size
    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FF3B30") // Trở đỏ bán trong suốt
        style = Paint.Style.FILL
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2 * context.resources.displayMetrics.density
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16 * context.resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // WindowManager Layout Params
    val layoutParamsWm = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        x = initialX - sizePx / 2
        y = initialY - sizePx / 2
    }

    // Touch dragging variables
    private var startX = 0
    private var startY = 0
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var downTime = 0L
    private val touchSlop = 8 * context.resources.displayMetrics.density

    init {
        isClickable = true
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = sizePx / 2f
        // Vẽ vòng tròn nền đỏ
        canvas.drawCircle(radius, radius, radius - paintBorder.strokeWidth, paintCircle)
        // Vẽ viền trắng
        canvas.drawCircle(radius, radius, radius - paintBorder.strokeWidth, paintBorder)
        // Vẽ nhãn (ví dụ: "1", "S1", "E1")
        val textY = radius - (paintText.descent() + paintText.ascent()) / 2f
        canvas.drawText(label, radius, textY, paintText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = layoutParamsWm.x
                startY = layoutParamsWm.y
                startTouchX = event.rawX
                startTouchY = event.rawY
                downTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startTouchX).toInt()
                val dy = (event.rawY - startTouchY).toInt()
                
                layoutParamsWm.x = startX + dx
                layoutParamsWm.y = startY + dy
                
                windowManager.updateViewLayout(this, layoutParamsWm)
                
                // Trả về tọa độ tâm của trỏ điểm
                val centerX = layoutParamsWm.x + sizePx / 2
                val centerY = layoutParamsWm.y + sizePx / 2
                onPositionChanged(centerX, centerY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val upTime = System.currentTimeMillis()
                val dx = event.rawX - startTouchX
                val dy = event.rawY - startTouchY
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                if (upTime - downTime < 300 && dist < touchSlop) {
                    onClicked()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Update position directly from code
     */
    fun updatePosition(centerX: Int, centerY: Int) {
        layoutParamsWm.x = centerX - sizePx / 2
        layoutParamsWm.y = centerY - sizePx / 2
        windowManager.updateViewLayout(this, layoutParamsWm)
    }
}

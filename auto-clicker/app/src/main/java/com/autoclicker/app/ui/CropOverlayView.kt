package com.autoclicker.app.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen overlay view that handles touch gestures to crop a template image from screenshot.
 * Supports pinch-to-zoom and pan using 2 fingers, and drawing crop box using 1 finger.
 * Coordinates are mapped to original screenshot bitmap pixels correctly.
 */
class CropOverlayView(
    context: Context,
    private val screenshot: Bitmap,
    private val onCropCompleted: (cropped: Bitmap, leftPct: Int, topPct: Int, rightPct: Int, bottomPct: Int) -> Unit,
    private val onCropCancelled: () -> Unit
) : View(context) {

    private val paintOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000") // Mờ tối
        style = Paint.Style.FILL
    }

    private val paintCropBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00E676") // Viền xanh lá sáng
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val paintButtonBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * context.resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val paintInstruction = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 14f * context.resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Zoom/Pan Matrix state
    private val displayMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val savedMatrix = Matrix()

    // Multi-touch tracking
    private var isZooming = false
    private var initialSpacing = 1f
    private val initialMidPoint = PointF()

    // Crop box state (stored in original Bitmap coordinates)
    private var cropRectBmp = RectF()
    private var isDrawing = false
    private var startTouchX = 0f
    private var startTouchY = 0f

    // UI Buttons layout (Screen coordinates)
    private val barHeight = (80 * context.resources.displayMetrics.density).toInt()
    private var btnCancelRect = RectF()
    private var btnConfirmRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        displayMatrix.reset()
        inverseMatrix.reset()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val btnWidth = w / 3f
        val btnHeight = barHeight * 0.6f
        val margin = barHeight * 0.2f
        val bottomY = h.toFloat()

        btnCancelRect.set(
            w / 6f - btnWidth / 2f,
            bottomY - margin - btnHeight,
            w / 6f + btnWidth / 2f,
            bottomY - margin
        )

        btnConfirmRect.set(
            w * 5f / 6f - btnWidth / 2f,
            bottomY - margin - btnHeight,
            w * 5f / 6f + btnWidth / 2f,
            bottomY - margin
        )
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Draw static background image using zoom/pan matrix
        canvas.drawBitmap(screenshot, displayMatrix, null)

        // 2. Map original crop rect to current screen coordinates
        val screenRect = RectF()
        var hasValidCrop = false
        if (cropRectBmp.width() > 10 && cropRectBmp.height() > 10) {
            val pts = floatArrayOf(
                cropRectBmp.left, cropRectBmp.top,
                cropRectBmp.right, cropRectBmp.bottom
            )
            displayMatrix.mapPoints(pts)
            val left = min(pts[0], pts[2])
            val top = min(pts[1], pts[3])
            val right = max(pts[0], pts[2])
            val bottom = max(pts[1], pts[3])
            screenRect.set(left, top, right, bottom)
            hasValidCrop = true
        }

        // 3. Draw dark overlay only outside the crop box
        canvas.save()
        if (hasValidCrop) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                canvas.clipOutRect(screenRect)
            } else {
                @Suppress("DEPRECATION")
                canvas.clipRect(screenRect, Region.Op.DIFFERENCE)
            }
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintOverlay)
        canvas.restore()

        // 4. Draw crop border
        if (hasValidCrop) {
            canvas.drawRect(screenRect, paintCropBorder)
        }

        // 5. Draw bottom control bar
        val barTop = height - barHeight
        val paintBarBg = Paint().apply {
            color = Color.parseColor("#CC111111")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, barTop.toFloat(), width.toFloat(), height.toFloat(), paintBarBg)

        // Cancel Button (Hủy)
        paintButtonBg.color = Color.parseColor("#FFE53935") // Đỏ
        canvas.drawRoundRect(btnCancelRect, 12f, 12f, paintButtonBg)
        canvas.drawText("Hủy", btnCancelRect.centerX(), btnCancelRect.centerY() - (paintText.descent() + paintText.ascent()) / 2f, paintText)

        // Confirm Button (Xác nhận)
        paintButtonBg.color = if (hasValidCrop) {
            Color.parseColor("#FF43A047") // Xanh lá
        } else {
            Color.parseColor("#FF757575") // Xám (Disabled)
        }
        canvas.drawRoundRect(btnConfirmRect, 12f, 12f, paintButtonBg)
        canvas.drawText("Xác Nhận", btnConfirmRect.centerX(), btnConfirmRect.centerY() - (paintText.descent() + paintText.ascent()) / 2f, paintText)

        // Instructions text
        canvas.drawText("1 ngón tay vẽ khung cắt • 2 ngón tay thu phóng/di chuyển ảnh", width / 2f, barTop - 20f, paintInstruction)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                // Check if user clicked Hủy button
                if (btnCancelRect.contains(x, y)) {
                    onCropCancelled()
                    return true
                }
                // Check if user clicked Xác Nhận button
                if (btnConfirmRect.contains(x, y)) {
                    if (cropRectBmp.width() > 10 && cropRectBmp.height() > 10) {
                        performCrop()
                    }
                    return true
                }

                // Start drawing crop rect if touching inside capture area
                if (y < height - barHeight && event.pointerCount == 1) {
                    startTouchX = x
                    startTouchY = y
                    isDrawing = true
                    cropRectBmp.set(0f, 0f, 0f, 0f)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Two fingers touched: Switch to zoom/pan mode
                isDrawing = false
                initialSpacing = getDistanceBetweenPointers(event)
                if (initialSpacing > 10f) {
                    savedMatrix.set(displayMatrix)
                    getMidPoint(initialMidPoint, event)
                    isZooming = true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isZooming && event.pointerCount >= 2) {
                    val newSpacing = getDistanceBetweenPointers(event)
                    if (newSpacing > 10f) {
                        displayMatrix.set(savedMatrix)
                        val scale = newSpacing / initialSpacing
                        displayMatrix.postScale(scale, scale, initialMidPoint.x, initialMidPoint.y)

                        // Calculate pan offset
                        val currentMid = PointF()
                        getMidPoint(currentMid, event)
                        val dx = currentMid.x - initialMidPoint.x
                        val dy = currentMid.y - initialMidPoint.y
                        displayMatrix.postTranslate(dx, dy)

                        displayMatrix.invert(inverseMatrix)
                        invalidate()
                    }
                } else if (isDrawing && event.pointerCount == 1) {
                    val currentX = x.coerceIn(0f, width.toFloat())
                    val currentY = y.coerceIn(0f, (height - barHeight).toFloat())

                    // Map screen touch coordinates to unscaled original Bitmap pixel coordinates
                    val startPts = floatArrayOf(startTouchX, startTouchY)
                    val currPts = floatArrayOf(currentX, currentY)
                    inverseMatrix.mapPoints(startPts)
                    inverseMatrix.mapPoints(currPts)

                    cropRectBmp.set(
                        min(startPts[0], currPts[0]),
                        min(startPts[1], currPts[1]),
                        max(startPts[0], currPts[0]),
                        max(startPts[1], currPts[1])
                    )
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                isZooming = false
                // When 2nd finger leaves, reset touch states to prevent jump
                isDrawing = false
                return true
            }

            MotionEvent.ACTION_UP -> {
                isDrawing = false
                isZooming = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getDistanceBetweenPointers(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun getMidPoint(point: PointF, event: MotionEvent) {
        if (event.pointerCount < 2) {
            point.set(0f, 0f)
            return
        }
        val mx = event.getX(0) + event.getX(1)
        val my = event.getY(0) + event.getY(1)
        point.set(mx / 2f, my / 2f)
    }

    private fun performCrop() {
        val bmpL = cropRectBmp.left.toInt().coerceIn(0, screenshot.width - 1)
        val bmpT = cropRectBmp.top.toInt().coerceIn(0, screenshot.height - 1)
        val bmpR = cropRectBmp.right.toInt().coerceIn(bmpL + 1, screenshot.width)
        val bmpB = cropRectBmp.bottom.toInt().coerceIn(bmpT + 1, screenshot.height)

        val cropW = bmpR - bmpL
        val cropH = bmpB - bmpT

        if (cropW <= 0 || cropH <= 0) {
            onCropCancelled()
            return
        }

        try {
            // Crop from original unscaled screenshot -> preserves high resolution!
            val croppedBmp = Bitmap.createBitmap(screenshot, bmpL, bmpT, cropW, cropH)
            
            // Calculate percentage coords based on original screenshot size
            val leftPct = (bmpL * 100 / screenshot.width).coerceIn(0, 100)
            val topPct = (bmpT * 100 / screenshot.height).coerceIn(0, 100)
            val rightPct = (bmpR * 100 / screenshot.width).coerceIn(0, 100)
            val bottomPct = (bmpB * 100 / screenshot.height).coerceIn(0, 100)

            onCropCompleted(croppedBmp, leftPct, topPct, rightPct, bottomPct)
        } catch (e: Exception) {
            android.util.Log.e("CropOverlayView", "Failed to crop bitmap: ${e.message}", e)
            onCropCancelled()
        }
    }
}

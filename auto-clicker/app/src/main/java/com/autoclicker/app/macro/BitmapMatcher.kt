package com.autoclicker.app.macro

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Base64
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * High-performance, pure Kotlin template matching engine for Android.
 * Designed to be extremely lightweight, fast, and battery-friendly.
 * 
 * Performance optimizations:
 * - Uses 16-point early-rejection heuristic (keypoint filtering)
 * - Grayscale down-conversion using integer bit shifts
 * - Stride-based pixel stepping (sub-sampling)
 * - Scan restricted to search region (bounding box)
 */
object BitmapMatcher {
    private const val TAG = "BitmapMatcher"

    data class Keypoint(val dx: Int, val dy: Int, val grayValue: Int)
    data class MatchResult(val centerPoint: Point, val score: Float)

    /**
     * Decode a base64 string into a Bitmap
     */
    fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 bitmap: ${e.message}")
            null
        }
    }

    /**
     * Encode a Bitmap to a base64 string
     */
    fun encodeBitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    /**
     * Searches for a template bitmap within a screen bitmap inside the specified region.
     * Returns the center Point of the match and the score if found, or null otherwise.
     * 
     * @param screen The screenshot bitmap (usually full screen).
     * @param template The template bitmap to search for.
     * @param leftPct Search region left boundary in percent (0..100)
     * @param topPct Search region top boundary in percent (0..100)
     * @param rightPct Search region right boundary in percent (0..100)
     * @param bottomPct Search region bottom boundary in percent (0..100)
     * @param threshold Similarity threshold (0.0 to 1.0)
     */
    fun findTemplate(
        screen: Bitmap,
        template: Bitmap,
        leftPct: Int = 0,
        topPct: Int = 0,
        rightPct: Int = 100,
        bottomPct: Int = 100,
        threshold: Float = 0.8f
    ): MatchResult? {
        val startTime = System.currentTimeMillis()
        
        // 1. Calculate pixel coordinates of search region
        val left = (leftPct * screen.width / 100).coerceIn(0, screen.width - 1)
        val top = (topPct * screen.height / 100).coerceIn(0, screen.height - 1)
        val right = (rightPct * screen.width / 100).coerceIn(left + 1, screen.width)
        val bottom = (bottomPct * screen.height / 100).coerceIn(top + 1, screen.height)
        
        val searchW = right - left
        val searchH = bottom - top

        if (template.width > searchW || template.height > searchH) {
            Log.w(TAG, "Template is larger than the search region")
            return null
        }

        // 2. Extract and convert template pixels to grayscale
        val tempW = template.width
        val tempH = template.height
        val tempSize = tempW * tempH
        val tempPixels = IntArray(tempSize)
        template.getPixels(tempPixels, 0, tempW, 0, 0, tempW, tempH)

        val tempGray = IntArray(tempSize)
        for (i in 0 until tempSize) {
            val color = tempPixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            tempGray[i] = (r * 77 + g * 150 + b * 29) shr 8 // Fast integer grayscale
        }

        // 3. Define 16 distributed keypoints from the template for early rejection
        val keypoints = ArrayList<Keypoint>(16)
        val stepX = (tempW / 5).coerceAtLeast(1)
        val stepY = (tempH / 5).coerceAtLeast(1)
        for (i in 1..4) {
            for (j in 1..4) {
                val kx = i * stepX
                val ky = j * stepY
                if (kx < tempW && ky < tempH) {
                    val gVal = tempGray[ky * tempW + kx]
                    keypoints.add(Keypoint(kx, ky, gVal))
                }
            }
        }

        // 4. Extract and convert search region pixels to grayscale
        val searchPixels = IntArray(searchW * searchH)
        screen.getPixels(searchPixels, 0, searchW, left, top, searchW, searchH)
        
        val searchGray = IntArray(searchPixels.size)
        for (i in searchPixels.indices) {
            val color = searchPixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            searchGray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        // Maximum allowed color difference for keypoints
        // Threshold 0.8 means 20% difference max (255 * 0.20 = 51)
        val maxKeypointDiff = ((1f - threshold) * 255).toInt()
        
        // Scan parameters
        val stride = 2 // Check every 2nd pixel to double scan speed
        var bestX = -1
        var bestY = -1
        var bestScore = 0.0f

        val limitY = searchH - tempH
        val limitX = searchW - tempW

        // 5. Sliding window scan
        var y = 0
        while (y <= limitY) {
            var x = 0
            while (x <= limitX) {
                // Keypoint check (early rejection)
                var passKeypoints = true
                for (kp in keypoints) {
                    val sIdx = (y + kp.dy) * searchW + (x + kp.dx)
                    val sGray = searchGray[sIdx]
                    if (abs(sGray - kp.grayValue) > maxKeypointDiff) {
                        passKeypoints = false
                        break
                    }
                }

                if (passKeypoints) {
                    // Perform full check using sub-sampled SAD (Sum of Absolute Differences)
                    var sad = 0
                    var checkedPixels = 0
                    
                    // Sample every 2nd pixel inside template to speed up SAD calculation
                    val sampleStep = 2
                    for (dy in 0 until tempH step sampleStep) {
                        val sY = y + dy
                        for (dx in 0 until tempW step sampleStep) {
                            val sX = x + dx
                            val sVal = searchGray[sY * searchW + sX]
                            val tVal = tempGray[dy * tempW + dx]
                            sad += abs(sVal - tVal)
                            checkedPixels++
                        }
                    }

                    val maxSad = checkedPixels * 255f
                    val score = 1.0f - (sad / maxSad)

                    if (score > bestScore) {
                        bestScore = score
                        bestX = x
                        bestY = y
                    }
                }
                x += stride
            }
            y += stride
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Scan completed in ${elapsed}ms. Best score: $bestScore")

        return if (bestScore >= threshold && bestX != -1) {
            val centerPoint = Point(
                left + bestX + tempW / 2,
                top + bestY + tempH / 2
            )
            MatchResult(centerPoint, bestScore)
        } else {
            null
        }
    }
}

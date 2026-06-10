package com.autoclicker.app.macro

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Helper class for offline OCR text recognition using Google ML Kit.
 * Provides binarization preprocessing to filter out moving livestream video backgrounds.
 */
object OcrHelper {
    private const val TAG = "OcrHelper"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Binarizes a bitmap (pure black & white) to filter out busy backgrounds.
     */
    fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var sum = 0L
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            sum += (r + g + b) / 3
        }
        val threshold = (sum / pixels.size).toInt()

        val outPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val gray = (r + g + b) / 3
            outPixels[i] = if (gray > threshold) Color.WHITE else Color.BLACK
        }
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Recognizes text from bitmap, blocking until completion (must run on Dispatchers.Default).
     */
    fun getTextFromBitmap(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val task = recognizer.process(image)
            
            // Wait synchronously since we are in a background worker coroutine
            val result = Tasks.await(task)
            result.text
        } catch (e: Exception) {
            Log.e(TAG, "OCR error: ${e.message}", e)
            ""
        }
    }

    /**
     * Extract all numbers from the recognized text.
     */
    fun extractAllNumbers(text: String): List<Double> {
        // Loại bỏ định dạng thời gian (ví dụ 00:13, 09:48) để tránh nhận diện nhầm đếm ngược
        val cleanText = text.replace(Regex("""\d+:\d+(:\d+)?"""), " ")
        val numberRegex = Regex("""\d+(\.\d+)?""")
        return numberRegex.findAll(cleanText)
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()
    }

    /**
     * Extract first number from the recognized text.
     */
    fun extractFirstNumber(text: String): Double? {
        val numberRegex = Regex("""\d+(\.\d+)?""")
        val match = numberRegex.find(text)
        return match?.value?.toDoubleOrNull()
    }

    /**
     * Write OCR run log to public app files directory
     */
    fun writeOcrLog(context: android.content.Context, log: String) {
        try {
            val file = java.io.File(context.getExternalFilesDir(null), "ocr_log.txt")
            val timeStamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            file.appendText("[$timeStamp] $log\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ocr log: ${e.message}")
        }
    }
}

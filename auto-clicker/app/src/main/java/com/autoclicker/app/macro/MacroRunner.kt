package com.autoclicker.app.macro

import android.graphics.Bitmap
import android.util.Log
import com.autoclicker.app.service.AutoClickerService
import com.autoclicker.app.service.OverlayService
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coroutine-based macro runner that executes MacroProfile actions.
 * 
 * Design principles:
 * - No while(true) without delay
 * - No Thread.sleep on main thread
 * - Cancellation via Job.cancel() for immediate stop
 * - Pause suspends before next action (not mid-gesture)
 * - Default delay 1000ms between actions
 * - Default loop count 20 (NOT infinite)
 */
class MacroRunner {
    companion object {
        private const val TAG = "MacroRunner"
    }

    private var runJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State
    private val _state = MutableStateFlow(MacroState.IDLE)
    val state: StateFlow<MacroState> = _state.asStateFlow()

    // Progress tracking
    private val _currentLoop = MutableStateFlow(0)
    val currentLoop: StateFlow<Int> = _currentLoop.asStateFlow()

    private val _currentActionIndex = MutableStateFlow(0)
    val currentActionIndex: StateFlow<Int> = _currentActionIndex.asStateFlow()

    private val _totalActionsExecuted = MutableStateFlow(0)
    val totalActionsExecuted: StateFlow<Int> = _totalActionsExecuted.asStateFlow()

    private val _startTimeMs = MutableStateFlow(0L)
    val startTimeMs: StateFlow<Long> = _startTimeMs.asStateFlow()

    private val _pauseReason = MutableStateFlow<String?>(null)
    val pauseReason: StateFlow<String?> = _pauseReason.asStateFlow()

    // Pause mechanism
    @Volatile
    private var isPauseRequested = false
    private var pauseContinuation: CancellableContinuation<Unit>? = null

    // External pause callbacks
    var onPauseBySystem: ((String) -> Unit)? = null

    /**
     * Start executing the given profile.
     */
    fun start(profile: MacroProfile) {
        if (_state.value == MacroState.RUNNING) {
            Log.w(TAG, "Already running, ignoring start request")
            return
        }

        val service = AutoClickerService.instance
        if (service == null) {
            Log.e(TAG, "AccessibilityService not available")
            _state.value = MacroState.ERROR
            return
        }

        if (profile.actions.isEmpty()) {
            Log.w(TAG, "No actions in profile, nothing to run")
            return
        }

        // Warn about infinite loop
        if (profile.isInfiniteLoop) {
            Log.w(TAG, "WARNING: Infinite loop enabled. Will auto-stop after ${profile.maxRunMinutes} minutes.")
        }

        Log.i(TAG, "Starting macro: ${profile.name}, loops=${profile.loopCountDisplay}, actions=${profile.actions.size}")

        _state.value = MacroState.RUNNING
        _currentLoop.value = 0
        _currentActionIndex.value = 0
        _totalActionsExecuted.value = 0
        _startTimeMs.value = System.currentTimeMillis()
        _pauseReason.value = null
        isPauseRequested = false

        runJob = scope.launch {
            try {
                executeProfile(profile, service)
                _state.value = MacroState.STOPPED
                Log.i(TAG, "Macro completed normally. Total actions: ${_totalActionsExecuted.value}")
            } catch (e: CancellationException) {
                _state.value = MacroState.STOPPED
                Log.i(TAG, "Macro cancelled/stopped. Total actions: ${_totalActionsExecuted.value}")
            } catch (e: Exception) {
                _state.value = MacroState.ERROR
                Log.e(TAG, "Macro error: ${e.message}", e)
            }
        }
    }

    /**
     * Stop the macro immediately.
     */
    fun stop() {
        Log.i(TAG, "Stop requested")
        isPauseRequested = false
        pauseContinuation?.cancel()
        pauseContinuation = null
        runJob?.cancel()
        runJob = null
        _state.value = MacroState.STOPPED
        _pauseReason.value = null
    }

    /**
     * Pause the macro (will pause before next action).
     */
    fun pause(reason: String? = null) {
        if (_state.value != MacroState.RUNNING) return
        Log.i(TAG, "Pause requested: ${reason ?: "user"}")
        isPauseRequested = true
        _pauseReason.value = reason
    }

    /**
     * Resume the macro after pause.
     */
    fun resume() {
        if (_state.value != MacroState.PAUSED) return
        Log.i(TAG, "Resume requested")
        isPauseRequested = false
        _state.value = MacroState.RUNNING
        _pauseReason.value = null
        pauseContinuation?.resume(Unit) {}
        pauseContinuation = null
    }

    /**
     * Returns elapsed runtime in milliseconds.
     */
    fun getElapsedMs(): Long {
        val start = _startTimeMs.value
        return if (start > 0) System.currentTimeMillis() - start else 0
    }

    private suspend fun executeProfile(profile: MacroProfile, service: AutoClickerService) {
        val sortedActions = profile.actions.sortedBy { it.orderIndex }
        val totalLoops = if (profile.isInfiniteLoop) Int.MAX_VALUE else profile.loopCount
        val maxRunMs = profile.maxRunMinutes * 60 * 1000L
        val restEveryMs = profile.restEveryMinutes * 60 * 1000L
        var lastRestTime = System.currentTimeMillis()

        for (loop in 1..totalLoops) {
            _currentLoop.value = loop

            // Check max run time
            if (getElapsedMs() >= maxRunMs) {
                Log.i(TAG, "Max run time reached (${profile.maxRunMinutes} min). Stopping.")
                break
            }

            // Log only at start of each loop (not every action to avoid spam)
            Log.d(TAG, "Loop $loop/${profile.loopCountDisplay}")

            var actionIndex = 0
            while (actionIndex < sortedActions.size) {
                val action = sortedActions[actionIndex]
                
                // Check for pause before each action
                checkPause()

                // Check cancellation
                coroutineContext.ensureActive()

                // Check max run time again
                if (getElapsedMs() >= maxRunMs) {
                    Log.i(TAG, "Max run time reached during loop. Stopping.")
                    return
                }

                // Rest period check
                if (restEveryMs > 0 && System.currentTimeMillis() - lastRestTime >= restEveryMs) {
                    Log.i(TAG, "Rest period: ${profile.restDurationSeconds}s")
                    delay(profile.restDurationSeconds * 1000L)
                    lastRestTime = System.currentTimeMillis()
                }

                _currentActionIndex.value = actionIndex

                // 1. Evaluate condition if present
                var conditionPassed = true
                var isConditionEvaluated = false
                var matchResult: BitmapMatcher.MatchResult? = null

                if (action.conditionType != ConditionType.NONE) {
                    isConditionEvaluated = true
                    
                    if (action.conditionType == ConditionType.TEXT_NUMBER_GE) {
                        val screenBmp = ScreenCaptureHelper.captureScreen()
                        if (screenBmp != null) {
                            try {
                                val left = (action.searchRegionLeft * screenBmp.width / 100).coerceIn(0, screenBmp.width - 1)
                                val top = (action.searchRegionTop * screenBmp.height / 100).coerceIn(0, screenBmp.height - 1)
                                val right = (action.searchRegionRight * screenBmp.width / 100).coerceIn(left + 1, screenBmp.width)
                                val bottom = (action.searchRegionBottom * screenBmp.height / 100).coerceIn(top + 1, screenBmp.height)
                                
                                val cropped = Bitmap.createBitmap(screenBmp, left, top, right - left, bottom - top)
                                val text = OcrHelper.getTextFromBitmap(cropped)
                                val numbers = OcrHelper.extractAllNumbers(text)
                                
                                conditionPassed = numbers.any { it >= action.conditionValue }
                                val logText = "OCR: text='$text' | Nums: $numbers | Target: >=${action.conditionValue} | Passed: $conditionPassed"
                                Log.i(TAG, logText)
                                
                                // Ghi log ra file
                                val appSvc = AutoClickerService.instance
                                if (appSvc != null) {
                                    OcrHelper.writeOcrLog(appSvc, logText)
                                }
                                
                                // Cập nhật log lên Overlay Panel UI nổi
                                val uiLogText = "OCR: '$text'\nNums: $numbers\nTarget: >=${action.conditionValue} -> $conditionPassed"
                                OverlayService.instance?.updateOcrLog(uiLogText)

                                cropped.recycle()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to perform OCR condition check: ${e.message}", e)
                                conditionPassed = false
                            } finally {
                                screenBmp.recycle()
                            }
                        } else {
                            Log.w(TAG, "Failed to capture screen for OCR")
                            conditionPassed = false
                        }
                    } else if (action.templateImageBase64 != null) {
                        val templateBmp = BitmapMatcher.decodeBase64ToBitmap(action.templateImageBase64!!)
                        if (templateBmp != null) {
                            val screenBmp = ScreenCaptureHelper.captureScreen()
                            if (screenBmp != null) {
                                matchResult = BitmapMatcher.findTemplate(
                                    screenBmp, templateBmp,
                                    action.searchRegionLeft, action.searchRegionTop,
                                    action.searchRegionRight, action.searchRegionBottom,
                                    action.similarityThreshold
                                )
                                
                                val isFound = matchResult != null
                                conditionPassed = if (action.conditionType == ConditionType.IMAGE_FOUND) isFound else !isFound
                                
                                screenBmp.recycle()
                            } else {
                                Log.w(TAG, "Failed to capture screen for image recognition")
                                conditionPassed = false
                            }
                            templateBmp.recycle()
                        } else {
                            Log.w(TAG, "Failed to decode template image")
                            conditionPassed = false
                        }
                    } else {
                        conditionPassed = false
                    }
                }

                // 2. Decide execution flow
                var nextActionIndex = actionIndex + 1 // Default to next action sequentially

                if (isConditionEvaluated) {
                    if (conditionPassed) {
                        Log.d(TAG, "Condition passed for Action ${action.orderIndex + 1}")
                        if (action.onTrueGoToIndex >= 0) {
                            nextActionIndex = action.onTrueGoToIndex
                        } else {
                            // If index is -1, execute and continue
                            executeAction(action, service, matchResult)
                            _totalActionsExecuted.value++
                        }
                    } else {
                        Log.d(TAG, "Condition failed for Action ${action.orderIndex + 1}")
                        if (action.onFalseGoToIndex >= 0) {
                            nextActionIndex = action.onFalseGoToIndex
                        } else {
                            // If index is -1, skip action and continue
                        }
                    }
                } else {
                    // No condition, execute normally
                    executeAction(action, service, null)
                    _totalActionsExecuted.value++
                }

                // Delay after action
                val delayMs = if (action.delayAfterMs > 0) action.delayAfterMs
                              else profile.delayBetweenActionsMs
                if (delayMs > 0) {
                    delay(delayMs)
                }

                actionIndex = nextActionIndex
            }
        }
    }

    private suspend fun executeAction(
        action: MacroAction,
        service: AutoClickerService,
        matchResult: BitmapMatcher.MatchResult?
    ) {
        when (action.type) {
            ActionType.TAP -> {
                val targetX = if (action.clickMatchedImage && matchResult != null) matchResult.centerPoint.x.toFloat() else action.x
                val targetY = if (action.clickMatchedImage && matchResult != null) matchResult.centerPoint.y.toFloat() else action.y
                Log.d(TAG, "Performing TAP at ($targetX, $targetY) [matched=${action.clickMatchedImage && matchResult != null}]")
                service.performTap(targetX, targetY, action.durationMs)
            }
            ActionType.SWIPE -> {
                Log.d(TAG, "Performing SWIPE from (${action.x}, ${action.y}) to (${action.x2}, ${action.y2})")
                service.performSwipe(
                    action.x, action.y,
                    action.x2, action.y2,
                    action.durationMs
                )
            }
            ActionType.WAIT -> {
                Log.d(TAG, "WAIT ${action.durationMs}ms")
                delay(action.durationMs)
            }
        }
    }

    /**
     * Suspends if pause is requested. Changes state to PAUSED.
     */
    private suspend fun checkPause() {
        if (isPauseRequested) {
            _state.value = MacroState.PAUSED
            Log.i(TAG, "Macro paused")
            suspendCancellableCoroutine<Unit> { cont ->
                pauseContinuation = cont
                cont.invokeOnCancellation {
                    pauseContinuation = null
                }
            }
        }
    }
}

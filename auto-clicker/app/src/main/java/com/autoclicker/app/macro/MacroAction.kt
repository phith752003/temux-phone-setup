package com.autoclicker.app.macro

import java.util.UUID

/**
 * Represents a single action within a macro (tap, swipe, or wait) with optional image recognition conditions.
 */
data class MacroAction(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    var x: Float = 0f,
    var y: Float = 0f,
    var x2: Float = 0f,       // End X for SWIPE
    var y2: Float = 0f,       // End Y for SWIPE
    var durationMs: Long = 100,    // Gesture duration
    var delayAfterMs: Long = 1000, // Delay after this action
    var orderIndex: Int = 0,
    
    // Condition fields for decision tree
    var conditionType: ConditionType = ConditionType.NONE,
    var templateImageBase64: String? = null,
    var searchRegionLeft: Int = 0,
    var searchRegionTop: Int = 0,
    var searchRegionRight: Int = 100,
    var searchRegionBottom: Int = 100,
    var similarityThreshold: Float = 0.8f,
    var onTrueGoToIndex: Int = -1,  // Jump target index if condition is true (-1 for sequential)
    var onFalseGoToIndex: Int = -1,  // Jump target index if condition is false (-1 for sequential)
    var conditionValue: Double = 0.0, // Threshold for numeric conditions
    var clickMatchedImage: Boolean = false // Click on matched template center
)

/**
 * Types of actions that can be performed.
 */
enum class ActionType {
    TAP,
    SWIPE,
    WAIT
}

/**
 * Image recognition condition types.
 */
enum class ConditionType {
    NONE,
    IMAGE_FOUND,
    IMAGE_NOT_FOUND,
    TEXT_NUMBER_GE
}

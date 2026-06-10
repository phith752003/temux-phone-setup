package com.autoclicker.app.macro

import java.util.UUID

/**
 * Represents a macro profile containing a sequence of actions and execution settings.
 */
data class MacroProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled",
    val loopCount: Int = 20,                   // Default 20 loops, NOT infinite
    val delayBetweenActionsMs: Long = 1000,    // Default 1000ms between actions
    val maxRunMinutes: Int = 30,               // Auto-stop after 30 minutes
    val restEveryMinutes: Int = 10,            // Rest every 10 minutes
    val restDurationSeconds: Int = 30,         // Rest for 30 seconds
    val actions: MutableList<MacroAction> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if this profile uses infinite looping.
     * loopCount <= 0 means infinite.
     */
    val isInfiniteLoop: Boolean get() = loopCount <= 0

    /**
     * Returns a display-friendly loop count string.
     */
    val loopCountDisplay: String
        get() = if (isInfiniteLoop) "∞ (max ${maxRunMinutes}min)" else "$loopCount"

    companion object {
        /**
         * Creates a default test profile for development/testing.
         */
        fun createDefaultTestProfile(): MacroProfile {
            return MacroProfile(
                name = "Default Test Macro",
                loopCount = 3,
                delayBetweenActionsMs = 1000,
                maxRunMinutes = 5,
                restEveryMinutes = 10,
                restDurationSeconds = 15,
                actions = mutableListOf(
                    MacroAction(type = ActionType.TAP, x = 540f, y = 800f,
                        durationMs = 100, delayAfterMs = 1000, orderIndex = 0),
                    MacroAction(type = ActionType.TAP, x = 540f, y = 1000f,
                        durationMs = 100, delayAfterMs = 1000, orderIndex = 1),
                    MacroAction(type = ActionType.TAP, x = 300f, y = 600f,
                        durationMs = 100, delayAfterMs = 1000, orderIndex = 2),
                    MacroAction(type = ActionType.TAP, x = 800f, y = 1200f,
                        durationMs = 100, delayAfterMs = 1000, orderIndex = 3),
                    MacroAction(type = ActionType.TAP, x = 540f, y = 960f,
                        durationMs = 100, delayAfterMs = 1000, orderIndex = 4),
                    MacroAction(type = ActionType.SWIPE, x = 300f, y = 800f,
                        x2 = 300f, y2 = 400f,
                        durationMs = 300, delayAfterMs = 1500, orderIndex = 5),
                    MacroAction(type = ActionType.WAIT,
                        durationMs = 2000, delayAfterMs = 0, orderIndex = 6)
                )
            )
        }
    }
}

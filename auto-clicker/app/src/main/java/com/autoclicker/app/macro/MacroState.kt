package com.autoclicker.app.macro

/**
 * Represents the current state of the macro runner.
 */
enum class MacroState {
    IDLE,
    RUNNING,
    PAUSED,
    STOPPED,
    ERROR
}

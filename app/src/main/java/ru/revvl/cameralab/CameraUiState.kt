package ru.revvl.cameralab

/**
 * Viewfinder state from ТЗ 4.1: initializing / ready / capturing / recording / processing / error.
 * Derived from the activity's flags in one place instead of repeating
 * `recording != null || photoBusy || timerRunnable != null` in every handler.
 */
enum class CameraUiState {
    NO_PERMISSION, INITIALIZING, READY, COUNTDOWN, CAPTURING, PROCESSING, RECORDING, PROBING, ERROR;

    /** Lens, mode, format, extension and PRO changes are allowed only when nothing is in progress. */
    val canReconfigure: Boolean
        get() = this == READY || this == ERROR

    val isBusy: Boolean
        get() = this == COUNTDOWN || this == CAPTURING || this == PROCESSING || this == RECORDING || this == PROBING

    companion object {
        fun from(
            hasPermission: Boolean,
            initializing: Boolean,
            hasCamera: Boolean,
            recording: Boolean,
            photoBusy: Boolean,
            burst: Boolean,
            countdown: Boolean,
            /** Diagnostics session probe rebinding the camera through declared combinations. */
            probing: Boolean = false
        ): CameraUiState = when {
            probing -> PROBING
            // Busy states first: they must block reconfiguration even if the camera is lost.
            recording -> RECORDING
            photoBusy && burst -> PROCESSING
            photoBusy -> CAPTURING
            countdown -> COUNTDOWN
            !hasPermission -> NO_PERMISSION
            initializing -> INITIALIZING
            !hasCamera -> ERROR
            else -> READY
        }
    }
}

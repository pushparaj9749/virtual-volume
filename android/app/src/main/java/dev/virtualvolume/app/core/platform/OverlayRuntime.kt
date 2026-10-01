package dev.virtualvolume.app.core.platform

import dev.virtualvolume.app.overlay.WindowPlacement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide runtime state of the floating control.
 *
 * The overlay service, the dashboard and the Quick Settings tile all live in the same
 * process, so a single in-memory holder keeps them in sync without broadcasts. The
 * persisted "enabled" preference stays the source of truth across restarts; this holder
 * only reports whether the service is actually alive right now.
 */
object OverlayRuntime {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _placement = MutableStateFlow<WindowPlacement?>(null)
    val placement: StateFlow<WindowPlacement?> = _placement.asStateFlow()
    fun setPlacement(value: WindowPlacement?) { _placement.value = value }

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun reportError(message: String?) {
        _lastError.value = message
    }
}

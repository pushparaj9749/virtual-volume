package dev.virtualvolume.app.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.virtualvolume.app.appContainer
import dev.virtualvolume.app.core.audio.VolumeState
import dev.virtualvolume.app.core.audio.VolumeResult
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.core.platform.NotificationPermission
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.core.platform.OverlayRuntime
import dev.virtualvolume.app.core.platform.OverlayStartResult
import dev.virtualvolume.app.tile.TileStateSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val settings: VolumeSettings = VolumeSettings.DEFAULT,
    val volume: VolumeState = VolumeState.UNKNOWN,
    val serviceRunning: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val runtimeError: String? = null,
    val isLoading: Boolean = true,
) {
    /** True only when the control is really on screen right now. */
    val controlActive: Boolean
        get() = overlayPermissionGranted && serviceRunning && settings.enabled

    val needsNotificationPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled
}

/** One-shot messages the UI turns into a snackbar or a settings screen. */
sealed interface UiEvent {
    data object RequestOverlayPermission : UiEvent
    data object RequestNotificationPermission : UiEvent
    data object OpenTilePreferences : UiEvent
    data class Error(val message: String) : UiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer

    private val permissions = MutableStateFlow(
        PermissionSnapshot(
            overlay = OverlayPermission.isGranted(application),
            notifications = NotificationPermission.isGranted(application),
        ),
    )

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<UiEvent> = events.receiveAsFlow()

    private val volumeFlow: Flow<VolumeState> = container.settingsRepository.settings
        .map { it.audioStream }
        .distinctUntilChanged()
        .flatMapLatest { stream ->
            merge(
                container.audioVolumeMonitor.observe(stream) { container.volumeController.snapshot(it).index }
                    .map { container.volumeController.snapshot(stream) },
                container.volumeController.changes.filter { it.stream == stream },
            )
        }

    val uiState: StateFlow<UiState> = combine(
        container.settingsRepository.settings,
        volumeFlow,
        OverlayRuntime.isServiceRunning,
        permissions,
        OverlayRuntime.lastError,
    ) { settings, volume, running, permission, error ->
        UiState(
            settings = settings,
            volume = volume,
            serviceRunning = running,
            overlayPermissionGranted = permission.overlay,
            notificationsEnabled = permission.notifications,
            runtimeError = error ?: settings.serviceNotice,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    /** Re-reads permission state; called whenever the activity resumes. */
    fun refreshPermissions() {
        val app = getApplication<Application>()
        permissions.value = PermissionSnapshot(
            overlay = OverlayPermission.isGranted(app),
            notifications = NotificationPermission.isGranted(app),
        )
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            if (settings.enabled && settings.serviceNotice == null && permissions.value.overlay && !OverlayRuntime.isServiceRunning.value) {
                container.overlayServiceController.start()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                when (val result = container.overlayServiceController.setEnabled(enabled)) {
                    OverlayStartResult.Started -> {
                        if (enabled && !NotificationPermission.isGranted(getApplication()) && Build.VERSION.SDK_INT >= 33) {
                            events.send(UiEvent.RequestNotificationPermission)
                        }
                    }
                    OverlayStartResult.MissingOverlayPermission -> events.send(UiEvent.RequestOverlayPermission)
                    is OverlayStartResult.Blocked -> events.send(UiEvent.Error(result.message))
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                events.send(UiEvent.Error("Settings could not be saved. Please retry."))
            }
            TileStateSync.refresh(getApplication())
        }
    }

    fun updateSettings(transform: (VolumeSettings) -> VolumeSettings) {
        viewModelScope.launch {
            try { container.settingsRepository.update(transform) }
            catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                events.send(UiEvent.Error("Your preference could not be saved. Please retry."))
            }
        }
    }

    /** Drives the media-volume slider in the Audio section. */
    fun setVolumeIndex(index: Int) {
        viewModelScope.launch {
            handleVolumeResult(container.volumeController.setIndex(uiState.value.settings.audioStream, index))
        }
    }

    /** Applies a step coming from the in-app preview of the floating control. */
    fun applyPreviewStep(deltaSteps: Int) {
        val settings = uiState.value.settings
        val result = container.volumeController.changeBy(
            stream = settings.audioStream,
            deltaSteps = deltaSteps,
            stepSize = settings.volumeStep,
        )
        handleVolumeResult(result)
        if (result is VolumeResult.Success && result.changed && settings.hapticsEnabled) container.haptics.tick()
    }

    private fun handleVolumeResult(result: VolumeResult) {
        if (result is VolumeResult.Failure) viewModelScope.launch { events.send(UiEvent.Error(result.message)) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(onboardingCompleted = true) }
        }
    }

    fun requestOverlayPermission() {
        viewModelScope.launch { events.send(UiEvent.RequestOverlayPermission) }
    }

    fun requestNotificationPermission() {
        viewModelScope.launch { events.send(UiEvent.RequestNotificationPermission) }
    }

    fun openTilePreferences() {
        viewModelScope.launch { events.send(UiEvent.OpenTilePreferences) }
    }

    fun consumeRuntimeError() {
        OverlayRuntime.reportError(null)
    }

    private data class PermissionSnapshot(val overlay: Boolean, val notifications: Boolean)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Application is required to build MainViewModel")
                MainViewModel(app)
            }
        }
    }
}

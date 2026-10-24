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
import dev.virtualvolume.app.core.audio.CallAudioMode
import dev.virtualvolume.app.core.audio.VolumeState
import dev.virtualvolume.app.core.audio.VolumeStreamResolver
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.core.data.VolumeStreamType
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val settings: VolumeSettings = VolumeSettings.DEFAULT,
    val volume: VolumeState = VolumeState.UNKNOWN,
    val callMode: CallAudioMode = CallAudioMode.IDLE,
    /**
     * The stream the control drives right now — the selected one, or call volume while a
     * call is up and the user allowed the control to follow calls.
     */
    val activeStream: VolumeStreamType = VolumeStreamType.DEFAULT,
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

    /** True while the control is driving call volume instead of the selected stream. */
    val followingCall: Boolean
        get() = VolumeStreamResolver.isFollowingCall(
            configured = settings.audioStream,
            active = activeStream,
        )
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

    /**
     * Polls the audio mode only while the call-following option is on, and stops the
     * moment it is turned off. Shared so the screen runs one poll, not one per consumer.
     */
    private val callModeFlow: StateFlow<CallAudioMode> = container.settingsRepository.settings
        .map { it.followActiveCall }
        .distinctUntilChanged()
        .flatMapLatest { follow ->
            if (follow) container.callAudioMonitor.modeChanges() else flowOf(CallAudioMode.IDLE)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CallAudioMode.IDLE,
        )

    private val activeStreamFlow: Flow<VolumeStreamType> =
        combine(container.settingsRepository.settings, callModeFlow) { settings, mode ->
            VolumeStreamResolver.resolve(settings.audioStream, mode, settings.followActiveCall)
        }.distinctUntilChanged()

    /** Service + permission state, which Android only lets the app re-read on resume. */
    private val runtimeFlow: Flow<RuntimeSnapshot> =
        combine(OverlayRuntime.isServiceRunning, permissions) { running, permission ->
            RuntimeSnapshot(running, permission)
        }

    private val volumeFlow: Flow<VolumeState> = activeStreamFlow
        .flatMapLatest { stream ->
            container.audioVolumeMonitor.observe(stream) { container.volumeController.snapshot(it).index }
                .map { container.volumeController.snapshot(stream) }
        }

    val uiState: StateFlow<UiState> = combine(
        container.settingsRepository.settings,
        volumeFlow,
        activeStreamFlow,
        callModeFlow,
        runtimeFlow,
    ) { settings, volume, activeStream, mode, runtime ->
        UiState(
            settings = settings,
            volume = volume,
            callMode = mode,
            activeStream = activeStream,
            serviceRunning = runtime.serviceRunning,
            overlayPermissionGranted = runtime.permission.overlay,
            notificationsEnabled = runtime.permission.notifications,
            runtimeError = OverlayRuntime.lastError.value,
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
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(enabled = enabled) }
            if (enabled) {
                when (val result = container.overlayServiceController.start()) {
                    is OverlayStartResult.Started -> Unit
                    is OverlayStartResult.MissingOverlayPermission ->
                        events.send(UiEvent.RequestOverlayPermission)

                    is OverlayStartResult.Blocked ->
                        events.send(UiEvent.Error(result.message))
                }
            } else {
                container.overlayServiceController.stop()
            }
            TileStateSync.refresh(getApplication())
        }
    }

    fun updateSettings(transform: (VolumeSettings) -> VolumeSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
        }
    }

    /** Drives the volume slider in the Audio section (call volume while on a call). */
    fun setVolumeIndex(index: Int) {
        viewModelScope.launch {
            container.volumeController.setIndex(uiState.value.activeStream, index)
        }
    }

    /** Applies a step coming from the in-app preview of the floating control. */
    fun applyPreviewStep(deltaSteps: Int) {
        val state = uiState.value
        val settings = state.settings
        val result = container.volumeController.changeBy(
            stream = state.activeStream,
            deltaSteps = deltaSteps,
            stepSize = settings.volumeStep,
        )
        if (result is dev.virtualvolume.app.core.audio.VolumeResult.Success && settings.hapticsEnabled) {
            container.haptics.tick()
        }
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

    private data class RuntimeSnapshot(
        val serviceRunning: Boolean,
        val permission: PermissionSnapshot,
    )

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

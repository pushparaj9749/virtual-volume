package dev.virtualvolume.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.virtualvolume.app.core.platform.NotificationPermission
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.ui.MainViewModel
import dev.virtualvolume.app.ui.UiEvent
import dev.virtualvolume.app.ui.UiState
import dev.virtualvolume.app.ui.about.AboutScreen
import dev.virtualvolume.app.ui.dashboard.DashboardScreen
import dev.virtualvolume.app.ui.onboarding.OnboardingScreen
import dev.virtualvolume.app.ui.theme.VirtualVolumeTheme

/**
 * The only activity in the app. Compose handles every screen; the activity owns the pieces
 * that genuinely need it — permission launchers, system-settings intents and the snackbar.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            VirtualVolumeTheme(themeMode = state.settings.themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AppRoot(state = state, viewModel = viewModel)

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                }
            }

            // Permissions can change in system settings, so they are re-read on every resume.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

            LaunchedEffect(Unit) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        UiEvent.RequestOverlayPermission -> openOverlaySettings()
                        UiEvent.RequestNotificationPermission -> requestNotificationPermission()
                        UiEvent.OpenTilePreferences -> openTilePreferences()
                        is UiEvent.Error -> snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    private fun openOverlaySettings() {
        runCatching { startActivity(OverlayPermission.settingsIntent(this)) }
            .onFailure { openAppDetails() }
    }

    private fun openAppDetails() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openTilePreferences() {
        runCatching {
            startActivity(
                Intent("android.settings.QS_TILE_PREFERENCES")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationPermission.isGranted(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            runCatching { startActivity(NotificationPermission.settingsIntent(this)) }
        }
    }
}

private enum class Destination { ONBOARDING, DASHBOARD, ABOUT, SETUP_GUIDE }

@Composable
private fun AppRoot(state: UiState, viewModel: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf<Destination?>(null) }

    val startDestination = when {
        state.isLoading -> null
        state.settings.onboardingCompleted -> Destination.DASHBOARD
        else -> Destination.ONBOARDING
    }

    LaunchedEffect(startDestination) {
        if (destination == null && startDestination != null) destination = startDestination
    }

    val current = destination
    if (current == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "destination",
    ) { target ->
        when (target) {
            Destination.ONBOARDING, Destination.SETUP_GUIDE -> OnboardingScreen(
                state = state,
                isReplay = target == Destination.SETUP_GUIDE,
                onRequestOverlayPermission = viewModel::requestOverlayPermission,
                onEnable = { viewModel.setEnabled(true) },
                onPreviewStep = viewModel::applyPreviewStep,
                onFinish = {
                    viewModel.completeOnboarding()
                    destination = Destination.DASHBOARD
                },
            )

            Destination.DASHBOARD -> DashboardScreen(
                state = state,
                onToggleEnabled = viewModel::setEnabled,
                onUpdateSettings = viewModel::updateSettings,
                onVolumeIndexChange = viewModel::setVolumeIndex,
                onPreviewStep = viewModel::applyPreviewStep,
                onGrantOverlayPermission = viewModel::requestOverlayPermission,
                onGrantNotificationPermission = viewModel::requestNotificationPermission,
                onOpenTilePreferences = viewModel::openTilePreferences,
                onOpenSetupGuide = { destination = Destination.SETUP_GUIDE },
                onOpenAbout = { destination = Destination.ABOUT },
            )

            Destination.ABOUT -> AboutScreen(onBack = { destination = Destination.DASHBOARD })
        }
    }
}

package dev.virtualvolume.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.virtualvolume.app.BuildConfig
import dev.virtualvolume.app.R
import dev.virtualvolume.app.core.data.ScreenEdge
import dev.virtualvolume.app.core.data.TapMode
import dev.virtualvolume.app.core.data.ThemeMode
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.core.data.VolumeStreamType
import dev.virtualvolume.app.ui.UiState
import dev.virtualvolume.app.ui.components.ActionRow
import dev.virtualvolume.app.ui.components.ChoiceChipRow
import dev.virtualvolume.app.ui.components.GlassCard
import dev.virtualvolume.app.ui.components.SectionHeader
import dev.virtualvolume.app.ui.components.SegmentedChoiceRow
import dev.virtualvolume.app.ui.components.SliderRow
import dev.virtualvolume.app.ui.components.SwitchRow
import dev.virtualvolume.app.ui.components.VolumeControlPreview
import dev.virtualvolume.app.ui.theme.LocalBrand
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    state: UiState,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateSettings: ((VolumeSettings) -> VolumeSettings) -> Unit,
    onVolumeIndexChange: (Int) -> Unit,
    onPreviewStep: (Int) -> Unit,
    onGrantOverlayPermission: () -> Unit,
    onGrantNotificationPermission: () -> Unit,
    onOpenTilePreferences: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        StatusCard(
            state = state,
            onToggleEnabled = onToggleEnabled,
            onPreviewStep = onPreviewStep,
        )

        if (!state.overlayPermissionGranted) {
            AttentionCard(
                icon = Icons.Rounded.Warning,
                title = "Overlay permission is off",
                body = "Android only lets an app draw on screen after you allow “Display over other apps”. " +
                    "Until then the control cannot appear.",
                actionLabel = "Open permission",
                onAction = onGrantOverlayPermission,
            )
        } else if (state.needsNotificationPermission && settings.enabled) {
            AttentionCard(
                icon = Icons.Rounded.Notifications,
                title = "Notifications are blocked",
                body = "Android hides the notification that keeps the control running. The control still " +
                    "works, but a silent notification is what keeps it alive.",
                actionLabel = "Allow notifications",
                onAction = onGrantNotificationPermission,
            )
        }

        state.runtimeError?.let { message ->
            AttentionCard(
                icon = Icons.Rounded.Warning,
                title = "Something blocked the control",
                body = message,
                actionLabel = "Open settings",
                onAction = onGrantOverlayPermission,
            )
        }

        ControlSection(
            settings = settings,
            serviceActive = state.controlActive,
            onToggleEnabled = onToggleEnabled,
            onUpdateSettings = onUpdateSettings,
        )

        AudioSection(
            state = state,
            onUpdateSettings = onUpdateSettings,
            onVolumeIndexChange = onVolumeIndexChange,
        )

        FeedbackSection(settings = settings, onUpdateSettings = onUpdateSettings)

        QuickSettingsSection(
            serviceActive = state.controlActive,
            onOpenTilePreferences = onOpenTilePreferences,
        )

        SupportSection(
            onOpenSetupGuide = onOpenSetupGuide,
            onOpenAbout = onOpenAbout,
        )

        ScreenOffNote()

        Text(
            text = "Virtual Volume ${BuildConfig.VERSION_NAME} · no analytics, no accounts, no network calls",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun StatusCard(
    state: UiState,
    onToggleEnabled: (Boolean) -> Unit,
    onPreviewStep: (Int) -> Unit,
) {
    val active = state.controlActive
    val accent by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "status-accent",
    )

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Virtual Volume",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        active -> "The control is live on the ${state.settings.edge.label.lowercase()} edge."
                        !state.overlayPermissionGranted -> "Waiting for overlay permission."
                        else -> "Turned off. The control is not on screen."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                StatusPill(active = active)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onToggleEnabled(!state.settings.enabled) },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (state.settings.enabled) Icons.Rounded.Close else Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.settings.enabled) "Turn off" else "Turn on")
                }
            }

            Spacer(Modifier.width(16.dp))

            PhonePreview(
                state = state,
                accent = accent,
                onStep = onPreviewStep,
                modifier = Modifier.width(122.dp).aspectRatio(0.48f),
            )
        }
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val color by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        label = "pill-color",
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pulse by animateFloatAsState(
            targetValue = if (active) 1f else 0.45f,
            label = "pill-pulse",
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulse)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (active) "Status: ON" else "Status: OFF",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun PhonePreview(
    state: UiState,
    accent: Color,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val shape = RoundedCornerShape(22.dp)
    val brand = LocalBrand.current

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    ),
                ),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        // Suggestion of an app running underneath the control.
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.fillMaxWidth(0.55f).height(10.dp).clip(CircleShape)
                    .background(accent.copy(alpha = 0.25f)),
            )
            Box(
                Modifier.fillMaxWidth(0.85f).height(6.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
            )
            Box(
                Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
            )
        }

        val touchLength = settings.touchZoneLengthDp.dp.coerceAtMost(maxHeight * 0.72f)
        val travel = (maxHeight - touchLength).coerceAtLeast(0.dp)

        VolumeControlPreview(
            settings = settings,
            level = state.volume.fraction,
            maxVolume = state.volume.max,
            onStep = onStep,
            modifier = Modifier
                .align(
                    if (settings.edge == ScreenEdge.RIGHT) Alignment.TopEnd else Alignment.TopStart,
                )
                .offset(y = travel * settings.offsetFraction)
                .width(settings.touchZoneWidthDp.dp)
                .height(touchLength),
        )

        if (brand.isDark) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x14000000)),
                    ),
                ),
            )
        }
    }
}

@Composable
private fun AttentionCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    GlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ControlSection(
    settings: VolumeSettings,
    serviceActive: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateSettings: ((VolumeSettings) -> VolumeSettings) -> Unit,
) {
    Column {
        SectionHeader("Control")
        GlassCard {
            SwitchRow(
                title = "Enable Virtual Volume",
                subtitle = if (serviceActive) "Control is on screen" else "Shows the floating control",
                checked = settings.enabled,
                onCheckedChange = onToggleEnabled,
            )
            RowDivider()
            SegmentedChoiceRow(
                label = "Edge",
                options = ScreenEdge.entries.toList(),
                optionLabel = { it.label },
                selected = settings.edge,
                onSelect = { edge -> onUpdateSettings { it.copy(edge = edge) } },
            )
            SliderRow(
                label = "Position",
                valueLabel = "${(settings.offsetFraction * 100).roundToInt()}%",
                value = settings.offsetFraction,
                valueRange = VolumeSettings.MIN_OFFSET_FRACTION..VolumeSettings.MAX_OFFSET_FRACTION,
                onValueChange = { value -> onUpdateSettings { it.copy(offsetFraction = value) } },
            )
            SliderRow(
                label = "Length",
                valueLabel = "${settings.lengthDp.roundToInt()} dp",
                value = settings.lengthDp,
                valueRange = VolumeSettings.MIN_LENGTH_DP..VolumeSettings.MAX_LENGTH_DP,
                onValueChange = { value -> onUpdateSettings { it.copy(lengthDp = value) } },
            )
            SliderRow(
                label = "Thickness",
                valueLabel = "${settings.thicknessDp.roundToInt()} dp",
                value = settings.thicknessDp,
                valueRange = VolumeSettings.MIN_THICKNESS_DP..VolumeSettings.MAX_THICKNESS_DP,
                onValueChange = { value -> onUpdateSettings { it.copy(thicknessDp = value) } },
            )
            SliderRow(
                label = "Idle opacity",
                valueLabel = "${(settings.idleOpacity * 100).roundToInt()}%",
                value = settings.idleOpacity,
                valueRange = VolumeSettings.MIN_IDLE_OPACITY..VolumeSettings.MAX_OPACITY,
                onValueChange = { value -> onUpdateSettings { it.copy(idleOpacity = value) } },
            )
            SliderRow(
                label = "Active opacity",
                valueLabel = "${(settings.activeOpacity * 100).roundToInt()}%",
                value = settings.activeOpacity,
                valueRange = VolumeSettings.MIN_ACTIVE_OPACITY..VolumeSettings.MAX_OPACITY,
                onValueChange = { value -> onUpdateSettings { it.copy(activeOpacity = value) } },
            )
            SliderRow(
                label = "Touch area width",
                valueLabel = "${settings.touchZoneWidthDp.roundToInt()} dp",
                value = settings.touchZoneWidthDp,
                valueRange = VolumeSettings.MIN_TOUCH_WIDTH_DP..VolumeSettings.MAX_TOUCH_WIDTH_DP,
                onValueChange = { value -> onUpdateSettings { it.copy(touchZoneWidthDp = value) } },
            )
            SliderRow(
                label = "Touch area length",
                valueLabel = "${settings.touchZoneLengthDp.roundToInt()} dp",
                value = settings.touchZoneLengthDp,
                valueRange = VolumeSettings.MIN_TOUCH_LENGTH_DP..VolumeSettings.MAX_TOUCH_LENGTH_DP,
                onValueChange = { value -> onUpdateSettings { it.copy(touchZoneLengthDp = value) } },
            )
            SliderRow(
                label = "Swipe sensitivity",
                valueLabel = String.format(Locale.US, "%.1f×", settings.swipeSensitivity),
                value = settings.swipeSensitivity,
                valueRange = VolumeSettings.MIN_SENSITIVITY..VolumeSettings.MAX_SENSITIVITY,
                onValueChange = { value -> onUpdateSettings { it.copy(swipeSensitivity = value) } },
            )
            RowDivider()
            SwitchRow(
                title = "Start after reboot",
                subtitle = "Brings the control back when the phone restarts",
                checked = settings.startOnBoot,
                onCheckedChange = { value -> onUpdateSettings { it.copy(startOnBoot = value) } },
            )
            SegmentedChoiceRow(
                label = "Appearance",
                options = ThemeMode.entries.toList(),
                optionLabel = { it.label },
                selected = settings.themeMode,
                onSelect = { mode -> onUpdateSettings { it.copy(themeMode = mode) } },
            )
        }
    }
}

@Composable
private fun AudioSection(
    state: UiState,
    onUpdateSettings: ((VolumeSettings) -> VolumeSettings) -> Unit,
    onVolumeIndexChange: (Int) -> Unit,
) {
    val settings = state.settings
    val maxVolume = state.volume.max.coerceAtLeast(1)
    val index = state.volume.index.coerceIn(0, maxVolume)

    Column {
        SectionHeader("Audio")
        GlassCard {
            ChoiceChipRow(
                label = "Volume stream",
                options = VolumeStreamType.entries.toList(),
                optionLabel = { it.label },
                selected = settings.audioStream,
                onSelect = { stream -> onUpdateSettings { it.copy(audioStream = stream) } },
            )
            Text(
                text = "Media is the default because it is what the physical rocker reaches for most often.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            SliderRow(
                label = "${settings.audioStream.label} volume",
                valueLabel = if (state.volume.index >= 0) "$index / $maxVolume" else "Unavailable",
                value = index.toFloat(),
                valueRange = 0f..maxVolume.toFloat(),
                steps = (maxVolume - 1).coerceAtLeast(0),
                onValueChange = { value -> onVolumeIndexChange(value.roundToInt()) },
            )
            SliderRow(
                label = "Volume step",
                valueLabel = "${settings.volumeStep} level${if (settings.volumeStep == 1) "" else "s"}",
                value = settings.volumeStep.toFloat(),
                valueRange = VolumeSettings.MIN_VOLUME_STEP.toFloat()..VolumeSettings.MAX_VOLUME_STEP.toFloat(),
                steps = VolumeSettings.MAX_VOLUME_STEP - VolumeSettings.MIN_VOLUME_STEP,
                onValueChange = { value -> onUpdateSettings { it.copy(volumeStep = value.roundToInt()) } },
            )
            RowDivider()
            SegmentedChoiceRow(
                label = "Tap behaviour",
                options = TapMode.entries.toList(),
                optionLabel = { it.label },
                selected = settings.tapMode,
                onSelect = { mode -> onUpdateSettings { it.copy(tapMode = mode) } },
            )
            Text(
                text = settings.tapMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedbackSection(
    settings: VolumeSettings,
    onUpdateSettings: ((VolumeSettings) -> VolumeSettings) -> Unit,
) {
    Column {
        SectionHeader("Feedback")
        GlassCard {
            SwitchRow(
                title = "Haptic feedback",
                subtitle = "One short tick per volume step",
                checked = settings.hapticsEnabled,
                onCheckedChange = { value -> onUpdateSettings { it.copy(hapticsEnabled = value) } },
            )
            RowDivider()
            SwitchRow(
                title = "Animations",
                subtitle = "Fade and fill transitions on the control",
                checked = settings.animationsEnabled,
                onCheckedChange = { value -> onUpdateSettings { it.copy(animationsEnabled = value) } },
            )
            SliderRow(
                label = "Animation duration",
                valueLabel = "${settings.animationDurationMs} ms",
                value = settings.animationDurationMs.toFloat(),
                valueRange = VolumeSettings.MIN_ANIMATION_MS.toFloat()..VolumeSettings.MAX_ANIMATION_MS.toFloat(),
                onValueChange = { value -> onUpdateSettings { it.copy(animationDurationMs = value.toLong()) } },
            )
        }
    }
}

@Composable
private fun QuickSettingsSection(
    serviceActive: Boolean,
    onOpenTilePreferences: () -> Unit,
) {
    Column {
        SectionHeader("Quick Settings")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tile),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Virtual Volume tile",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (serviceActive) "Shows as ON in the panel" else "Shows as OFF in the panel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Add it once: swipe down twice to open Quick Settings, tap the pencil (Edit), " +
                    "then drag Virtual Volume into your tiles. Tapping the tile turns the control on and off " +
                    "without opening the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ActionRow(
                title = "Open tile settings",
                subtitle = "Jump straight to the Quick Settings editor",
                icon = Icons.Rounded.Settings,
                onClick = onOpenTilePreferences,
            )
        }
    }
}

@Composable
private fun SupportSection(
    onOpenSetupGuide: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column {
        SectionHeader("Support")
        GlassCard {
            ActionRow(
                title = "Setup guide",
                subtitle = "Replay the five-step introduction",
                icon = Icons.Rounded.Refresh,
                onClick = onOpenSetupGuide,
            )
            RowDivider()
            ActionRow(
                title = "About",
                subtitle = "How it works, permissions and privacy",
                icon = Icons.Rounded.Info,
                onClick = onOpenAbout,
            )
            RowDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScreenOffNote() {
    GlassCard {
        Text(
            text = "WHEN THE SCREEN IS OFF",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Android does not deliver touch events to overlays while the display is powered down, " +
                "and no app can change that. Virtual Volume keeps running in the background and restores the " +
                "control the moment the screen turns back on — it does not claim to work with the screen off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

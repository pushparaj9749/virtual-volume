package dev.virtualvolume.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.virtualvolume.app.R
import dev.virtualvolume.app.core.data.ScreenEdge
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.ui.UiState
import dev.virtualvolume.app.ui.components.VolumeControlPreview

private const val STEP_COUNT = 5

/**
 * First-run flow: welcome → permission → enable → hands-on practice → Quick Settings.
 *
 * The tutorial step is not a video or a picture; it is the real control, wired to the real
 * volume stream, so the gesture is learned on the thing the user will actually use.
 */
@Composable
fun OnboardingScreen(
    state: UiState,
    onRequestOverlayPermission: () -> Unit,
    onEnable: () -> Unit,
    onPreviewStep: (Int) -> Unit,
    onFinish: () -> Unit,
    isReplay: Boolean,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0 || isReplay) { if (step > 0) step -= 1 else onFinish() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                IconButton(onClick = { step -= 1 }) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Step ${step + 1} of $STEP_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFinish) {
                Text(
                    text = if (isReplay) "Close" else "Skip",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ProgressDots(step = step, total = STEP_COUNT, modifier = Modifier.padding(vertical = 10.dp))

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInHorizontally(tween(320)) { width -> if (forward) width else -width } +
                    fadeIn(tween(320))
                val exit = slideOutHorizontally(tween(320)) { width -> if (forward) -width else width } +
                    fadeOut(tween(200))
                enter togetherWith exit
            },
            label = "onboarding-step",
        ) { current ->
            when (current) {
                0 -> WelcomeStep(settings = state.settings, state = state)
                1 -> PermissionStep(
                    granted = state.overlayPermissionGranted,
                    onGrant = onRequestOverlayPermission,
                )

                2 -> EnableStep(running = state.controlActive, error = state.runtimeError, onEnable = onEnable)
                3 -> TutorialStep(state = state, onPreviewStep = onPreviewStep)
                else -> QuickSettingsStep()
            }
        }

        val nextEnabled = when (step) {
            1 -> state.overlayPermissionGranted
            2 -> isReplay || state.controlActive
            else -> true
        }
        Button(
            onClick = { if (step == STEP_COUNT - 1) onFinish() else step += 1 },
            enabled = nextEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
                .height(52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = when (step) {
                    STEP_COUNT - 1 -> if (isReplay) "Close" else "Got it"
                    else -> "Continue"
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProgressDots(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val selected = index == step
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(if (selected) 22.dp else 7.dp)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    body: String,
    footer: @Composable () -> Unit = {},
    visual: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val visualHeight = (maxHeight * 0.5f).coerceIn(150.dp, 300.dp)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(visualHeight), contentAlignment = Alignment.Center, content = visual)
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            footer()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WelcomeStep(settings: VolumeSettings, state: UiState) {
    StepScaffold(
        title = "Virtual Volume",
        body = "Your volume button, rebuilt for your screen. A thin, translucent control stays near your " +
            "physical volume buttons, over other apps where Android allows it. No root. No ads. Just your sound.",
        footer = { Text("Welcome · Made for Android", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) },
    ) {
        HeroPreview(
            settings = settings,
            level = state.volume.fraction,
            maxVolume = state.volume.max,
            onStep = null,
        )
    }
}

@Composable
private fun PermissionStep(granted: Boolean, onGrant: () -> Unit) {
    StepScaffold(
        title = "Allow drawing over other apps",
        body = "The control has to sit on top of whatever you are doing, so Android asks for one " +
            "permission: “Display over other apps”. It only draws a compact edge control. Your settings stay " +
            "on this phone; the app has no network access.",
        footer = {
            OutlinedButton(
                onClick = onGrant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (granted) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Permission granted")
                } else {
                    Text("Grant permission")
                }
            }
        },
    ) {
        PhoneFrame(modifier = Modifier.width(190.dp).aspectRatio(0.5f)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(3) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (index == 1) 0.18f else 0.08f,
                                ),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (granted && index == 1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                                    },
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnableStep(running: Boolean, error: String?, onEnable: () -> Unit) {
    StepScaffold(
        title = "Turn Virtual Volume on",
        body = "A small background service keeps the control available while you use other apps. " +
            "Android shows a quiet notification for it — that is the system's rule for anything " +
            "long-running, and it doubles as your off switch.",
        footer = {
            OutlinedButton(
                onClick = onEnable,
                enabled = !running,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (running) "Running" else "Enable Virtual Volume")
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        },
    ) {
        NotificationPreview(running = running)
    }
}

@Composable
private fun TutorialStep(state: UiState, onPreviewStep: (Int) -> Unit) {
    StepScaffold(
        title = "Try it now",
        body = "This is the real control. Tap the top half to raise the volume, tap the bottom half " +
            "to lower it, or drag up and down for smooth changes.",
        footer = { VolumeReadout(state = state) },
    ) {
        HeroPreview(
            settings = state.settings,
            level = state.volume.fraction,
            maxVolume = state.volume.max,
            onStep = onPreviewStep,
        )
    }
}

@Composable
private fun QuickSettingsStep() {
    StepScaffold(
        title = "Add it to Quick Settings",
        body = "For instant access, add Virtual Volume to Quick Settings: swipe down twice, tap the " +
            "pencil, then drag the Virtual Volume tile into your panel. Tapping the tile turns the " +
            "control on and off. Adding a tile is always your choice; the editor can look different on your phone.",
    ) {
        QuickSettingsMock()
    }
}

@Composable
private fun VolumeReadout(state: UiState) {
    val max = state.volume.max
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tile),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (max > 0) {
                "${state.volume.index.coerceAtLeast(0)} of $max · ${state.settings.audioStream.label}"
            } else {
                "Volume unavailable on this device"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeroPreview(
    settings: VolumeSettings,
    level: Float,
    maxVolume: Int,
    onStep: ((Int) -> Unit)?,
) {
    PhoneFrame(modifier = Modifier.width(190.dp).aspectRatio(0.48f)) {
        ScaledPreview(
            settings = settings,
            level = level,
            maxVolume = maxVolume,
            onStep = onStep,
        )
        if (onStep == null) BreathingHint()
    }
}

/** Places the control on the chosen edge, at the stored relative offset, inside any box. */
@Composable
private fun BoxScope.ScaledPreview(
    settings: VolumeSettings,
    level: Float,
    maxVolume: Int,
    onStep: ((Int) -> Unit)?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        PlaceControl(settings = settings, level = level, maxVolume = maxVolume, onStep = onStep)
    }
}

@Composable
private fun BoxWithConstraintsScope.PlaceControl(
    settings: VolumeSettings,
    level: Float,
    maxVolume: Int,
    onStep: ((Int) -> Unit)?,
) {
    val touchLength: Dp = settings.touchZoneLengthDp.dp.coerceAtMost(maxHeight * 0.72f)
    val travel = (maxHeight - touchLength).coerceAtLeast(0.dp)
    VolumeControlPreview(
        settings = settings,
        level = level,
        maxVolume = maxVolume,
        onStep = onStep,
        modifier = Modifier
            .align(if (settings.edge == ScreenEdge.RIGHT) Alignment.TopEnd else Alignment.TopStart)
            .offset(y = travel * settings.offsetFraction)
            .width(settings.touchZoneWidthDp.dp)
            .height(touchLength),
    )
}

/** Soft pulse that draws the eye to the control in the non-interactive previews. */
@Composable
private fun BreathingHint() {
    val transition = rememberInfiniteTransition(label = "hint")
    val alpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "hint-alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                ),
            ),
    )
}

@Composable
private fun NotificationPreview(running: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(18.dp),
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Virtual Volume is active",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Touch the edge of your screen to change the volume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (running) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (running) "Service running" else "Service stopped",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (running) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickSettingsMock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(26.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { TilePlaceholder() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tile),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Virtual Volume",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            repeat(2) { TilePlaceholder() }
        }
        Text(
            text = "Quick Settings → Edit → drag the tile into your panel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RowScope.TilePlaceholder() {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun PhoneFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    ),
                ),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content,
    )
}

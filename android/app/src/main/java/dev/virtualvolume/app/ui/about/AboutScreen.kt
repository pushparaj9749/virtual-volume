package dev.virtualvolume.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.virtualvolume.app.BuildConfig
import dev.virtualvolume.app.R
import dev.virtualvolume.app.ui.components.ActionRow
import dev.virtualvolume.app.ui.components.GlassCard
import dev.virtualvolume.app.ui.components.SectionHeader
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings

private const val REPOSITORY_URL = "https://github.com/pushparaj9749/virtual-volume"
private const val WEBSITE_URL = "https://pushparaj9749.github.io/virtual-volume/"

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tile),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Virtual Volume",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "A software replacement for the physical volume rocker. The control draws over " +
                    "other apps through the standard Android overlay API and changes the hardware volume " +
                    "through AudioManager — the same APIs the system buttons use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column {
            SectionHeader("Permissions")
            GlassCard {
                PermissionItem(
                    title = "Display over other apps",
                    body = "Lets the control appear above other apps. Required, and revocable at any time.",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionItem(
                    title = "Foreground service",
                    body = "Keeps the control alive while you use other apps, with the notification Android requires.",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionItem(
                    title = "Notifications",
                    body = "Android 13+ asks before showing the foreground-service notification.",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionItem(
                    title = "Vibration",
                    body = "Only used for the optional haptic tick. Never for anything else.",
                )
            }
        }

        Column {
            SectionHeader("Privacy")
            GlassCard {
                Text(
                    text = "No analytics. No accounts. No tracking. No advertising SDKs. Virtual Volume " +
                        "has no internet permission at all — your settings live in private app storage on " +
                        "this device and nowhere else.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column {
            SectionHeader("Android limitations")
            GlassCard {
                Bullet(
                    "The screen must be on. Android does not deliver touch events to overlays while the " +
                        "display is off, and no app can change that.",
                )
                Bullet(
                    "Some system surfaces — the lock screen, secure payment sheets and full-screen system " +
                        "dialogs — hide every overlay by design.",
                )
                Bullet(
                    "Android 12+ can refuse a background start after a reboot. If that happens, opening the " +
                        "app or tapping the Quick Settings tile brings the control back.",
                )
                Bullet(
                    "A few OEM skins add their own battery rules. Excluding Virtual Volume from battery " +
                        "optimisation keeps the control alive longer.",
                )
            }
        }

        Column {
            SectionHeader("Links")
            GlassCard {
                ActionRow(
                    title = "Website",
                    subtitle = WEBSITE_URL,
                    icon = Icons.Rounded.Info,
                    onClick = { context.openUrl(WEBSITE_URL) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ActionRow(
                    title = "Source code and releases",
                    subtitle = REPOSITORY_URL,
                    icon = Icons.Rounded.Settings,
                    onClick = { context.openUrl(REPOSITORY_URL) },
                )
            }
        }

        Text(
            text = "Built as a native Android app. No WebView, no web runtime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun PermissionItem(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

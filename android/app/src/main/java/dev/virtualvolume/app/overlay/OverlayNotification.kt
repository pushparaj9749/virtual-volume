package dev.virtualvolume.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.virtualvolume.app.MainActivity
import dev.virtualvolume.app.R

/** The one notification the app shows: the foreground-service notice. */
object OverlayNotification {

    const val CHANNEL_ID = "virtual_volume_control"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        runCatching { manager.createNotificationChannel(channel) }
    }

    fun build(context: Context, subtitle: String? = null): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(subtitle ?: context.getString(R.string.notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.notification_action_open), openIntent)
            .addAction(0, context.getString(R.string.notification_action_stop), stopIntent)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                }
            }
            .build()
    }

    private const val REQUEST_STOP = 1
    private const val REQUEST_OPEN = 2
}

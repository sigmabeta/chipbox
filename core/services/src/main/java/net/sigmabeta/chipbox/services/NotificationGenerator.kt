package net.sigmabeta.chipbox.services

import android.app.Notification
import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NotificationGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun generate(mediaSession: MediaSessionCompat): Notification {
        // Get the session's metadata
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_PLAYBACK).apply {
            // Add the metadata for the currently playing track
            setContentTitle(description.title)
            setContentText(description.subtitle)
            setSubText(description.description)
            setLargeIcon(description.iconBitmap)

            // Enable launching the player by clicking the notification
            setContentIntent(controller.sessionActivity)

            val stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_STOP
            )

            // Stop the service when the notification is swiped away
            setDeleteIntent(stopIntent)

            // Make the transport controls visible on the lockscreen
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Add an app icon and set its accent color
            val playbackState = controller.playbackState
            val notificationIcon = if (playbackState?.state == PlaybackStateCompat.STATE_PLAYING)
                R.drawable.ic_stat_play
            else
                R.drawable.ic_stat_pause
            setSmallIcon(notificationIcon)
            color = ContextCompat.getColor(context, R.color.colorPrimaryDark)

            // Add a pause button
            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause_24,
                    context.getString(R.string.action_pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )

            // Take advantage of MediaStyle features
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopIntent)

            setStyle(mediaStyle)
        }
        return builder.build()
    }

    companion object {
        val CHANNEL_ID_PLAYBACK = BuildConfig.LIBRARY_PACKAGE_NAME + ".playback"
        val NOTIFICATION_ID = 5678
    }
}
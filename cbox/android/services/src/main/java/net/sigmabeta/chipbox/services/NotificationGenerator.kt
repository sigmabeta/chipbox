package net.sigmabeta.chipbox.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.chipbox.colors.R as ColorsR
import net.sigmabeta.chipbox.drawables.R as DrawablesR
import net.sigmabeta.chipbox.strings.R as StringsR

class NotificationGenerator(
    private val context: Context,
    private val hatchet: Hatchet,
) {
    init {
        val channel = NotificationChannel(
            CHANNEL_ID_PLAYBACK,
            context.getString(StringsR.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun generate(mediaSession: MediaSessionCompat): Notification {
        // Get the session's metadata
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_PLAYBACK).apply {
            // Add the metadata for the currently playing track
            if (description != null) {
                setContentTitle(description.title)
                setContentText(description.subtitle)
                setSubText(description.description)
                setLargeIcon(description.iconBitmap)
            } else {
                setContentTitle("Loading...")
            }

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
                DrawablesR.drawable.ic_stat_play
            else
                DrawablesR.drawable.ic_stat_pause
            setSmallIcon(notificationIcon)
            color = ContextCompat.getColor(context, ColorsR.color.colorPrimaryDark)

            val actionsAdded = addActions(playbackState.actions)

            // Take advantage of MediaStyle features
            val sessionToken = mediaSession.sessionToken
            hatchet.v("Notifying with Token: $sessionToken  active: ${mediaSession.isActive}")
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopIntent)

            if (actionsAdded > 1) {
                mediaStyle.setShowActionsInCompactView(1)
            }

            setStyle(mediaStyle)
        }
        return builder.build()
    }


    private fun NotificationCompat.Builder.addActions(actions: Long): Int {
        var addedActions = 0

        if (addActionIfAvailable(
                actions,
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                DrawablesR.drawable.ic_previous_24,
                StringsR.string.action_previous
            )) addedActions++

        if (addActionIfAvailable(
                actions,
                PlaybackStateCompat.ACTION_PLAY,
                DrawablesR.drawable.ic_play_24,
                StringsR.string.action_play
            )) addedActions++


        if (addActionIfAvailable(
                actions,
                PlaybackStateCompat.ACTION_PAUSE,
                DrawablesR.drawable.ic_pause_24,
                StringsR.string.action_pause
            )) addedActions++


        if (addActionIfAvailable(
                actions,
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                DrawablesR.drawable.ic_next_24,
                StringsR.string.action_next
            )) addedActions++

        return addedActions
    }

    private fun NotificationCompat.Builder.addActionIfAvailable(
        actions: Long,
        actionId: Long,
        drawableId: Int,
        labelId: Int
    ): Boolean {
        if (actions and actionId != 0L) {
            addAction(
                NotificationCompat.Action(
                    drawableId,
                    context.getString(labelId),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        actionId
                    )
                )
            )
            return true
        }
        return false
    }

    companion object {
        val CHANNEL_ID_PLAYBACK = "net.sigmabeta.chipbox.services.playback"
        val NOTIFICATION_ID = 5678
    }
}


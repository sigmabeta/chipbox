package net.sigmabeta.chipbox.services

import android.app.Notification
import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import timber.log.Timber

class NotificationGenerator(
    private val context: Context
) {
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
                R.drawable.ic_stat_play
            else
                R.drawable.ic_stat_pause
            setSmallIcon(notificationIcon)
            color = ContextCompat.getColor(context, R.color.colorPrimaryDark)

            val actionsAdded = addActions(playbackState.actions)

            // Take advantage of MediaStyle features
            val sessionToken = mediaSession.sessionToken
            Timber.v("Notifying with Token: $sessionToken  active: ${mediaSession.isActive}")
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
                R.drawable.ic_previous_24,
                R.string.action_previous
            )) addedActions++

        if (addActionIfAvailable(
                actions,
                PlaybackStateCompat.ACTION_PLAY,
                R.drawable.ic_play_24,
                R.string.action_play
            )) addedActions++


        if (addActionIfAvailable(
                actions,
                PlaybackStateCompat.ACTION_PAUSE,
                R.drawable.ic_pause_24,
                R.string.action_pause
            )) addedActions++


        if (addActionIfAvailable(
                actions,
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                R.drawable.ic_next_24,
                R.string.action_next
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
        val CHANNEL_ID_PLAYBACK = BuildConfig.LIBRARY_PACKAGE_NAME + ".playback"
        val NOTIFICATION_ID = 5678
    }
}


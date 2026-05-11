package net.sigmabeta.chipbox

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import net.sigmabeta.chipbox.appui.ChipboxAppUi
import net.sigmabeta.chipbox.services.ChipboxPlaybackService
import net.sigmabeta.sage.ui.StringProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var stringProvider: StringProvider

    private lateinit var mediaBrowser: MediaBrowserCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Bind to the playback service so it can promote itself to a started+foreground
        // state when playback begins (see ChipboxSessionCallback.handlePlayingState).
        // Without this, the service is never created, no notification is posted, and
        // playback dies with the activity.
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, ChipboxPlaybackService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {},
            null,
        )

        setContent { ChipboxAppUi(stringProvider) }
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaBrowser.disconnect()
    }
}

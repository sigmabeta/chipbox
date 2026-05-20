package net.sigmabeta.chipbox

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import javax.inject.Inject
import net.sigmabeta.chipbox.appui.ChipboxAppUi
import net.sigmabeta.chipbox.services.ChipboxPlaybackService
import net.sigmabeta.chipbox.ui.vm.LocalViewModelProvider
import net.sigmabeta.chipbox.vm.AndroidHiltViewModelProvider
import net.sigmabeta.sage.ui.StringProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var stringProvider: StringProvider

    private val viewModelProvider = AndroidHiltViewModelProvider()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appGraph = (application as ChipboxApplication).appGraph
        Log.i("Metro", "ChipboxAppGraph.appInfo = ${appGraph.appInfo}")
        setContent {
            CompositionLocalProvider(
                LocalViewModelProvider provides viewModelProvider,
                LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
            ) {
                ChipboxAppUi(stringProvider)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to the playback service so it can promote itself to a started+foreground
        // state when playback begins. Without this, the service is never created, no
        // notification is posted, and playback dies with the activity.
        val token = SessionToken(this, ComponentName(this, ChipboxPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { controller = future.get() },
            MoreExecutors.directExecutor(),
        )
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}

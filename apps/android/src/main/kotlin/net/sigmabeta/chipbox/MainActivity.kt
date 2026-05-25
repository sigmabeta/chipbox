package net.sigmabeta.chipbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.services.api.ChipboxPlaybackService
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // Hardware-keyboard back keys (Escape/Backspace) for parity with desktop. onKeyDown is reached
    // only when the focused view didn't consume the key, so a focused text field keeps Backspace
    // for editing. extraBufferCapacity = 1 lets tryEmit deliver to the shell's collector.
    private val backKeyEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appGraph = (application as ChipboxApplication).appGraph
        setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
                LocalChipboxStringProvider provides appGraph.stringProvider,
            ) {
                ChipboxAppUi(
                    onOpenUrl = { url -> openUrl(url) },
                    onCopyToClipboard = { label, text -> copyToClipboard(label, text) },
                    backKeyEvents = backKeyEvents,
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // repeatCount == 0 → only the first event of a held key, so auto-repeat doesn't pop twice.
        if ((keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DEL) &&
            event?.repeatCount == 0
        ) {
            backKeyEvents.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
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

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

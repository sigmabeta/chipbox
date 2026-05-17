package net.sigmabeta.chipbox.services

import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.services.ChipboxPlaybackService.Companion.ID_ROOT_FULL
import net.sigmabeta.sage.logging.Hatchet

class ChipboxLibrarySessionCallback(
    private val libraryBrowser: LibraryBrowser,
    private val scope: CoroutineScope,
    private val hatchet: Hatchet,
) : MediaLibrarySession.Callback {

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(libraryBrowser.rootItem(), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (parentId == ID_ROOT_FULL) {
            val items = ImmutableList.copyOf(libraryBrowser.getTopLevelMenuItems())
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val children = libraryBrowser.browseTo(parentId)
                if (children == null) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                } else {
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
                }
            } catch (cancel: CancellationException) {
                future.cancel(false)
                throw cancel
            } catch (t: Throwable) {
                hatchet.e("onGetChildren($parentId) failed: $t")
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            try {
                val item = libraryBrowser.getItem(mediaId)
                if (item == null) {
                    hatchet.v("onGetItem unknown id: $mediaId")
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                } else {
                    future.set(LibraryResult.ofItem(item, null))
                }
            } catch (cancel: CancellationException) {
                future.cancel(false)
                throw cancel
            } catch (t: Throwable) {
                hatchet.e("onGetItem($mediaId) failed: $t")
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
}

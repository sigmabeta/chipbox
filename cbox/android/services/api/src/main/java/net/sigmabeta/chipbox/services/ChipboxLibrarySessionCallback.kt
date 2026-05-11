package net.sigmabeta.chipbox.services

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = MediaItem.Builder()
            .setMediaId(ID_ROOT_FULL)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Chipbox")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(root, params))
    }

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
        hatchet.v("onGetItem unsupported for $mediaId")
        return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> {
        return Futures.immediateFuture(mediaItems)
    }
}

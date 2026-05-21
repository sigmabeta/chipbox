package net.sigmabeta.chipbox.images.api

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

class FakeOtherImageFetcher(val data: Uri) : Fetcher {
    override suspend fun fetch(): FetchResult? = SourceFetchResult(
            source = ImageSource(
                file = File(data.toString()).toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
            ),
            dataSource = DataSource.MEMORY,
            mimeType = "application/notpdf"
        )

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ) = FakeOtherImageFetcher(
            data,
        )
    }
}

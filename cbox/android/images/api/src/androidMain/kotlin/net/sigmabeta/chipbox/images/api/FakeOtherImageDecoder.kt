package net.sigmabeta.chipbox.images.api

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options

class FakeOtherImageDecoder(
    private val result: SourceFetchResult,
) : Decoder {
    override suspend fun decode(): DecodeResult? {
        if (result.mimeType == MIMETYPE) {
            return null
        }

        val bitmap = BitmapGenerator
            .generateBitmap(result)
            .asAndroidBitmap()

        return DecodeResult(
            image = bitmap
                .copy(
                    Bitmap.Config.ARGB_8888,
                    false
                )
                .asImage(),
            isSampled = true,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ) = FakeOtherImageDecoder(
            result,
        )
    }

    companion object {
        const val MIMETYPE = "application/pdf"
    }
}

package net.sigmabeta.chipbox.util

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.reactivex.Single
import net.sigmabeta.chipbox.R
import timber.log.Timber


fun loadBitmapLowQuality(context: Context, path: String): Single<Bitmap> {
    return Single.create {
        val bitmap = Picasso.with(context)
                .load(path)
                .config(Bitmap.Config.RGB_565)
                .resize(400, 400)
                .centerCrop()
                .error(R.drawable.img_album_art_blank)
                .get()

        it.onSuccess(bitmap)
    }
}

fun ImageView.loadImageLowQuality(path: String, fade: Boolean, placeholder: Boolean, callback: Callback? = null) {
    val requestCreator = Picasso.with(context)
            .load(path)
            .config(Bitmap.Config.RGB_565)
            .centerCrop()
            .fit()
            .error(R.drawable.img_album_art_blank)

    if (!fade) {
        requestCreator.noFade()
    }

    if (!placeholder) {
        requestCreator.noPlaceholder()
    }

    callback?.let {
        requestCreator.into(this, callback)
    } ?: let {
        requestCreator.into(this)
    }
}

fun ImageView.loadImageHighQuality(path: String, fade: Boolean, placeholder: Boolean, callback: Callback? = null) {
    val requestCreator = Picasso.with(context)
            .load(path)
            .centerCrop()
            .fit()
            .error(R.drawable.img_album_art_blank)

    if (!fade) {
        requestCreator.noFade()
    }

    if (!placeholder) {
        requestCreator.noPlaceholder()
    }

    callback?.let {
        requestCreator.into(this, callback)
    } ?: let {
        requestCreator.into(this)
    }
}

fun ImageView.loadImageSetSize(path: String,
                               width: Int,
                               height: Int,
                               callback: Callback? = null) {
    Timber.v("Loading ${width}x${height} image into ${this.width}x${this.height} view")

    Picasso.with(context)
            .load(path)
            .resize(width, height)
            .centerCrop()
            .error(R.drawable.img_album_art_blank)
            .noFade()
            .noPlaceholder()
            .into(this, callback)
}

fun ImageView.loadImageHighQualityThumbnailFirst(path: String,
                                                 width: Int,
                                                 height: Int,
                                                 callback: Callback) {
    loadImageSetSize(path, width, height, object : Callback {
                override fun onSuccess() {
                    Timber.v("Loaded ${width}x${height} image into ${this@loadImageHighQualityThumbnailFirst.width}x${this@loadImageHighQualityThumbnailFirst.height} view")

                    this@loadImageHighQualityThumbnailFirst.postDelayed({
                        val bigWidth = this@loadImageHighQualityThumbnailFirst.width
                        val bigHeight = (bigWidth / calculateAspectRatio(width, height)).toInt()

                        loadImageSetSize(path, bigWidth, bigHeight)
                    }, 200)

                    callback.onSuccess()
                }

                override fun onError() {
                    callback.onError()
                }
            })
}

fun calculateAspectRatio(width: Int, height: Int) = width / height.toFloat()

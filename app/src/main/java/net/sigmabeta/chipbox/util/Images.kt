package net.sigmabeta.chipbox.util

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.reactivex.Single
import net.sigmabeta.chipbox.R


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

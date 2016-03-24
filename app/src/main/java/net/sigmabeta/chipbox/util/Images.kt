package net.sigmabeta.chipbox.util

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import net.sigmabeta.chipbox.R
import rx.Observable


fun loadBitmapLowQuality(context: Context, path: String): Observable<Bitmap> {
    return Observable.create {
        val bitmap = Picasso.with(context)
                .load(path)
                .config(Bitmap.Config.RGB_565)
                .resize(400, 400)
                .centerCrop()
                .error(R.drawable.img_album_art_blank)
                .get()

        it.onNext(bitmap)
        it.onCompleted()
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

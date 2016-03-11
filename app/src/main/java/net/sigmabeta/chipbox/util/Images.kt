package net.sigmabeta.chipbox.util

import android.graphics.Bitmap
import android.widget.ImageView
import com.squareup.picasso.Picasso
import net.sigmabeta.chipbox.R

fun ImageView.loadImageLowQuality(path: String) {
    Picasso.with(context)
            .load(path)
            .config(Bitmap.Config.RGB_565)
            .centerCrop()
            .fit()
            .noPlaceholder()
            .error(R.drawable.img_album_art_blank)
            .into(this)
}

fun ImageView.loadImageLowQuality(resource: Int) {
    Picasso.with(context)
            .load(resource)
            .config(Bitmap.Config.RGB_565)
            .centerCrop()
            .fit()
            .noPlaceholder()
            .error(R.drawable.img_album_art_blank)
            .into(this)
}

fun ImageView.loadImageHighQuality(path: String) {
    Picasso.with(context)
            .load(path)
            .centerCrop()
            .fit()
            .noPlaceholder()
            .error(R.drawable.img_album_art_blank)
            .into(this)
}


fun ImageView.loadImageHighQuality(resource: Int) {
    Picasso.with(context)
            .load(resource)
            .centerCrop()
            .fit()
            .noPlaceholder()
            .error(R.drawable.img_album_art_blank)
            .into(this)
}

package net.sigmabeta.chipbox.util

import android.graphics.Bitmap
import android.widget.ImageView
import com.squareup.picasso.Picasso
import net.sigmabeta.chipbox.R

fun loadImageLowQuality(view: ImageView, path: String) {
    Picasso.with(view.context)
            .load(path)
            .config(Bitmap.Config.RGB_565)
            .centerCrop()
            .fit()
            .noPlaceholder()
            .error(R.drawable.img_album_art_blank)
            .into(view)
}

fun loadImageLowQuality(view: ImageView, resource: Int) {
    Picasso.with(view.context)
            .load(resource)
            .config(Bitmap.Config.RGB_565)
            .centerCrop()
            .fit()
            .noPlaceholder()
            .error(R.drawable.img_album_art_blank)
            .into(view)
}

fun loadImageHighQuality(view: ImageView, path: String) {
    Picasso.with(view.context)
            .load(path)
            .centerCrop()
            .fit()
            .error(R.drawable.img_album_art_blank)
            .into(view)
}


fun loadImageHighQuality(view: ImageView, resource: Int) {
    Picasso.with(view.context)
            .load(resource)
            .centerCrop()
            .fit()
            .error(R.drawable.img_album_art_blank)
            .into(view)
}

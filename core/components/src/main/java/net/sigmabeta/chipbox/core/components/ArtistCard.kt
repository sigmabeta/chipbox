package net.sigmabeta.chipbox.core.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.coil.rememberCoilPainter
import com.google.accompanist.imageloading.ImageLoadState
import net.sigmabeta.chipbox.components.R

@Composable
fun ArtistCard(
    name: String,
    image: String,
    previewResourceId: Int = 0,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .width(192.dp)
            .padding(8.dp)
    ) {
        val coilPainter = rememberCoilPainter(
            request = image,
            previewPlaceholder = previewResourceId,
        )

        Card(
            elevation = 4.dp,
            shape = CircleShape
        ) {
            Crossfade(targetState = coilPainter.loadState) {
                when (it) {
                    ImageLoadState.Empty -> RealImage(
                        name,
                        coilPainter,
                        R.string.cont_desc_artist_photo
                    )
                    is ImageLoadState.Loading -> PlaceholderImage(
                        R.drawable.img_album_art_blank,
                        R.string.cont_desc_artist_photo_blank
                    )
                    is ImageLoadState.Error -> PlaceholderImage(
                        R.drawable.img_album_art_blank,
                        R.string.cont_desc_artist_photo_blank
                    )
                    is ImageLoadState.Success -> RealImage(
                        name,
                        coilPainter,
                        R.string.cont_desc_artist_photo
                    )
                }
            }
        }

        Text(
            text = name,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
@Preview
fun PreviewTallArtist() {
    MaterialTheme {
        ArtistCard(
            name = "Takushi Hiyamuta",
            image = "",
            previewResourceId = R.drawable.square_pixels
        ) { }
    }
}

@Composable
@Preview
fun PreviewWideArtist() {
    MaterialTheme {
        ArtistCard(
            name = "Hitoshi Sakimoto",
            image = "",
            previewResourceId = R.drawable.square_pixels
        ) { }
    }
}
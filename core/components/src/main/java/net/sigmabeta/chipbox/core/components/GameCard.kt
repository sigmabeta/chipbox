package net.sigmabeta.chipbox.core.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.coil.rememberCoilPainter
import com.google.accompanist.imageloading.ImageLoadState
import com.google.accompanist.imageloading.LoadPainter
import net.sigmabeta.chipbox.components.R

@Composable
fun GameCard(
    title: String,
    artist: String,
    image: String,
    previewResourceId: Int = 0,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(256.dp)
            .padding(8.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.clickable(onClick = onClick)) {
            val coilPainter = rememberCoilPainter(
                request = image,
                previewPlaceholder = previewResourceId,
            )

            Crossfade(targetState = coilPainter.loadState) {
                when (it) {
                    ImageLoadState.Empty -> RealImage(title, coilPainter)
                    ImageLoadState.Loading -> PlaceholderImage()
                    is ImageLoadState.Error -> PlaceholderImage()
                    is ImageLoadState.Success -> RealImage(title, coilPainter)
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    top = 4.dp,
                    start = 8.dp,
                    end = 8.dp
                )
            )

            Text(
                text = artist,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    vertical = 4.dp,
                    horizontal = 8.dp
                )
            )
        }
    }
}

@Composable
private fun RealImage(
    title: String,
    coilPainter: LoadPainter<Any>
) {
    Image(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f),
        contentDescription = stringResource(R.string.cont_desc_game_art, title),
        painter = coilPainter,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun PlaceholderImage() {
    Image(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f),
        bitmap = ImageBitmap.imageResource(id = R.drawable.img_album_art_blank),
        contentDescription = stringResource(id = R.string.cont_desc_game_art_blank)
    )
}

@Composable
@Preview
fun PreviewSingleArtist() {
    MaterialTheme {
        GameCard(
            title = "Neo Turf Masters",
            artist = "Takushi Hiyamuta",
            image = "",
            previewResourceId = R.drawable.neo_turf_masters
        ) { }
    }
}

@Composable
@Preview
fun PreviewVariousArtists() {
    MaterialTheme {
        GameCard(
            title = "Leading Company",
            artist = "Various Artists",
            image = "",
            previewResourceId = R.drawable.leading_company
        ) { }
    }
}

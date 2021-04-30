package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.coil.rememberCoilPainter
import net.sigmabeta.chipbox.components.R

@Composable
fun Game(
    title: String,
    artist: String,
    image: String,
    previewResourceId: Int = 0
) {
    Card(
        modifier = Modifier
            .width(192.dp),
        elevation = 16.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.0f),
                contentDescription = stringResource(R.string.cont_desc_game_art, title),
                painter = rememberCoilPainter(
                    request = image,
                    previewPlaceholder = previewResourceId,
                ),
                contentScale = ContentScale.Crop
            )

            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(
                    top = 4.dp,
                    start = 8.dp,
                    end = 8.dp
                )
            )

            Text(
                text = artist,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(
                    vertical = 4.dp,
                    horizontal = 8.dp
                )
            )
        }
    }
}

@Composable
@Preview
fun PreviewSingleArtist() {
    MaterialTheme {
        Game(
            title = "Neo Turf Masters",
            artist = "Takushi Hiyamuta",
            image = "",
            previewResourceId = R.drawable.neo_turf_masters
        )
    }
}

@Composable
@Preview
fun PreviewVariousArtists() {
    MaterialTheme {
        Game(
            title = "Leading Company",
            artist = "Various Artists",
            image = "",
            previewResourceId = R.drawable.leading_company
        )
    }
}

package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.coil.rememberCoilPainter
import net.sigmabeta.chipbox.components.R
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game

@Composable
fun GameDetailListItem(
    game: Game,
    previewResourceId: Int = 0
) {
    val coilPainter = rememberCoilPainter(
        request = game.photoUrl,
        previewPlaceholder = previewResourceId,
        fadeIn = false
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(MaterialTheme.colors.background)
    ) {
        Row() {
            Image(
                painter = coilPainter,
                contentDescription = stringResource(id = R.string.cont_desc_game_art, game.title),
                modifier = Modifier
                    .weight(2.0f)
                    .padding(16.dp)
            )
            Column(
                modifier = Modifier
                    .weight(3.0f)
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(top = 16.dp, end = 16.dp)
                )
                Text(
                    text = game.artists?.joinToString { it.name }
                        ?: stringResource(id = R.string.caption_unknown_artist),
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = 4.dp, end = 16.dp)
                )
                Text(
                    text = stringResource(
                        id = R.string.caption_track_count,
                        game.tracks?.size ?: 0
                    ),
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 4.dp, end = 16.dp)
                )

            }
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Button(
                onClick = { /*TODO*/ },
                modifier = Modifier
                    .weight(1.0f)
                    .padding(end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shuffle_24),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(text = "Shuffle")
            }
            Button(
                onClick = { /*TODO*/ },
                modifier = Modifier
                    .weight(1.0f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play_24),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(text = "Play")
            }
        }
    }
}

@Preview
@Composable
fun PreviewGameListItem() {
    MaterialTheme {
        GameDetailListItem(
            game = Game(
                1234L,
                "Neo Turf Masters",
                listOf(
                    Artist(
                        0L,
                        "Takushi Hiyamuta",
                        mutableListOf(),
                        mutableListOf(),
                        ""
                    )
                ),
                "urlol",
                null
            ),
            previewResourceId = R.drawable.neo_turf_masters
        )
    }
}

@Preview
@Composable
fun PreviewUnknownArtist() {
    MaterialTheme {
        GameDetailListItem(
            game = Game(
                1234L,
                "Leading Company",
                null,
                "urlol",
                null
            ),
            previewResourceId = R.drawable.leading_company
        )
    }
}
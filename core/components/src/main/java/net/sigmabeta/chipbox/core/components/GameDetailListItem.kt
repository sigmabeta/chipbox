package net.sigmabeta.chipbox.core.components

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.components.R
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game

@Composable
fun GameDetailListItem(
    game: Game,
    previewResourceId: Int = 0
) {
    DetailHeaderListItem(
        title = game.title,
        subtitle = game.artists?.joinToString { it.name }
            ?: stringResource(id = R.string.caption_unknown_artist),
        subsubtitle = stringResource(
            id = R.string.caption_track_count,
            game.tracks?.size ?: 0
        ),
        imageUrl = game.photoUrl,
        contDescId = R.string.cont_desc_game_art,
        previewResourceId = previewResourceId
    )
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
            previewResourceId = R.drawable.game_tall
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
                "Lufia II: Rise of the Sinistrals",
                null,
                "urlol",
                null
            ),
            previewResourceId = R.drawable.game_wide
        )
    }
}
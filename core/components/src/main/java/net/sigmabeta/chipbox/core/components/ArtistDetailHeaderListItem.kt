package net.sigmabeta.chipbox.core.components

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.components.R
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Track

@Composable
fun ArtistDetailHeaderListItem(
    artist: Artist,
) {
    DetailHeaderListItem(
        title = artist.name,
        subtitle = null,
        subsubtitle = stringResource(
            id = R.string.caption_track_count,
            artist.tracks?.size ?: 0
        ),
        imageUrl = artist.photoUrl,
        contDescId = R.string.cont_desc_artist_photo,
    )
}

@Preview
@Composable
fun PreviewArtistListItem() {
    MaterialTheme {
        ArtistDetailHeaderListItem(
            artist = Artist(
                1234L,
                "Takushi Hiyamuta",
                null,
                mutableListOf(
                    Track(
                        1234L,
                        "",
                        "",
                        12345L,
                        -1,
                        false,
                        null,
                        null
                    )
                ),
                null
            )
        )
    }
}

@Preview
@Composable
fun PreviewTwoTracks() {
    MaterialTheme {
        ArtistDetailHeaderListItem(
            artist = Artist(
                1234L,
                "Hitoshi Sakimoto",
                null,
                mutableListOf(
                    Track(
                        1234L,
                        "",
                        "",
                        12345L,
                        -1,
                        false,
                        null,
                        null
                    ), Track(
                        1234L,
                        "",
                        "",
                        12345L,
                        -1,
                        false,
                        null,
                        null
                    )
                ),
                null
            )
        )
    }
}
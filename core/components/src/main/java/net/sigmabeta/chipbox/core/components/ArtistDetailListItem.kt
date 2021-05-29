package net.sigmabeta.chipbox.core.components

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.sigmabeta.chipbox.components.R
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Track

@Composable
fun ArtistDetailListItem(
    artist: Artist,
    previewResourceId: Int = 0
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
        previewResourceId = previewResourceId
    )
}

@Preview
@Composable
fun PreviewArtistListItem() {
    MaterialTheme {
        ArtistDetailListItem(
            artist = Artist(
                1234L,
                "Takushi Hiyamuta",
                mutableListOf(
                    Track(
                        1234L,
                        1,
                        "",
                        "",
                        null,
                        null,
                        12345L,
                        ""
                    )
                ),
                null,
                null
            ),
            previewResourceId = R.drawable.artist_wide
        )
    }
}

@Preview
@Composable
fun PreviewTwoTracks() {
    MaterialTheme {
        ArtistDetailListItem(
            artist = Artist(
                1234L,
                "Hitoshi Sakimoto",
                mutableListOf(
                    Track(
                        1234L,
                        1,
                        "",
                        "",
                        null,
                        null,
                        12345L,
                        ""
                    ), Track(
                        1234L,
                        1,
                        "",
                        "",
                        null,
                        null,
                        12345L,
                        ""
                    )
                ),
                null,
                null
            ),
            previewResourceId = R.drawable.artist_tall
        )
    }
}
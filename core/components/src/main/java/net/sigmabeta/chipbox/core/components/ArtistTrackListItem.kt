package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track

@Composable
fun ArtistTrackListItem(
    track: Track,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .height(60.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colors.background)
    ) {
        Text(
            text = track.number.toString(),
            textAlign = TextAlign.Right,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.caption,
            modifier = Modifier
                .wrapContentHeight()
                .width(36.dp)
                .align(Alignment.CenterVertically)
                .padding(start = 8.dp)
        )
        Column(
            Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .padding(
                    start = 20.dp,
                    end = 8.dp
                )
        ) {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Text(
                text = track.game?.title ?: "Unknown Game",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
        Text(
            text = getTimeStringFromMillis(track.trackLengthMs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.caption,
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.CenterVertically)
                .padding(end = 16.dp)
        )
    }
}

@Composable
@Preview
fun PreviewArtistTrackListItem() {
    MaterialTheme {
        ArtistTrackListItem(
            track = Track(
                0L,
                144,
                "",
                "Fujiyama Oriental Golf Club - Japan",
                null,
                Game(
                    1234L,
                    "Neo Turf Masters",
                    null,
                    null,
                    null
                ),
                214000,
                ""
            )
        ) {}
    }
}
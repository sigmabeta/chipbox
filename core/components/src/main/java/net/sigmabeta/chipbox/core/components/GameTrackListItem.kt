package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Track
import java.util.concurrent.TimeUnit

@Composable
fun GameTrackListItem(
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
                text = track.artists?.joinToString { it.name } ?: "Unknown Artist",
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

fun getTimeStringFromMillis(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val displaySeconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)

    return "%d:%02d".format(minutes, displaySeconds)
}

@Composable
@Preview
fun PreviewGameTrackListItem() {
    MaterialTheme {
        GameTrackListItem(
            track = Track(
                0L,
                "",
                "Fujiyama Oriental Golf Club - Japan",
                listOf(
                    Artist(
                        0L,
                        "Takushi Hiyamuta",
                        mutableListOf(),
                        mutableListOf(),
                        ""
                    )
                ),
                null,
                214000,
                ""
            )
        ) {}
    }
}
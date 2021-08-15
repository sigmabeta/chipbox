package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import net.sigmabeta.chipbox.components.R

@Composable
fun DetailHeaderListItem(
    title: String,
    subtitle: String?,
    subsubtitle: String,
    imageUrl: String?,
    contDescId: Int
) {
    val coilPainter = rememberImagePainter(
        data = imageUrl,
        builder = {
            crossfade(true)
            placeholder(R.drawable.img_album_art_blank)
            error(R.drawable.img_album_art_blank)
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(MaterialTheme.colors.background)
            .padding(top = 16.dp)
    ) {
        Row {
            Card(
                elevation = 4.dp,
                modifier = Modifier
                    .weight(2.0f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .wrapContentSize()
            ) {
                RealImage(
                    title = title,
                    coilPainter = coilPainter,
                    contDescId
                )
            }

            Column(
                modifier = Modifier
                    .weight(3.0f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(end = 16.dp)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(top = 4.dp, end = 16.dp)
                    )
                }
                Text(
                    text = subsubtitle,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 4.dp, end = 16.dp, bottom = 16.dp)
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
fun PreviewDetailHeader() {
    DetailHeaderListItem(
        title = "Chrono Cross",
        subtitle = "Yasunori Mitsuda",
        subsubtitle = "68 Tracks",
        imageUrl = "",
        contDescId = 0
    )
}
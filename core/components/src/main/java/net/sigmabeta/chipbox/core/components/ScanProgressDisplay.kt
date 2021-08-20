package net.sigmabeta.chipbox.core.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import net.sigmabeta.chipbox.components.R
import net.sigmabeta.chipbox.models.state.ScannerEvent

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScanProgressDisplay(
    lastScannerEvent: ScannerEvent,
) {
    AnimatedVisibility(
        visible = lastScannerEvent is ScannerEvent.GameFoundEvent
    ) {
        lastScannerEvent as ScannerEvent.GameFoundEvent

        val coilPainter = rememberImagePainter(
            data = lastScannerEvent.imageUrl,
            builder = {
                crossfade(true)
                placeholder(R.drawable.img_album_art_blank)
                error(R.drawable.img_album_art_blank)
            }
        )

        Row(
            modifier = Modifier
                .height(72.dp)
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                )
        ) {
            Card(
                elevation = 4.dp,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1.0f)
            ) {
                RealImage(
                    lastScannerEvent.name,
                    coilPainter,
                    R.string.cont_desc_scan_game
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.0f)
                    .padding(
                        start = 8.dp,
                        top = 8.dp,
                        bottom = 4.dp
                    )
            ) {
                AnimatedContent(targetState = lastScannerEvent) {
                    Text(
                        text = lastScannerEvent.name,
                        color = MaterialTheme.colors.onPrimary,
                        style = MaterialTheme.typography.subtitle1,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }

                AnimatedContent(targetState = lastScannerEvent) {
                    Text(
                        text = "${lastScannerEvent.trackCount} tracks",
                        color = MaterialTheme.colors.onPrimary,
                        style = MaterialTheme.typography.caption,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewScanProgress() {
    Box(
        modifier = Modifier.background(MaterialTheme.colors.primary)
    ) {
        ScanProgressDisplay(
            lastScannerEvent = ScannerEvent.GameFoundEvent(
                "Lufia II: Rise of the Sinistrals",
                37,
                ""
            )
        )
    }
}
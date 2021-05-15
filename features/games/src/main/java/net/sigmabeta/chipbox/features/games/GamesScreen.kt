package net.sigmabeta.chipbox.features.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.accompanist.insets.statusBarsPadding

@Composable
fun GamesScreen() {
    Text(
        "Red",
        color = Color.White,
        modifier = Modifier
            .wrapContentSize()
            .background(Color.Red)
            .statusBarsPadding()
    )
}

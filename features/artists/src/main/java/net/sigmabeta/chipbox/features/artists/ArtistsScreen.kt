package net.sigmabeta.chipbox.features.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.statusBarsPadding

@Composable
fun ArtistsScreen() {
    Text(
        "Blue",
        color = Color.White,
        modifier = Modifier
            .wrapContentSize()
            .background(Color.Blue)
            .statusBarsPadding()
    )
}

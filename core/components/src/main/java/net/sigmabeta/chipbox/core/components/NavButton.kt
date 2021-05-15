package net.sigmabeta.chipbox.core.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigate
import androidx.navigation.compose.popUpTo

@Composable
fun NavButton(
    navController: NavHostController,
    destination: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            navController.navigate(destination) {
                launchSingleTop = true
                popUpTo("games") { }
            }
        },
        modifier = modifier
            .padding(8.dp)
    ) {
        Text(text = label)
    }
}
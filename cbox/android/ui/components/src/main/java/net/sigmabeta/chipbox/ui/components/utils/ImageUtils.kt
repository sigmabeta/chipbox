package net.sigmabeta.chipbox.ui.components.utils

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource

@Composable
fun previewPlaceholder(
    @DrawableRes previewPlaceholderId: Int,
    @DrawableRes realPlaceholderId: Int = 0
) = painterResource(
    id = if (LocalInspectionMode.current) {
        previewPlaceholderId
    } else {
        realPlaceholderId
    }
)

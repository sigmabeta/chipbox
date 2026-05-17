@file:Suppress("MaxLineLength")

package net.sigmabeta.chipbox.ui.previews

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Light | Phone | Portrait", device = "spec:width=360dp,height=640dp,dpi=480")
@Preview(name = "Light | Phone | Landscape", device = "spec:width=640dp,height=360dp,dpi=480")
@Preview(name = "Light | Foldable", device = "spec:width=673dp,height=841dp,dpi=480")
@Preview(name = "Light | Tablet", device = "spec:width=1280dp,height=800dp,dpi=480")
@Preview(name = "Dark | Phone | Portrait", device = "spec:width=360dp,height=640dp,dpi=480", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Dark | Phone | Landscape", device = "spec:width=640dp,height=360dp,dpi=480", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Dark | Foldable", device = "spec:width=673dp,height=841dp,dpi=480", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Dark | Tablet", device = "spec:width=1280dp,height=800dp,dpi=480", uiMode = UI_MODE_NIGHT_YES)
annotation class DevicePreviews

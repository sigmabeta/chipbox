package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.sage.images.SourceInfo

/**
 * Inspection-only stand-in for a real Coil-loaded image: a deterministic generated gradient
 * (seeded by the source) so `@Preview` / Paparazzi render meaningful artwork instead of the
 * loading/error placeholder Coil falls back to when it can't fetch in a preview.
 *
 * `expect`/`actual` because the Android side reuses the historical `android.graphics`-based
 * [net.sigmabeta.chipbox.images.api.BitmapGenerator] (keeping recorded Paparazzi goldens
 * byte-identical to pre-M9-slice-6f), which `jvmSharedMain` can't reference; desktop draws an
 * equivalent gradient with multiplatform Compose graphics. Reached via `CrossfadeImage`'s
 * `forceGenBitmap` branch (defaulting to `LocalInspectionMode.current`).
 */
@Composable
internal expect fun FakeImage(sourceInfo: SourceInfo, modifier: Modifier)

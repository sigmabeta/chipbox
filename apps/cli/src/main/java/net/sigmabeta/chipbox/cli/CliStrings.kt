package net.sigmabeta.chipbox.cli

import net.sigmabeta.sage.ui.StringProvider

/**
 * The CLI's [StringProvider], backed by the single multiplatform string source
 * (cbox/common/strings/real's composeResources) — the same values the Android/desktop apps resolve,
 * instead of the old hand-maintained PLATFORM_* subset. Loaded once, lazily, since the menus only
 * touch it to render platform / section labels.
 *
 * Unlike the apps, the CLI reads the packaged `.cvr` files itself (see [cliClasspathStringProvider])
 * rather than calling strings.real's `loadChipboxStrings()`, which would route through Compose
 * Resources and pull skiko into a headless process just to probe the system theme.
 */
val cliStringProvider: StringProvider by lazy { cliClasspathStringProvider() }

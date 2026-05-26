plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

// Shared Chipbox theme — color schemes, typography tokens + builder, and the multiplatform
// `ChipboxTheme()` composable that wraps Material3. Pure Compose, no Android dependencies.
// The Android `AppTheme()` (in cbox/android/ui/theme/api) is a thin wrapper that supplies
// ChipboxFont-derived font families to this module's `ChipboxTheme`; the JVM/desktop entry
// calls `ChipboxTheme()` with the same defaults.
//
// `:ui:fonts:real` (not `:api`) because `AppTheme()` calls `ChipboxFont.toFontFamily()` — the
// Compose binding extension lives there. The `:api` enum alone wouldn't be enough.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.ui.theme.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.ui.fonts.real)
            }
        }
    }
}

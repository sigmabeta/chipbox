plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

// Shared Chipbox theme — color schemes, typography tokens + builder, and the multiplatform
// `ChipboxTheme()` composable that wraps Material3. Pure Compose, no Android dependencies.
// The Android `AppTheme()` (in cbox/android/ui/theme/api) is a thin wrapper that supplies
// ChipboxFont-derived font families to this module's `ChipboxTheme`; the JVM/desktop entry
// calls `ChipboxTheme()` with the same defaults. Custom Chipbox fonts now ship as Compose
// Multiplatform resources in `cbox/common/ui/fonts/api`, depended on here so the public
// `ChipboxFontDefaults` (Brand / Plain) can name `ChipboxFont` entries directly.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.ui.theme.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.ui.fonts.api)
            }
        }
    }
}

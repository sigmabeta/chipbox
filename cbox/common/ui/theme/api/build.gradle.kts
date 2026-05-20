plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

// Shared Chipbox theme — color schemes, typography tokens + builder, and the multiplatform
// `ChipboxTheme()` composable that wraps Material3. Pure Compose, no Android dependencies.
// The Android `AppTheme()` (in cbox/android/ui/theme/api) is a thin wrapper that supplies
// ChipboxFont-derived font families to this module's `ChipboxTheme`; the JVM/desktop entry
// calls `ChipboxTheme()` directly with `FontFamily.Default`. Custom fonts on JVM (the
// pixel-art `.otf` files currently shipped as Android resources) wait for a later slice —
// the font-resource story (Compose-MP resources vs `expect`/`actual` `FontFamily`) hasn't
// been picked yet.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.ui.theme.api"
    }
}

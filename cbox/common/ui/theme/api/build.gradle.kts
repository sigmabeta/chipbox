plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

// Shared color schemes for Chipbox (ChipboxLight / ChipboxDark / ChipboxMenu) — pure Compose,
// no Android dependencies, so the source lives in commonMain and feeds both the existing
// Android theme (which still owns Typography / AppTheme / Paparazzi previews against
// SageMaterial) and the JVM/desktop entry. Typography + fonts move in a follow-up slice once
// the font-resource story (Compose-MP resources vs expect/actual FontFamily) is settled.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.ui.theme.api"
    }
}

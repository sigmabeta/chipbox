plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Pure-Kotlin metadata module for [ChipboxFont] — the enum carries name / description / URL only,
// with no Compose / Compose-Resources / FontResource references. The Compose binding (Res.font.*
// accessors, the toFontFamily extension, the .otf assets) lives in the sibling `:real` module.
// The split exists so consumers that only carry a ChipboxFont through actions/state (e.g.
// SettingsAction.BrandFontSelected, ChipboxAppUiViewModel) don't drag Compose-Resources into
// their classpath — which would pull Skiko on Kotlin/JS, breaking jsTest there.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.ui.fonts.api"
    }
}

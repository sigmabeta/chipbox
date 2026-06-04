plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.settings.api"
    }

    sourceSets {
        // ChipboxSettingsManager + ThemeMode live in commonMain so the multiplatform
        // ChipboxAppUi / ChipboxAppUiViewModel (appui commonMain) can read the theme setting.
        named("commonMain") {
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}

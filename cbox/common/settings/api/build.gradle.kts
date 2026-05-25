plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    js { nodejs() }

    androidLibrary {
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

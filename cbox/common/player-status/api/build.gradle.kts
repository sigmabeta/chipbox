import net.sigmabeta.sage.plugins.components.namespaceFromPath

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
    alias(chipbox.plugins.kmp.test)
}

// Sage.kmp so PlayerStatus + PlayerStatusViewModel are reachable from the shared
// `ChipboxAppUi` composable in `cbox/android/appui/api` (also KMP). No `android.*` imports
// in the sources today — pure Compose + androidx.lifecycle ViewModel. Same Metro
// multibinding contribution pattern as `features/settings/real` and `features/library/real`.
// The earlier `cbox.android.ui.theme.api` dep was unused (no AppTheme/ChipboxTheme refs in
// any source file); dropped during the KMP conversion.
kotlin {
    android {
        namespace = namespaceFromPath()
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.appcomm.api)

                implementation(libs.sage.common.appcomm)
                implementation(libs.sage.common.di)
                implementation(libs.sage.common.images)
                implementation(libs.sage.common.logging)

                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
                implementation(libs.jetbrains.compose.material.icons.extended)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.player.director.fake)
            }
        }
    }
}

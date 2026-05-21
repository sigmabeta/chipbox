plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
}

// Browse-by-game on sage.kmp — pure-Kotlin/Compose VM + Route, no Android-specific surface.
// Conversion is a plugin swap; the existing `src/main/java` source layout maps to
// `jvmSharedMain` (VM + Route are shared between Android and JVM/desktop).
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.features.browsebygame.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.browseByGame.api)

                implementation(projects.cbox.android.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.models.api)

                implementation(projects.features.gameDetail.api)

                implementation(libs.sage.common.di)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }
    }
}

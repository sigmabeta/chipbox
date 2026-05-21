plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.features.nowplaying.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.nowPlaying.api)

                implementation(projects.cbox.android.ui.chrome.api)
                implementation(projects.cbox.android.ui.freeform.api)
                implementation(projects.cbox.android.ui.components.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)

                implementation(libs.sage.common.appcomm)
                implementation(libs.sage.common.freeform)
                implementation(libs.sage.common.images)
                implementation(libs.sage.common.ui.components)
                implementation(libs.sage.common.ui.strings)
                implementation(libs.jetbrains.compose.material.icons.extended)
                implementation(libs.sage.common.di)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }
    }
}

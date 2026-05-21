plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    alias(libs.plugins.metro)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.features.browsebyplatform.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.browseByPlatform.api)

                implementation(projects.cbox.android.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.models.api)

                implementation(projects.features.gamesForPlatform.api)

                implementation(libs.sage.common.di)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }
    }
}

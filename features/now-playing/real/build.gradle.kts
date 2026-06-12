plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.nowPlaying.api)

                implementation(projects.cbox.common.ui.freeform.api)
                implementation(projects.cbox.common.ui.components.api)
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
                // Icon.vector() renders the sage Icon set for the whole transport/error UI.
                implementation(libs.sage.common.ui.iconsReal)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.player.director.fake)
            }
        }
    }
}

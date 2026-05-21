plugins {
    alias(libs.plugins.sage.feature.real)
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.gamesForPlatform.api)

                implementation(projects.cbox.android.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)

                implementation(projects.features.gameDetail.api)
            }
        }
    }
}

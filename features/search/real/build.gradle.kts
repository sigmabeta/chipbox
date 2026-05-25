plugins {
    alias(libs.plugins.sage.feature.real)
}

kotlin {
    js { nodejs() }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.search.api)

                implementation(projects.cbox.common.ui.chrome.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.ui.components.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.features.gameDetail.api)
                implementation(projects.features.artistDetail.api)

                implementation(libs.sage.common.appcomm)
                implementation(libs.sage.common.images)
                implementation(libs.sage.common.ui.components)
                implementation(libs.sage.common.ui.strings)
                implementation(libs.jetbrains.compose.material.icons.extended)
            }
        }
    }
}

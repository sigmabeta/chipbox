plugins {
    alias(chipbox.plugins.feature.api)
}

// State/action types live in :api; declare the public surface they expose.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                api(projects.cbox.common.strings.api)
                api(projects.cbox.common.appcomm.api)
                api(projects.cbox.common.player.common.api)
                api(projects.cbox.common.player.director.api)
                api(libs.sage.common.ui.components)
                api(libs.sage.common.images)
                api(libs.sage.common.ui.iconsApi)
                api(libs.sage.common.freeform)
                api(libs.sage.common.ui.strings)
            }
        }
    }
}

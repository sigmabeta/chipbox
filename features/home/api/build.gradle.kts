plugins {
    alias(chipbox.plugins.feature.api)
}

// State/action types live in :api; declare the public surface they expose.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.strings.api)
                api(projects.cbox.common.appcomm.api)
                api(libs.sage.common.appcomm)
                api(libs.sage.common.ui.components)
                api(libs.sage.common.list)
                api(libs.sage.common.ui.iconsApi)
                api(libs.kotlinx.collections.immutable)
                api(libs.kotlinx.coroutines.core)
                api(libs.sage.common.ui.strings)
            }
        }
    }
}

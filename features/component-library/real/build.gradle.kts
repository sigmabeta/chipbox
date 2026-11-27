plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

// One ViewModel and one route, both commonMain. The catalog (and its sample-text generator) lives in
// SAGE's :common:ui:component-library; this module only supplies Chipbox's cards (in :api) and hosts it.
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.componentLibrary.api)

                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(libs.sage.common.ui.component.library)
            }
        }
    }
}

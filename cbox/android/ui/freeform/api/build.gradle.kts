plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.ui.freeform"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.freeform)
                api(libs.sage.common.appcomm)
                api(libs.sage.common.ui.strings)
                api(libs.sage.common.ui.components)
                api(projects.cbox.common.appcomm.api)
                api(projects.cbox.android.ui.list.api)

                implementation(projects.cbox.android.ui.chrome.api)
            }
        }
    }
}

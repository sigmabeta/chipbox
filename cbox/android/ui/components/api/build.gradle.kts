plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.ui.components"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.ui.components)

                implementation(libs.sage.common.appcomm)
                implementation(libs.sage.common.images)

                implementation(libs.sage.android.perf)
                implementation(libs.sage.android.ui.icons)

                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.ui.fonts.api)

                implementation(libs.coil.kt.core)
                implementation(libs.coil.kt.compose)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(projects.cbox.android.ui.theme.api)
                implementation(libs.androidx.compose.ui.tooling.preview)
            }
        }
    }
}

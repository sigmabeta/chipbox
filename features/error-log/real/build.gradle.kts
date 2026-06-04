plugins {
    id("chipbox.feature.real")
    id("chipbox.kmp.test")
}

// ViewModel/State + the ErrorLogRoute composable are all commonMain. The only platform split is
// the timestamp formatter (ErrorTimestamp): java.time on the shared JVM/Android source set, an
// epoch-string stub on the enforcement-only JS target (which has no debug UI).
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.errorLog.api)

                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.strings.api)

                implementation(libs.sage.common.ui.components)
            }
        }
    }
}

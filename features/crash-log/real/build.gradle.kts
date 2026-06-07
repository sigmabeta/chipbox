plugins {
    alias(chipbox.plugins.feature.real)
    alias(chipbox.plugins.kmp.test)
}

// ViewModel/State + the CrashLogRoute composable are all commonMain. The only platform split is
// the timestamp formatter (CrashTimestamp): java.time on the shared JVM/Android source set, an
// epoch-string stub on the enforcement-only JS target (which has no debug UI).
kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.features.crashLog.api)

                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.strings.api)
                implementation(projects.cbox.common.crash.api)

                implementation(libs.sage.common.ui.components)
                implementation(libs.kotlinx.collections.immutable)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.crash.api)
            }
        }
    }
}

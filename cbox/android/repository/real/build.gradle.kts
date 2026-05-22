plugins {
    alias(libs.plugins.sage.kmp)
}

// DatabaseRepository — already pure Kotlin (its only platform tie was
// `ChipboxDatabase`, now KMP). One module for both variants.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.repository.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.repository.api)
                api(projects.cbox.android.database.all)
                implementation(projects.cbox.common.perf.api)
                implementation(libs.sage.common.logging)
            }
        }
    }
}

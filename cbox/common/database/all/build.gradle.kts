plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.database.all"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.database.api)
                api(projects.cbox.common.database.real)
            }
        }
    }
}

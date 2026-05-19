plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.repository.api"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}

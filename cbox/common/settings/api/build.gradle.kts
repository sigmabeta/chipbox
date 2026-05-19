plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.settings.api"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}

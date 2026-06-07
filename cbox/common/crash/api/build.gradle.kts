plugins {
    alias(libs.plugins.sage.kmp)
    // CrashReport is persisted as JSON on disk, so it carries @Serializable.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.crash.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.kotlinx.serialization.core)
            }
        }
    }
}

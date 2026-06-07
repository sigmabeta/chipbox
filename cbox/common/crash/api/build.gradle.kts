plugins {
    alias(libs.plugins.sage.kmp)
    // Pulled into the JS dependency graph transitively (appui:api -> crash-log:real -> crash:api),
    // so it needs the (flag-gated) JS target even though the writer/reader impls are JVM-only.
    alias(libs.plugins.sage.kmp.js)
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

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
    // ChipboxStringId is persisted inside NotifState, so the enum needs a serializer.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.strings.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.sage.common.ui.strings)
                implementation(libs.kotlinx.serialization.core)
            }
        }
    }
}

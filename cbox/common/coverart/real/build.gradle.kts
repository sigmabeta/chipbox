plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.kotlin.serialization)
}

// Implementations behind the cover-art domain (coverart/api). The logic is all commonMain — files
// via okio, time via kotlin.time.Clock, waits via coroutines, and the HTTP boundary behind the
// [CoverArtHttp] interface — so Android, the desktop JVM app and the CLI all share it (the js target
// keeps the metadata compile honest about purity). The one platform-bound piece, the OkHttp-backed
// [CoverArtHttp] impl, lives in jvmSharedMain (src/main/java), shared by the android + jvm targets.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.coverart.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.coverart.api)
                api(projects.cbox.common.models.api)
                implementation(libs.okio)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        named("jvmSharedMain") {
            dependencies {
                implementation(libs.okhttp)
            }
        }
    }
}

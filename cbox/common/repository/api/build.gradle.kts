plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    // The scanner's raw output types (RawGame/RawTrack) are the payload of a library snapshot, which
    // `:apps:perfbench-core` serializes with kotlinx.serialization. Annotating them here lets the
    // snapshot carry the real scan output (paths, chain files, folder signature, versions) rather
    // than a parallel DTO that could drift from it.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.repository.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                api(libs.kotlinx.coroutines.core)
                // Generated serializers for RawGame/RawTrack reference kotlinx.serialization.core,
                // so any module that serializes them needs it resolvable.
                api(libs.kotlinx.serialization.core)
            }
        }
    }
}

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    // The scanner's raw output types (RawGame/RawTrack) are the payload of a library snapshot, and
    // the snapshot DTO + its import capability live here (see LibrarySnapshot.kt / LibraryImport.kt).
    // Annotating them here lets the snapshot carry the real scan output (paths, chain files, folder
    // signature, versions) rather than a parallel DTO that could drift from it.
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
                // so any module that serializes them needs it resolvable. The JSON dialect the
                // snapshot is written/read with (LibrarySnapshotJson) also lives here, so json is
                // exposed too — a consumer that encodes or decodes a snapshot needs both.
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.kotlin.serialization)
}

// Pure cover-art domain shared by every app: the IGDB lookup outcome (CoverLookup), the per-game
// fetch result + run summary, IGDB credentials, the manual override record, and the cache key +
// freshness window. All commonMain (no java.* / no I/O), so Android, the desktop JVM app and the
// CLI build against the same contract; the File/OkHttp implementations live in cbox/common/coverart/real.
kotlin {
    // js target keeps this layer honestly multiplatform: it has no jvmSharedMain, so everything here
    // is forced through commonMain (no java.* / no I/O) and the metadata compile actually verifies it.
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.coverart.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.models.api)
                // @Serializable annotation only; JSON encoding/decoding lives in coverart/real.
                implementation(libs.kotlinx.serialization.core)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

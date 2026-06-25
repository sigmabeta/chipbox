plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.ksp)
    alias(chipbox.plugins.kmp.test)
}

// Separate KMP Room database for playback history (HistoryDatabase), plus the repository that
// writes to it and the recorder that observes the director and decides when a play counts. Mirrors
// cbox/common/database/real: KSP runs room-compiler per target; the bundled SQLite driver is the
// JVM Room driver (Android keeps framework SQLite).
//
// JS variant: RealPlaybackHistoryRecorder + RealPlaybackHistoryRepository are pure common code
// (the recorder needs only the Director; the repository talks to Room DAO *interfaces*), so they
// live in commonMain and now compile for the browser too. apps/js reuses the tested recorder over
// a localStorage-backed repository — see apps/js LocalStoragePlaybackHistoryRepository. Only the
// Room runtime stays platform-bound: the @Database (src/main/java = jvmShared) and the bundled
// SQLite driver (jvmMain) are JVM/Android-only and never reach the JS klib.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.history.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.history.api)
                api(projects.cbox.common.entities.api)

                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
            }
        }
        named("jvmSharedMain") {
            dependencies {
                // Room runtime backs the @Database in src/main/java — JVM/Android only.
                api(libs.room.runtime)
            }
        }
        named("jvmMain") {
            dependencies {
                implementation(libs.sqlite.bundled)
            }
        }
        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.player.director.fake)
                implementation(projects.cbox.common.history.fake)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

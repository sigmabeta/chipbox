plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.ksp)
    alias(chipbox.plugins.kmp.test)
}

// Separate KMP Room database for playback history (HistoryDatabase), plus the repository that
// writes to it and the recorder that observes the director and decides when a play counts. Mirrors
// cbox/common/database/real: KSP runs room-compiler per target; the bundled SQLite driver is the
// JVM Room driver (Android keeps framework SQLite).
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.history.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.history.api)
                api(projects.cbox.common.entities.api)
                api(libs.room.runtime)

                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.player.common.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
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

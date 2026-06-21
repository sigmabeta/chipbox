plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.ksp)
    alias(chipbox.plugins.kmp.test)
}

// Separate KMP Room database for user favorites (FavoritesDatabase), plus the repository that reads
// and writes it. Mirrors cbox/common/history/real: KSP runs room-compiler per target; the bundled
// SQLite driver is the JVM Room driver (Android keeps framework SQLite). Kept apart from the library
// ChipboxDatabase so favorites survive the library's destructive rebuilds.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.favorites.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.favorites.api)
                api(projects.cbox.common.entities.api)
                api(libs.room.runtime)

                implementation(projects.cbox.common.models.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
            }
        }
        named("jvmMain") {
            dependencies {
                implementation(libs.sqlite.bundled)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

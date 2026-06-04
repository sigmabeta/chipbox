plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.ksp)
}

// KMP Room database. The `@Database` class + DAOs are shared between Android
// and JVM via Room 2.7+'s multiplatform support. The bundled SQLite driver
// (jvmMain only) is the JVM Room driver; Android keeps the framework SQLite.
// KSP runs room-compiler against each target so the Room generated code lands
// in androidMain and jvmMain respectively.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.database.real"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(projects.cbox.common.database.api)
                api(projects.cbox.common.entities.api)
                api(libs.room.runtime)
                // Note: dropped `libs.room.ktx` (Android-only artifact); Room
                // 2.7+ `room-runtime` is multiplatform and ships its own
                // coroutines/Flow support.
            }
        }
        named("jvmMain") {
            dependencies {
                // JVM Room driver. Android uses the framework SQLite via the
                // databaseBuilder(Context, …) overload, so it doesn't need this.
                implementation(libs.sqlite.bundled)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

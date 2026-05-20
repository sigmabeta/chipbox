plugins {
    alias(libs.plugins.sage.kmp)
}

// Room entities. Room 2.7+ is multiplatform, so the `@Entity` / `@PrimaryKey`
// annotations and `androidx.room.*` types here build for both the android and
// jvm variants from one module.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.entities.api"
    }

    sourceSets {
        named("jvmSharedMain") {
            dependencies {
                api(libs.room.runtime)
            }
        }
    }
}

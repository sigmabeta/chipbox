plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
}

// Room entities — annotations only (@Entity/@PrimaryKey/@ColumnInfo/@Index). Those live in
// room-common, which (unlike room-runtime) publishes a Kotlin/JS variant, so this module builds
// for android, jvm AND the js() purity gate. room-compiler still reads the annotations from
// database/real, which keeps the (jvm/android/native-only) room-runtime.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.entities.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.room.common)
            }
        }
    }
}

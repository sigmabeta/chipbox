plugins {
    alias(libs.plugins.sage.kmp)
}

// The @Dao interfaces. They need only Room's annotations (room-common, which publishes a JS
// variant) plus the entity types + Flow — no room-runtime/KSP. So this layer builds for android,
// jvm AND the js() purity gate, letting repository/real depend on the DAO contracts without the
// (jvm/android/native-only) Room runtime. database/real's @Database + room-compiler reference these.
kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.database.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.room.common)
                api(projects.cbox.common.entities.api)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

plugins {
    alias(libs.plugins.sage.kmp)
    // Models cross the HTTP boundary between apps/server and apps/js (RemoteRepository).
    // kotlinx.serialization gives us @Serializable for every wire type without DTO duplication.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    js { nodejs() }

    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.models.api"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.strings.api)
                // `api` (not implementation) because the @Serializable annotation generates
                // companion objects that implement kotlinx.serialization.internal.SerializerFactory
                // — any module that imports a model (Track, Game, Platform, …) needs that type
                // resolvable on its classpath. `api` makes the transitive dep automatic.
                api(libs.kotlinx.serialization.core)
            }
        }

        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

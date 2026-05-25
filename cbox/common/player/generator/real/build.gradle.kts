plugins {
    alias(libs.plugins.sage.kmp)
}

// The production Generator wiring is pure Kotlin (the real work lives in the pure-JVM
// :cbox:common:player:cache:real). The Android Context that used to appear here was just
// caller-supplied config (the two cache dirs) — now passed as plain File params from the
// Android Hilt module — so this is one KMP module for both variants, no platform seam.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.player.generator.real"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.generator.api)
                api(projects.cbox.common.player.emulators.api)
                api(projects.cbox.common.player.cache.real)
            }
        }
    }
}

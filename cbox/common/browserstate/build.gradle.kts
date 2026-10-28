plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.kotlin.serialization)
    alias(chipbox.plugins.kmp.test)
}

// The root build disables the Kotlin/JS toolchain download on the root project only; a subproject
// with a `js { nodejs() }` target gets its own per-project NodeJsEnvSpec. Use the system Node
// instead of letting kotlinNodeJsSetup add the nodejs.org project repository that
// RepositoriesMode.FAIL_ON_PROJECT_REPOS rejects.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
}

// Browser-local state shared by the web app and ticket 07's history/session stores: a versioned
// JSON envelope plus Storage-backed repositories for favorites and playlists. Pure commonMain so
// the JSON/ordering rules run as Node tests (`jsNodeTest`) as well as on the JVM.
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.browserstate"
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.favorites.api)
                api(projects.cbox.common.playlists.api)
                api(projects.cbox.common.history.api)
                api(projects.cbox.common.models.api)
                // The web playback-session store persists SessionSnapshot (player.persistence.api);
                // history/session state share the envelope in this module.
                api(projects.cbox.common.player.persistence.api)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.sage.common.storage.common)
            }
        }
    }
}

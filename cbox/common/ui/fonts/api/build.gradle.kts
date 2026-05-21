import org.jetbrains.compose.resources.ResourcesExtension

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.compose.kmp)
    // JetBrains Compose Gradle plugin — pulled in here (in addition to sage.compose.kmp,
    // which handles the Kotlin compose-compiler) because this module ships Compose Multiplatform
    // resources under `src/commonMain/composeResources/` and we need the plugin's codegen to
    // produce the `Res.font.*` accessors. Other shared UI modules (e.g. the theme color/typography
    // module) stay on `sage.compose.kmp` alone since they have no resources.
    alias(libs.plugins.compose.multiplatform)
}

// Chipbox's pixel-art font catalog — 18 .otf files shipped via Compose Multiplatform resources
// under `src/commonMain/composeResources/font/`. Both the Android app and the JVM/desktop entry
// load them through the generated `Res.font.<filename>` accessors; no more Android `R.font.*`.
kotlin {
    androidLibrary {
        namespace = "net.sigmabeta.chipbox.common.ui.fonts.api"
        // KMP libraries default to `androidResources.enable = false`; that gates the AAR
        // assets pipeline too. We need it on so AGP picks up `src/androidMain/assets/`
        // (where `composeResources/<pkg>/font/` is symlinked back to `commonMain/
        // composeResources/font/`) and packages those into the APK's `assets/`. Android
        // Compose's `Font(resource)` reads via `AssetManager.open(path)`, which only sees
        // `assets/`; the JetBrains Compose plugin's `copyAndroidMainComposeResourcesToAndroidAssets`
        // task that would normally automate this leaves `outputDirectory` unset on AGP 9 +
        // `com.android.kotlin.multiplatform.library` (latent upstream incompat), so we
        // route the files into the asset pipeline by hand.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(libs.jetbrains.compose.resources)
            }
        }
    }
}

// Explicit `Res` package + always-generate (the plugin's `auto` default no-ops in this setup
// because the module has no other Compose entry signaling intent). `publicResClass = true`
// because the JVM entry in `apps/jvm` calls `Res.font.<x>` directly via `ChipboxFont.toFontFamily()`.
compose.resources {
    publicResClass = true
    generateResClass = ResourcesExtension.ResourceClassGeneration.Always
    packageOfResClass = "net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources"
}

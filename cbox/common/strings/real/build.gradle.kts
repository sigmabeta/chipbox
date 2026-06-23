import org.jetbrains.compose.resources.ResourcesExtension

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
    // Compose Multiplatform plugin for the string-resource codegen (Res.allStringResources),
    // same as cbox/common/ui/fonts/api uses for fonts. This is the SINGLE source of string
    // values (src/commonMain/composeResources/values/*.xml) for every platform — replacing the
    // old Android R.string + generated JVM map + hardcoded CLI subset.
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.strings.real"

        // AGP 9's `com.android.kotlin.multiplatform.library` ships with Android resource/asset
        // processing OFF by default. While off, AGP never calls
        // `variant.sources.assets.addGeneratedSourceDirectory(...)`, so the Compose Resources
        // plugin's `copyAndroidMainComposeResourcesToAndroidAssets` task is registered but its
        // `outputDirectory` is never configured and it stays out of the `assemble` graph — the
        // generated `*.cvr` value resources never reach the AAR/APK assets and the app crashes at
        // runtime with MissingResourceException (JetBrains CMP-9547). Enable assets so they ship.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.strings.api)
                implementation(libs.sage.common.ui.strings)
                implementation(libs.jetbrains.compose.resources)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = ResourcesExtension.ResourceClassGeneration.Always
    packageOfResClass = "net.sigmabeta.chipbox.common.strings.real.generated.resources"
}

// Paparazzi composeResources export. CMP 1.11 packages androidMain composeResources as Android assets
// only — off the JVM unit-test classpath — which breaks the ClasspathResourceReader in
// ChipboxPreviewStrings under Paparazzi (strings fall back to their resource keys). Expose the
// prepared composeResources as a classpath-shaped jar (`composeResources/<packageOfResClass>/...`)
// that the :features:*:screenshot modules add to their test classpath (see ChipboxScreenshotPlugin).
val composeResourcesElements: Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}
val composeResourcesElementsJar = tasks.register<Jar>("composeResourcesElementsJar") {
    archiveClassifier.set("compose-resources")
    // Same-module ordering: this task produces preparedResources/commonMain, read below.
    dependsOn("prepareComposeResourcesTaskForCommonMain")
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources")) {
        into("composeResources/net.sigmabeta.chipbox.common.strings.real.generated.resources")
    }
}
artifacts {
    add(composeResourcesElements.name, composeResourcesElementsJar)
}

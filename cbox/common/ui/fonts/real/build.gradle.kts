import org.jetbrains.compose.resources.ResourcesExtension

plugins {
    alias(libs.plugins.sage.kmp)
    alias(libs.plugins.sage.kmp.js)
    alias(libs.plugins.sage.compose.kmp)
    // JetBrains Compose Gradle plugin — needed for the `Res.font.*` codegen over the .otf
    // files under `src/commonMain/composeResources/font/`. Sibling :api is now pure Kotlin
    // (the enum metadata only) so consumers that only carry a ChipboxFont through actions /
    // state don't drag Compose-Resources (and on Kotlin/JS, Skiko) into their classpath.
    alias(libs.plugins.compose.multiplatform)
}

// Compose binding for ChipboxFont. Holds the 18 .otf assets, the generated `Res.font.*`
// accessors, and the [ChipboxFont.resource] / [ChipboxFont.toFontFamily] extensions. Apply
// this module only where Compose UI is also in scope (e.g. cbox/common/ui/theme/api).
kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.common.ui.fonts.real"
        // Same `androidResources.enable = true` + manual symlink workaround the sibling :api
        // used before the split — AGP 9 + KMP library leaves `outputDirectory` unset on
        // `copyAndroidMainComposeResourcesToAndroidAssets`, so we route .otf files into the
        // APK's `assets/composeResources/<pkg>/font/` by hand via the androidMain symlink.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.ui.fonts.api)
                api(libs.jetbrains.compose.resources)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = ResourcesExtension.ResourceClassGeneration.Always
    packageOfResClass = "net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources"
}

// Paparazzi composeResources export — see the matching block in cbox/common/strings/real. CMP 1.11
// packages androidMain composeResources as assets only, off the JVM unit-test classpath, which breaks
// resource loading under Paparazzi; expose them as a classpath-shaped jar the screenshot modules add
// to their test classpath (here it carries the `Res.font.*` .otf files).
val composeResourcesElements: Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}
val composeResourcesElementsJar = tasks.register<Jar>("composeResourcesElementsJar") {
    archiveClassifier.set("compose-resources")
    dependsOn("prepareComposeResourcesTaskForCommonMain")
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources")) {
        into("composeResources/net.sigmabeta.chipbox.common.ui.fonts.real.generated.resources")
    }
}
artifacts {
    add(composeResourcesElements.name, composeResourcesElementsJar)
}

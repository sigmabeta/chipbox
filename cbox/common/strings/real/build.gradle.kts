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

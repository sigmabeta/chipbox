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

@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

plugins {
    alias(libs.plugins.sage.kmp)
    // Compose compiler (@Composable codegen) + the JetBrains Compose plugin, which is what
    // exposes the `compose.uiTest` (runComposeUiTest) and `compose.desktop.currentOs` (Skia at
    // test runtime) accessors used below. Both come from the catalog, same as apps/jvm. Phase 0
    // wires these inline to prove the cross-platform `runComposeUiTest` rails; once green this
    // moves into a `sage.compose.uitest` convention plugin in sage-build-logic.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.uitest"
    }

    sourceSets {
        named("commonTest") {
            dependencies {
                // runComposeUiTest + the onNode*/assert* matcher surface, multiplatform.
                implementation(compose.uiTest)
                // Text + Compose UI used by the smoke test's hosted content.
                implementation(compose.material3)
            }
        }

        named("jvmTest") {
            dependencies {
                // Per-OS Skia native — the desktop backend `runComposeUiTest` renders onto.
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

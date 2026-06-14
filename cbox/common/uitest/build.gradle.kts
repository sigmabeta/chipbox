@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

plugins {
    alias(libs.plugins.sage.kmp)
    // Compose compiler (@Composable codegen) + the JetBrains Compose plugin, which is what
    // exposes the `compose.uiTest` (runComposeUiTest) and `compose.desktop.currentOs` (Skia at
    // test runtime) accessors used below. Both come from the catalog, same as apps/jvm. Phase 0
    // wires these inline to prove the rails; once green this moves into a `sage.compose.uitest`
    // convention plugin in sage-build-logic (this is intended to be a sage-wide capability).
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro compiler plugin — lets the test source sets declare a @DependencyGraph (the
    // TestAppGraph that hosts real screens over fake bindings). AppScope + the metrox ViewModel
    // multibindings come from the deps below, not the sage.di mixin (which targets non-KMP bases).
    alias(libs.plugins.metro)
    alias(chipbox.plugins.kmp.test)
}

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.uitest"

        // The Android half of the suite runs ON-DEVICE (instrumented), not on the JVM host:
        // runComposeUiTest needs a real Android framework (Looper/Context), which the
        // androidHostTest target lacks and Robolectric would otherwise have to fake. This creates
        // the `androidDeviceTest` source set and the device-test (connectedAndroidTest-style)
        // tasks that deploy to a connected device/emulator.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        // The shared UI-test specs live in src/uiTest and are compiled INTO both leaf test source
        // sets directly (via srcDir), NOT shared with dependsOn. jvmTest is in the unit-test tree
        // and androidDeviceTest is in the instrumented-test tree; KMP forbids a source set from
        // depending on sets across two different trees. Same physical files, two independent
        // compilations — the specs use only multiplatform APIs (compose.uiTest, compose.material3,
        // kotlin.test) available to both. The dir is kept off androidHostTest on purpose (no
        // Android framework there → runComposeUiTest NPEs).
        named("commonTest") {
            dependencies {
                // runComposeUiTest + the onNode*/assert* matcher surface, multiplatform. In
                // commonTest so the JVM unit-test tree (jvmTest) inherits it; the instrumented
                // tree gets its own copy below.
                implementation(compose.uiTest)
                // Text + Compose UI used by the smoke test's hosted content.
                implementation(compose.material3)
            }
        }

        named("jvmTest") {
            kotlin.srcDir("src/uiTest/kotlin")
            dependencies {
                // Per-OS Skia native — the desktop backend `runComposeUiTest` renders onto.
                implementation(compose.desktop.currentOs)

                // --- Phase 1 harness (jvmTest-only for now; generalised to androidDeviceTest +
                // the shared uiTest dir once green). Hosts the real ChipboxAppUi shell over a fake
                // Metro graph + seeded MemoryRepository. appui.api api-exposes every feature `:real`
                // VM, so the graph must satisfy all their deps — fakes added below. ---
                implementation(projects.cbox.common.appui.api)
                implementation(projects.features.gameDetail.real)
                implementation(projects.features.gameDetail.api)
                implementation(projects.features.artistDetail.api)
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.repository.fake)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.player.director.fake)
                implementation(projects.cbox.common.settings.api)
                implementation(projects.cbox.common.settings.fake)
                // Remaining leaf bindings every feature VM transitively needs (Metro listed them).
                implementation(projects.cbox.common.contentsource.api)
                implementation(projects.cbox.common.contentsource.fake)
                implementation(projects.cbox.common.scanner.api)
                implementation(projects.cbox.common.scanner.fake)
                implementation(projects.cbox.common.debug.api)
                implementation(projects.cbox.common.debug.fake)
                implementation(projects.cbox.common.debugInfo.api)
                implementation(projects.cbox.common.debugInfo.fake)
                implementation(projects.cbox.common.crash.api)
                implementation(libs.okio.fakefilesystem)
                implementation(projects.cbox.common.models.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.ui.chrome.api)
                implementation(projects.cbox.common.ui.theme.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(projects.cbox.common.strings.api)
                implementation(libs.sage.common.di)
                implementation(libs.sage.common.logging)
                implementation(libs.sage.common.appinfo)
                implementation(libs.sage.common.ui.strings)
                implementation(libs.sage.common.ui.perfCompose)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }

        named("androidDeviceTest") {
            kotlin.srcDir("src/uiTest/kotlin")
            dependencies {
                // The instrumented tree inherits nothing from commonTest, so re-declare the
                // multiplatform test APIs the shared specs compile against.
                implementation(compose.uiTest)
                implementation(compose.material3)
                implementation(kotlin("test"))
                // Provides the empty Activity the on-device Compose test hosts content in.
                implementation(libs.androidx.compose.ui.testing.manifest)
                // AndroidJUnitRunner + the instrumentation registry the device tests run under.
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

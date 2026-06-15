import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

plugins {
    alias(libs.plugins.sage.kmp)
    // The generic UI-test rails — Compose compiler + JetBrains Compose plugin, the on-device
    // (`withDeviceTest`) Android half, the canonical `src/jvmTest` specs mirrored onto
    // androidDeviceTest, and the compose-test + instrumentation deps — live in this sage convention
    // plugin (a sage-wide capability, not chipbox-specific). The app-specific harness deps stay below.
    alias(libs.plugins.sage.compose.uitest)
    // Metro compiler plugin — lets the test source sets declare a @DependencyGraph (the
    // TestAppGraph that hosts real screens over fake bindings). AppScope + the metrox ViewModel
    // multibindings come from the deps below, not the sage.di mixin (which targets non-KMP bases).
    alias(libs.plugins.metro)
    alias(chipbox.plugins.kmp.test)
}

// The harness's app-specific dependencies. The shared specs in src/jvmTest compile into BOTH the
// jvmTest (unit-test tree) and androidDeviceTest (instrumented tree) source sets, and those trees
// share no dependsOn (KMP forbids it across trees), so each must carry the same classpath. Applied
// to both below. appui.api api-exposes every feature `:real` VM, so the Metro graph must satisfy all
// their deps — hence the fakes + leaf bindings.
val harnessDependencies: KotlinDependencyHandler.() -> Unit = {
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

kotlin {
    android {
        namespace = "net.sigmabeta.chipbox.uitest"
    }

    sourceSets {
        named("jvmTest") {
            // Desktop-only platform seam (the JVM `platformArtifactDir`). NOT mirrored to
            // androidDeviceTest — the device gets its own impl from src/androidDeviceTest.
            kotlin.srcDir("src/jvmTestPlatform/kotlin")
            dependencies { harnessDependencies() }
        }

        named("androidDeviceTest") {
            dependencies { harnessDependencies() }
        }
    }
}

// On a failed desktop spec, the harness dumps a screenshot + semantics tree (see FailureArtifacts.kt).
// Point them at the module's build dir so they're a discoverable, CI-collectable artifact rather than
// scattered in the machine's tmp. The on-device run has no build dir, so it keeps the tmpdir fallback.
tasks.named<Test>("jvmTest") {
    systemProperty(
        "chipbox.uitest.artifactDir",
        layout.buildDirectory.dir("uitest-failures").get().asFile.absolutePath,
    )
}

plugins {
    alias(libs.plugins.sage.jvm)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.jvm.MainKt")
}

// libgme.so is host-built (see apps/jvm/README) into apps/jvm/libs and loaded at runtime via
// System.loadLibrary("gme"); point the JVM library path at it for `gradle run`.
val nativeLibsDir = layout.projectDirectory.dir("libs")

tasks.named<JavaExec>("run") {
    systemProperty("java.library.path", nativeLibsDir.asFile.absolutePath)
}

dependencies {
    implementation(projects.cbox.jvm.player.generator.real)
    implementation(projects.cbox.jvm.player.emulators.gba.real)
    implementation(projects.cbox.jvm.player.emulators.gme.real)
    implementation(projects.cbox.jvm.player.emulators.psf.real)
    implementation(projects.cbox.jvm.player.emulators.ssf.real)
    implementation(projects.cbox.jvm.player.emulators.twosf.real)
    implementation(projects.cbox.jvm.player.emulators.usf.real)
    implementation(projects.cbox.jvm.player.emulators.vgm.real)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.player.speaker.fake)
    implementation(projects.cbox.common.player.buffer.real)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.models.api)
    implementation(libs.sage.common.logging)
    implementation(libs.kotlinx.coroutines.core)
}

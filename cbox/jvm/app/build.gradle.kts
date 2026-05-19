plugins {
    alias(libs.plugins.sage.jvm)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.jvm.MainKt")
}

dependencies {
    implementation(projects.cbox.common.repository.fake)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.player.generator.fake)
    implementation(projects.cbox.common.player.speaker.fake)
    implementation(projects.cbox.common.player.buffer.real)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.models.api)
    implementation(libs.sage.common.logging)
    implementation(libs.kotlinx.coroutines.core)
}

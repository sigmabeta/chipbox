plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = "net.sigmabeta.chipbox.player.generator.di"
}

dependencies {
    api(projects.cbox.android.player.generator.fake.di)
    api(projects.cbox.android.player.generator.real.di)
    api(projects.cbox.common.player.generator)
}

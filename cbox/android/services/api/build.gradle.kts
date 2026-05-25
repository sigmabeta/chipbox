import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = chipboxNamespace()
}

dependencies {
    api(libs.androidx.media3.session)

    implementation(projects.cbox.android.artworkprovider.api)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.player.director.api)
    implementation(projects.cbox.common.repository.api)
}

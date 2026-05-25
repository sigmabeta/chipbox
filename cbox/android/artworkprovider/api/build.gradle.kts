import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di)
}

android {
    namespace = chipboxNamespace()
}

dependencies {
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.contentsource.file.real)
    implementation(projects.cbox.common.models.api)
}

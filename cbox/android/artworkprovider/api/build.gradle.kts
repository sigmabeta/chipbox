import net.sigmabeta.sage.plugins.components.chipboxNamespace

plugins {
    alias(libs.plugins.sage.android)
    alias(libs.plugins.sage.di.android)
}

android {
    namespace = chipboxNamespace()
}

dependencies {
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.android.contentsource.file.all)
    implementation(projects.cbox.common.models.api)
}

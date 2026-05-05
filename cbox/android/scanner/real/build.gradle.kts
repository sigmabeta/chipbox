plugins {
    alias(libs.plugins.sage.android)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.real"
}

dependencies {
    api(projects.cbox.common.scanner)
    implementation(projects.cbox.common.readers)
    implementation(projects.cbox.common.repository)
    implementation(projects.cbox.android.contentsource.file)
    implementation(libs.sage.common.logging)
}

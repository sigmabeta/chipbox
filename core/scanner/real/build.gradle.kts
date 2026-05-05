plugins {
    id("sage.android")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.sigmabeta.chipbox.scanner.real"
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    api(projects.cbox.common.scanner)
    implementation(projects.cbox.common.readers)
    implementation(projects.cbox.common.repository)
    implementation(projects.cbox.android.contentsource.file)
    implementation(libs.sage.common.logging)
}

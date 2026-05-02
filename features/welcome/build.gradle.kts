plugins {
    id("sage.jvm")
}

dependencies {
    api(libs.sage.common.list)
    api(libs.sage.common.appcomm)
    api(libs.sage.common.analytics)
    api(libs.sage.common.logging)
    api(libs.sage.common.ui.components)
    api(libs.sage.common.ui.strings)
}

plugins {
    alias(libs.plugins.sage.feature.api)
}

kotlin {
    js { nodejs() }
}

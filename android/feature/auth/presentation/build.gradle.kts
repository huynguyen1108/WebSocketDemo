plugins {
    alias(libs.plugins.wschat.android.feature)
}

android {
    namespace = "com.example.feature.auth.presentation"
}

dependencies {
    implementation(project(":feature:auth:domain"))
    implementation(libs.androidx.material.icons.extended)
}

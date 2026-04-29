plugins {
    alias(libs.plugins.wschat.android.feature)
}

android {
    namespace = "com.example.feature.order.presentation"
}

dependencies {
    implementation(project(":feature:order:domain"))
    implementation(libs.androidx.material.icons.extended)
}

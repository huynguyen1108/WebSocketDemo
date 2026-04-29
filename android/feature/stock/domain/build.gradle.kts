plugins {
    alias(libs.plugins.wschat.android.library)
}

android {
    namespace = "com.example.feature.stock.domain"
}

dependencies {
    implementation(project(":core:common"))
}

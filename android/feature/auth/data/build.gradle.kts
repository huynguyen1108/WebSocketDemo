plugins {
    alias(libs.plugins.wschat.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.feature.auth.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))
    implementation(project(":feature:auth:domain"))

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
}

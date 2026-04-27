plugins {
    alias(libs.plugins.wschat.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.feature.order.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":feature:order:domain"))
    implementation(project(":feature:chat:domain")) // ConnectionState
    implementation(libs.kotlinx.serialization.json)
}

plugins {
    alias(libs.plugins.wschat.android.library)
}

android {
    namespace = "com.example.feature.order.domain"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:chat:domain")) // dùng ConnectionState
}

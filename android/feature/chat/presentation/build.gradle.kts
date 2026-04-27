plugins {
    alias(libs.plugins.wschat.android.feature)
}

android {
    namespace = "com.example.feature.chat.presentation"
}

dependencies {
    implementation(project(":feature:chat:domain"))
    implementation(libs.androidx.material.icons.extended)
}

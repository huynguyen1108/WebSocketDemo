plugins {
    alias(libs.plugins.wschat.android.feature)
}

android {
    namespace = "com.example.feature.stock.presentation"
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(project(":feature:stock:domain"))
    implementation(project(":feature:auth:domain"))
    implementation(project(":feature:chat:domain")) // ConnectionState
    implementation(project(":core:security"))
    implementation(libs.androidx.material.icons.extended)

    // XML Views
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material.views)
    implementation(libs.androidx.fragment.ktx)
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.hakanbs"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.hakanbs"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("io.coil-kt:coil-gif:2.6.0") // BU SATIRI EKLEYİN
    implementation("androidx.cardview:cardview:1.0.0")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Test kütüphaneleri
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Firebase (Remote Config ve Firestore)
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // GSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Coil
    implementation("io.coil-kt:coil:2.6.0")
    implementation("io.coil-kt:coil-base:2.6.0")
}
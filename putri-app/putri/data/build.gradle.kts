plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "putri.data"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":core"))
    val room = "2.6.1"
    api("androidx.room:room-runtime:$room")
    api("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(kotlin("test"))
}

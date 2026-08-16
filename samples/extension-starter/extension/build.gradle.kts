plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.newshub.extension"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.newshub.extension"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

val newshubDir = providers.gradleProperty("newshubDir").orNull?.takeIf(String::isNotBlank)
val newshubApiPin = providers.gradleProperty("newshubApiPin").orNull?.takeIf(String::isNotBlank)
val localApi = newshubDir?.let {
    rootProject.file("$it/extension-api/build/outputs/aar/extension-api-debug.aar")
}

if (localApi != null && !localApi.exists()) {
    throw GradleException(
        "Local extension-api AAR is missing at $localApi. " +
            "Run ../../gradlew :extension-api:assembleDebug from this starter.",
    )
}
if (localApi == null && newshubApiPin == null) {
    throw GradleException("Set newshubDir or set newshubApiPin to a reviewed commit SHA.")
}

dependencies {
    if (localApi != null) {
        implementation(files(localApi))
        testImplementation(files(localApi))
    } else {
        val coordinate = "com.github.komicaviewer.NewsHub:extension-api:$newshubApiPin"
        implementation(coordinate)
        testImplementation(coordinate)
    }
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

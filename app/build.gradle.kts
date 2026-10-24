plugins { id("com.android.application") }

android {
    namespace = "jp.touringhub.assistant"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "jp.touringhub.assistant"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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

// On-device speech recognition uses the official sherpa-onnx Android AAR.
// It is not committed to Git; this task downloads the pinned release on first
// build and verifies its SHA-256 before the AAR is used.
val sherpaOnnxVersion = "2.0.1"
val sherpaOnnxSha256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
val sherpaOnnxAar = layout.projectDirectory.file("libs/sherpa-onnx-$sherpaOnnxVersion.aar")

fun sha256(file: java.io.File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadSherpaOnnxAar by tasks.registering {
    description = "Downloads the pinned sherpa-onnx Android AAR into app/libs (SHA-256 verified)."
    val target = sherpaOnnxAar.asFile
    outputs.file(target)
    outputs.upToDateWhen { target.isFile && sha256(target) == sherpaOnnxSha256 }
    doLast {
        if (target.isFile && sha256(target) == sherpaOnnxSha256) return@doLast
        target.parentFile.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar")
            .toURL().openStream().use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
        check(sha256(partial) == sherpaOnnxSha256) { "Downloaded sherpa-onnx AAR failed SHA-256 verification" }
        Files.move(partial.toPath(), target.toPath(),
            StandardCopyOption.REPLACE_EXISTING)
    }
}
tasks.named("preBuild") { dependsOn(downloadSherpaOnnxAar) }

dependencies {
    implementation(files("libs/sherpa-onnx-$sherpaOnnxVersion.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

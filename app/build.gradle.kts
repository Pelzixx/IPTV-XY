import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional developer API keys from "secrets.properties" (ignored by Git) or environment variables.
// Normal users enter their own keys in the app (Settings > API keys); without keys the build contains none.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String =
    (secrets.getProperty(name) ?: System.getenv(name) ?: "").trim().filter { it.isLetterOrDigit() }

// Fest eingebaute Server/EPG-Adressen (Secrets PRESET_SERVERS / PRESET_EPG), als Java-Text maskiert
fun presetText(name: String): String =
    (secrets.getProperty(name) ?: System.getenv(name) ?: "").trim()
        .replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n")

android {
    namespace = "com.ipvio.stream"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pelzixx.iptvxy"
        minSdk = 24
        targetSdk = 37
        // Release-Builds der GitHub-Action setzen Versionsnummer und -namen (siehe .github/workflows/release.yml)
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0"

        buildConfigField("String", "TMDB_KEY", "\"${secret("TMDB_KEY")}\"")
        buildConfigField("String", "OMDB_KEY", "\"${secret("OMDB_KEY")}\"")
        buildConfigField("String", "PRESET_SERVERS", "\"${presetText("PRESET_SERVERS")}\"")
        buildConfigField("String", "PRESET_EPG", "\"${presetText("PRESET_EPG")}\"")
        buildConfigField("String", "PRESET_CONFIG_URL", "\"${presetText("PRESET_CONFIG_URL")}\"")
    }

    // Signaturschlüssel für Releases kommt aus den GitHub-Secrets. Fehlt er (lokaler Build), bleibt die Release-APK unsigniert.
    // Updates lassen sich nur installieren, wenn sie mit demselben Schlüssel signiert sind wie die installierte App.
    val keystoreFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystoreFile != null) create("release") {
            storeFile = keystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "iptvxy"
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        // Test-Builds laufen neben der installierten App (eigene App-ID, eigene Daten)
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                // Aus, bis die verkleinerte Release-Version auf einem Gerät getestet ist (R8 kann per Reflexion genutzte Klassen entfernen)
                enable = false
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    // Software audio decoder (AC3/E-AC3/DTS ...) for headphones/Bluetooth, see SoftAudio.kt. GPL-3.0 – the reason the app is GPL-3.0.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // QR-Code auf der Startseite (HomeInfo.kt)
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    // Echtes org.json für Unit-Tests (im Android-Stub sind die Methoden leer)
    testImplementation("org.json:json:20240303")
}

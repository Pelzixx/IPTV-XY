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
        manifestPlaceholders["appLabel"] = "IPTV-XY"
        minSdk = 24
        targetSdk = 37
        // Release-Builds der GitHub-Action setzen Versionsnummer und -namen (siehe .github/workflows/release.yml)
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0"

        buildConfigField("String", "TMDB_KEY", "\"${secret("TMDB_KEY")}\"")
        buildConfigField("String", "OMDB_KEY", "\"${secret("OMDB_KEY")}\"")
        manifestPlaceholders["labelSuffix"] = ""
    }

    // Ausgaben (Wunsch des Nutzers, 2026-11-08), nebeneinander installierbar, jede mit eigenen Updates:
    // - xy:       die App des Nutzers (XY-TV-Logo, eigene Server/EPG/Panel aus den Secrets PRESET_*), App-ID …iptvxy.xy, Link iptv-xy-tv.apk
    // - standard: die Original-App für alle, ohne Voreinstellungen (freie Eingabe M3U/Xtream/Stalker), neutrales Logo,
    //             bisherige App-ID und bisheriger Link iptv-xy.apk / update.json (Nutzer der alten App bekommen sie als Update)
    // Weitere Betreiber bekommen je eine eigene Ausgabe nach dem Muster unten (create("name") …): eigene App-ID,
    // eigener Name, Logo unter app/src/<name>/res, eigene Panel-Adresse, eigene Update-Datei.
    flavorDimensions += "edition"
    productFlavors {
        create("xy") {
            dimension = "edition"
            buildConfigField("String", "EDITION", "\"xy\"")
            buildConfigField("String", "APP_NAME", "\"IPTV-XY\"")
            // Name des Anbieters auf dem Reiter der Anmeldeseite (statt „Vorgegeben“)
            buildConfigField("String", "PROVIDER", "\"XY TV\"")
            applicationIdSuffix = ".xy"
            buildConfigField("String", "UPDATE_FILE", "\"update-xy.json\"")
            buildConfigField("boolean", "NEWS", "true")
            buildConfigField("String", "PRESET_SERVERS", "\"${presetText("PRESET_SERVERS")}\"")
            buildConfigField("String", "PRESET_EPG", "\"${presetText("PRESET_EPG")}\"")
            buildConfigField("String", "PRESET_CONFIG_URL", "\"${presetText("PRESET_CONFIG_URL")}\"")
        }
        create("standard") {
            dimension = "edition"
            manifestPlaceholders["appLabel"] = "IPTV-XY Player"
            buildConfigField("String", "EDITION", "\"standard\"")
            buildConfigField("String", "APP_NAME", "\"IPTV-XY\"")
            buildConfigField("String", "PROVIDER", "\"\"")
            buildConfigField("String", "UPDATE_FILE", "\"update.json\"")
            buildConfigField("boolean", "NEWS", "false")
            buildConfigField("String", "PRESET_SERVERS", "\"\"")
            buildConfigField("String", "PRESET_EPG", "\"\"")
            buildConfigField("String", "PRESET_CONFIG_URL", "\"\"")
        }
        // Township TV (Betreiber-Ausgabe, 2026-11-08): eigenes Logo, eigenes Panel. Server/EPG/Panel-Adresse aus den
        // Secrets TOWNSHIP_SERVERS / TOWNSHIP_EPG / TOWNSHIP_CONFIG_URL; Link township-tv.apk
        create("township") {
            dimension = "edition"
            applicationIdSuffix = ".township"
            manifestPlaceholders["appLabel"] = "Township TV"
            buildConfigField("String", "EDITION", "\"township\"")
            buildConfigField("String", "APP_NAME", "\"Township TV\"")
            buildConfigField("String", "PROVIDER", "\"Township TV\"")
            buildConfigField("String", "UPDATE_FILE", "\"update-township.json\"")
            buildConfigField("boolean", "NEWS", "false")
            buildConfigField("String", "PRESET_SERVERS", "\"${presetText("TOWNSHIP_SERVERS")}\"")
            buildConfigField("String", "PRESET_EPG", "\"${presetText("TOWNSHIP_EPG")}\"")
            buildConfigField("String", "PRESET_CONFIG_URL", "\"${presetText("TOWNSHIP_CONFIG_URL")}\"")
        }
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
            manifestPlaceholders["labelSuffix"] = " Test"
        }
        release {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                // Aus, bis die verkleinerte Release-Version auf einem Gerät getestet ist (R8 kann per Reflexion genutzte Klassen entfernen)
                // Getestet 2026-11-08: mit packageScope stürzt die App beim Start ab (IllegalAccessError kotlin.text) – bleibt aus
                enable = false
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
        // Test-App mit Release-Geschwindigkeit (nicht debugbar): gleiche App-ID und gleicher Test-Schlüssel wie „debug“,
        // lässt sich also über die installierte Test-App spielen. Compose läuft im Debug-Build spürbar träger.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            manifestPlaceholders["labelSuffix"] = " Test"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
    // DASH-Streams (.mpd, z. B. manche Radio-/TV-Sender) – ohne das Modul stürzte die App beim Abspielen ab
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    // Software audio decoder (AC3/E-AC3/DTS ...) for headphones/Bluetooth, see SoftAudio.kt. GPL-3.0 – the reason the app is GPL-3.0.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Sichere DNS-Abfrage (DNS über HTTPS) und Laden der Streams über denselben Client
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    // Aufnahmen auf SMB-Netzwerkfreigaben (TiviMate: Aufnahmeverzeichnis smb://…)
    implementation("org.codelibs:jcifs:2.1.39")
    // QR-Code auf der Startseite (HomeInfo.kt)
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Installiert Baseline-Profile auch bei Installation per APK (ohne Play Store)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    // Echtes org.json für Unit-Tests (im Android-Stub sind die Methoden leer)
    testImplementation("org.json:json:20240303")
}

# Hinweise für Claude

- **Sprache:** Antworte immer auf Deutsch – in Chat-Antworten, Zusammenfassungen und Rückfragen.
- Projekt: IPTV-XY, ein IPTV-Player für Android TV / Google TV / Fire TV (Kotlin, Jetpack Compose, Media3). Basiert auf IPVIO Stream (GPL-3.0).
- Quellcode: `app/src/main/java/com/ipvio/stream/` (der interne Paketname bleibt `com.ipvio.stream`, die App-ID ist `com.pelzixx.iptvxy`).
- Übersetzungen stehen in `Dict*.kt` (eine Zeile pro Text: de¦en¦tr¦bs¦hr¦sr¦es¦fr¦it). Neue Texte in allen 9 Sprachen eintragen.
- Tests: `./gradlew testDebugUnitTest`. Die APK baut die GitHub-Action (`.github/workflows/build.yml`); keine APKs ins Repo committen.

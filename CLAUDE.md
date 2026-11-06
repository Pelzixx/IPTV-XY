# Hinweise für Claude

- **Sprache:** Antworte immer auf Deutsch – in Chat-Antworten, Zusammenfassungen und Rückfragen.
- Projekt: **XY IPTV**, IPTV-Player für Android TV / Google TV / Fire TV. Grundlage ist **OpenTV**
  (https://github.com/opentvproject/opentv, GPL-3.0), Stand Commit 4438119 (v0.11.10). Die frühere eigene App
  (basierend auf IPVIO Stream) liegt im Branch `legacy-ipvio`.
- Quellcode: `app/src/main/java/app/opentv/` (Paketname bleibt `app.opentv`, App-ID ist `com.pelzixx.iptvxy`).
- Texte: `app/src/main/res/values*/strings.xml` (Deutsch in `values-de`). Neue Texte mindestens in `values` und `values-de`.
- Releases: Workflow „Release“ (Versionsnummer eingeben) baut die signierte APK und veröffentlicht sie im öffentlichen
  Repo `Pelzixx/IPTV-XY-Releases` (`iptv-xy.apk`, `update.json`). Die App sucht dort nach Updates.
- Secrets: KEYSTORE_BASE64, KEYSTORE_PASSWORD, RELEASES_TOKEN, PRESET_SERVERS, PRESET_EPG, PRESET_CONFIG_URL.
- Keine APKs ins Repo committen.

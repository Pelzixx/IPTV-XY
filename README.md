# IPTV-XY

IPTV-Player für **Android TV, Google TV und Amazon Fire TV**, gebaut mit Kotlin, Jetpack Compose und Media3 und ganz auf die Fernbedienung ausgelegt.

![Startseite](docs/screenshots/startseite.jpg)

> **IPTV-XY liefert keine Inhalte.** Die App enthält keine Sender, Playlists, Filme oder Serien. Sie spielt nur Playlists ab, die du selbst hinzufügst (Xtream-Codes-Zugang, M3U-Adresse oder Stalker-Portal). Nutze nur Quellen, die du rechtmäßig nutzen darfst.

## Grundlage

IPTV-XY basiert auf **[IPVIO Stream](https://github.com/Emrodev/IPVIO-Stream)** von Emrodev und wird unter der GPL-3.0 mit eigenem Namen und Logo weiterentwickelt. Oberfläche und Funktionen wurden seitdem weitgehend neu gebaut.

## Installation auf Fire TV / Android TV

**Downloader-Code: `6420457`** (oder http://aftv.news/6420457)

1. Die kostenlose App **Downloader** (von AFTVnews) aus dem Amazon Appstore oder Google Play installieren.
2. Downloader das Installieren erlauben: Fire TV *Einstellungen > Mein Fire TV > Entwickleroptionen > Unbekannte Apps installieren > Downloader: An*.
3. Downloader öffnen, den Code oben (oder die Adresse unten) eingeben, *Go* drücken und die APK installieren.

Direkter Link zur neuesten Version: https://github.com/Pelzixx/IPTV-XY-Releases/releases/latest/download/iptv-xy.apk

**Updates:** in der App unter *Einstellungen > Über*.

## Funktionen

**Playlists**
- Xtream Codes, M3U-Adresse, lokale M3U-Datei oder Stalker-Portal; mehrere Playlists; Eingabe bequem über das Handy im WLAN
- Sehr große Listen: Sprachfilter beim Laden, fortsetzbarer Download, Aktualisierung im Hintergrund
- Gruppen und Kategorien in der Reihenfolge des Anbieters, eigene Gruppen, Sender und Gruppen ausblenden oder sperren

**TV**
- Gruppenliste, Senderliste und TV-Guide auf einer Seite, oben läuft die Vorschau mit Ton
- Nahtloser Übergang von der Vorschau ins Vollbild, ohne Neuladen
- Beim Umschalten eine kleine Info mit Sendung, Auflösung (HD/FHD/4K), Bildrate und Tonformat
- Archiv/Catch-up bei Sendern, die es anbieten: im Guide zurückgehen und alte Sendungen mit Spulen abspielen
- Favoriten, Erinnerungen, Aufnahmen (auch auf ein NAS per SMB), Multi-View, Bild-in-Bild

**Sport Guide**
- Spiele aus dem TV-Programm: Bundesliga, 2. und 3. Liga, DFB-Pokal, Europapokal, Länderspiele, Formel 1 u. a.
- Live-Spielstände und Spielminute, Sender zum Spiel (beste Qualität zuerst), Erinnerung für kommende Spiele

**Filme und Serien**
- Kategorien in der Reihenfolge des Anbieters, Sortierung nach Name, Bewertung, Jahr oder „zuletzt hinzugefügt“
- Detailseite mit Fortsetzen, Trailer, Meine Liste; Staffeln und Folgen übersichtlich
- Meine Liste und Verlauf

**Player** (Media3 / ExoPlayer)
- Ton- und Untertitelwahl, FFmpeg-Tondecoder für AC3/E-AC3/DTS
- Bildratenanpassung (AFR), Seitenverhältnis/Zoom, Audioversatz, Schlummertimer
- Der Stream stoppt immer, wenn die App verlassen wird

**Einstellungen**
- Aufbau wie bei bekannten TV-Playern: Allgemein, Wiedergabelisten, EPG, Optik, Wiedergabe, Fernbedienung, Kindersicherung, Sonstiges, Über
- Menü frei sortierbar, Farbthemen, Schriftgröße, Kindersicherung mit PIN, Sicherung und Wiederherstellung

## Screenshots

Alle Bilder stammen aus einer Test-Installation mit frei verfügbaren Test-Sendern.

| | |
|---|---|
| ![Startseite](docs/screenshots/startseite.jpg)<br>**Startseite** | ![TV](docs/screenshots/tv-gruppen.jpg)<br>**TV** – Gruppen, Sender, Guide und Vorschau |
| ![TV-Guide](docs/screenshots/tv-guide.jpg)<br>**TV-Guide** – Zeitleiste mit Vorschau | ![Sport Guide](docs/screenshots/sport-guide.jpg)<br>**Sport Guide** – Spiele, Spielstände, Sender |
| ![Einstellungen](docs/screenshots/einstellungen.jpg)<br>**Einstellungen** | |

## Eigene API-Schlüssel (optional)

Die App kommt **ohne API-Schlüssel**. Eigene kostenlose Schlüssel lassen sich in den **Einstellungen** eintragen:

| Dienst | Wofür | Schlüssel |
|---|---|---|
| TMDB | Poster, Beschreibungen, Besetzung, Trailer, Genres | kostenlos |
| OMDb | IMDb- und Rotten-Tomatoes-Bewertungen | kostenlos |
| TheIntroDB | Intro-, Rückblick- und Abspannzeiten | nicht nötig |

Ohne Schlüssel funktioniert die App trotzdem; bei Xtream-Playlists kommen Beschreibung, Cover und Bewertung direkt vom Anbieter. Schlüssel werden nur auf dem Gerät gespeichert.

## Quellen und Danksagungen

- **[IPVIO Stream](https://github.com/Emrodev/IPVIO-Stream)** (Emrodev, GPL-3.0): Ausgangsbasis des Quellcodes.
- **[OpenLigaDB](https://www.openligadb.de/)**: Spielstände im Sport Guide. Die Daten stehen unter der [Open Database License (ODbL 1.0)](https://opendatacommons.org/licenses/odbl/1-0/); in der App wird die Quelle genannt.
- **[StreamVault IPTV](https://github.com/Davidona/StreamVault-IPTV)**: Vorbild für die Idee des Sport Guides. Es wurde kein Code übernommen.
- **TiviMate**: Vorbild für Bedienung und Aufbau mancher Bildschirme. Es wurde kein Code, keine Grafik und kein Logo übernommen; IPTV-XY steht in keiner Verbindung zu TiviMate.
- **TMDB, OMDb, TheIntroDB**: optionale Online-Dienste, siehe oben. *Dieses Produkt nutzt die TMDB-API, ist aber nicht von TMDB unterstützt oder zertifiziert.*
- Bibliotheken und Schriften: siehe [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Selbst bauen

Voraussetzung: Android Studio mit Android SDK; alles Weitere lädt der Gradle-Wrapper.

```
git clone https://github.com/Pelzixx/IPTV-XY.git
cd IPTV-XY
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

Die APK liegt danach in `app/build/outputs/apk/debug/`. Tests der reinen Logik: `./gradlew testDebugUnitTest`

## Lizenz

Copyright (C) 2026 IPVIO Stream contributors
Copyright (C) 2026 IPTV-XY contributors

IPTV-XY ist freie Software unter der **GNU General Public License Version 3** (siehe [LICENSE](LICENSE)). Die GPL ist auch deshalb nötig, weil die App den GPL-3.0-lizenzierten FFmpeg-Tondecoder enthält. Verwendete Bibliotheken, Schriften und Dienste stehen in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Der Name „IPVIO“ und das IPVIO-Logo kennzeichnen das Originalprojekt; IPTV-XY verwendet deshalb einen eigenen Namen und ein eigenes Logo. Wer eine veränderte Fassung veröffentlicht, sollte es ebenso halten.

IMDb, Rotten Tomatoes, TMDB, TiviMate, Android TV, Google TV und Fire TV sind Marken ihrer jeweiligen Inhaber. IPTV-XY steht mit ihnen in keiner Verbindung.

## Haftungsausschluss

Die Software wird ohne jede Gewährleistung bereitgestellt. Die Autoren stellen keine Streams oder Playlists bereit, hosten oder verkaufen sie nicht und sind nicht verantwortlich dafür, wie die App genutzt wird. Playlists ohne Erlaubnis der Rechteinhaber zu nutzen kann in deinem Land verboten sein.

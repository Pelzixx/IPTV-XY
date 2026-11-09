# Schnittstelle App ↔ Panel (XY TV und Township TV)

Stand 2026-11-09. Beide Panels liefern dieselbe Schnittstelle; die App unterscheidet nur über die Konfigurationsadresse
(GitHub-Secret `PRESET_CONFIG_URL` bzw. `TOWNSHIP_CONFIG_URL`, jeweils mit `?key=…`).

## 1. Konfiguration (`config.php?key=…`, GET, JSON)

```json
{
  "servers": [{"name": "XY TV", "url": "http://host:8080", "epg": ["https://…/epg.xml.gz"]}],
  "epg": ["https://…/epg.xml.gz"],
  "news": {"title": "Willkommen", "text": "Hinweis …", "qr": "https://…", "qrLabel": "Support", "image": "https://…/bild.jpg"},
  "diag": "https://panel/app/diag.php?key=…",
  "backup": "https://panel/app/backup.php?key=…"
}
```

Neu sind `news`, `diag` und `backup` (alle optional). Fehlt `news`, zeigt die Startseite keine Mitteilung.
Leere Felder in `news` weglassen oder `""`.

## 2. Diagnose (`diag`, POST, `Content-Type: application/json`)

Die App sendet nur auf Knopfdruck (Einstellungen → Über → „Diagnose senden“):

```json
{"edition": "xy", "app": "3.51", "versionCode": 173, "device": "Amazon AFTKA", "android": "11 (API 30)",
 "modes": ["1920x1080@60", "1920x1080@50"], "fps": 25, "afr": "Anzeigemodus wechseln",
 "lastError": "…", "user": "xtreamname", "server": "XY TV", "note": "Freitext vom Nutzer", "ts": 1762700000}
```

Keine Passwörter, keine Stream-Adressen. Antwort: `{"ok": true, "id": "A1B2"}` (kurze ID, die App zeigt sie an,
damit der Nutzer sie dem Betreiber nennen kann). Panel: speichern (z. B. letzte 2000, je max. 64 KB), Admin-Seite
„Diagnosen“ neueste zuerst, Suche nach `user` und `id`.

## 3. Sicherung (`backup`, POST, JSON)

```json
{"action": "save", "server": "http://host:8080", "user": "xtreamname", "pass": "…", "data": "<Text, max. 2 MB>"}
{"action": "load", "server": "…", "user": "…", "pass": "…"}
{"action": "info", "server": "…", "user": "…", "pass": "…"}
```

Ablauf im Panel:
1. `server` muss einer der Server aus der eigenen Konfiguration sein (sonst 400) – keine beliebigen Adressen abfragen.
2. Zugang prüfen: `GET {server}/player_api.php?username=…&password=…` (Timeout 8 s), nur bei `user_info.auth == 1` weiter, sonst 401.
3. Schlüssel: `sha256(lower(server) + "|" + user)`. Das Passwort wird **nicht** gespeichert.
4. `save`: Daten + Zeitpunkt speichern → `{"ok": true, "ts": …}`; über 2 MB → 413.
   `load` → `{"ok": true, "data": "…", "ts": …}` oder `{"ok": false, "error": "none"}`.
   `info` → `{"ok": true, "ts": …}` oder `{"ok": false, "error": "none"}`.
5. Begrenzung je IP (z. B. 30 Anfragen/Stunde), nur HTTPS.

Admin-Seite „Sicherungen“: Liste mit Benutzername, Server, Zeitpunkt, Größe; Löschen möglich (keine Inhalte anzeigen nötig).

## 4. Element-Name und Nachrichten (`member`, POST, JSON)

Neu in der Konfiguration (beide optional):

```json
{"member": "https://panel/app/member.php?key=…", "memberFrom": "2026-11-20"}
```

Ist `member` gesetzt, fragt die App nach dem Element-Benutzernamen des Nutzers (z. B. `vojadsvv_xy`).
Bis `memberFrom` (fehlt es: 7 Tage nach dem ersten Hinweis auf dem Gerät) nur als Hinweis mit „Später“ (einmal am Tag),
danach ist die Eingabe Pflicht – ohne bestätigten Namen lässt sich die App nicht mehr nutzen.
Die App schickt den Namen so: ohne `@`, ohne `:server`, klein geschrieben.

Jede Anfrage enthält zusätzlich `edition` (`xy`/`township`), `app` (Version), `device` (zufällige Geräte-ID der App)
und `model` (Gerätename).

```json
{"action": "check",    "name": "vojadsvv_xy", "user": "xtreamname"}
{"action": "messages", "name": "vojadsvv_xy"}
{"action": "read",     "name": "vojadsvv_xy", "ids": ["a1"]}
```

- `check`: Gibt es den Element-Nutzer im Panel? Ja → `{"ok": true, "name": "vojadsvv_xy"}`, sonst
  `{"ok": false, "error": "unknown"}` (oder HTTP 404). Die App prüft beim Eingeben und danach bei jedem Start;
  löscht der Betreiber den Nutzer, fragt die App wieder. Ist das Panel nicht erreichbar, bleibt der gespeicherte Name gültig.
  Panel merkt sich je Nutzer die Geräte (`device`, `model`, `app`, `edition`, zuletzt gesehen, `user` = Xtream-Name).
- `messages`: ungelesene und gelesene Nachrichten an diesen Nutzer (die App zeigt nur, was sie noch nicht kennt):
  `{"ok": true, "messages": [{"id": "a1", "title": "Hallo", "text": "…", "ts": 1762700000}]}`. Abfrage beim Start und alle 5 Minuten.
- `read`: Nutzer hat „OK“ gedrückt → im Panel als gelesen markieren (mit Zeitpunkt und Gerät). Antwort `{"ok": true}`.

Diagnose (`diag`) und Sicherung (`backup`) schicken zusätzlich `"member": "vojadsvv_xy"` (leer, wenn noch keiner bestätigt).
Panel: beim Nutzer einen Reiter „TV App“ mit Geräten, Diagnosen und Sicherung dieses Nutzers sowie einem Feld
„Nachricht schreiben“ (Titel optional, Text, Liste der gesendeten Nachrichten mit gelesen/ungelesen, löschen).
Begrenzung je IP wie bei den anderen Endpunkten.

## 5. Nutzung melden (`member`, action `seen`) und Pflicht-Update

Sobald sich jemand in der App angemeldet hat (mindestens ein Zugang auf einem Server aus der Konfiguration), meldet die App
die Nutzung an `member` – beim Start, nach jeder Änderung der Zugänge (neue Anmeldung, anderer aktiver Zugang,
Element-Name bestätigt, neue App-Version) und sonst höchstens alle 6 Stunden:

```json
{"action": "seen", "name": "vojadsvv_xy", "user": "xtreamname",
 "accounts": [{"server": "XY TV", "user": "xtreamname"}, {"server": "XY Backup", "user": "xtreamname2"}],
 "android": "11", "versionCode": 174,
 "edition": "xy", "app": "3.52", "device": "<zufällige Geräte-ID>", "model": "SEI Robotics SEI500"}
```

`name` ist leer, solange kein Element-Name bestätigt ist – trotzdem speichern. Kein Passwort, keine Server-Adresse.
Antwort `{"ok": true}`. Panel: Admin-Seite „App-Nutzer“ (alle Geräte, neueste Meldung zuerst): Gerät, Modell, App-Version,
Android, Element-Name, Xtream-Namen je Server, erste und letzte Meldung; Suche; Hinweis, wenn die App-Version älter als
die aktuelle ist. Beim Nutzer im Reiter „TV App“ dieselben Geräte (Zuordnung über Element-Name oder Xtream-Namen).

**Pflicht-Update:** In der Konfiguration `"forceUpdate": true|false` (fehlt es: `true`). Bei `true` muss in XY TV und
Township jede neue Version installiert werden, sonst ist die App gesperrt; bei `false` nur Hinweis mit „Später“.
Bitte im Admin als Schalter „Update ist Pflicht“ anbieten.

## 6. Strenge Prüfung: Element-Name gehört zum Xtream-Zugang (ab 3.53) und Testzugänge

Ab 3.53 schickt `check` zusätzlich den Zugang mit, sobald einer auf einem Server aus der Konfiguration angemeldet ist:

```json
{"action": "check", "name": "vojadsvv_xy", "server": "http://host:8080", "user": "xtreamname", "pass": "…"}
```

- **Neue Nutzer** geben Element-Name, Benutzername und Passwort zusammen auf der Anmeldeseite ein (auch per Handy);
  die App prüft zuerst `check` und lädt die Playlist nur bei `ok`.
- **Bestehende Nutzer** (Zugang schon vorhanden) werden wie bisher nach dem Element-Namen gefragt, mit Karenzzeit bis
  `memberFrom` bzw. 7 Tage. Die App prüft den gespeicherten Namen bei jedem Start erneut mit dem Zugang.

Ablauf im Panel bei `check` mit `pass`:
1. `server` muss einer der eigenen Server sein; Zugang prüfen wie bei der Sicherung (`player_api.php`, `auth == 1`),
   sonst `{"ok": false, "error": "auth"}`. Das Passwort wird nicht gespeichert.
2. **Testname** (im Admin einstellbar, z. B. `test`, Laufzeit einstellbar, Standard 7 Tage): Ist `name` der Testname,
   wird je Zugang (`server` + `user`) der erste Tag gespeichert. Innerhalb der Laufzeit `{"ok": true, "name": "test"}`,
   danach `{"ok": false, "error": "trial_expired"}`. Admin-Seite „Testzugänge“: Zugang, Start, Ende, verlängern, zurücksetzen.
3. **XY-Panel:** Den Element-Namen gibt es, und der Xtream-Name gehört zu diesem Kunden (hinterlegte Zugänge),
   sonst `{"ok": false, "error": "mismatch"}`; unbekannter Name `unknown`.
4. **Township-Panel:** Den Element-Namen gibt es; beim ersten erfolgreichen `check` wird er an diesen Zugang
   (`server` + `user`) gebunden. Kommt der Name später mit einem anderen Zugang: `{"ok": false, "error": "bound"}` und
   im Admin markieren; Knopf „Bindung lösen“ beim Element-Nutzer.
5. Ohne `pass` (App 3.52) wie bisher nur prüfen, ob es den Namen gibt.

Die App zeigt je `error` einen eigenen Text. Bei `mismatch`, `bound` und `trial_expired` sperrt sie sofort (kein „Später“),
bei `auth` bleibt der gespeicherte Name (Passwort vielleicht nur kurz falsch).

**Eigener Hinweistext:** Jede Ablehnung darf zusätzlich `"message": "…"` enthalten (max. 300 Zeichen, Deutsch).
Die App zeigt dann genau diesen Text statt ihres Standardtexts, z. B.
`{"ok": false, "error": "unknown", "message": "Diesen Element-Namen gibt es nicht. Bitte wende dich an deinen Verkäufer."}`.

**Testname:** nur `clown` (Groß-/Kleinschreibung egal, die App schickt klein).

## 7. Laufband aus einem Element-Raum (`ticker`, GET, JSON)

In der Konfiguration: `"ticker": "https://panel/…/ticker.php?key=…"`. Die App ruft die Adresse beim Start und alle
2 Minuten ab und zeigt die Meldungen als Laufband unten am Bildschirm (nicht im Vollbild-Player). Leere Liste = kein Band.

```json
{"ok": true, "items": [
  {"id": "$eventid", "text": "Server XY TV gestört, wir arbeiten dran", "level": "error", "ts": 1762700000},
  {"id": "$eventid2", "text": "Neue Sender im Sportpaket", "level": "info", "ts": 1762690000}]}
```

Panel: liest einen Element-Raum (Raum-ID im Admin einstellbar) über die Matrix-API mit einem Bot-/Admin-Zugang.
- Nur Nachrichten von berechtigten Absendern (Power-Level ≥ 50 im Raum oder Liste im Admin) – sonst könnte jeder
  Raummitglied ins Band schreiben.
- Höchstens die letzten 5 Textnachrichten der letzten 24 Stunden (Dauer im Admin einstellbar).
- `level: "error"`, wenn die Nachricht mit `!`, „Störung“ oder „Fehler“ beginnt (das `!` wird entfernt).
- Eine Nachricht „/clear“ bzw. „leeren“ beendet die Anzeige: nur Nachrichten danach zählen.
- Bearbeitete Nachrichten: neueste Fassung; gelöschte (redacted) nicht anzeigen. Formatierung entfernen, max. 400 Zeichen.
- Antwort zwischenspeichern (z. B. 60 s), damit nicht jede App die Matrix-API anfragt.
- Admin-Seite „Laufband“: Vorschau der aktuellen Meldungen, Raum-ID, Dauer, Knopf „Band leeren“.

## 8. Kacheln auf der Startseite: für alle und für einen Nutzer

- **Für alle:** `news` in der Konfiguration (Abschnitt 1), z. B. `{"title": "Willkommen", "text": "Willkommen bei der neuen TV App!"}`.
  Erscheint als breite Kachel neben der Laufzeit.
- **Nur für einen Nutzer:** `messages` (Abschnitt 4) liefert zusätzlich `"note": {"title": "…", "text": "…"}` für diesen
  Element-Nutzer (fehlt oder leer = keine Kachel). Erscheint als eigene Kachel neben der Laufzeit; Titel optional
  (sonst „Für dich“), Text max. 500 Zeichen. Die App holt sie beim Start und alle 5 Minuten (nur mit bestätigtem Element-Namen).
  Panel: im Reiter „TV App“ beim Nutzer ein Feld „Kachel auf der Startseite“ (Titel, Text, leeren).

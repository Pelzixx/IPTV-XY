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

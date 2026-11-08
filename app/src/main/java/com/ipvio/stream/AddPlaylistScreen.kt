package com.ipvio.stream

import androidx.compose.ui.graphics.lerp
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddPlaylistScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Zuletzt eingegebene Playlist, die noch nicht fertig geladen wurde (Absturz, Stromausfall, Abbruch): vorausfüllen
    val pend = remember { PlaylistStore.pending(ctx) }
    val pendX = remember { pend?.let { LoadLogic.xtreamParts(it.url) } }
    var xtream by remember { mutableStateOf(pendX != null) }
    var url by remember { mutableStateOf(if (pend != null && pendX == null) pend.url else "") }
    var host by remember { mutableStateOf(pendX?.first ?: "") }
    var user by remember { mutableStateOf(pendX?.second ?: "") }
    var pass by remember { mutableStateOf(pendX?.third ?: "") }
    var showPass by remember { mutableStateOf(false) }
    // TiviMate: Stalker-Portal mit MAC-Adresse
    var stalker by remember { mutableStateOf(false) }
    var mac by remember { mutableStateOf("00:1A:79:") }
    var stSn by remember { mutableStateOf("") }
    var stDev by remember { mutableStateOf("") }
    var localFiles by remember { mutableStateOf<List<java.io.File>?>(null) }
    // Lokale M3U-Datei wählen; dauerhafte Leseberechtigung, damit „Aktualisieren“ die Datei neu liest
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur -> if (cur.moveToFirst()) cur.getString(0) else null }
            }.getOrNull()?.substringBeforeLast('.')
            if (!name.isNullOrBlank()) PlaylistStore.pendingName = name
            scope.launch { if (PlaylistStore.load(ctx, uri.toString(), true)) onDone() }
        }
    }
    // Fest eingebaute Server (Presets.kt): dann reichen Benutzername und Passwort. "Andere Quelle" öffnet die freie Eingabe.
    val servers = Presets.servers
    val pendIdx = remember { pendX?.let { PresetLogic.indexOf(it.first, servers) } ?: -1 }
    // Freie Eingabe nur, wenn gewählt oder (noch) keine Server da sind. Die Server aus dem Panel kommen beim ersten Start
    // erst nach dem Aufbau der Seite an (Township 3.43): dann von selbst zur Anmeldung wechseln.
    var manualPick by remember { mutableStateOf<Boolean?>(if (pend != null && (pendX == null || (pendIdx < 0 && servers.isNotEmpty()))) true else null) }
    val manual = manualPick ?: servers.isEmpty()
    var sel by remember { mutableStateOf(pendIdx.coerceAtLeast(0)) }
    // Vom Handy senden: lokaler Mini-Server, solange dieser Bildschirm offen ist
    val ip = remember { PhoneImport.localIp() }
    val receiver = remember {
        PhoneImport { inp ->
            Handler(Looper.getMainLooper()).post {
                // Ohne Server vom Handy: den gewählten fest eingebauten Server nehmen
                val h = if (inp.isXtream && inp.host.isBlank()) servers.getOrNull(sel)?.url.orEmpty() else inp.host
                val u = if (inp.isXtream) PlaylistStore.xtreamUrl(h, inp.user, inp.pass) else inp.url
                if (inp.isXtream) {
                    xtream = true; host = h; user = inp.user; pass = inp.pass
                    val i = PresetLogic.indexOf(h, servers)
                    if (i >= 0) { sel = i; manualPick = false } else manualPick = true
                } else { xtream = false; url = inp.url; manualPick = true }
                if (u == null) PlaylistStore.error = "Angaben unvollständig: Server, Benutzername und Passwort nötig."
                else scope.launch { if (PlaylistStore.load(ctx, u, true)) onDone() }
            }
        }
    }
    var serving by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        serving = receiver.start()
        onDispose { receiver.stop() }
    }
    val canCancel = PlaylistStore.lists.isNotEmpty() || PlaylistStore.channels.isNotEmpty()
    BackHandler(enabled = canCancel) { PlaylistStore.clearPending(ctx); onDone() }

    // Beim letzten Mal mitten im Laden unterbrochen: automatisch weitermachen, ohne etwas neu einzugeben
    LaunchedEffect(Unit) {
        val p = pend ?: return@LaunchedEffect
        when {
            p.autoResume && !PlaylistStore.loading -> { if (PlaylistStore.load(ctx, p.url, true, resumed = true)) onDone() }
            p.interrupted -> PlaylistStore.error = "Das Laden wurde mehrmals unterbrochen. Deine Angaben sind gespeichert – drücke „Hinzufügen“, um es erneut zu versuchen."
            p.msg != null -> PlaylistStore.error = p.msg
        }
    }

    if (PlaylistStore.loading) { PlaylistLoadingOverlay(); return }
    localFiles?.let { fl ->
        val dirs = LocalM3u.dirs(ctx)
        val sys = SideEntry(Lang.tr("System-Dateiauswahl öffnen …"), raw = true) { runCatching { pickFile.launch(arrayOf("*/*")) } }
        val entries = listOf(sys) + if (fl.isEmpty()) listOf(SideEntry(Lang.tr("Keine M3U-Datei gefunden. Kopiere sie in:") + " " + dirs.joinToString("  ·  ") { it.absolutePath }, raw = true) {})
            else fl.map { f -> SideEntry(f.name + "  ·  " + f.parentFile?.absolutePath.orEmpty(), raw = true) {
                PlaylistStore.pendingName = f.nameWithoutExtension
                scope.launch { if (PlaylistStore.load(ctx, android.net.Uri.fromFile(f).toString(), true)) onDone() }
            } }
        SideMenu(listOf(SideSection(Lang.tr("M3U-Datei wählen"), entries)), { localFiles = null })
    }

    // Start-Fokus auf einen Reiter/Knopf, nicht auf ein Eingabefeld (das würde sofort die Bildschirmtastatur öffnen)
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(manual) { delay(120); runCatching { startFocus.requestFocus() } }

    // Die Bildschirmtastatur soll die Seite nicht hochschieben: alles steht oben und bleibt sichtbar
    DisposableEffect(Unit) {
        val w = (ctx as? android.app.Activity)?.window
        val old = w?.attributes?.softInputMode
        w?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose { if (w != null && old != null) w.setSoftInputMode(old) }
    }

    // Aufbau (Wunsch des Nutzers, 2026-11-08, komplett neu): links schlicht das Formular ohne Kasten, oben Logo und Titel,
    // darunter Reiter „Vorgegeben“ / „Selbst eingeben“ (nur mit fest hinterlegten Servern), kleine Knöpfe.
    // Rechts eine schmale Karte für die Eingabe am Handy.
    Row(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(lerp(Bg, Accent1, 0.10f), Bg), radius = 1600f))
            .padding(start = 64.dp, end = 48.dp, top = 40.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            // Innenabstand im Scrollbereich: der fokussierte Knopf wird leicht vergrößert und darf nicht abgeschnitten werden
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(4.dp)
        ) {
            BrandMark(Modifier.height(44.dp).widthIn(max = 130.dp))
            Spacer(Modifier.height(18.dp))
            Text(if (!manual) "Anmelden" else "Playlist hinzufügen", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            if (servers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoginTab(BuildConfig.PROVIDER.ifBlank { "Vorgegeben" }, !manual, if (!manual) Modifier.focusRequester(startFocus) else Modifier) {
                        manualPick = false; PlaylistStore.error = null
                    }
                    LoginTab("Selbst eingeben", manual, if (manual) Modifier.focusRequester(startFocus) else Modifier) {
                        if (!manual) { manualPick = true; xtream = true; host = ""; PlaylistStore.error = null }
                    }
                }
                Box(Modifier.widthIn(max = FormWidth).fillMaxWidth().height(1.dp).background(Surf2))
                Spacer(Modifier.height(20.dp))
            }

            Column(Modifier.widthIn(max = FormWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!manual) {
                    if (servers.size > 1) {
                        FieldLabel("Server")
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            servers.forEachIndexed { i, s -> SmallButton(s.name, selected = i == sel) { sel = i } }
                        }
                    }
                    UserPassFields(user, { user = it }, pass, { pass = it }, showPass, { showPass = !showPass })
                    PlaylistStore.error?.let { ErrorText(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        SmallButton("Anmelden", primary = true) {
                            if (PlaylistStore.loading) return@SmallButton
                            val finalUrl = PlaylistStore.xtreamUrl(servers[sel].url, user, pass)
                            if (finalUrl == null) PlaylistStore.error = "Angaben unvollständig: Benutzername und Passwort nötig."
                            else { host = servers[sel].url; PlaylistStore.pendingName = servers[sel].name; scope.launch { if (PlaylistStore.load(ctx, finalUrl, true)) onDone() } }
                        }
                        if (canCancel) SmallButton("Abbrechen") { PlaylistStore.clearPending(ctx); onDone() }
                    }
                } else {
                    FieldLabel("Art der Playlist")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("M3U-Link", if (servers.isEmpty()) Modifier.focusRequester(startFocus) else Modifier, selected = !xtream && !stalker) { xtream = false; stalker = false }
                        SmallButton("Xtream-Login", selected = xtream) { xtream = true; stalker = false }
                        SmallButton("Stalker-Portal", selected = stalker) { stalker = true; xtream = false }
                        // TiviMate: Playlist aus einer lokalen Datei; viele TV-Geräte haben keine Dateiauswahl → eigene Liste
                        SmallButton("Datei") { localFiles = LocalM3u.find(ctx) }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (stalker) {
                        SmallField(host, { host = it }, "Portal (z. B. http://portal.tld:8080/c/)")
                        SmallField(mac, { mac = it }, "MAC-Adresse (00:1A:79:XX:XX:XX)")
                        SmallField(stSn, { stSn = it }, "Seriennummer (optional)")
                        SmallField(stDev, { stDev = it }, "Geräte-ID (optional)")
                    } else if (!xtream) {
                        SmallField(url, { url = it }, "http://…/playlist.m3u")
                    } else {
                        SmallField(host, { host = it }, "Server (z. B. http://server.tld:8080)")
                        UserPassFields(user, { user = it }, pass, { pass = it }, showPass, { showPass = !showPass })
                    }
                    PlaylistStore.error?.let { ErrorText(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        SmallButton("Hinzufügen", primary = true) {
                            if (PlaylistStore.loading) return@SmallButton
                            val macOk = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(mac.trim())
                            val finalUrl = if (stalker) (if (host.isNotBlank() && macOk) Stalker.listUrl(host, mac, stSn, stDev) else null) else if (xtream) PlaylistStore.xtreamUrl(host, user, pass) else url
                            if (finalUrl == null) PlaylistStore.error = if (stalker) "Angaben unvollständig: Portal-Adresse und gültige MAC-Adresse nötig." else "Angaben unvollständig: Server, Benutzername und Passwort nötig."
                            else scope.launch { if (PlaylistStore.load(ctx, finalUrl, true)) onDone() }
                        }
                        if (!xtream && !stalker) SmallButton("Aus Zwischenablage einfügen") {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.primaryClip?.getItemAt(0)?.text?.let { url = it.toString() }
                        }
                        if (canCancel) SmallButton("Abbrechen") { PlaylistStore.clearPending(ctx); onDone() }
                    }
                }
            }
        }
        // Rechts: Anleitung für die Eingabe am Handy
        PhoneCard(Modifier.width(400.dp), if (serving && ip != null) "http://" + ip + ":" + receiver.port else null, receiver.code)
    }
}

private val FormWidth = 520.dp

/** Schmale Karte: Zugangsdaten bequem am Handy eingeben statt mit der Fernbedienung. */
@Composable
private fun PhoneCard(modifier: Modifier, address: String?, code: String) {
    val shape = RoundedCornerShape(DS.RadiusL)
    Column(
        modifier.clip(shape).background(Surf).border(1.dp, Surf2, shape).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Einfacher mit dem Handy", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (address == null) {
            Text(
                "Die Übertragung vom Handy ist gerade nicht verfügbar. Das Gerät muss mit dem WLAN verbunden sein.",
                color = Color(0xFFFFB86B), fontSize = 15.sp, lineHeight = 21.sp
            )
        } else {
            Step(1, "Handy mit demselben WLAN verbinden wie dieses Gerät.")
            Step(2, "Im Browser des Handys diese Adresse öffnen:")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(address, color = Accent2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Step(3, "Den Code von hier eingeben:")
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Bg).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(code, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            }
            Step(4, "Zugangsdaten eintragen und senden. Die Playlist wird danach automatisch geladen.")
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(Surf2), contentAlignment = Alignment.Center) {
            Text(n.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** Reiter mit Unterstrich (ausgewählt) – fokussiert hell hinterlegt wie überall in der App. */
@Composable
private fun LoginTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Column(modifier.onFocusChanged { f = it.isFocused }.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (f) DS.FocusFill else Color.Transparent).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text, color = if (f) DS.FocusInk else if (selected) Color.White else Muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Accent1 else Color.Transparent))
    }
}

/** Kleiner Knopf: primär in der Akzentfarbe, ausgewählt leicht getönt, fokussiert weiß. */
@Composable
private fun SmallButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        f -> DS.FocusFill
        primary -> Accent1
        selected -> lerp(Surf2, Accent1, 0.35f)
        else -> Surf
    }
    Box(
        modifier.clip(shape).background(bg)
            .border(1.dp, if (f || primary) Color.Transparent else if (selected) Accent1 else Surf2, shape)
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (f) DS.FocusInk else if (primary) OnAccent else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun FieldLabel(text: String) { Text(text, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium) }

@Composable
private fun ErrorText(text: String) { Text(text, color = DS.Danger, fontSize = 15.sp, lineHeight = 21.sp) }

/** Eingabefeld in normaler Höhe (das allgemeine Field ist für die Fernbedienung recht hoch). */
@Composable
private fun SmallField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, inner: Modifier = Modifier, password: Boolean = false, onDone: (() -> Unit)? = null) =
    Field(value, onChange, hint, modifier.height(52.dp), inner, password, onDone = onDone)

/**
 * Benutzername und Passwort untereinander, „Anzeigen“ klein neben dem Passwort.
 * „Fertig“ auf der Tastatur beim Benutzernamen springt gleich ins Passwortfeld.
 */
@Composable
private fun UserPassFields(
    user: String, onUser: (String) -> Unit, pass: String, onPass: (String) -> Unit,
    showPass: Boolean, onTogglePass: () -> Unit
) {
    val passFocus = remember { FocusRequester() }
    SmallField(user, onUser, "Benutzername", onDone = { runCatching { passFocus.requestFocus() } })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallField(pass, onPass, "Passwort", modifier = Modifier.weight(1f), inner = Modifier.focusRequester(passFocus), password = !showPass)
        SmallButton(if (showPass) "Verbergen" else "Anzeigen") { onTogglePass() }
    }
}

/** M3U-Dateien ohne System-Dateiauswahl: App-Ordner auf dem Gerät und auf USB-Sticks/SD-Karten (ohne Zusatzrechte lesbar). */
object LocalM3u {
    fun dirs(c: Context): List<java.io.File> = c.getExternalFilesDirs(null).filterNotNull().map { java.io.File(it, "Playlists").also { d -> d.mkdirs() } }

    fun find(c: Context): List<java.io.File> = dirs(c).flatMap { d ->
        (d.listFiles()?.toList() ?: emptyList()) + (d.parentFile?.listFiles()?.toList() ?: emptyList())
    }.filter { it.isFile && (it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true)) }.distinctBy { it.absolutePath }
}

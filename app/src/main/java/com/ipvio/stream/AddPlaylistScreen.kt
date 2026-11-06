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
    // Fest eingebaute Server (Presets.kt): dann reichen Benutzername und Passwort. "Andere Quelle" öffnet die freie Eingabe.
    val servers = Presets.servers
    val pendIdx = remember { pendX?.let { PresetLogic.indexOf(it.first, servers) } ?: -1 }
    var manual by remember { mutableStateOf(servers.isEmpty() || (pend != null && pendIdx < 0)) }
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
                    if (i >= 0) { sel = i; manual = false } else manual = true
                } else { xtream = false; url = inp.url; manual = true }
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

    // Start-Fokus auf die Auswahl M3U/Xtream (nicht auf ein Eingabefeld: das würde sofort die Bildschirmtastatur öffnen)
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(manual) { delay(120); runCatching { startFocus.requestFocus() } }

    Row(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(lerp(Bg, Accent1, 0.14f), Bg), radius = 1400f))
            .padding(horizontal = 46.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Links: das Formular
        Column(
            // Innenabstand im Scrollbereich: der fokussierte Knopf wird vergrößert und darf nicht abgeschnitten werden
            Modifier.weight(1.1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Das Logo enthält den Namen schon
            BrandMark(Modifier.size(110.dp))
            if (!manual) {
                PresetLogin(
                    servers, sel, { sel = it }, user, { user = it }, pass, { pass = it }, showPass, { showPass = !showPass },
                    startFocus, canCancel,
                    onLogin = {
                        val finalUrl = PlaylistStore.xtreamUrl(servers[sel].url, user, pass)
                        if (finalUrl == null) PlaylistStore.error = "Angaben unvollständig: Benutzername und Passwort nötig."
                        else { host = servers[sel].url; scope.launch { if (PlaylistStore.load(ctx, finalUrl, true)) onDone() } }
                    },
                    onOther = { manual = true; xtream = true; host = ""; PlaylistStore.error = null },
                    onCancel = { PlaylistStore.clearPending(ctx); onDone() }
                )
            } else {
            Text("Playlist hinzufügen", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton("M3U-Link", Modifier.focusRequester(startFocus), primary = !xtream) { xtream = false }
                FocusButton("Xtream-Login", primary = xtream) { xtream = true }
            }
            if (!xtream) {
                Field(url, { url = it }, "http://…/playlist.m3u")
                FocusButton("Aus Zwischenablage einfügen") {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.text?.let { url = it.toString() }
                }
            } else {
                Field(host, { host = it }, "Server (z. B. http://server.tld:8080)")
                Field(user, { user = it }, "Benutzername")
                Field(pass, { pass = it }, "Passwort", password = !showPass)
                FocusButton(if (showPass) "Passwort verbergen" else "Passwort anzeigen") { showPass = !showPass }
            }
            PlaylistStore.error?.let { Text(it, color = DS.Danger, fontSize = 18.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton(if (PlaylistStore.loading) "Lade…" else "Hinzufügen", primary = true) {
                    if (PlaylistStore.loading) return@FocusButton
                    val finalUrl = if (xtream) PlaylistStore.xtreamUrl(host, user, pass) else url
                    if (finalUrl == null) PlaylistStore.error = "Angaben unvollständig: Server, Benutzername und Passwort nötig."
                    else scope.launch { if (PlaylistStore.load(ctx, finalUrl, true)) onDone() }
                }
                if (canCancel) FocusButton("Abbrechen") { PlaylistStore.clearPending(ctx); onDone() }
            }
            if (servers.isNotEmpty()) FocusButton("Zurück zur Anmeldung") { manual = false; PlaylistStore.error = null }
            }
        }
        // Rechts: Anleitung für die Eingabe am Handy
        PhoneCard(Modifier.weight(1f), if (serving && ip != null) "http://" + ip + ":" + receiver.port else null, receiver.code)
    }
}

/** Karte mit Anleitung: Playlist bequem am Handy eingeben statt mit der Fernbedienung. */
@Composable
private fun PhoneCard(modifier: Modifier, address: String?, code: String) {
    val shape = RoundedCornerShape(DS.RadiusXl)
    Column(
        modifier.clip(shape).background(Surf).border(2.dp, Surf2, shape).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Einfacher mit dem Handy", color = Accent2, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Mit der Fernbedienung zu tippen ist mühsam. Schick die Zugangsdaten lieber vom Handy:",
            color = Muted, fontSize = 16.sp, lineHeight = 22.sp
        )
        if (address == null) {
            Text(
                "Die Übertragung vom Handy ist gerade nicht verfügbar. Das Gerät muss mit dem WLAN verbunden sein.",
                color = Color(0xFFFFB86B), fontSize = 17.sp, lineHeight = 24.sp
            )
        } else {
            Step(1, "Handy mit demselben WLAN verbinden wie dieses Gerät.")
            Step(2, "Im Browser des Handys diese Adresse öffnen:")
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(DS.RadiusM)).background(Bg).padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(address, color = Accent2, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Step(3, "Den Code von hier eingeben:")
            Row(
                Modifier.clip(RoundedCornerShape(DS.RadiusM)).background(Bg).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Code", color = Muted, fontSize = 18.sp)
                Text(code, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            }
            Step(4, "Zugangsdaten eintragen und senden. Die Playlist wird danach automatisch geladen.")
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(Accent1),
            contentAlignment = Alignment.Center
        ) {
            Text(n.toString(), color = OnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = Color.White, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Anmeldung mit fest eingebautem Server: Server wählen (bei mehreren), Benutzername, Passwort. */
@Composable
private fun PresetLogin(
    servers: List<PresetServer>, sel: Int, onSel: (Int) -> Unit,
    user: String, onUser: (String) -> Unit, pass: String, onPass: (String) -> Unit,
    showPass: Boolean, onTogglePass: () -> Unit,
    startFocus: FocusRequester, canCancel: Boolean,
    onLogin: () -> Unit, onOther: () -> Unit, onCancel: () -> Unit
) {
    Text("Anmelden", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    // Start-Fokus auf einen Knopf, nicht auf ein Eingabefeld (das würde sofort die Bildschirmtastatur öffnen)
    if (servers.size > 1) {
        Text("Server wählen", color = Muted, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            servers.forEachIndexed { i, s ->
                FocusButton(s.name, if (i == sel) Modifier.focusRequester(startFocus) else Modifier, primary = i == sel) { onSel(i) }
            }
        }
    }
    Field(user, onUser, "Benutzername")
    Field(pass, onPass, "Passwort", password = !showPass)
    FocusButton(if (showPass) "Passwort verbergen" else "Passwort anzeigen") { onTogglePass() }
    PlaylistStore.error?.let { Text(it, color = DS.Danger, fontSize = 18.sp) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusButton("Anmelden", if (servers.size > 1) Modifier else Modifier.focusRequester(startFocus), primary = true) {
            if (!PlaylistStore.loading) onLogin()
        }
        FocusButton("Andere Quelle") { onOther() }
        if (canCancel) FocusButton("Abbrechen") { onCancel() }
    }
}

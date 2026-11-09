package com.ipvio.stream

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hinweis auf eine neue Version (Wunsch des Nutzers, 2026-11-08): kurz nach dem Start und danach alle 6 Stunden,
 * solange die App offen ist. Erscheint nicht im Player und nicht beim Hinzufügen einer Playlist, sondern erst danach.
 * „Später“ blendet die Version bis zum nächsten App-Start aus.
 */
object UpdateNotice {
    /** Gefundene neue Version, die noch angezeigt werden soll (null = nichts). */
    var pending by mutableStateOf<UpdateInfo?>(null)
    /** Mit „Später“ weggeklickte Version (gilt bis zum nächsten App-Start). */
    private var dismissed = 0L

    suspend fun watch(c: Context) {
        // Test-Apps (…debug) haben eine eigene App-ID und einen anderen Schlüssel: das Update wäre eine ganz andere App
        // (Ausnahme zum Testen der Anzeige: Test-App mit Versionsname „…-updtest“)
        if (BuildConfig.APPLICATION_ID.endsWith(".debug") && !BuildConfig.VERSION_NAME.endsWith("-updtest")) return
        delay(8_000)
        while (true) {
            AppUpdate.check(c).getOrNull()?.let { if (it.code != dismissed) pending = it }
            delay(6 * 60 * 60 * 1000L)
        }
    }

    fun later() { if (forced) return; pending?.let { dismissed = it.code }; pending = null }

    /** XY TV und Township: neue Versionen sind Pflicht (siehe UpdateGate). Die Original-App zeigt nur den Hinweis. */
    val forced: Boolean get() = BuildConfig.EDITION != "standard" && Presets.forceUpdate
}

/** Karte oben in der Mitte: „Neue Version 3.46 verfügbar“, Neuerungen, „Jetzt aktualisieren“ / „Später“. */
@Composable
fun UpdateCard(info: UpdateInfo, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }
    BackHandler { if (!busy) UpdateNotice.later() }
    LaunchedEffect(info.code) { delay(150); runCatching { fr.requestFocus() } }
    fun install(f: java.io.File) {
        if (AppUpdate.install(ctx, f)) { status = Lang.tr("Installation gestartet"); return }
        // Freigabe „Apps installieren“ ist offen: sobald erteilt, geht es von selbst weiter
        status = Lang.tr("Erlaube der App einmal „Apps installieren“. Danach startet die Installation von selbst.")
        scope.launch { if (AppUpdate.installWhenAllowed(ctx, f)) status = Lang.tr("Installation gestartet") }
    }
    val shape = RoundedCornerShape(DS.RadiusL)
    Row(
        modifier.widthIn(max = 820.dp).clip(shape).background(Tok.GlassStrong).border(1.dp, Accent2, shape).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppIcon(AIcon.Download, Accent2, size = 30.dp)
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(raw = true, text = Lang.tr("Neue Version") + " " + info.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            val sub = status.ifBlank { info.notes }
            if (sub.isNotBlank()) Text(raw = true, text = sub, color = if (status.isBlank()) Muted else Accent2, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PillButton("Jetzt aktualisieren", Modifier.focusRequester(fr), light = true) {
            if (busy) return@PillButton
            apk?.let { install(it); return@PillButton }
            busy = true
            scope.launch {
                AppUpdate.download(ctx, info) { p -> status = if (p >= 0) Lang.tr("Lade …") + " $p %" else Lang.tr("Lade …") }
                    .onSuccess { f -> apk = f; install(f) }
                    .onFailure { status = it.message ?: Lang.tr("Fehler") }
                busy = false
            }
        }
        PillButton("Später") { if (!busy) UpdateNotice.later() }
    }
}

/**
 * Pflicht-Update (Wunsch des Nutzers, 2026-11-09): Bei XY TV und Township muss eine neue Version installiert werden,
 * sonst ist die App gesperrt. Fenster über allem, nur „Jetzt aktualisieren“ oder „App beenden“.
 * Ist GitHub nicht erreichbar, wird nicht gesperrt (es gibt dann keine bekannte neue Version).
 */
@Composable
fun UpdateGate(info: UpdateInfo) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }
    fun quit() { (ctx as? android.app.Activity)?.finish() }
    fun install(f: java.io.File) {
        if (AppUpdate.install(ctx, f)) { status = Lang.tr("Installation gestartet"); return }
        status = Lang.tr("Erlaube der App einmal „Apps installieren“. Danach startet die Installation von selbst.")
        scope.launch { if (AppUpdate.installWhenAllowed(ctx, f)) status = Lang.tr("Installation gestartet") }
    }
    fun start() {
        if (busy) return
        apk?.let { install(it); return }
        busy = true
        scope.launch {
            AppUpdate.download(ctx, info) { p -> status = if (p >= 0) Lang.tr("Lade …") + " $p %" else Lang.tr("Lade …") }
                .onSuccess { f -> apk = f; install(f) }
                .onFailure { status = Lang.tr("Fehler") + ": " + (it.message ?: "") }
            busy = false
        }
    }
    LaunchedEffect(info.code) { delay(150); runCatching { fr.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = { quit() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BackHandler { quit() }
        Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(DS.RadiusL)
            Column(
                Modifier.widthIn(max = 720.dp).clip(shape).background(Surf).border(1.dp, Accent2, shape).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(AIcon.Download, Accent2, size = 30.dp)
                    Text(raw = true, text = Lang.tr("Update erforderlich") + ": " + info.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text("Eine neue Version ist da. Bitte jetzt installieren – ohne Update lässt sich die App nicht weiter nutzen. Deine Listen und Einstellungen bleiben erhalten.",
                    color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
                if (info.notes.isNotBlank()) Text(raw = true, text = info.notes, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                if (status.isNotBlank()) Text(raw = true, text = status, color = Accent2, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Jetzt aktualisieren", Modifier.focusRequester(fr), light = true) { start() }
                    PillButton("App beenden") { quit() }
                }
            }
        }
    }
}

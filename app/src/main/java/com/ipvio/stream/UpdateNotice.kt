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
        if (BuildConfig.APPLICATION_ID.endsWith(".debug")) return
        delay(8_000)
        while (true) {
            AppUpdate.check(c).getOrNull()?.let { if (it.code != dismissed) pending = it }
            delay(6 * 60 * 60 * 1000L)
        }
    }

    fun later() { pending?.let { dismissed = it.code }; pending = null }
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
        status = Lang.tr(if (AppUpdate.install(ctx, f)) "Installation gestartet" else "Erlaube der App einmal „Apps installieren“ und tippe dann erneut.")
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

package com.ipvio.stream

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Programmbeschreibung (Menü im TV-Guide). */
@Composable
fun ProgDescDialog(item: Item, e: EpgEntry, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { delay(50); runCatching { fr.requestFocus() } }
        Column(
            Modifier.widthIn(max = 900.dp).clip(RoundedCornerShape(DS.RadiusL)).background(Surf).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(raw = true, text = e.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(raw = true, text = ChannelEdit.title(item) + "  ·  " + dayLabel(e.start) + " " + hhmm(e.start) + " — " + hhmm(e.end) +
                "  ·  " + ((e.end - e.start) / 60) + " Min", color = Muted, fontSize = 16.sp)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(raw = true, text = e.desc.ifBlank { Lang.tr("Keine Beschreibung vorhanden.") }, color = Tok.TextMid, fontSize = 18.sp, lineHeight = 26.sp)
            }
            Row(Modifier.padding(top = 8.dp)) { PillButton("Schließen", Modifier.focusRequester(fr), light = true) { onClose() } }
        }
    }
}

/** Benutzerdefinierte Aufnahme (TiviMate): Startdatum, Startzeit, Dauer, Wiederholung. */
@Composable
fun CustomRecDialog(item: Item, prog: EpgEntry?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis() / 1000
    val start0 = prog?.start?.coerceAtLeast(now) ?: (now / 300 * 300 + 300)
    val cal = remember { java.util.Calendar.getInstance().apply { timeInMillis = start0 * 1000 } }
    val today = remember { java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) } }
    var dayOff by remember { mutableIntStateOf(((start0 * 1000 - today.timeInMillis) / 86_400_000L).toInt().coerceIn(0, 13)) }
    var hour by remember { mutableIntStateOf(cal.get(java.util.Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(cal.get(java.util.Calendar.MINUTE) / 5 * 5) }
    val durs = listOf(15, 30, 45, 60, 90, 120, 150, 180, 240, 300, 360)
    val d0 = prog?.let { ((it.end - start0) / 60).toInt() } ?: 60
    var dur by remember { mutableIntStateOf(durs.minByOrNull { kotlin.math.abs(it - d0) } ?: 60) }
    var rep by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { delay(50); runCatching { fr.requestFocus() } }
        Column(
            Modifier.width(720.dp).clip(RoundedCornerShape(DS.RadiusL)).background(Surf).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Benutzerdefinierte Aufnahme", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(raw = true, text = ChannelEdit.title(item), color = Accent2, fontSize = 17.sp, modifier = Modifier.padding(bottom = 8.dp))
            val days = (0..13).map { off ->
                val c = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, off) }
                java.text.SimpleDateFormat("EE, dd.MM.", Lang.locale()).format(c.time)
            }
            Box(Modifier.focusRequester(fr)) { SelectRow("Startdatum", days[dayOff], days, dayOff) { dayOff = it } }
            val hours = (0..23).map { String.format("%02d", it) }
            SelectRow("Startzeit (Stunde)", hours[hour], hours, hour) { hour = it }
            val mins = (0..55 step 5).map { String.format("%02d", it) }
            SelectRow("Startzeit (Minute)", String.format("%02d", minute), mins, minute / 5) { minute = it * 5 }
            SelectRow("Dauer (Minuten)", dur.toString(), durs.map { it.toString() }, durs.indexOf(dur).coerceAtLeast(0)) { dur = durs[it] }
            val reps = listOf(Lang.tr("Nie"), Lang.tr("Täglich"), Lang.tr("Wöchentlich"))
            SelectRow("Wiederholen", reps[rep], reps, rep) { rep = it }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Speichern", light = true) {
                    val c = (today.clone() as java.util.Calendar).apply {
                        add(java.util.Calendar.DAY_OF_YEAR, dayOff); set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute)
                    }
                    val s = c.timeInMillis / 1000
                    val title = prog?.takeIf { it.start == s }?.title ?: ChannelEdit.title(item)
                    Recorder.add(ctx, item, title, s, s + dur * 60L, rep)
                    Toast.makeText(ctx, Lang.tr("Aufnahme geplant"), Toast.LENGTH_SHORT).show()
                    onClose()
                }
                PillButton("Abbrechen") { onClose() }
            }
        }
    }
}

private fun sizeText(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.1f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format("%.0f MB", b / 1048576.0)
    b > 0 -> String.format("%.0f KB", b / 1024.0)
    else -> ""
}

/** Bereich „Aufnahmen“ in der Seitenleiste (TiviMate): laufende, geplante und fertige Aufnahmen. */
@Composable
fun RecordingsScreen(tab: Int, onTab: (Int) -> Unit, onPlay: (Rec) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Recorder.init(ctx); Recorder.schedule(ctx) }
    SideEffect { NavState.tab = tab; ContentFocus.tab = tab }
    // Größe laufender Aufnahmen regelmäßig neu anzeigen
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val all = remember(Recorder.version, Recorder.list.size, tick) { Recorder.list.toList() }
    val running = all.filter { it.state == Rec.RUNNING }
    val planned = all.filter { it.state == Rec.PLANNED }.sortedBy { it.start }
    val done = all.filter { it.state == Rec.DONE || it.state == Rec.FAILED }.sortedByDescending { it.start }
    var menuFor by remember { mutableStateOf<Rec?>(null) }
    val sideFocus = remember { FocusRequester() }
    var inSidebar by remember { mutableStateOf(false) }
    BackHandler(enabled = !inSidebar) { runCatching { sideFocus.requestFocus() } }
    LaunchedEffect(Unit) { repeat(10) { delay(80); if (runCatching { ContentFocus.req.requestFocus() }.isSuccess) return@LaunchedEffect } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(start = DS.SidebarW + DS.PageX, end = DS.PageX), contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aufnahmen", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    val free = RecStore.free(ctx)
                    Text(raw = true, text = RecStore.dir(ctx) + if (free > 0) "  ·  " + Lang.tr("frei") + " " + sizeText(free) else "",
                        color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 700.dp))
                }
            }
            if (all.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    EmptyState(AIcon.Rec, "Keine Aufnahmen", "Im TV-Guide OK gedrückt halten und „Aufnahme hinzufügen“ wählen, oder im Player über das Menü aufnehmen.",
                        action = "Zum TV-Guide", actionModifier = Modifier.focusRequester(ContentFocus.req)) { onTab(3) }
                }
            }
            var first = true
            fun section(title: String, l: List<Rec>) {
                if (l.isEmpty()) return
                item { Text(title, color = Accent2, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) }
                val isFirst = first
                first = false
                items(l, key = { it.id }) { r ->
                    RecRow(r, if (isFirst && r === l.first()) Modifier.focusRequester(ContentFocus.req) else Modifier,
                        onOk = { if (r.state == Rec.DONE || r.state == Rec.RUNNING) onPlay(r) else menuFor = r }, onMenu = { menuFor = r })
                }
            }
            section(Lang.tr("Laufende Aufnahmen"), running)
            section(Lang.tr("Geplante Aufnahmen"), planned)
            section(Lang.tr("Aufgenommen"), done)
        }
        Sidebar(tab, { onTab(it) }, Modifier.align(Alignment.CenterStart), sideFocus) { inSidebar = it }
    }
    menuFor?.let { r ->
        val e = ArrayList<SideEntry>()
        if (r.state == Rec.DONE || r.state == Rec.RUNNING) e.add(SideEntry("Abspielen", AIcon.Play) { onPlay(r) })
        if (r.state == Rec.RUNNING) e.add(SideEntry("Aufnahme stoppen", AIcon.Close) { Recorder.stop(ctx, r.id) })
        if (r.state == Rec.PLANNED) e.add(SideEntry("Aufnahme abbrechen", AIcon.Close) { Recorder.stop(ctx, r.id) })
        if (r.state != Rec.PLANNED) e.add(SideEntry("Löschen", AIcon.Close) {
            Recorder.delete(ctx, r.id); Toast.makeText(ctx, Lang.tr("Aufnahme gelöscht"), Toast.LENGTH_SHORT).show()
        })
        SideMenu(listOf(SideSection(r.title, e)), { menuFor = null })
    }
}

@Composable
private fun RecRow(r: Rec, modifier: Modifier, onOk: () -> Unit, onMenu: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusM)
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(if (f) TvFocusFill else Surf)
            .onFocusChanged { f = it.isFocused }
            .menuKeys(onMenu = onMenu, onLong = onMenu)
            .clickable { onOk() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppIcon(AIcon.Rec, if (r.state == Rec.RUNNING) Color(0xFFE5484D) else if (f) DS.FocusInk else Muted, size = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(raw = true, text = r.title, color = if (f) DS.FocusInk else Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val rep = when (r.repeat) { 1 -> "  ·  " + Lang.tr("Täglich"); 2 -> "  ·  " + Lang.tr("Wöchentlich"); else -> "" }
            Text(raw = true, text = r.chTitle + "  ·  " + dayLabel(r.start) + " " + hhmm(r.start) + " — " + hhmm(r.end) + rep,
                color = if (f) Color(0xFF3A3D42) else Muted, fontSize = 15.sp, maxLines = 1)
        }
        val status = when (r.state) {
            Rec.RUNNING -> Lang.tr("Aufnahme läuft") + (sizeText(r.size).let { if (it.isEmpty()) "" else " · $it" })
            Rec.FAILED -> Lang.tr("Fehlgeschlagen") + if (r.msg.isNotEmpty()) ": " + Lang.tr(r.msg) else ""
            Rec.DONE -> sizeText(r.size)
            else -> ""
        }
        if (status.isNotEmpty()) Text(raw = true, text = status, color = if (r.state == Rec.FAILED) Color(0xFFE5484D) else if (f) DS.FocusInk else Muted, fontSize = 15.sp, maxLines = 1)
    }
}

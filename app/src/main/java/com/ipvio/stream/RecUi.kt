package com.ipvio.stream

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
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

/**
 * Bereich „Aufnahmen“ im Aufbau von TiviMate: links die Tage (dazu „Alle Aufnahmen“), rechts die Aufnahmen mit Uhrzeit, Dauer,
 * Senderlogo und Papierkorb. Im Inhalt oben rechts „Aufnahmen“ mit Zahnrad und eine Karte zur markierten Aufnahme.
 */
@Composable
fun RecordingsScreen(tab: Int, onTab: (Int) -> Unit, onPlay: (Rec) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Recorder.init(ctx); Recorder.schedule(ctx) }
    SideEffect { NavState.tab = tab; ContentFocus.tab = tab }
    // Größe laufender Aufnahmen regelmäßig neu anzeigen
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val all = remember(Recorder.version, Recorder.list.size, tick) { Recorder.list.toList().sortedByDescending { it.start } }
    val days = remember(all) { all.map { recDay(it.start) }.distinct() }
    var selDay by remember { mutableStateOf("") }   // "" = Alle Aufnahmen
    val shown = if (selDay.isEmpty()) all else all.filter { recDay(it.start) == selDay }
    var menuFor by remember { mutableStateOf<Rec?>(null) }
    var focusRec by remember { mutableStateOf<Rec?>(null) }
    var inContent by remember { mutableStateOf(false) }
    val sideFocus = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    var inSidebar by remember { mutableStateOf(false) }
    BackHandler(enabled = !inSidebar) { runCatching { sideFocus.requestFocus() } }
    LaunchedEffect(Unit) { repeat(10) { delay(80); if (runCatching { ContentFocus.req.requestFocus() }.isSuccess) return@LaunchedEffect } }

    Box(Modifier.fillMaxSize()) {
        if (all.isEmpty()) Box(Modifier.fillMaxSize().padding(start = DS.SidebarW), contentAlignment = Alignment.Center) {
            EmptyState(AIcon.Rec, "Keine Aufnahmen", "Im TV-Guide OK gedrückt halten und „Aufnahme hinzufügen“ wählen, oder im Player über das Menü aufnehmen.",
                action = "Zum TV-Guide", actionModifier = Modifier.focusRequester(ContentFocus.req)) { onTab(3) }
        } else Row(Modifier.fillMaxSize().padding(start = DS.SidebarW)) {
            if (!inContent) LazyColumn(
                Modifier.width(250.dp).fillMaxHeight().padding(start = 14.dp),
                contentPadding = PaddingValues(top = 180.dp, bottom = 200.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(key = "all") {
                    RecDayRow(Lang.tr("Alle Aufnahmen"), selDay.isEmpty(), if (selDay.isEmpty()) Modifier.focusRequester(ContentFocus.req) else Modifier,
                        onFocus = { selDay = "" }) { runCatching { firstRow.requestFocus() } }
                }
                items(days, key = { it }) { d ->
                    RecDayRow(recDayLabel(d, false), selDay == d, if (selDay == d) Modifier.focusRequester(ContentFocus.req) else Modifier,
                        onFocus = { selDay = d }) { runCatching { firstRow.requestFocus() } }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 24.dp)) {
                if (inContent) {
                    Row(Modifier.align(Alignment.TopEnd).padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Aufnahmen", color = Tok.TextHi, fontSize = 30.sp, fontWeight = FontWeight.Light)
                        RecGear { SettingsOpen.value = true }
                    }
                    focusRec?.let { r ->
                        Column(Modifier.align(Alignment.TopEnd).padding(top = 80.dp).width(340.dp).clip(RoundedCornerShape(10.dp)).background(Tok.Card).padding(16.dp)) {
                            Text(raw = true, text = r.title, color = Tok.TextHi, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(raw = true, text = java.text.SimpleDateFormat("EE, d. MMM", java.util.Locale.GERMAN).format(java.util.Date(r.start * 1000)) + ", " + hhmm(r.start) + " — " + hhmm(r.end),
                                color = Tok.TextLow, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                            val status = when (r.state) {
                                Rec.RUNNING -> Lang.tr("Aufnahme läuft") + (sizeText(r.size).let { if (it.isEmpty()) "" else " · $it" })
                                Rec.PLANNED -> Lang.tr("Geplant") + when (r.repeat) { 1 -> " · " + Lang.tr("Täglich"); 2 -> " · " + Lang.tr("Wöchentlich"); else -> "" }
                                Rec.FAILED -> Lang.tr("Fehlgeschlagen") + if (r.msg.isNotEmpty()) ": " + Lang.tr(r.msg) else ""
                                else -> sizeText(r.size)
                            }
                            if (status.isNotEmpty()) Text(raw = true, text = status, color = if (r.state == Rec.FAILED) Color(0xFFE5484D) else Tok.TextLow, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                LazyColumn(
                    Modifier.fillMaxHeight().fillMaxWidth(if (inContent) 0.58f else 1f).onFocusChanged { inContent = it.hasFocus },
                    contentPadding = PaddingValues(top = 180.dp, bottom = 120.dp)
                ) {
                    var firstSet = false
                    shown.groupBy { recDay(it.start) }.forEach { (d, l) ->
                        item(key = "h$d") {
                            Text(raw = true, text = recDayLabel(d, true), color = Tok.TextLow, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        }
                        val isFirst = !firstSet
                        firstSet = true
                        items(l, key = { it.id }) { r ->
                            RecRow(r, if (isFirst && r === l.first()) Modifier.focusRequester(firstRow) else Modifier,
                                onFocus = { focusRec = r },
                                onOk = { if (r.state == Rec.DONE || r.state == Rec.RUNNING) onPlay(r) else menuFor = r }, onMenu = { menuFor = r })
                        }
                    }
                }
            }
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

private fun recDay(sec: Long): String = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(sec * 1000))
private fun recDayLabel(d: String, long: Boolean): String {
    val date = runCatching { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).parse(d) }.getOrNull() ?: return d
    return java.text.SimpleDateFormat(if (long) "d. MMMM" else "d. MMM", java.util.Locale.GERMAN).format(date)
}

@Composable
private fun RecDayRow(name: String, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (f) TvFocusFill else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }.clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text(raw = true, text = name, color = if (f) Color(0xFF15171A) else if (selected) Accent2 else Tok.TextHi, fontSize = 17.sp, maxLines = 1) }
}

@Composable
private fun RecGear(onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(if (f) TvFocusFill else Color(0x33FFFFFF)).onFocusChanged { f = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { AppIcon(AIcon.Gear, if (f) Color(0xFF15171A) else Tok.TextHi, size = 20.dp) }
}

/** Zeile wie in TiviMate: Titel, „00:25 — 00:26, 1 Min“, Senderlogo und Name, Papierkorb (bzw. roter Punkt bei laufender Aufnahme). */
@Composable
private fun RecRow(r: Rec, modifier: Modifier, onFocus: () -> Unit, onOk: () -> Unit, onMenu: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val ink = Color(0xFF15171A)
    val ch = remember(r.chId) { PlaylistStore.channels.firstOrNull { it.id == r.chId } }
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape).background(if (f) TvFocusFill else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .menuKeys(onMenu = onMenu, onLong = onMenu)
            .clickable { onOk() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(raw = true, text = r.title, color = if (f) ink else Tok.TextHi, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val mins = ((r.end - r.start) / 60).coerceAtLeast(0)
            Text(raw = true, text = hhmm(r.start) + " — " + hhmm(r.end) + ",  " + mins + " Min", color = if (f) Color(0xFF3A3D42) else Tok.TextLow, fontSize = 14.sp, maxLines = 1)
        }
        Box(Modifier.size(width = 36.dp, height = 36.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3D42)), contentAlignment = Alignment.Center) {
            val logo = ch?.let { Logos.of(it) }
            if (logo != null) AsyncImage(logo, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
            else Text(raw = true, text = "TV", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(raw = true, text = r.chTitle, color = if (f) ink else Tok.TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
        when (r.state) {
            Rec.RUNNING -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE5484D)))
            Rec.FAILED -> AppIcon(AIcon.Warning, Color(0xFFE5484D), size = 20.dp)
            Rec.PLANNED -> AppIcon(AIcon.Bell, if (f) ink else Tok.TextLow, size = 20.dp)
            else -> AppIcon(AIcon.Close, if (f) ink else Tok.TextLow, size = 20.dp)
        }
    }
}

/** Kanaloptionen > EPG zuweisen (TiviMate): EPG-Kanal aus den Quellen wählen, mit Suche und Vorschlägen. */
@Composable
fun EpgAssignDialog(item: Item, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val all by produceState(emptyList<EpgChan>(), Xmltv.channelCount, Xmltv.status) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Xmltv.epgChannels(ctx) }
    }
    // Quelle vorher wählen (Wunsch des Nutzers, 2026-11-10): „Alle“ oder eine Quelle, Reihenfolge wie beim Laden
    var srcSel by remember { mutableStateOf<String?>(null) }
    val srcs = remember(all) {
        val have = all.flatMap { it.src.split(SRC_SEP) }.filter { it.isNotEmpty() }.toSet()
        // Alle eingerichteten Quellen anbieten, auch wenn eine gerade keine Kanäle geliefert hat
        (Xmltv.sources.map { it.label } + have).distinct()
    }
    val cur = EpgAssign.get(item.id)
    val own = EpgNames.norm(ChannelEdit.title(item))
    val shown = remember(all, q, srcSel) {
        val base = srcSel?.let { s -> all.filter { s in it.src.split(SRC_SEP) } } ?: all
        val n = EpgNames.norm(q)
        if (n.isNotEmpty()) base.filter { EpgNames.norm(it.name).contains(n) || it.id.contains(q.trim().lowercase()) }.take(200)
        else {
            // Vorschläge: ähnlicher Name zuerst
            val sug = if (own.length >= 2) base.filter { val x = EpgNames.norm(it.name); x.isNotEmpty() && (x.contains(own) || own.contains(x)) } else emptyList()
            (sug + base.filter { it !in sug }).take(200)
        }
    }
    val fr = remember { FocusRequester() }
    fun reload() = Xmltv.start(ctx, (PlaylistStore.epgUrls + PlaylistStore.extraEpg).distinct(), PlaylistStore.channels, force = true)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
        Column(
            Modifier.width(820.dp).heightIn(max = 640.dp).clip(RoundedCornerShape(DS.RadiusL)).background(Surf).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("EPG zuweisen", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(raw = true, text = ChannelEdit.title(item) + (cur?.let { "  ·  " + Lang.tr("Zugewiesen") + ": " + (all.firstOrNull { p -> p.id == it }?.let { p -> p.name + (if (p.src.isNotEmpty()) " · " + p.src else "") } ?: it) + " (" + Xmltv.forChannel(it).size + ")" } ?: ""),
                color = Accent2, fontSize = 16.sp)
            Field(q, { q = it }, "EPG-Kanal suchen …", Modifier.fillMaxWidth())
            if (srcs.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { PillButton(Lang.tr("Alle Quellen") + " (" + all.size + ")", light = srcSel == null) { srcSel = null } }
                items(srcs) { s ->
                    val n = all.count { s in it.src.split(SRC_SEP) }
                    PillButton("$s ($n)", light = srcSel == s) { srcSel = s }
                }
            }
            if (all.isEmpty()) {
                Text(raw = true, text = Lang.tr("Noch keine EPG-Kanäle bekannt.") + "  " + Xmltv.status, color = Muted, fontSize = 15.sp)
                PillButton("EPG jetzt aktualisieren", Modifier.focusRequester(fr), light = true) { reload() }
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cur != null) item {
                    PillButton("Zuweisung entfernen", Modifier.focusRequester(fr)) {
                        EpgAssign.set(ctx, item.id, null); reload()
                        Toast.makeText(ctx, Lang.tr("Zuweisung entfernt – EPG wird neu geladen"), Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                }
                items(shown, key = { it.id }) { ch ->
                    val id = ch.id; val name = ch.name
                    var f by remember { mutableStateOf(false) }
                    val first = cur == null && id == shown.firstOrNull()?.id
                    Row(
                        Modifier.fillMaxWidth().then(if (first) Modifier.focusRequester(fr) else Modifier).clip(RoundedCornerShape(DS.RadiusS))
                            .background(if (f) TvFocusFill else Color.Transparent)
                            .onFocusChanged { f = it.isFocused }
                            .clickable {
                                EpgAssign.set(ctx, item.id, id); reload()
                                Toast.makeText(ctx, Lang.tr("EPG zugewiesen – wird neu geladen"), Toast.LENGTH_SHORT).show()
                                onClose()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(raw = true, text = name, color = if (f) DS.FocusInk else Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        // Woher das Programm kommt (Quelle), darunter die Kennung
                        Column(horizontalAlignment = Alignment.End) {
                            if (ch.src.isNotEmpty()) Text(raw = true, text = ch.src, color = if (f) DS.FocusInk else Accent2, fontSize = 13.sp, maxLines = 1)
                            Text(raw = true, text = id, color = if (f) Color(0xFF3A3D42) else Muted, fontSize = 12.sp, maxLines = 1)
                        }
                        if (id == cur) AppIcon(AIcon.Check, if (f) DS.FocusInk else Accent2, size = 18.dp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** Meine Liste > TV-Sendungen: Karten mit Sendung, Sender und Zeit; OK wie im Guide, lang OK = entfernen. */
@Composable
fun ProgMarkRow(list: List<ProgMark>, firstReq: FocusRequester?, onOk: (ProgMark) -> Unit, onRemove: (ProgMark) -> Unit) {
    val now = System.currentTimeMillis() / 1000
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = DS.PageX, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(list.size) { i ->
            val m = list[i]
            var f by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(DS.RadiusM)
            val running = now in m.start until m.end
            Column(
                Modifier.width(320.dp).then(if (i == 0 && firstReq != null) Modifier.focusRequester(firstReq) else Modifier)
                    .clip(shape).background(if (f) TvFocusFill else Surf)
                    .onFocusChanged { f = it.isFocused }
                    .menuKeys(onMenu = { onRemove(m) }, onLong = { onRemove(m) })
                    .clickable { onOk(m) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(raw = true, text = m.title, color = if (f) DS.FocusInk else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(raw = true, text = m.chTitle, color = if (f) Color(0xFF3A3D42) else Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(raw = true, text = dayLabel(m.start) + " " + hhmm(m.start) + " — " + hhmm(m.end) + if (running) "  ·  " + Lang.tr("Läuft jetzt") else "",
                    color = if (running && !f) Accent2 else if (f) Color(0xFF3A3D42) else Muted, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

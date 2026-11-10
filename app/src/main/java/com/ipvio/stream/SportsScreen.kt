package com.ipvio.stream

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/*
 * Sport Guide (Vorbild StreamVault „Sports“): oben Filter nach Sportart und Liga, darunter eine große Karte zum markierten
 * Spiel (Liga, LIVE und Spielstand, Teams, Uhrzeit, Beschreibung, Knopf zum besten Sender, weitere Sender), darunter
 * Reihen je Liga mit Spielkarten. OK: laufendes Spiel ansehen, künftiges = Erinnerung an/aus.
 */

private val CardBg = Color(0xFF1C2230)
private val CardFocus = Color(0xFF2A3346)
private val LiveRed = Color(0xFFE5484D)

/** Interner Schlüssel der Rubrik „Live“ (kein echter Wettbewerb). */
private const val LIVE_TAB = "#live"

@Composable
fun SportsScreen(tab: Int, onTab: (Int) -> Unit, onPlay: (Item) -> Unit) {
    val ctx = LocalContext.current
    SideEffect { NavState.tab = tab; ContentFocus.tab = tab }
    val all = PlaylistStore.channels
    val live = remember(all) { all.filter { it.kind == Kind.LIVE } }
    val byId = remember(live) { live.associateBy { it.id } }
    // Beim Öffnen und dann jede Minute neu (Spielstände)
    // Nicht bei jedem EPG-Zwischenstand neu starten (das brach die Suche ab): alle 5 s prüfen, ob neues
    // Programm da ist, sonst jede Minute
    LaunchedEffect(live) {
        var lastCount = -1; var lastAt = 0L
        while (true) {
            val n = Xmltv.channelCount
            if (n != lastCount || System.currentTimeMillis() - lastAt >= 60_000) {
                lastCount = n; lastAt = System.currentTimeMillis()
                SportsRepo.refresh(live)
            }
            delay(5_000)
        }
    }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }
    val events = SportsRepo.events
    // Wenig Auswahl (Wunsch des Nutzers): „Alle“ und die Wettbewerbe in fester Reihenfolge, Bundesliga zuerst
    var league by remember { mutableStateOf<String?>(null) }
    // Ligen mit Tabelle auch ohne Spiele im Programm anbieten (Tabelle steht dann unter der Liga)
    val tables = SportsRepo.tables
    val leagues = remember(events, tables) { (events.map { it.league } + tables.keys).distinct().sortedBy { SportsLogic.prio(it) } }
    // Rubrik „Live“ (Wunsch des Nutzers, 2026-11-10): nur Spiele, die gerade laufen, mit Live-Stand – keine TV-Live-Sendungen
    val liveNow = events.filter { it.liveScore(nowSec) }
    val shown = when (league) { null -> events; LIVE_TAB -> liveNow; else -> events.filter { it.league == league } }
    var focused by remember { mutableStateOf<SportEvent?>(null) }
    val hero = focused?.takeIf { f -> shown.any { it.key == f.key } }?.let { f -> shown.first { it.key == f.key } } ?: shown.firstOrNull()
    var chanMenu by remember { mutableStateOf<SportEvent?>(null) }
    val sideFocus = remember { FocusRequester() }
    var inSidebar by remember { mutableStateOf(false) }
    BackHandler(enabled = !inSidebar) { runCatching { sideFocus.requestFocus() } }

    fun act(e: SportEvent) {
        val ch = e.channels.firstNotNullOfOrNull { byId[it] }
        when {
            ch == null -> Toast.makeText(ctx, Lang.tr("Kein Sender mit diesem Spiel gefunden"), Toast.LENGTH_SHORT).show()
            e.start <= nowSec -> onPlay(ch)
            else -> {
                val on = Reminders.toggle(ctx, Reminder(ch.id, ChannelEdit.title(ch), e.title, e.start, e.end))
                Toast.makeText(ctx, Lang.tr(if (on) "Erinnerung gesetzt" else "Erinnerung entfernt"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(start = DS.SidebarW), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "chips1") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SportChip(Lang.tr("Alle"), league == null, Modifier.focusRequester(ContentFocus.req)) { league = null } }
                    item { SportChip(Lang.tr("Live") + if (liveNow.isNotEmpty()) " (${liveNow.size})" else "", league == LIVE_TAB, raw = true) { league = if (league == LIVE_TAB) null else LIVE_TAB } }
                    if (leagues.size > 1) items(leagues) { l -> SportChip(l, league == l, raw = true) { league = if (league == l) null else l } }
                }
            }
            item(key = "hero") {
                if (hero != null) SportHero(hero, nowSec, byId, onWatch = { act(hero) }, onMore = { chanMenu = hero },
                    onGoal = if (GoalAlert.supported(hero)) ({ val on = GoalAlert.toggle(ctx, hero); Toast.makeText(ctx, Lang.tr(if (on) "Tor-Alarm an: Du bekommst Tore und Abpfiff angezeigt" else "Tor-Alarm aus"), Toast.LENGTH_SHORT).show() }) else null)
                else Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)).background(CardBg), contentAlignment = Alignment.Center) {
                    Text(if (SportsRepo.loading || SportsRepo.updatedAt == 0L) "Sportprogramm wird gesucht …" else if (league == LIVE_TAB) "Gerade läuft kein Spiel" else "Gerade keine Sportereignisse im TV-Programm",
                        color = Tok.TextMid, fontSize = 17.sp)
                }
            }
            val byLeague = shown.groupBy { it.league }.toList().sortedBy { SportsLogic.prio(it.first) }
            byLeague.forEach { (lg, list) ->
                item(key = "h$lg") { Text(raw = true, text = lg, color = Tok.TextHi, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
                item(key = "r$lg") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.key }) { e -> SportCard(e, nowSec, onFocus = { focused = e }) { act(e) } }
                    }
                }
            }
            league?.let { lg -> tables[lg] }?.let { rows -> leagueTable(league!!, rows) }
            if (events.any { it.source == "OpenLigaDB" } || tables.isNotEmpty()) item(key = "credit") {
                Text("Spielstände: OpenLigaDB (ODbL 1.0)", color = Tok.TextLow, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Sidebar(tab, onTab, Modifier.align(Alignment.CenterStart), sideFocus) { inSidebar = it }
    }
    chanMenu?.let { e ->
        val entries = e.channels.mapNotNull { byId[it] }.map { ch -> SideEntry(ChannelEdit.title(ch), AIcon.Live, raw = true) { onPlay(ch) } }
        SideMenu(listOf(SideSection(e.title, entries)), { chanMenu = null })
    }
}

@Composable
private fun SportChip(label: String, selected: Boolean, modifier: Modifier = Modifier, raw: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (f) TvFocusFill else if (selected) Color(0xFF2F3B55) else CardBg)
            .border(1.dp, if (selected && !f) Accent2 else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 8.dp)
    ) { Text(label, raw = raw, color = if (f) Color(0xFF15171A) else if (selected) Accent2 else Tok.TextHi, fontSize = if (small) 14.sp else 15.sp) }
}

@Composable
private fun SportLiveBadge() {
    Box(Modifier.border(1.dp, LiveRed, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text("LIVE", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TeamRow(name: String, score: Int?, big: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (big) 34.dp else 26.dp).clip(CircleShape).background(Color(0xFF34405A)), contentAlignment = Alignment.Center) {
            Text(raw = true, text = SportsLogic.initials(name), color = Color.White, fontSize = if (big) 13.sp else 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(raw = true, text = name, color = Color.White, fontSize = if (big) 22.sp else 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (score != null) Text(raw = true, text = score.toString(), color = Color.White, fontSize = if (big) 24.sp else 17.sp, fontWeight = FontWeight.Bold, style = TNUM)
    }
}

private fun sportTimeText(e: SportEvent): String {
    val d = java.text.SimpleDateFormat("EE, d. MMM", java.util.Locale.GERMAN).format(java.util.Date(e.start * 1000))
    return d + "  " + hhmm(e.start) + " – " + hhmm(e.end)
}

@Composable
private fun SportHero(e: SportEvent, now: Long, byId: Map<String, Item>, onWatch: () -> Unit, onMore: () -> Unit, onGoal: (() -> Unit)? = null) {
    val ch = e.channels.firstNotNullOfOrNull { byId[it] }
    Row(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)).background(CardBg).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(raw = true, text = e.league, color = Tok.TextMid, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (e.live(now)) { SportLiveBadge(); SportsLogic.gameMinute(e, now)?.let { Text(raw = true, text = "  " + it, color = Tok.TextMid, fontSize = 14.sp) } }
                else if (e.finished) Text("Beendet", color = Tok.TextLow, fontSize = 13.sp)
            }
            if (e.home != null && e.away != null) {
                TeamRow(e.home, e.scoreHome, true)
                TeamRow(e.away, e.scoreAway, true)
            } else Text(raw = true, text = e.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(raw = true, text = sportTimeText(e), color = Tok.TextMid, fontSize = 14.sp)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x22FFFFFF)))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (e.desc.isNotBlank()) Text(raw = true, text = e.desc, color = Tok.TextMid, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ch != null) HeroBtn(Modifier.weight(1f), onWatch) { fg ->
                    if (Logos.of(ch) != null) AsyncImage(Logos.of(ch), null, Modifier.size(width = 40.dp, height = 24.dp), contentScale = ContentScale.Fit)
                    Text(raw = true, text = ChannelEdit.title(ch), color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Text("Kein Sender mit diesem Spiel gefunden", color = Tok.TextLow, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (e.channels.size > 1) HeroBtn(Modifier, onMore) { fg -> Text("Weitere Sender", color = fg, fontSize = 15.sp) }
                // Tor-Alarm: Einblendung bei Tor/Abpfiff, auch wenn ein anderer Sender läuft
                if (onGoal != null) HeroBtn(Modifier, onGoal) { fg -> Text(if (GoalAlert.isOn(e.key)) "Tor-Alarm an" else "Tor-Alarm", color = if (GoalAlert.isOn(e.key) && fg == Color.White) Accent2 else fg, fontSize = 15.sp) }
            }
            if (e.channels.isNotEmpty()) Text(Lang.tr("Verfügbare Sender") + ": " + e.channels.size, raw = true, color = Tok.TextLow, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HeroBtn(modifier: Modifier, onClick: () -> Unit, content: @Composable RowScope.(Color) -> Unit) {
    var f by remember { mutableStateOf(false) }
    val fg = if (f) Color(0xFF15171A) else Color.White
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(10.dp)).background(if (f) TvFocusFill else CardFocus)
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) { content(fg) }
}

@Composable
private fun SportCard(e: SportEvent, now: Long, onFocus: () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Column(
        Modifier.width(300.dp).height(150.dp).clip(RoundedCornerShape(12.dp)).background(if (f) CardFocus else CardBg)
            .border(2.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }.clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(raw = true, text = e.league, color = Tok.TextMid, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            if (e.live(now)) SportLiveBadge()
            else if (e.channels.isNotEmpty() && Reminders.isSet(e.channels.first(), e.start)) AppIcon(AIcon.Bell, Accent2, size = 14.dp)
        }
        if (e.home != null && e.away != null) {
            TeamRow(e.home, e.scoreHome, false)
            TeamRow(e.away, e.scoreAway, false)
        } else Text(raw = true, text = e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(raw = true, text = sportTimeText(e), color = Tok.TextLow, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * Startseite unten (Wunsch des Nutzers): aktuelle Begegnungen mit Ergebnis, klein gehalten. Laufende zuerst, dann die
 * heute beendeten. OK öffnet den Sport Guide.
 */
@Composable
fun HomeSportRow(onOpenSport: () -> Unit) {
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }
    val list = SportsRepo.events.filter { it.scoreHome != null && it.home != null && it.away != null && (it.live(nowSec) || (it.finished && it.end > nowSec - 8 * 3600)) }
        .sortedWith(compareBy<SportEvent> { if (it.live(nowSec)) 0 else 1 }.thenBy { SportsLogic.prio(it.league) }).take(10)
    if (list.isEmpty()) return
    // Gleicher Rand und Titel wie die übrigen Reihen der Startseite (RowTitle / RowPadX)
    Column {
        RowTitle("Sport", null, null, onAll = onOpenSport)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = RowPadX, end = RowPadX, top = dpx(18), bottom = dpx(18))) {
            items(list, key = { it.key }) { e -> HomeSportCard(e, nowSec, onOpenSport) }
        }
    }
}

@Composable
private fun HomeSportCard(e: SportEvent, now: Long, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Column(
        Modifier.width(230.dp).clip(RoundedCornerShape(10.dp)).background(if (f) CardFocus else CardBg)
            .border(2.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(raw = true, text = e.league, color = Tok.TextMid, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
            if (e.live(now)) SportLiveBadge() else Text("Beendet", color = Tok.TextLow, fontSize = 11.sp)
        }
        for ((team, score) in listOf(e.home!! to e.scoreHome, e.away!! to e.scoreAway)) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(raw = true, text = team, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(raw = true, text = score?.toString() ?: "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Aktuelle Tabelle einer Liga (OpenLigaDB) als ein Block; jede Zeile ist mit der Fernbedienung erreichbar (scrollt mit). */
private fun androidx.compose.foundation.lazy.LazyListScope.leagueTable(league: String, rows: List<TableRow>) {
    item(key = "table") {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(CardBg).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(raw = true, text = Lang.tr("Tabelle") + " · " + league, color = Tok.TextHi, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            TableLine("#", Lang.tr("Verein"), listOf("Sp", "S", "U", "N", Lang.tr("Tore"), "Diff"), "Pkt", true, Color.Transparent)
            val n = rows.size
            rows.forEach { r ->
                // Farbstreifen: oben grün, Relegation gelb, Abstieg rot – nur zur Orientierung
                val tint = when { r.pos <= 4 -> Color(0xFF2E9E5B); r.pos > n - 2 -> Color(0xFFC0392B); r.pos == n - 2 -> Color(0xFFE6A23C); else -> Color.Transparent }
                TableLine("${r.pos}.", r.team, listOf("${r.games}", "${r.won}", "${r.draw}", "${r.lost}", "${r.goals}:${r.against}", (if (r.diff > 0) "+" else "") + r.diff), "${r.points}", false, tint, r.icon)
            }
        }
    }
}

@Composable
private fun TableLine(p: String, t: String, cols: List<String>, pts: String, head: Boolean, tint: Color, icon: String? = null) {
    var fz by remember { mutableStateOf(false) }
    val c = if (head) Tok.TextMid else Color.White
    val fs = if (head) 13.sp else 16.sp
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (fz) Color(0x22FFFFFF) else Color.Transparent)
        .onFocusChanged { fz = it.isFocused }.then(if (head) Modifier else Modifier.focusable()).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).background(tint))
        Text(raw = true, text = p, color = c, fontSize = fs, modifier = Modifier.width(44.dp).padding(start = 8.dp), style = TNUM)
        // Vereinslogo (OpenLigaDB bzw. Wikimedia); Platz bleibt frei, wenn keins da ist
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            if (!head && icon != null) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val req = remember(icon) { coil.request.ImageRequest.Builder(ctx).data(icon).addHeader("User-Agent", "IPTV-XY/" + BuildConfig.VERSION_NAME + " (Android TV)").build() }
                coil.compose.AsyncImage(req, null, Modifier.size(24.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
        Text(raw = true, text = t, color = c, fontSize = fs, fontWeight = if (head) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        cols.forEachIndexed { i, v -> Text(raw = true, text = v, color = c, fontSize = fs, modifier = Modifier.width(listOf(44, 40, 40, 40, 80, 56)[i].dp), style = TNUM) }
        Text(raw = true, text = pts, color = if (head) c else Accent2, fontSize = fs, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), style = TNUM)
    }
}

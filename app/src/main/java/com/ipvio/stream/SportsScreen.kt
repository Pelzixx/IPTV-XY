package com.ipvio.stream

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    val leagues = remember(events) { events.map { it.league }.distinct().sortedBy { SportsLogic.prio(it) } }
    val shown = if (league == null) events else events.filter { it.league == league }
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
                    if (leagues.size > 1) items(leagues) { l -> SportChip(l, league == l, raw = true) { league = if (league == l) null else l } }
                }
            }
            item(key = "hero") {
                if (hero != null) SportHero(hero, nowSec, byId, onWatch = { act(hero) }, onMore = { chanMenu = hero })
                else Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)).background(CardBg), contentAlignment = Alignment.Center) {
                    Text(if (SportsRepo.loading || SportsRepo.updatedAt == 0L) "Sportprogramm wird gesucht …" else "Gerade keine Sportereignisse im TV-Programm",
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
            if (events.any { it.source == "OpenLigaDB" }) item(key = "credit") {
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
private fun SportHero(e: SportEvent, now: Long, byId: Map<String, Item>, onWatch: () -> Unit, onMore: () -> Unit) {
    val ch = e.channels.firstNotNullOfOrNull { byId[it] }
    Row(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)).background(CardBg).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(raw = true, text = e.league, color = Tok.TextMid, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (e.live(now)) { SportLiveBadge(); Text(raw = true, text = "  " + ((now - e.start) / 60).coerceAtMost(130) + "'", color = Tok.TextMid, fontSize = 14.sp) }
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

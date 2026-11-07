package com.ipvio.stream

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

object ZapContext {
    var list: List<Item> = emptyList()
    /** Kategorie, aus der gestartet wurde (z. B. eigene Senderliste „L:3“) – der Player zeigt sie im Senderwechsel. */
    var key: String? = null
}

/** Merkt sich die zuletzt gewählte Kategorie / Liste in Live TV (auch über einen Neustart). */
object LiveMemory {
    fun sel(c: android.content.Context): String? = Profiles.sp(c, "ipvio_live").getString("sel", null)
    fun setSel(c: android.content.Context, k: String) { Profiles.sp(c, "ipvio_live").edit().putString("sel", k).apply() }
}

/** Eingabe eines Namens (neue Senderliste, Umbenennen). */
private class NameAsk(val title: String, val initial: String, val onSave: (String) -> Unit)

@Composable
fun LiveTab(live: List<Item>, groups: Map<String, List<Item>>, favs: List<Item>, onOpen: (Item) -> Unit) {
    val ctx = LocalContext.current
    CatFavs.init(ctx)
    ChannelLists.init(ctx)
    ChannelEdit.init(ctx)
    // Liest den Stand, damit Ausblenden/Umbenennen sofort sichtbar wird
    @Suppress("UNUSED_VARIABLE") val editVersion = ChannelEdit.version
    // Zuletzt gewählte Kategorie / Liste (Runde 14): nach dem Zurück aus dem Player und nach einem Neustart wieder dieselbe
    var sel by remember {
        val saved = LiveMemory.sel(ctx)?.takeIf { k ->
            (k == FAV_CAT && favs.isNotEmpty()) || k == "ALL" || k in groups || ChannelLists.byKey(k) != null
        }
        mutableStateOf(saved ?: if (favs.isNotEmpty()) FAV_CAT else "ALL")
    }
    val favCats = CatFavs.list.filter { it in groups }
    val myLists = ChannelLists.lists.toList()
    val byId = remember(live) { live.associateBy { it.id } }
    // Reihenfolge: TV-Guide, Favoriten, eigene Senderlisten, alle Sender, Kategorien (neue Liste: Menü eines Senders)
    // „Ausgeblendete Sender“ nur, wenn es welche gibt (dort holt man sie per Menü zurück)
    val hiddenCat = if (ChannelEdit.hiddenCount() > 0) listOf(HIDDEN_CAT) else emptyList()
    val names = listOf("GUIDE", FAV_CAT) + myLists.map { ChannelListLogic.key(it) } + listOf("ALL") + hiddenCat + favCats + groups.keys.filter { it !in favCats }
    val curList = ChannelLists.byKey(sel)
    val showHidden = sel == HIDDEN_CAT
    val list = ChannelEditLogic.visible(
        when {
            sel == FAV_CAT -> favs
            sel == "ALL" || showHidden -> live
            curList != null -> ChannelListLogic.resolve(curList, byId)
            else -> groups[sel].orEmpty()
        },
        { it.id }, ChannelEdit::isHidden, showHidden
    )
    // Letzter ausgeblendeter Sender wieder eingeblendet: Kategorie gibt es nicht mehr
    LaunchedEffect(showHidden, hiddenCat.isEmpty()) { if (showHidden && hiddenCat.isEmpty()) sel = "ALL" }
    val firstChan = remember { FocusRequester() }
    // Zurück aus dem Player: der zuletzt gesehene Sender bekommt den Fokus (nicht die Seitenleiste)
    val chanState = remember { LazyListState() }
    val restoreReq = remember { FocusRequester() }
    val restoreId = remember { LastFocus.pending }
    LaunchedEffect(Unit) {
        val id = restoreId ?: return@LaunchedEffect
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return@LaunchedEffect
        runCatching { chanState.scrollToItem(maxOf(i - 2, 0)) }
        repeat(15) {
            kotlinx.coroutines.delay(80)
            if (runCatching { restoreReq.requestFocus() }.isSuccess) { LastFocus.pending = null; return@LaunchedEffect }
        }
    }
    val catState = remember { LazyListState() }
    var ask by remember { mutableStateOf<NameAsk?>(null) }
    // Gruppenliste links: offen, solange man dort ist; beim Durchblättern der Sender eingeklappt (mehr Platz fürs Programm).
    // Links auf einem Sender klappt sie wieder auf.
    // Runde 20: Start mit dem Fokus auf den Sendern (Gruppen zu), wie bei TV-Playern; Links öffnet die Gruppen
    var catOpen by remember { mutableStateOf(false) }
    val catSelReq = remember { FocusRequester() }
    var catFocusReq by remember { mutableIntStateOf(0) }
    val catW by androidx.compose.animation.core.animateDpAsState(
        if (catOpen) 270.dp else 0.dp,
        if (Look.motion == 0) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(180), label = "catW"
    )
    LaunchedEffect(catFocusReq) {
        if (catFocusReq == 0) return@LaunchedEffect
        // Gewählte Gruppe erst in den sichtbaren Bereich holen, sonst gibt es sie in der Liste noch nicht (kein Fokus möglich)
        val si = names.indexOf(sel)
        if (si >= 0) runCatching { catState.scrollToItem(maxOf(si - 3, 0)) }
        repeat(10) {
            kotlinx.coroutines.delay(40)
            if (runCatching { catSelReq.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    SideEffect { ContentFocus.tab = 3 }
    SideEffect { LiveUi.groupsOpen = catOpen }
    DisposableEffect(Unit) { onDispose { LiveUi.groupsOpen = false } }
    // Auswahl zeigt auf eine gelöschte Liste: zurück zu „Alle Sender“
    LaunchedEffect(sel, myLists.size) { if (ChannelListLogic.idOfKey(sel) != null && curList == null) sel = "ALL" }

    // Sender zu einer eigenen Liste hinzufügen: in „Alle Sender“/Kategorien fügt OK den Sender hinzu (statt ihn zu starten)
    var addTo by remember { mutableStateOf<Int?>(null) }
    val addList = addTo?.let { id -> myLists.firstOrNull { it.id == id } }
    fun finishAdd() { val id = addTo; addTo = null; if (id != null) sel = ChannelListLogic.PREFIX + id }
    androidx.activity.compose.BackHandler(enabled = addList != null) { finishAdd() }

    // Ohne eigenes Fenster und ohne Tastatur: die Liste wird gleich mit Standardnamen angelegt (umbenennen geht jederzeit)
    fun newList(first: String?) {
        val id = ChannelLists.create(ctx, Lang.tr("Senderliste " + (myLists.size + 1)), first)
        sel = ChannelListLogic.PREFIX + id
        if (first == null) Toast.makeText(ctx, Lang.tr("Liste angelegt. Mit „Sender hinzufügen“ füllen."), Toast.LENGTH_SHORT).show()
    }

    // Markierter Sender bestimmt die Anzeige oben (Sendung und Vorschau).
    // Erst nach kurzem Verweilen wechseln (beim schnellen Durchblättern kein Laden von Logo, Programm und Vorschau)
    var focusedCh by remember { mutableStateOf<Item?>(null) }
    var focusTarget by remember { mutableStateOf<Item?>(null) }
    LaunchedEffect(focusTarget) { if (focusedCh != null) kotlinx.coroutines.delay(300); focusedCh = focusTarget }
    val shown = focusedCh ?: list.firstOrNull()
    // Vorschau nur, solange ein Sender in der Liste markiert ist (nicht beim Wechseln der Kategorie)
    val preview = remember { LivePreviewPlayer(ctx) }
    DisposableEffect(Unit) { onDispose { preview.release() } }
    fun open(ch: Item) { preview.stop(); ZapContext.list = list; ZapContext.key = sel; LiveMemory.setSel(ctx, sel); onOpen(ch) }
    Box(Modifier.fillMaxSize()) {
    // Zeitleiste: ab der laufenden halben Stunde, 2 Stunden breit; „jetzt“ wird alle 30 s nachgeführt
    val nowSec by produceState(System.currentTimeMillis() / 1000) {
        while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() / 1000 }
    }
    val ws = nowSec / 1800 * 1800
    Column(Modifier.fillMaxSize().background(Bg).padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // Links: Kategorien und Listen
            LazyColumn(
                Modifier.width(catW).fillMaxHeight().padding(end = if (catW > 0.dp) 16.dp else 0.dp).background(Color(0xFF1E2125))
                    .onFocusChanged { if (it.hasFocus) catOpen = true },
                state = catState, verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(names) { _, n ->
                    val ml = ChannelLists.byKey(n)
                    if (n == "GUIDE") {
                        // TV-Guide: startet den zuletzt gesehenen (sonst ersten) Sender der Auswahl und öffnet den Guide
                        CatRow("TV-Guide", -1, false, Modifier, onFocus = {}, onRight = { runCatching { firstChan.requestFocus() } }, onFav = {}, icon = AIcon.Grid) {
                            val src = list.ifEmpty { live }
                            val ch = History.ids.firstNotNullOfOrNull { id -> src.firstOrNull { it.id == id } } ?: src.firstOrNull()
                            if (ch != null) { preview.stop(); ZapContext.list = src; ZapContext.key = sel; AppNav.openGuide = true; onOpen(ch) }
                        }
                        return@itemsIndexed
                    }
                    val label = when {
                        n == FAV_CAT -> "Favoriten"
                        n == "ALL" -> "Alle Sender"
                        n == HIDDEN_CAT -> "Ausgeblendete Sender"
                        ml != null -> ml.name
                        else -> n
                    }
                    val icon = when {
                        n == FAV_CAT || n in CatFavs.list -> AIcon.Star
                        n == HIDDEN_CAT -> AIcon.EyeOff
                        ml != null -> AIcon.Lists
                        else -> null
                    }
                    val count = when {
                        n == FAV_CAT -> favs.size
                        n == "ALL" -> live.size
                        n == HIDDEN_CAT -> ChannelEdit.hiddenCount()
                        ml != null -> ChannelListLogic.resolve(ml, byId).size
                        else -> groups[n]?.size ?: 0
                    }
                    var listMenu by remember { mutableStateOf(false) }
                    var confirmDelete by remember { mutableStateOf(false) }
                    Box {
                        CatRow(
                            label, count, n == sel,
                            if (n == sel) Modifier.focusRequester(catSelReq).then(if (list.isEmpty()) Modifier.focusRequester(ContentFocus.req) else Modifier) else Modifier,
                            onFocus = { sel = n }, onRight = { runCatching { firstChan.requestFocus() } },
                            onFav = { if (ml != null) listMenu = true else if (n != FAV_CAT && n != "ALL" && n != HIDDEN_CAT) CatFavs.toggle(ctx, n) },
                            icon = icon, raw = ml != null
                        ) { runCatching { firstChan.requestFocus() } }
                        // Menü einer eigenen Liste (OK gedrückt halten): Sender hinzufügen, umbenennen, löschen
                        if (listMenu && ml != null) OptionPopup(
                            listOf("Sender hinzufügen", "Umbenennen", "Liste löschen"), -1, { listMenu = false },
                            listOf(AIcon.Plus, AIcon.Edit, AIcon.Close)
                        ) { i ->
                            listMenu = false
                            when (i) {
                                0 -> { addTo = ml.id; sel = "ALL" }
                                1 -> ask = NameAsk("Liste umbenennen", ml.name) { ChannelLists.rename(ctx, ml.id, it) }
                                else -> confirmDelete = true
                            }
                        }
                        if (confirmDelete && ml != null) OptionPopup(listOf("Löschen", "Abbrechen"), -1, { confirmDelete = false }, listOf(AIcon.Close, AIcon.Left)) { i ->
                            confirmDelete = false
                            if (i == 0) { ChannelLists.delete(ctx, ml.id); sel = "ALL"; LiveMemory.setSel(ctx, "ALL") }
                        }
                    }
                }
            }
            // Rechts: oben Vorschau und Sendung, darunter Zeitleiste und Sender
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    LivePreview(preview, focusedCh, Modifier.fillMaxHeight().aspectRatio(16f / 9f), fallback = shown)
                    LiveInfo(shown, Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(Modifier.height(14.dp))
                if (list.isNotEmpty()) TimelineHeader(ws, nowSec)
                if (addList != null) Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIcon(AIcon.Plus, Accent1, size = 20.dp)
                    Text(raw = true, text = Lang.tr("Zu „") + addList.name + Lang.tr("“ hinzufügen: OK auf einem Sender fügt ihn hinzu bzw. entfernt ihn wieder"),
                        color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    PillButton("Fertig", light = true) { finishAdd() }
                }
                if (list.isEmpty()) EmptyState(
                    if (sel == FAV_CAT) AIcon.Heart else AIcon.Live,
                    if (sel == FAV_CAT) "Noch keine Favoriten" else if (curList != null) "Diese Liste ist noch leer" else "Keine Sender",
                    when {
                        sel == FAV_CAT -> "Auf einem Sender OK gedrückt halten und „Zu Favoriten“ wählen."
                        curList != null -> "In „Alle Sender“ oder einer Kategorie auf einem Sender OK gedrückt halten und „Zu …“ wählen."
                        else -> "In dieser Kategorie gibt es keine Sender. Links eine andere Kategorie wählen."
                    },
                    action = if (curList != null) "Sender hinzufügen" else null,
                    actionModifier = Modifier.focusRequester(firstChan),
                    onAction = if (curList != null) ({ addTo = curList.id; sel = "ALL" }) else null
                ) else LazyColumn(state = chanState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    // In eigenen Listen wandert der Fokus beim Verschieben mit dem Sender mit (Schlüssel = Sender)
                    itemsIndexed(list, key = if (curList != null) { _, ch -> ch.id } else null) { i, ch ->
                        ChannelLine(
                            ch, ws, nowSec, (if (i == 0) Modifier.focusRequester(firstChan).focusRequester(ContentFocus.req) else Modifier)
                                .then(if (ch.id == restoreId) Modifier.focusRequester(restoreReq) else Modifier), curList,
                            onNewList = { newList(ch.id) },
                            onRename = { ask = NameAsk("Sender umbenennen", ChannelEdit.title(ch)) { ChannelEdit.rename(ctx, ch.id, it) } },
                            onLeft = { catOpen = true; catFocusReq++ },
                            onFocus = { focusTarget = ch; catOpen = false }
                        ) {
                            if (addList != null) {
                                val has = ch.id in addList.ids
                                if (has) ChannelLists.remove(ctx, addList.id, ch.id) else ChannelLists.add(ctx, addList.id, ch.id)
                                Toast.makeText(ctx, (if (has) Lang.tr("Entfernt: ") else Lang.tr("Hinzugefügt: ")) + ch.title, Toast.LENGTH_SHORT).show()
                            } else open(ch)
                        }
                    }
                }
            }
        }
    }
        ask?.let { a -> NameDialog(a.title, a.initial, onDismiss = { ask = null }) { a.onSave(it); ask = null } }
    }
}

/** Kleiner Dialog: Name eingeben (Bildschirmtastatur), Speichern oder Abbrechen. */
@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    androidx.activity.compose.BackHandler { onDismiss() }
    // Fokus auf „Speichern“, nicht ins Textfeld: sonst geht die Bildschirmtastatur sofort auf und „Zurück“ schließt erst sie, dann erst das Fenster
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(DS.RadiusL)).background(Surf).border(1.dp, DS.Overlay3, RoundedCornerShape(DS.RadiusL)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Field(text, { text = it.take(ChannelListLogic.MAX_NAME) }, "Name", onDone = { onSave(text) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Speichern", Modifier.focusRequester(fr), light = true) { onSave(text) }
                PillButton("Abbrechen") { onDismiss() }
            }
        }
    }
}

@Composable
private fun CatRow(
    label: String, count: Int, selected: Boolean, modifier: Modifier,
    onFocus: () -> Unit, onRight: () -> Unit, onFav: () -> Unit, icon: AIcon? = null, raw: Boolean = false, onClick: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusS)
    // TV-Stil: große, ruhige Zeilen; markiert = helle Fläche mit dunkler Schrift, gewählte Gruppe = leicht getönt
    val ink = if (f) DS.FocusInk else Color.White
    Row(
        modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape)
            .background(if (f) TvFocusFill else if (selected) Color(0x1AFFFFFF) else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && e.nativeKeyEvent.repeatCount == 0) { onRight(); true } else false
            }
            .favKeys { onFav() }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) AppIcon(icon, if (icon == AIcon.Star) DS.Gold else ink, size = 18.dp)
            Text(label, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, raw = raw)
        }
        // Anzahl nur beim markierten Eintrag (die Liste bleibt ruhig)
        if (count >= 0 && f) Text("$count", color = DS.FocusInk.copy(alpha = 0.6f), fontSize = 14.sp)
    }
}

/** Breite der Senderspalte links in der Liste (Logo + Name); rechts davon die Zeitleiste. */
private val CH_W = 280.dp
/** Sichtbarer Zeitraum der Zeitleiste in Sekunden. */
private const val SPAN = 7200L

/** Kopf der Zeitleiste: Datum und Uhrzeit über den Sendern, darüber die halben Stunden. */
@Composable
private fun TimelineHeader(ws: Long, nowSec: Long) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(raw = true, text = longDay(nowSec) + ", " + hhmm(nowSec), color = Accent2, fontSize = 17.sp,
                modifier = Modifier.width(CH_W).padding(start = 4.dp), maxLines = 1)
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val w = maxWidth
                var t = ws
                while (t < ws + SPAN) {
                    Text(raw = true, text = hhmm(t), color = Tok.TextMid, fontSize = 16.sp,
                        modifier = Modifier.offset(x = w * ((t - ws).toFloat() / SPAN)).align(Alignment.CenterStart))
                    t += 1800
                }
                // Punkt über der „Jetzt“-Linie
                Box(Modifier.offset(x = w * ((nowSec - ws).toFloat() / SPAN) - 4.dp).align(Alignment.BottomStart)
                    .size(8.dp).clip(RoundedCornerShape(50)).background(Accent2))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DS.Hairline))
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ChannelLine(
    item: Item, ws: Long, nowSec: Long, modifier: Modifier, inList: ChList?, onNewList: () -> Unit, onRename: () -> Unit, onLeft: () -> Unit,
    onFocus: () -> Unit = {}, onClick: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // Neu zeichnen, wenn der Sender umbenannt wurde
    @Suppress("UNUSED_VARIABLE") val editVersion = ChannelEdit.version
    val epg by produceState(emptyList<EpgEntry>(), item.id) { value = Epg.now(item) }
    Box {
    if (menu) ChannelMenu(item, inList, onNewList, onRename, next = epg.firstOrNull { it.start > nowSec }) { menu = false }
    val cell = RoundedCornerShape(4.dp)
    // Schlicht wie eine Programmzeitschrift: links Sender, rechts die Sendungen auf der Zeitleiste.
    // Markiert = hellere Fläche (kein Vergrößern, kein Schatten).
    // TV-Stil: markierte Zeile mit hellem Rahmen um Sender und Programm, Sendername in der Hervorhebungsfarbe
    Row(
        modifier.fillMaxWidth().height(46.dp)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { e ->
                // Links: Gruppenliste aufklappen
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { onLeft(); true } else false
            }
            .menuKeys(onMenu = { menu = true }, onLong = { menu = true })
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.width(CH_W).fillMaxHeight().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(width = 54.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                if (item.logo != null) AsyncImage(item.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text(raw = true, text = item.title.take(2).uppercase(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(raw = true, text = ChannelEdit.title(item), color = if (f) Accent2 else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (Library.isFav(item.id)) AppIcon(AIcon.Star, DS.Gold, size = 14.dp)
            if (Catchup.version >= 0 && Catchup.has(item)) AppIcon(AIcon.Catchup, Accent2, size = 14.dp)
            if (DeadLinks.isDead(item.url)) AppIcon(AIcon.Warning, DS.Gold, size = 14.dp)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(vertical = 2.dp)) {
            val w = maxWidth
            val shownE = epg.filter { it.end > ws && it.start < ws + SPAN }
            if (shownE.isEmpty()) {
                // Kein Programm: eine Fläche mit dem Sendernamen über die ganze Breite
                Box(Modifier.fillMaxSize().padding(start = 3.dp).clip(cell).background(if (f) TvFocusFill else TvCell).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text(raw = true, text = ChannelEdit.title(item), color = if (f) DS.FocusInk else Tok.TextMid, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else shownE.forEach { e ->
                val a = ((maxOf(e.start, ws) - ws).toFloat() / SPAN).coerceIn(0f, 1f)
                val b = ((minOf(e.end, ws + SPAN) - ws).toFloat() / SPAN).coerceIn(0f, 1f)
                val running = nowSec in e.start until e.end
                // Laufende Sendung der markierten Zeile: hell mit dunkler Schrift (wie die Markierung im Menü)
                val lit = running && f
                Box(
                    Modifier.offset(x = w * a).width(w * (b - a)).fillMaxHeight().padding(start = 3.dp).clip(cell)
                        .background(if (lit) TvFocusFill else if (running) TvCellNow else TvCell)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(raw = true, text = e.title, color = if (lit) DS.FocusInk else Color(0xFFD0D3D8), fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
            // Linie für „jetzt“
            Box(Modifier.offset(x = w * ((nowSec - ws).toFloat() / SPAN)).width(1.dp).fillMaxHeight().background(Accent2.copy(alpha = 0.7f)))
        }
    }
    }
}

/**
 * Menü eines Senders (Menü-Taste oder OK gedrückt halten): Favorit, Erinnerung an die nächste Sendung, eigene Listen
 * (hinzufügen/entfernen), in der Liste verschieben, neue Liste, umbenennen, ausblenden.
 * [onNewList] = null blendet „Neue Senderliste“ aus (Player); [onRename] = null blendet Umbenennen und Ausblenden aus.
 * [next] = nächste Sendung des Senders (für die Erinnerung).
 */
@Composable
fun ChannelMenu(
    item: Item, inList: ChList?, onNewList: (() -> Unit)?, onRename: (() -> Unit)? = null, next: EpgEntry? = null,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    // Abschnitt 1: nächste Sendung (Erinnerung)
    val prog = ArrayList<SideEntry>()
    if (next != null && ReminderLogic.canRemind(next.start, System.currentTimeMillis() / 1000)) {
        val set = Reminders.isSet(item.id, next.start)
        prog.add(SideEntry(if (set) "Erinnerung löschen" else "Erinnern an", if (set) AIcon.BellOff else AIcon.Bell) {
            val on = Reminders.toggle(ctx, Reminder(item.id, ChannelEdit.title(item), next.title, next.start, next.end))
            if (on) Reminders.askPermission(ctx as? android.app.Activity)
            Toast.makeText(ctx, if (on) Lang.tr("Erinnerung gesetzt – ${Reminders.leadMin} Min vorher") else Lang.tr("Erinnerung gelöscht"), Toast.LENGTH_SHORT).show()
        })
    }
    // Abschnitt 2: der Sender (Überschrift = Sendername)
    val chan = ArrayList<SideEntry>()
    chan.add(SideEntry(if (Library.isFav(item.id)) "Aus Favoriten entfernen" else "Zu Favoriten", AIcon.Star) { Library.toggleFav(ctx, item.id) })
    if (onRename != null) {
        chan.add(SideEntry("Umbenennen …", AIcon.Edit, onPick = onRename))
        val hidden = ChannelEdit.isHidden(item.id)
        chan.add(SideEntry(if (hidden) "Wieder einblenden" else "Sender ausblenden", if (hidden) AIcon.Eye else AIcon.EyeOff) {
            ChannelEdit.toggleHidden(ctx, item.id)
            Toast.makeText(ctx, Lang.tr(if (hidden) "Sender wieder eingeblendet" else "Sender ausgeblendet. Zurückholen: „Ausgeblendete Sender“ links."), Toast.LENGTH_SHORT).show()
        })
    }
    // Abschnitt 3: eigene Senderlisten
    val lists = ArrayList<SideEntry>()
    if (inList != null) {
        lists.add(SideEntry("Nach oben", AIcon.Up) { ChannelLists.move(ctx, inList.id, item.id, -1) })
        lists.add(SideEntry("Nach unten", AIcon.Down) { ChannelLists.move(ctx, inList.id, item.id, 1) })
        lists.add(SideEntry("Aus „${inList.name}“ entfernen", AIcon.Close) { ChannelLists.remove(ctx, inList.id, item.id) })
    }
    for (l in ChannelLists.lists.toList()) {
        if (l.id == inList?.id) continue
        if (item.id in l.ids) lists.add(SideEntry("In „${l.name}“ – entfernen", AIcon.Check) { ChannelLists.remove(ctx, l.id, item.id) })
        else lists.add(SideEntry("Zu „${l.name}“", AIcon.Plus) { ChannelLists.add(ctx, l.id, item.id) })
    }
    if (onNewList != null) lists.add(SideEntry("Neue Senderliste …", AIcon.Plus, onPick = onNewList))
    SideMenu(
        listOf(
            SideSection(next?.let { hhmm(it.start) + "  " + it.title }, prog),
            SideSection(ChannelEdit.title(item), chan),
            SideSection(Lang.tr("Senderlisten"), lists)
        ),
        onClose
    )
}

/**
 * Erinnerung, während die App offen ist (auch im Player): „In 5 Min: Tagesschau · Das Erste“ mit Umschalten/Schließen.
 * Verschwindet nach einer Minute von selbst.
 */
@Composable
fun ReminderCard(r: Reminder, modifier: Modifier, onClose: () -> Unit, onSwitch: () -> Unit) {
    val fr = remember { FocusRequester() }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    androidx.activity.compose.BackHandler { onClose() }
    LaunchedEffect(r.key) {
        kotlinx.coroutines.delay(150)
        runCatching { fr.requestFocus() }
        repeat(4) { kotlinx.coroutines.delay(15_000); nowSec = System.currentTimeMillis() / 1000 }
        onClose()
    }
    val shape = RoundedCornerShape(DS.RadiusL)
    Row(
        modifier.widthIn(max = 760.dp).clip(shape).background(Tok.GlassStrong).border(1.dp, Accent2, shape).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppIcon(AIcon.Bell, Accent2, size = 30.dp)
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(raw = true, text = Reminders.whenText(r, nowSec), color = Accent2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(raw = true, text = r.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(raw = true, text = r.chTitle, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PillButton("Umschalten", Modifier.focusRequester(fr), light = true) { onSwitch() }
        PillButton("Schließen") { onClose() }
    }
}

/** TiviMate: Im TV-Guide ist die Symbolleiste links ausgeblendet; sie erscheint erst zusammen mit den Gruppen („Links“). */
object LiveUi {
    var groupsOpen by mutableStateOf(false)
}

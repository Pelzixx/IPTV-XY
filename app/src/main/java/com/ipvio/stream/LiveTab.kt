package com.ipvio.stream

import kotlinx.coroutines.launch
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LiveTab(live: List<Item>, groups0: Map<String, List<Item>>, favs: List<Item>, onOpen: (Item) -> Unit) {
    val ctx = LocalContext.current
    // Optik > Gruppen > „Kanäle aus ausgeblendeten Gruppen anzeigen“: die Sender bleiben in „Alle Kanäle“, die Gruppen selbst fehlen
    val hidAll = Tm.b(Tm.GRP_HIDDEN_ALL, false)
    val mainPl = PlaylistStore.lists.getOrNull(PlaylistStore.active)?.name.orEmpty()
    val groups = if (hidAll && PlOpts.anyHidden) groups0.filterKeys { g -> !PlOpts.isHidden(mainPl, g) } else groups0
    WatchTime.init(ctx)
    CatFavs.init(ctx)
    ChannelLists.init(ctx)
    ChannelEdit.init(ctx)
    // Liest den Stand, damit Ausblenden/Umbenennen sofort sichtbar wird
    @Suppress("UNUSED_VARIABLE") val editVersion = ChannelEdit.version
    // Zuletzt gewählte Kategorie / Liste (Runde 14): nach dem Zurück aus dem Player und nach einem Neustart wieder dieselbe
    var sel by remember {
        val saved = LiveMemory.sel(ctx)?.takeIf { k ->
            (k == FAV_CAT && favs.isNotEmpty()) || k == "ALL" || k in groups || ChannelLists.byKey(k) != null ||
                (PlGroups.isPl(k) && PlaylistStore.multiPlaylist)
        }
        mutableStateOf(saved ?: if (favs.isNotEmpty()) FAV_CAT else "ALL")
    }
    val favCats = CatFavs.list.filter { it in groups }
    val myLists = ChannelLists.lists.toList()
    val byId = remember(live) { live.associateBy { it.id } }
    // Reihenfolge: TV-Guide, Favoriten, eigene Senderlisten, alle Sender, Kategorien (neue Liste: Menü eines Senders)
    // „Ausgeblendete Sender“ nur, wenn es welche gibt (dort holt man sie per Menü zurück)
    val hiddenCat = if (ChannelEdit.hiddenCount() > 0) listOf(HIDDEN_CAT) else emptyList()
    // TiviMate: mehrere Wiedergabelisten aktiv = je Liste ein Abschnitt mit ihren Ordnern (statt einer gemischten Gruppenliste)
    val multi = PlaylistStore.multiPlaylist
    val sections = remember(live, multi, hidAll, PlOpts.version) { if (multi) PlGroups.sections(live).map { s -> if (hidAll) PlGroups.Section(s.playlist, s.count, s.groups.filter { (g, _) -> !PlOpts.isHidden(s.playlist, g) }) else s } else emptyList() }
    val plCounts = remember(sections) {
        HashMap<String, Int>().also { m -> sections.forEach { s -> m[PlGroups.keyP(s.playlist)] = s.count; s.groups.forEach { (g, c) -> m[PlGroups.keyG(s.playlist, g)] = c } } }
    }
    // Optik > Gruppen (TiviMate): Sortierung und welche Kategorien oben stehen
    val grpSort = Tm.i(Tm.GRP_SORT, 0)
    val showFavCat = Tm.b(Tm.GRP_FAVS, true)
    val showAllCat = Tm.b(if (multi) Tm.GRP_ALL_PLAYLISTS else Tm.GRP_ALL, true)
    val names = listOf("GUIDE") + (if (showFavCat) listOf(FAV_CAT) else emptyList()) + myLists.map { ChannelListLogic.key(it) } +
        (if (showAllCat) listOf("ALL") else emptyList()) + hiddenCat +
        // TiviMate: Wiedergabelisten als Kopfzeile, auf- und zuklappbar (eingeklappt = Ordner ausgeblendet)
        if (multi) sections.flatMap { s -> listOf(PlGroups.keyP(s.playlist)) +
            if (PlGroups.collapsed(s.playlist)) emptyList() else Tm.sorted(s.groups, grpSort) { it.first }.map { PlGroups.keyG(s.playlist, it.first) } }
        else favCats + Tm.sorted(groups.keys.filter { it !in favCats }, grpSort) { it }
    // Gespeicherte Gruppe aus der Zeit ohne Abschnitte („Music“) auf die Gruppe der Wiedergabeliste umstellen
    LaunchedEffect(multi, sections) {
        if (multi && sel !in names && sel in groups) sel = names.firstOrNull { it.startsWith(PlGroups.G) && PlGroups.label(it) == sel } ?: "ALL"
    }
    val curList = ChannelLists.byKey(sel)
    // TiviMate: Sichtbarkeit (1), Sperren (2) oder Favoriten (3) verwalten: OK schaltet je Sender um
    var manage by remember { mutableIntStateOf(0) }
    var moveFor by remember { mutableStateOf<Item?>(null) }
    // Kanäle auswählen (TiviMate): markierte Sender, Menü für Auswahl bzw. Kopieren/Verschieben
    val picked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var pickMenu by remember { mutableIntStateOf(0) }   // 1 = Auswahloptionen, 2 = Kopieren/Verschieben
    LaunchedEffect(manage) { if (manage != 6) { picked.clear(); pickMenu = 0 } }
    androidx.activity.compose.BackHandler(enabled = manage != 0) { manage = 0 }
    val showHidden = sel == HIDDEN_CAT
    // Nur neu filtern, wenn sich Auswahl oder Daten ändern – nicht bei jedem Fokuswechsel (tausende Sender)
    // Gruppenoptionen (TiviMate): eigene Sortierung je Gruppe, sonst die allgemeine
    val chSort = GroupOpts.sort(sel).takeIf { it >= 0 } ?: Tm.i(Tm.EPG_CH_SORT, 0)
    val list = remember(sel, live, favs, groups, curList, editVersion, showHidden, multi, chSort, manage, ChOrder.version) { ChannelEditLogic.visible(
        when {
            sel == FAV_CAT -> favs
            sel == "ALL" || showHidden -> live
            curList != null -> ChannelListLogic.resolve(curList, byId)
            PlGroups.isPl(sel) -> PlGroups.resolve(sel, live).orEmpty()
            else -> groups[sel].orEmpty()
        },
        { it.id }, ChannelEdit::isHidden, showHidden || manage == 1
    ).let { l -> if (curList != null) l else (when (chSort) { 3 -> l.sortedByDescending { WatchTime.sec(it.id) }; 4 -> ChOrder.apply(sel, l); else -> Tm.sorted(l, chSort) { ChannelEdit.title(it) } }) } }
    // Optik > EPG: Darstellung der Zeilen, einmal je Liste
    val look = GuideLook(
        Tm.b(Tm.EPG_NUMBERS, false), Tm.b(Tm.EPG_NAMES, true), Tm.b(Tm.EPG_TWO_LINE_NAMES, false), Tm.b(Tm.EPG_CATCHUP_ICON, true),
        if (Tm.b(Tm.EPG_HL_CURRENT, true)) History.ids.firstOrNull() else null, Tm.b(Tm.EPG_TWO_LINE_TITLES, false),
        Tm.b(Tm.EPG_MARK_RUNNING, true), Tm.b(Tm.EPG_MARK_PROGRESS, true), Tm.b(Tm.EPG_NOW_FULL, true)
    )
    val bivSpec = if (Tm.b(Tm.EPG_SCROLL_ANIM, false)) androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current else InstantSpec
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
    var grpSortFor by remember { mutableStateOf<String?>(null) }
    // Gruppenliste links: offen, solange man dort ist; beim Durchblättern der Sender eingeklappt (mehr Platz fürs Programm).
    // Links auf einem Sender klappt sie wieder auf.
    // Runde 20: Start mit dem Fokus auf den Sendern (Gruppen zu), wie bei TV-Playern; Links öffnet die Gruppen
    // TiviMate: über das Menü geöffnet startet der TV-Bereich mit der Gruppenliste (Fokus auf der aktuellen Gruppe);
    // zurück aus dem Player bleibt der Fokus auf dem Sender
    val startInGroups = remember { LastFocus.pending == null }
    var catOpen by remember { mutableStateOf(startInGroups) }
    val catSelReq = remember { FocusRequester() }
    val catFirstReq = remember { FocusRequester() }
    var catFocusReq by remember { mutableIntStateOf(0) }
    // Beim Öffnen des Reiters ohne Animation (sonst wird der ganze Guide in jedem Bild neu ausgemessen)
    var catAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(400); catAnim = true }
    val catW by androidx.compose.animation.core.animateDpAsState(
        if (catOpen) 270.dp else 0.dp,
        if (Look.motion == 0 || !catAnim) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(180), label = "catW"
    )
    LaunchedEffect(catFocusReq) {
        if (catFocusReq == 0) return@LaunchedEffect
        // Gruppe liegt in einer zugeklappten Wiedergabeliste: erst aufklappen, sonst gibt es kein Ziel und Links tut nichts
        if (sel.startsWith(PlGroups.G)) PlGroups.playlistOfKey(sel)?.let { pl -> if (PlGroups.collapsed(pl)) { PlGroups.toggle(ctx, pl); kotlinx.coroutines.delay(60) } }
        // Gewählte Gruppe erst in den sichtbaren Bereich holen, sonst gibt es sie in der Liste noch nicht (kein Fokus möglich)
        val si = names.indexOf(sel)
        if (si >= 0) runCatching { catState.scrollToItem(maxOf(si - 3, 0)) }
        repeat(10) {
            kotlinx.coroutines.delay(40)
            if (runCatching { catSelReq.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        // Notlösung: an den Anfang der Gruppenliste (TV-Guide)
        runCatching { catState.scrollToItem(0) }
        repeat(5) { kotlinx.coroutines.delay(40); if (runCatching { catFirstReq.requestFocus() }.isSuccess) return@LaunchedEffect }
    }
    // Start mit Gruppen: gewählte Gruppe ins Bild holen und markieren
    LaunchedEffect(Unit) { if (startInGroups) catFocusReq++ }
    // Rechts aus den Gruppen: Senderliste nach oben holen (sonst gibt es den ersten Sender noch nicht) und so lange
    // fokussieren, bis ein Sender den Fokus hat (dann klappen die Gruppen zu)
    val chanScope = rememberCoroutineScope()
    // Gruppe erst nach kurzem Verweilen anzeigen: beim schnellen Blättern durch die Gruppen wird nicht jede Senderliste geladen
    // Kein Compose-Zustand (sonst baute jeder Schritt die ganze Seite neu auf): nur ein Hintergrund-Timer
    val selTimer = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val pendingSel = remember { arrayOfNulls<String>(1) }
    fun hoverCat(n: String) {
        pendingSel[0] = n
        selTimer[0]?.cancel()
        selTimer[0] = chanScope.launch { kotlinx.coroutines.delay(280); sel = n; pendingSel[0] = null }
    }
    fun toChannels() {
        selTimer[0]?.cancel()
        pendingSel[0]?.let { sel = it; pendingSel[0] = null }
        chanScope.launch {
            runCatching { chanState.scrollToItem(0) }
            repeat(15) {
                runCatching { firstChan.requestFocus() }
                kotlinx.coroutines.delay(50)
                if (!catOpen) return@launch
            }
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
    // Markierter Sender: nur der Kopf (Vorschau, Sendung) liest ihn – die Liste wird beim Blättern nicht neu aufgebaut
    val focus = remember { GuideFocusState() }
    var tOff by remember { mutableIntStateOf(0) }
    // Optik > EPG „Verwende Zurück, um zu den aktuellen Programmen zurückzukehren“
    androidx.activity.compose.BackHandler(enabled = tOff != 0 && Tm.b(Tm.EPG_BACK_NOW, true)) { tOff = 0 }
    // Vorschau nur, solange ein Sender in der Liste markiert ist (nicht beim Wechseln der Kategorie)
    val preview = remember { PreviewHolder.player(ctx) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    fun open(ch: Item) { preview.stop(); ZapContext.list = list; ZapContext.key = sel; LiveMemory.setSel(ctx, sel); onOpen(ch) }
    // Sender, der gerade läuft (zuletzt gesehen) – für Vorschau und ▶
    val playingCh = History.ids.firstOrNull()?.let { id -> list.firstOrNull { it.id == id } ?: PlaylistStore.channels.firstOrNull { it.id == id && it.kind == Kind.LIVE } }
    // Fernbedienung > EPG: Aktionen, die die ganze Liste betreffen
    val fmGuide = androidx.compose.ui.platform.LocalFocusManager.current
    fun guideAct(a: EAct): Boolean = when (a) {
        EAct.PAGE_UP, EAct.PAGE_DOWN -> {
            val dir = if (a == EAct.PAGE_UP) androidx.compose.ui.focus.FocusDirection.Up else androidx.compose.ui.focus.FocusDirection.Down
            val n = (chanState.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)
            repeat(n) { fmGuide.moveFocus(dir) }
            true
        }
        EAct.TO_PLAYER -> { History.ids.firstNotNullOfOrNull { id -> list.firstOrNull { it.id == id } ?: PlaylistStore.channels.firstOrNull { it.id == id && it.kind == Kind.LIVE } }?.let { open(it) }; true }
        EAct.SEARCH -> { AppNav.tabNow = MenuLogic.SEARCH; true }
        else -> false
    }
    // OK auf einer gewählten Sendung (TiviMate): vergangen = Archiv, künftig = Erinnerung, laufend = Sender
    fun progOk(ch: Item, e: EpgEntry) = progAction(ctx, ch, e) { open(it) }
    // Vorschaumodus > „Timeout für Vollbildumschaltung“ (TiviMate): ohne Tastendruck nach N Sekunden ins Vollbild
    var idleTick by remember { mutableIntStateOf(0) }
    var guideHasFocus by remember { mutableStateOf(false) }
    val fsTo = Tm.i(Tm.CV_FS_TIMEOUT, 0)
    LaunchedEffect(idleTick, playingCh?.id, fsTo, manage, guideHasFocus) {
        // nur solange der Fokus im TV-Guide liegt (nicht in der Seitenleiste, in Menüs oder Dialogen)
        if (fsTo <= 0 || !AppSettings.livePreview || manage != 0 || !guideHasFocus) return@LaunchedEffect
        val ch = (if (Tm.b(Tm.CV_AUTOPLAY, false)) focus.shown else null) ?: playingCh ?: return@LaunchedEffect
        kotlinx.coroutines.delay(fsTo * 1000L)
        open(ch)
    }
    Box(Modifier.fillMaxSize().onFocusChanged { guideHasFocus = it.hasFocus }.onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) idleTick++; false }) {
    // Zeitleiste: ab der laufenden halben Stunde, 2 Stunden breit; „jetzt“ wird alle 30 s nachgeführt
    val nowSec by produceState(System.currentTimeMillis() / 1000) {
        while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() / 1000 }
    }
    // Zeitfenster: ab der laufenden halben Stunde, beim Weiterblättern durch die Sendungen nach rechts verschoben
    val ws = nowSec / 1800 * 1800 + tOff * 1800L
    Column(Modifier.fillMaxSize().background(Bg).padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        // Ohne Scroll-Animation (Gruppen und Sender): beim schnellen Blättern staut sich nichts
        CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides bivSpec) {
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
                        CatRow("TV-Guide", -1, false, Modifier, onFocus = {}, onRight = { toChannels() }, onFav = {}, icon = AIcon.Grid) {
                            val src = list.ifEmpty { live }
                            val ch = History.ids.firstNotNullOfOrNull { id -> src.firstOrNull { it.id == id } } ?: src.firstOrNull()
                            if (ch != null) { preview.stop(); ZapContext.list = src; ZapContext.key = sel; AppNav.openGuide = true; onOpen(ch) }
                        }
                        return@itemsIndexed
                    }
                    val label = when {
                        n == FAV_CAT -> "Favoriten"
                        n == "ALL" -> if (multi) "Alle Playlists" else "Alle Sender"
                        n == HIDDEN_CAT -> "Ausgeblendete Sender"
                        ml != null -> ml.name
                        else -> GroupOpts.show(PlGroups.label(n) ?: n)
                    }
                    val icon = when {
                        n == FAV_CAT || n in CatFavs.list -> AIcon.Star
                        n == HIDDEN_CAT -> AIcon.EyeOff
                        ml != null -> AIcon.Lists
                        n.startsWith(PlGroups.P) -> AIcon.Layers
                        else -> null
                    }
                    val count = when {
                        n == FAV_CAT -> favs.size
                        n == "ALL" -> live.size
                        n == HIDDEN_CAT -> ChannelEdit.hiddenCount()
                        ml != null -> ChannelListLogic.resolve(ml, byId).size
                        PlGroups.isPl(n) -> plCounts[n] ?: 0
                        else -> groups[n]?.size ?: 0
                    }
                    var listMenu by remember { mutableStateOf(false) }
                    var grpMenu by remember { mutableStateOf(false) }
                    var confirmDelete by remember { mutableStateOf(false) }
                    Box {
                        CatRow(
                            label, count, n == sel,
                            (if (n == "GUIDE") Modifier.focusRequester(catFirstReq) else Modifier).then(
                                if (n == sel) Modifier.focusRequester(catSelReq).then(if (list.isEmpty() || startInGroups) Modifier.focusRequester(ContentFocus.req) else Modifier) else Modifier),
                            onFocus = { hoverCat(n) }, onRight = { toChannels() },
                            onFav = { if (ml != null) listMenu = true else if (n != FAV_CAT && n != "ALL" && n != HIDDEN_CAT && n != "GUIDE" && !n.startsWith(PlGroups.P)) grpMenu = true },
                            icon = if (n.startsWith(PlGroups.P)) null else if (ChLock.version >= 0 && ChLock.isGroupLocked(PlGroups.label(n) ?: n)) AIcon.Lock else icon, raw = ml != null || PlGroups.isPl(n), header = n.startsWith(PlGroups.P),
                            indent = n.startsWith(PlGroups.G),
                            expanded = if (n.startsWith(PlGroups.P)) !PlGroups.collapsed(PlGroups.label(n) ?: "") else null
                        ) {
                            // Optik > Gruppen > „Beim Antippen einer Wiedergabeliste“ (TiviMate): auf-/zuklappen oder alle Kanäle der Liste
                            if (n.startsWith(PlGroups.P) && Tm.i(Tm.GRP_PL_TAP, 0) == 0) PlGroups.toggle(ctx, PlGroups.label(n) ?: "")
                            else if (n.startsWith(PlGroups.P)) { sel = n; toChannels() }
                            else toChannels()
                        }
                        // Menü einer Gruppe (OK gedrückt halten, TiviMate): Favorit, sperren, ausblenden
                        if (grpMenu) {
                            val gName = PlGroups.label(n) ?: n
                            val plName = PlGroups.playlistOfKey(n) ?: PlaylistStore.lists.getOrNull(PlaylistStore.active)?.name.orEmpty()
                            val isFav = n in CatFavs.list
                            val locked = ChLock.isGroupLocked(gName)
                            SideMenu(listOf(SideSection(gName, listOf(
                                SideEntry(if (isFav) "Aus den Favoriten entfernen" else "Zu den Favoriten hinzufügen", AIcon.Star) { CatFavs.toggle(ctx, n) },
                                SideEntry(if (locked) "Gruppe entsperren" else "Gruppe sperren", AIcon.Lock) {
                                    if (!Parental.pinSet) Toast.makeText(ctx, Lang.tr("Bitte zuerst unter Einstellungen > Kindersicherung eine PIN festlegen."), Toast.LENGTH_LONG).show()
                                    else Toast.makeText(ctx, Lang.tr(if (ChLock.toggleGroup(ctx, gName)) "Gruppe gesperrt" else "Gruppe entsperrt"), Toast.LENGTH_SHORT).show()
                                },
                                SideEntry("Gruppe ausblenden", AIcon.EyeOff) {
                                    PlOpts.setHidden(ctx, plName, gName, true)
                                    Toast.makeText(ctx, Lang.tr("Gruppe ausgeblendet. Zurückholen: Wiedergabeliste > Gruppen verwalten."), Toast.LENGTH_LONG).show()
                                }
                            )), SideSection(Lang.tr("Gruppenoptionen"), listOfNotNull(
                                SideEntry("Umbenennen …", AIcon.Edit) { ask = NameAsk("Gruppe umbenennen", GroupOpts.show(gName)) { GroupOpts.rename(ctx, gName, if (it.trim() == gName) "" else it) } },
                                if (GroupOpts.isRenamed(gName)) SideEntry("Gruppennamen wiederherstellen") { GroupOpts.rename(ctx, gName, "") } else null,
                                SideEntry("Sortierung", AIcon.Sliders) { grpSortFor = n }
                            ))), { grpMenu = false })
                        }
                        // Sortierung nur für diese Gruppe (TiviMate: überschreibt die allgemeine Sortierung)
                        if (grpSortFor == n) {
                            val cur = GroupOpts.sort(n)
                            val names = listOf("Wie allgemein eingestellt", "Reihenfolge in der Playlist", "Name A–Z", "Name Z–A", "nach Häufigkeit der Sehdauer", "Manuell")
                            SideMenu(listOf(SideSection(Lang.tr("Sortierung"), names.mapIndexed { i, s ->
                                SideEntry(s, if (cur == i - 1) AIcon.Check else null) { GroupOpts.setSort(ctx, n, i - 1) }
                            })), { grpSortFor = null })
                        }
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
                    GuideTop(focus, list.firstOrNull(), preview, playingCh)
                }
                Spacer(Modifier.height(14.dp))
                if (list.isNotEmpty()) TimelineHeader(ws, nowSec)
                if (manage == 6) Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIcon(AIcon.Check, Accent1, size = 20.dp)
                    Text(raw = true, text = Lang.tr("Kanäle auswählen") + ": " + picked.size + " " + Lang.tr("ausgewählt"),
                        color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    PillButton("Auswahl") { pickMenu = 1 }
                    PillButton("Kopieren / Verschieben") { if (picked.isEmpty()) Toast.makeText(ctx, Lang.tr("Keine Kanäle ausgewählt"), Toast.LENGTH_SHORT).show() else pickMenu = 2 }
                    PillButton("Fertig", light = true) { manage = 0 }
                }
                if (manage != 0 && manage != 6) Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIcon(when (manage) { 1 -> AIcon.EyeOff; 2 -> AIcon.Lock; 4 -> AIcon.Lists; else -> AIcon.Star }, Accent1, size = 20.dp)
                    Text(Lang.tr(when (manage) { 1 -> "Sichtbarkeit verwalten: OK blendet einen Sender aus bzw. wieder ein"; 2 -> "Sperren verwalten: OK sperrt einen Sender bzw. entsperrt ihn"; 4 -> "Kanäle neu anordnen: OK auf einem Sender verschiebt ihn"; else -> "Favoriten verwalten: OK fügt einen Sender hinzu bzw. entfernt ihn" }),
                        raw = true, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    PillButton("Fertig", light = true) { manage = 0 }
                }
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
                ) else BoxWithConstraints(Modifier.fillMaxSize()) {
                    val tlW = maxWidth - CH_W
                    // Ohne Scroll-Animation: beim Halten der Taste staut sich nichts (wie in TV-Playern)
                    CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides bivSpec) {
                    LazyColumn(state = chanState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    // In eigenen Listen wandert der Fokus beim Verschieben mit dem Sender mit (Schlüssel = Sender)
                    itemsIndexed(list, key = if (curList != null) { _, ch -> ch.id } else null) { i, ch ->
                        ChannelLine(
                            ch, i + 1, look, ws, nowSec, tlW, (if (i == 0) Modifier.focusRequester(firstChan).then(if (startInGroups) Modifier else Modifier.focusRequester(ContentFocus.req)) else Modifier)
                                .then(if (ch.id == restoreId) Modifier.focusRequester(restoreReq) else Modifier), curList,
                            onNewList = { newList(ch.id) },
                            onRename = { ask = NameAsk("Sender umbenennen", ChannelEdit.title(ch)) { ChannelEdit.rename(ctx, ch.id, it) } },
                            onLeft = { catOpen = true; catFocusReq++ },
                            onFocus = { focus.target = ch; catOpen = false },
                            onProg = { focus.prog = it }, onShift = { d -> tOff = (tOff + d).coerceIn(-14 * 48, 96) }, onProgOk = { e -> progOk(ch, e) },
                            onKeyAct = { a -> guideAct(a) }, onManage = { m -> if (m == 5) { ChOrder.reset(ctx, sel); Toast.makeText(ctx, Lang.tr("Positionen zurückgesetzt"), Toast.LENGTH_SHORT).show() } else manage = m },
                            picked = if (manage == 6) ch.id in picked else null
                        ) {
                            if (manage != 0) {
                                when (manage) {
                                    4 -> moveFor = ch
                                    6 -> if (ch.id in picked) picked.remove(ch.id) else picked.add(ch.id)
                                    1 -> ChannelEdit.toggleHidden(ctx, ch.id)
                                    2 -> if (Parental.pinSet) ChLock.toggle(ctx, ch.id) else Toast.makeText(ctx, Lang.tr("Bitte zuerst unter Einstellungen > Kindersicherung eine PIN festlegen."), Toast.LENGTH_LONG).show()
                                    else -> Library.toggleFav(ctx, ch.id)
                                }
                            } else if (addList != null) {
                                val has = ch.id in addList.ids
                                if (has) ChannelLists.remove(ctx, addList.id, ch.id) else ChannelLists.add(ctx, addList.id, ch.id)
                                Toast.makeText(ctx, (if (has) Lang.tr("Entfernt: ") else Lang.tr("Hinzugefügt: ")) + ch.title, Toast.LENGTH_SHORT).show()
                            } else if (Tm.b(Tm.CV_STAY, true) && AppSettings.livePreview && !Tm.b(Tm.CV_AUTOPLAY, false) && playingCh?.id != ch.id) {
                                // Vorschaumodus > „Bleib in der Liste beim Sender wechseln“: erst in der Vorschau, das zweite OK im Vollbild
                                History.add(ctx, ch.id)
                            } else open(ch)
                        }
                    }
                }
                    }
                }
            }
        }
        }
    }
        if (pickMenu == 1) {
            val ids = list.map { it.id }
            SideMenu(listOf(SideSection(Lang.tr("Auswahl"), listOf(
                SideEntry("Alle markieren", AIcon.Check) { picked.clear(); picked.addAll(ids) },
                SideEntry("Alle abwählen") { picked.clear() },
                SideEntry("Auswahl umkehren") { val inv = ids.filter { it !in picked }; picked.clear(); picked.addAll(inv) }
            ))), { pickMenu = 0 })
        }
        if (pickMenu == 2) {
            val ids = picked.toList()
            fun copyTo(id: Int, move: Boolean) {
                ids.forEach { ChannelLists.add(ctx, id, it) }
                if (move && curList != null && curList.id != id) ids.forEach { ChannelLists.remove(ctx, curList.id, it) }
                Toast.makeText(ctx, Lang.tr(if (move) "Sender wurden verschoben" else "Sender wurden kopiert") + " (" + ids.size + ")", Toast.LENGTH_SHORT).show()
                picked.clear(); manage = 0
            }
            val targets = ChannelLists.lists.filter { it.id != curList?.id }
            val copy = targets.map { l -> SideEntry(l.name, AIcon.Lists, raw = true) { copyTo(l.id, false) } } +
                SideEntry("Neue Gruppe …", AIcon.Plus) { ask = NameAsk("Gruppe erstellen", "") { n -> val id = ChannelLists.create(ctx, n); copyTo(id, false) } }
            val secs = arrayListOf(SideSection(Lang.tr("Kopieren nach"), copy))
            if (curList != null && targets.isNotEmpty()) secs.add(SideSection(Lang.tr("Verschieben nach"), targets.map { l -> SideEntry(l.name, AIcon.Lists, raw = true) { copyTo(l.id, true) } }))
            SideMenu(secs, { pickMenu = 0 })
        }
        // Kanäle neu anordnen: Ziel für den gewählten Sender
        moveFor?.let { mv ->
            val i = list.indexOfFirst { it.id == mv.id }
            val last = list.lastIndex
            fun go(to: Int) { Tm.set(ctx, Tm.EPG_CH_SORT, 4); ChOrder.move(ctx, sel, list, mv.id, to); moveFor = null }
            val e = ArrayList<SideEntry>()
            if (i > 0) { e.add(SideEntry("Nach oben", AIcon.Up) { go(i - 1) }); e.add(SideEntry("Ganz nach oben", AIcon.Up) { go(0) }) }
            if (i in 0 until last) { e.add(SideEntry("Nach unten", AIcon.Down) { go(i + 1) }); e.add(SideEntry("Ganz nach unten", AIcon.Down) { go(last) }) }
            SideMenu(listOf(SideSection(ChannelEdit.title(mv), e)), { if (moveFor?.id == mv.id) moveFor = null })
        }
        ask?.let { a -> NameDialog(a.title, a.initial, onDismiss = { ask = null }) { a.onSave(it); ask = null } }
    }
}

/** Kleiner Dialog: Name eingeben (Bildschirmtastatur), Speichern oder Abbrechen. */
@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
    onFocus: () -> Unit, onRight: () -> Unit, onFav: () -> Unit, icon: AIcon? = null, raw: Boolean = false,
    header: Boolean = false, indent: Boolean = false, expanded: Boolean? = null, onClick: () -> Unit
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
            .padding(start = if (indent) 30.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) AppIcon(icon, if (icon == AIcon.Star) DS.Gold else if (header && !f) Accent2 else ink, size = 18.dp)
            Text(label, color = ink, fontSize = if (header) 19.sp else 18.sp, fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, raw = raw)
        }
        // Anzahl nur beim markierten Eintrag (die Liste bleibt ruhig)
        if (expanded != null) AppIcon(if (expanded) AIcon.Up else AIcon.Down, if (f) DS.FocusInk else Tok.TextMid, size = 18.dp)
        else if (count >= 0 && f) Text("$count", color = DS.FocusInk.copy(alpha = 0.6f), fontSize = 14.sp)
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

/** Darstellung der Guide-Zeilen nach Optik > EPG (TiviMate). Einmal je Liste gelesen, nicht je Zeile. */
@Immutable
data class GuideLook(
    val numbers: Boolean, val names: Boolean, val twoLineNames: Boolean, val catchupIcon: Boolean, val currentId: String?,
    val twoLineTitles: Boolean, val markRunning: Boolean, val progressOnly: Boolean, val nowFull: Boolean
) {
    val rowH get() = if (twoLineTitles || twoLineNames) 64.dp else 46.dp
}

@Composable
private fun ChannelLine(
    item: Item, num: Int, look: GuideLook, ws: Long, nowSec: Long, tlW: androidx.compose.ui.unit.Dp, modifier: Modifier, inList: ChList?,
    onNewList: () -> Unit, onRename: () -> Unit, onLeft: () -> Unit,
    onFocus: () -> Unit = {}, onProg: (EpgEntry?) -> Unit = {}, onShift: (Int) -> Unit = {}, onProgOk: (EpgEntry) -> Unit = {},
    onKeyAct: (EAct) -> Boolean = { false }, onManage: (Int) -> Unit = {}, picked: Boolean? = null, onClick: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // Neu zeichnen, wenn der Sender umbenannt wurde
    @Suppress("UNUSED_VARIABLE") val editVersion = ChannelEdit.version
    // Ohne Netz (gespeichertes EPG): beim schnellen Scrollen startet je Zeile kein Abruf mehr. Neu, wenn das EPG geladen ist.
    val epg = remember(item.id, Xmltv.channelCount, Xmltv.status, nowSec / 300) { Epg.nowLocal(item) }
    val isCurrent = look.currentId == item.id
    // TiviMate: Rechts/Links wählt einzelne Sendungen (auch über das Zeitfenster hinaus, die Zeitleiste läuft mit)
    // TiviMate: ohne EPG volle Stunden als Blöcke mit dem Sendernamen (anwählbar wie Sendungen)
    val all = remember(item.id, Xmltv.channelCount, Xmltv.status, nowSec / 300) {
        Xmltv.forItem(item).filter { it.end > nowSec - (if (Catchup.has(item)) (Catchup.of(item)?.days ?: 0) * 86400L else 0L) }.ifEmpty { epg }.ifEmpty {
            val h0 = nowSec / 3600 * 3600
            val t = ChannelEdit.title(item)
            (0 until 48).map { k -> EpgEntry(t, "", h0 + k * 3600L, h0 + (k + 1) * 3600L) }
        }
    }
    var pi by remember { mutableIntStateOf(-1) }
    val nowIdx = all.indexOfFirst { it.end > nowSec }
    fun pick(i: Int) {
        pi = i
        val e = all.getOrNull(i) ?: run { onProg(null); return }
        if (e.start >= ws + SPAN - 900) onShift(((e.start - (ws + SPAN - 1800)) / 1800 + 1).toInt())
        else if (e.end <= ws) onShift(-(((ws - e.start) / 1800) + 1).toInt())
        onProg(e)
    }
    // Fernbedienung > EPG (TiviMate): jede Taste mit eigener Aktion
    val ctx = LocalContext.current
    var descE by remember { mutableStateOf<EpgEntry?>(null) }
    var longFired by remember { mutableStateOf(false) }
    var leftDone by remember { mutableStateOf(false) }
    fun act(a: EAct): Boolean {
        val sel = all.getOrNull(pi)
        when (a) {
            EAct.NONE -> {}
            EAct.WATCH -> if (sel != null && !(nowSec in sel.start until sel.end)) onProgOk(sel) else onClick()
            EAct.MENU -> menu = true
            EAct.GROUPS -> onLeft()
            EAct.NEXT_PROG -> if (pi in 0 until all.lastIndex) pick(pi + 1)
            EAct.PREV_PROG -> if (pi > 0) pick(pi - 1)
            EAct.DESC -> descE = sel
            EAct.EXTERNAL -> openExternal(ctx, item.url, ChannelEdit.title(item))
            EAct.FAV -> Library.toggleFav(ctx, item.id)
            EAct.RECORD -> {
                val now = System.currentTimeMillis() / 1000
                val r = if (sel != null) Recorder.isPlanned(item.id, sel.start) else Recorder.runningFor(item.id)
                if (r != null) { Recorder.stop(ctx, r.id); Toast.makeText(ctx, Lang.tr("Aufnahme abgebrochen"), Toast.LENGTH_SHORT).show() }
                else if (sel == null || sel.end > now) {
                    if (sel != null) Recorder.add(ctx, item, sel.title, maxOf(sel.start, now), sel.end) else Recorder.recordNow(ctx, item)
                    Toast.makeText(ctx, Lang.tr(if (sel == null || sel.start <= now) "Aufnahme läuft" else "Aufnahme geplant"), Toast.LENGTH_SHORT).show()
                }
            }
            else -> return onKeyAct(a)
        }
        return true
    }
    fun guideKey(ke: android.view.KeyEvent): Boolean {
        val code = ke.keyCode
        val down = ke.action == android.view.KeyEvent.ACTION_DOWN
        val up = ke.action == android.view.KeyEvent.ACTION_UP
        val short = EKey.of(code, false) ?: return false
        val longK = EKey.of(code, true)
        return when (short) {
            // OK: Standard (ansehen/Menü) bleibt beim Klick-Verhalten der Zeile
            EKey.OK -> {
                if (GuideKeys.action(EKey.OK) == EAct.WATCH && GuideKeys.action(EKey.OK_LONG) == EAct.MENU) false
                else if (down && ke.repeatCount == 1) { longFired = true; act(GuideKeys.action(EKey.OK_LONG)); true }
                else if (up) { if (!longFired) act(GuideKeys.action(EKey.OK)); longFired = false; true }
                else true
            }
            // Links/Rechts: Standard = vorherige Sendung bzw. Gruppen, nächste Sendung
            EKey.LEFT -> {
                val a = GuideKeys.action(EKey.LEFT)
                if (a != EAct.GROUPS) { if (down) act(a) else false }
                // An der laufenden Sendung: kurz = Gruppen, lang = in die vergangenen Sendungen (TiviMate, nur mit Archiv)
                else if (up && leftDone) { leftDone = false; longFired = false; true }
                else if (down && ke.repeatCount == 0 && (pi > nowIdx || pi in 1 until nowIdx)) { leftDone = true; pick(pi - 1); true }
                else if (down && ke.repeatCount == 1 && pi > 0 && !leftDone) { longFired = true; leftDone = true; pick(pi - 1); true }
                else if (down && ke.repeatCount > 1 && pi > 0) { pick(pi - 1); true }
                else if (up) { if (!longFired) onLeft(); longFired = false; true }
                else true
            }
            EKey.RIGHT -> if (!down) false else act(GuideKeys.action(EKey.RIGHT))
            // Zurück: kurz = zurück (System), lang = Zurück zum Player
            EKey.BACK -> {
                val la = GuideKeys.action(EKey.BACK_LONG)
                if (down && ke.repeatCount == 1 && la != EAct.NONE && la != EAct.BACK) { longFired = true; act(la); true }
                else if (up && longFired) { longFired = false; true }
                else if (up && GuideKeys.action(EKey.BACK) != EAct.BACK) act(GuideKeys.action(EKey.BACK))
                else false
            }
            else -> {
                if (longK != null) {
                    // Taste mit Lang-Belegung (Play/Pause): kurz erst beim Loslassen
                    if (down && ke.repeatCount == 1) { longFired = true; act(GuideKeys.action(longK)); true }
                    else if (up) { if (!longFired) act(GuideKeys.action(short)); longFired = false; true }
                    else true
                } else if (down && ke.repeatCount == 0) act(GuideKeys.action(short)) else down
            }
        }
    }
    Box {
    if (menu) ChannelMenu(item, inList, onNewList, onRename, prog = all.getOrNull(pi), onManage = onManage) { menu = false }
    descE?.let { e -> ProgDescDialog(item, e) { descE = null } }
    val cell = RoundedCornerShape(4.dp)
    val titleLines = if (look.twoLineTitles) 2 else 1
    Row(
        modifier.fillMaxWidth().height(look.rowH)
            // TiviMate: Zeile des laufenden Senders mit hellem Rahmen (dazu ▶ beim Namen)
            .border(1.dp, if (isCurrent) Color(0xFFDADCE0) else Color.Transparent, RoundedCornerShape(6.dp))
            .onFocusChanged {
                f = it.isFocused
                // TiviMate: markiert ist die laufende (bzw. erste sichtbare) Sendung der Zeile
                if (it.isFocused) { onFocus(); pick(all.indexOfFirst { e -> e.end > maxOf(nowSec, ws) }.let { i -> if (i < 0) nowIdx else i }) }
            }
            .onPreviewKeyEvent { e -> guideKey(e.nativeKeyEvent) }
            .menuKeys(onMenu = { menu = true }, onLong = { if (GuideKeys.action(EKey.OK_LONG) == EAct.MENU) menu = true })
            .clickable {
                val sel = all.getOrNull(pi)
                if (sel != null && !(nowSec in sel.start until sel.end)) onProgOk(sel) else onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.width(CH_W).fillMaxHeight().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (look.numbers) Text(raw = true, text = num.toString(), color = Tok.TextMid, fontSize = 15.sp, style = TNUM,
                textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(34.dp))
            // Kanäle auswählen (TiviMate): Kästchen je Sender
            if (picked != null) Box(Modifier.size(22.dp).border(2.dp, if (picked) Accent1 else Muted, RoundedCornerShape(4.dp)).background(if (picked) Accent1 else Color.Transparent, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                if (picked) AppIcon(AIcon.Check, Color.Black, size = 16.dp)
            }
            Box(Modifier.size(width = 54.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                if (Logos.of(item) != null) AsyncImage(Logos.of(item), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text(raw = true, text = item.title.take(2).uppercase(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (look.names) Text(raw = true, text = ChannelEdit.title(item),
                color = if (f || (isCurrent)) Accent2 else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Normal,
                maxLines = if (look.twoLineNames) 2 else 1, lineHeight = 20.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            if (Library.isFav(item.id)) AppIcon(AIcon.Star, DS.Gold, size = 14.dp)
            if (ChLock.version >= 0 && ChLock.isLocked(item.id)) AppIcon(AIcon.Lock, Muted, size = 14.dp)
            if (ChannelEdit.isHidden(item.id)) AppIcon(AIcon.EyeOff, Muted, size = 14.dp)
            if (look.catchupIcon && Catchup.version >= 0 && Catchup.has(item)) AppIcon(AIcon.Catchup, Accent2, size = 14.dp)
            if (DeadLinks.isDead(item.url)) AppIcon(AIcon.Warning, DS.Gold, size = 14.dp)
            // TiviMate: kleines Play-Symbol beim Sender, der gerade läuft
            if (isCurrent) AppIcon(AIcon.Play, Accent2, size = 16.dp)
        }
        // Breite der Zeitleiste wird einmal für die ganze Liste gemessen (nicht je Zeile)
        Box(Modifier.width(tlW).fillMaxHeight().padding(vertical = 2.dp)) {
            val w = tlW
            val shownE = all.filter { it.end > ws && it.start < ws + SPAN }
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
                val lit = f && (all.getOrNull(pi)?.let { it.start == e.start } ?: running)
                // TiviMate „Markiere laufendes Programm“: ganze Fläche heller, oder („nur Fortschritt“) nur der abgelaufene Teil
                val runFill = running && !f && look.markRunning && !look.progressOnly
                Box(
                    Modifier.offset(x = w * a).width(w * (b - a)).fillMaxHeight().padding(start = 3.dp).clip(cell)
                        .background(if (lit) TvFocusFill else if (f) Color(0xFF3A3E44) else if (runFill) TvCellNow else TvCell),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (running && !f && look.markRunning && look.progressOnly) {
                        val frac = ((nowSec - e.start).toFloat() / (e.end - e.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(TvCellNow))
                    }
                    Text(raw = true, text = e.title, color = if (lit) DS.FocusInk else if (f) Color.White else Color(0xFFC9CCD1), fontSize = 17.sp,
                        maxLines = titleLines, lineHeight = 20.sp, overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
            // Linie für „jetzt“ (TiviMate: wahlweise über die volle Höhe)
            if (look.nowFull) Box(Modifier.offset(x = w * ((nowSec - ws).toFloat() / SPAN)).width(1.dp).fillMaxHeight().background(Accent2.copy(alpha = 0.7f)))
        }
    }
    }
}

/**
 * Menü eines Senders im TV-Guide (OK gedrückt halten / Menü-Taste), Aufbau wie TiviMate:
 * Suchen, Einstellungen | Sendung: Extern öffnen, Aufnahme, Benutzerdefinierte Aufnahme, Erinnerung, Programmbeschreibung |
 * Kanal: Favoriten, Kanal sperren, Kanal ausblenden, Umbenennen | eigene Senderlisten.
 * [prog] = markierte Sendung (null = keine EPG-Daten).
 */
@Composable
fun ChannelMenu(
    item: Item, inList: ChList?, onNewList: (() -> Unit)?, onRename: (() -> Unit)? = null, prog: EpgEntry? = null,
    onManage: ((Int) -> Unit)? = null, onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var desc by remember { mutableStateOf<EpgEntry?>(null) }
    var custom by remember { mutableStateOf(false) }
    var assign by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(true) }
    val now = System.currentTimeMillis() / 1000
    val top = listOf(
        SideEntry("Suchen", AIcon.Search) { AppNav.tabNow = MenuLogic.SEARCH },
        SideEntry("Einstellungen", AIcon.Gear) { SettingsOpen.value = true }
    )
    // Sendung
    val pe = ArrayList<SideEntry>()
    pe.add(SideEntry("Extern öffnen") { openExternal(ctx, item.url, ChannelEdit.title(item)) })
    // TiviMate: laufende Sendung von Anfang an (über das Archiv)
    if (prog != null && prog.start <= now && prog.end > now && Catchup.has(item)) pe.add(SideEntry("Von Anfang an ansehen") { AppNav.archiveStart = item.id to prog; AppNav.openChannel = item.id })
    if (prog == null || prog.end > now) {
        val planned = if (prog != null) Recorder.isPlanned(item.id, prog.start) else Recorder.runningFor(item.id)
        if (planned != null) pe.add(SideEntry(if (planned.state == Rec.RUNNING) "Aufnahme stoppen" else "Aufnahme abbrechen") {
            Recorder.stop(ctx, planned.id)
            Toast.makeText(ctx, Lang.tr("Aufnahme abgebrochen"), Toast.LENGTH_SHORT).show()
        }) else pe.add(SideEntry("Aufnahme hinzufügen") {
            Recorder.init(ctx)
            if (prog != null) Recorder.add(ctx, item, prog.title, maxOf(prog.start, now), prog.end) else Recorder.recordNow(ctx, item)
            Toast.makeText(ctx, Lang.tr(if (prog == null || prog.start <= now) "Aufnahme läuft" else "Aufnahme geplant"), Toast.LENGTH_SHORT).show()
        })
    }
    pe.add(SideEntry("Benutzerdefinierte Aufnahme") { custom = true })
    if (prog != null && ReminderLogic.canRemind(prog.start, now)) {
        val set = Reminders.isSet(item.id, prog.start)
        pe.add(SideEntry(if (set) "Erinnerung löschen" else "Erinnern an") {
            val on = Reminders.toggle(ctx, Reminder(item.id, ChannelEdit.title(item), prog.title, prog.start, prog.end))
            if (on) Reminders.askPermission(ctx as? android.app.Activity)
            Toast.makeText(ctx, Lang.tr(if (on) "Erinnerung gesetzt" else "Erinnerung entfernt"), Toast.LENGTH_SHORT).show()
        })
    }
    if (prog != null && prog.end <= now && Catchup.has(item)) pe.add(SideEntry("Aus dem Archiv abspielen") { AppNav.archiveStart = item.id to prog; AppNav.openChannel = item.id })
    if (prog != null) {
        val inMy = ProgList.has(item.id, prog.start)
        pe.add(SideEntry(if (inMy) "Aus meiner Liste entfernen" else "Zu meiner Liste hinzufügen") {
            val on = ProgList.toggle(ctx, item, prog)
            Toast.makeText(ctx, Lang.tr(if (on) "Zu meiner Liste hinzugefügt" else "Aus meiner Liste entfernt"), Toast.LENGTH_SHORT).show()
        })
    }
    if (prog != null) pe.add(SideEntry("Programmbeschreibung") { desc = prog })
    // Kanal
    val chan = ArrayList<SideEntry>()
    chan.add(SideEntry(if (Library.isFav(item.id)) "Aus den Favoriten entfernen" else "Zu den Favoriten hinzufügen") { Library.toggleFav(ctx, item.id) })
    chan.add(SideEntry(if (ChLock.isLocked(item.id)) "Kanal entsperren" else "Kanal sperren") {
        if (!Parental.pinSet) Toast.makeText(ctx, Lang.tr("Bitte zuerst unter Einstellungen > Kindersicherung eine PIN festlegen."), Toast.LENGTH_LONG).show()
        else {
            val on = ChLock.toggle(ctx, item.id)
            Toast.makeText(ctx, Lang.tr(if (on) "Kanal gesperrt" else "Kanal entsperrt"), Toast.LENGTH_SHORT).show()
        }
    })
    if (onRename != null) {
        val hidden = ChannelEdit.isHidden(item.id)
        chan.add(SideEntry(if (hidden) "Kanal einblenden" else "Kanal ausblenden") {
            ChannelEdit.toggleHidden(ctx, item.id)
            Toast.makeText(ctx, Lang.tr(if (hidden) "Sender wieder eingeblendet" else "Sender ausgeblendet. Zurückholen: „Ausgeblendete Sender“ links."), Toast.LENGTH_SHORT).show()
        })
        chan.add(SideEntry("Umbenennen …", onPick = onRename))
        if (ChannelEdit.isRenamed(item.id)) chan.add(SideEntry("Namen wiederherstellen") { ChannelEdit.rename(ctx, item.id, "") })
    }
    chan.add(SideEntry("EPG zuweisen") { assign = true })
    if (onManage != null) {
        chan.add(SideEntry("Sichtbarkeit verwalten") { onManage(1) })
        if (inList == null) {
            chan.add(SideEntry("Kanäle neu anordnen") { onManage(4) })
            chan.add(SideEntry("Positionen zurücksetzen") { onManage(5) })
        }
        chan.add(SideEntry("Sperren verwalten") { onManage(2) })
        chan.add(SideEntry("Favoriten verwalten") { onManage(3) })
        chan.add(SideEntry("Kanäle auswählen") { onManage(6) })
    }
    // eigene Senderlisten
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
    if (open) SideMenu(
        listOf(
            SideSection(null, top),
            SideSection(prog?.title ?: ChannelEdit.title(item), pe),
            SideSection(ChannelEdit.title(item), chan),
            SideSection(Lang.tr("Senderlisten"), lists)
        )
    ) { open = false }
    LaunchedEffect(open, desc, custom, assign) { if (!open && desc == null && !custom && !assign) onClose() }
    desc?.let { e -> ProgDescDialog(item, e) { desc = null; onClose() } }
    if (custom) CustomRecDialog(item, prog) { custom = false; onClose() }
    if (assign) EpgAssignDialog(item) { assign = false; onClose() }
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
        // Sonstiges > Erinnerungen: Popup-Timeout, danach die Standardaktion (Anschauen oder nur schließen)
        var left = Tm.i(Tm.REM_TIMEOUT, 10).coerceAtLeast(3)
        while (left > 0) { kotlinx.coroutines.delay(1000); left--; nowSec = System.currentTimeMillis() / 1000 }
        if (Tm.i(Tm.REM_ACTION, 0) == 0) onSwitch() else onClose()
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

/** Markierter Sender im TV-Guide (eigener Zustand, damit nur der Kopf neu zeichnet). */
@Stable
class GuideFocusState {
    var target by mutableStateOf<Item?>(null)
    var shown by mutableStateOf<Item?>(null)
    /** Im Guide gewählte Sendung des markierten Senders (für die Kopfzeile). */
    var prog by mutableStateOf<EpgEntry?>(null)
}

/** Kopf des TV-Guides: Vorschau und Sendung des markierten Senders (wechselt erst nach kurzem Verweilen). */
@Composable
private fun RowScope.GuideTop(focus: GuideFocusState, first: Item?, preview: LivePreviewPlayer, playingCh: Item? = null) {
    LaunchedEffect(focus.target) { if (focus.shown != null) kotlinx.coroutines.delay(300); focus.shown = focus.target }
    val shown = focus.shown ?: first
    // Optik > EPG > Vorschau: aus = kein Vorschaubild, die Sendung nutzt die ganze Breite
    // Optik > Player > Senderliste > Vorschaumodus (TiviMate): ohne „Kanäle automatisch abspielen“ zeigt die Vorschau
    // den laufenden Sender, nicht jeden markierten
    val auto = Tm.b(Tm.CV_AUTOPLAY, false)
    if (AppSettings.livePreview) LivePreview(preview, if (auto) focus.shown else (playingCh ?: focus.shown), Modifier.fillMaxHeight().aspectRatio(16f / 9f), fallback = if (auto) shown else (playingCh ?: shown))
    LiveInfo(shown, Modifier.weight(1f).fillMaxHeight(), prog = if (shown != null && shown.id == focus.target?.id) focus.prog else null)
}

/** OK auf eine Sendung (TV-Guide, Suche): vergangen → Archiv, künftig → Erinnerung an/aus, laufend → Sender öffnen. */
fun progAction(ctx: android.content.Context, ch: Item, e: EpgEntry, open: (Item) -> Unit) {
    val now = System.currentTimeMillis() / 1000
    when {
        e.end <= now -> if (Catchup.has(ch)) { AppNav.archiveStart = ch.id to e; open(ch) }
            else Toast.makeText(ctx, Lang.tr("Für diesen Sender gibt es kein Archiv."), Toast.LENGTH_SHORT).show()
        e.start > now -> {
            val on = Reminders.toggle(ctx, Reminder(ch.id, ChannelEdit.title(ch), e.title, e.start, e.end))
            Toast.makeText(ctx, Lang.tr(if (on) "Erinnerung gesetzt" else "Erinnerung entfernt"), Toast.LENGTH_SHORT).show()
        }
        else -> open(ch)
    }
}

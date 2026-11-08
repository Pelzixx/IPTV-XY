package com.ipvio.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/*
 * Filme und Serien im Aufbau von TiviMate:
 *  - links die Gruppen (Meine Liste, Verlauf, Alle Filme/Serien, dann die Gruppen der Wiedergabeliste)
 *  - oben die Infos zum markierten Titel (Titel (Jahr), Wertung, Jahr • Länge • Genres, Besetzung, Regisseur, Beschreibung),
 *    rechts das Szenenbild, weich in den Hintergrund übergehend
 *  - darunter die Gruppe als Posterraster (Wertung unten links auf dem Poster, Titel darunter)
 *  - im Raster verschwindet die Gruppenliste, oben rechts erscheinen Suche und Sortierung, rechts „1 / 276“
 */

private const val K_MY = "\u0001my"
private const val K_HIST = "\u0001hist"
private const val K_ALL = "\u0001all"

/** Sortierung je Art (TiviMate): 0 Wiedergabeliste, 1 Name, 2 Bewertung, 3 zuletzt hinzugefügt. */
object VodPrefs {
    fun sort(kind: Kind) = Tm.i(if (kind == Kind.MOVIE) Tm.VOD_SORT_M else Tm.VOD_SORT_S, 0)
    fun setSort(c: android.content.Context, kind: Kind, v: Int) = Tm.set(c, if (kind == Kind.MOVIE) Tm.VOD_SORT_M else Tm.VOD_SORT_S, v)
    fun groupAll(kind: Kind) = Tm.b(if (kind == Kind.MOVIE) Tm.VOD_GRP_ALL_M else Tm.VOD_GRP_ALL_S, false)
    fun setGroupAll(c: android.content.Context, kind: Kind, v: Boolean) = Tm.set(c, if (kind == Kind.MOVIE) Tm.VOD_GRP_ALL_M else Tm.VOD_GRP_ALL_S, v)

    fun sorted(list: List<Item>, sort: Int): List<Item> = when (sort) {
        1 -> list.sortedBy { it.title.lowercase() }
        // Bewertung: TMDB, sonst die des Anbieters (Xtream „rating“)
        2 -> list.sortedByDescending { MetaRepo.cached(it)?.rating ?: VodMeta.rating(it) ?: -1.0 }
        // Zuletzt hinzugefügt: Datum des Anbieters (Xtream „added“), ohne Angabe die umgekehrte Reihenfolge der Liste
        3 -> if (VodMeta.hasAdded()) list.sortedByDescending { VodMeta.added(it) } else list.asReversed()
        else -> list
    }
}

private class VodGroup(val key: String, val name: String, val raw: Boolean, val items: List<Item>)

@Composable
fun VodTab(tab: Int, onTab: (Int) -> Unit, onOpen: (Item) -> Unit, onPlay: (Item) -> Unit, onAdd: () -> Unit = {}) {
    val ctx = LocalContext.current
    val kind = if (tab == 1) Kind.MOVIE else Kind.SERIES
    val all = PlaylistStore.channels
    val noun = if (kind == Kind.MOVIE) "Filme" else "Serien"

    val sort = VodPrefs.sort(kind)
    // Daten je Art einmal berechnen und über Reiterwechsel hinweg behalten (sonst bei jedem Öffnen alles neu, sichtbar als Ruckeln)
    val data by produceState(VodCache.get(all, kind) ?: VodData.EMPTY, all, kind, PlaylistStore.catOrder) {
        value = VodCache.get(all, kind) ?: withContext(Dispatchers.Default) { VodCache.build(all, kind) }
    }
    val byKind = data.items
    val providerGroups = data.groups
    val byId = data.byId
    // Meine Liste / Verlauf nur neu, wenn sich die Listen wirklich ändern (nicht bei jedem Fokuswechsel)
    History.init(ctx)
    val mine by remember(byId) { derivedStateOf { Library.watch.mapNotNull { byId[it] } } }
    // Verlauf (TiviMate): jeder gestartete Titel dieser Art, zuletzt gestartet zuerst
    val hist by remember(byId) { derivedStateOf { History.ids.mapNotNull { byId[it] } } }
    val groups = remember(data, mine, hist) {
        listOf(
            VodGroup(K_MY, "Meine Liste", false, mine),
            VodGroup(K_HIST, "Verlauf", false, hist),
            VodGroup(K_ALL, "Alle $noun", false, byKind)
        ) + providerGroups.map { (n, l) -> VodGroup(n, n, true, l) }
    }
    var selKey by rememberSaveable(tab) { mutableStateOf(Tm.s(if (kind == Kind.MOVIE) Tm.VOD_LAST_M else Tm.VOD_LAST_S).ifEmpty { K_ALL }) }
    val sel = groups.firstOrNull { it.key == selKey } ?: groups.getOrNull(2)
    var query by remember(tab) { mutableStateOf("") }
    // „Alle Filme“ nach Kategorien gruppieren: Abschnitte mit Überschrift im Raster
    val grouped = sel?.key == K_ALL && VodPrefs.groupAll(kind) && query.isBlank()
    val items = remember(sel?.key, sel?.items, sort, query) {
        val base = sel?.items.orEmpty().let { l -> if (query.isBlank()) l else l.filter { it.title.contains(query, ignoreCase = true) } }
        if (sel?.key == K_HIST || sel?.key == K_MY) base else VodPrefs.sorted(base, sort)
    }
    val groupedSections = remember(grouped, providerGroups, sort) {
        if (!grouped) emptyList() else providerGroups.map { (g, l) -> g to VodPrefs.sorted(l, sort) }
    }
    val gridState = rememberLazyGridState()
    // Zurück von der Detailseite: wieder auf dem Titel im Raster stehen (wie TiviMate)
    var gridMode by remember(tab) { mutableStateOf(VodMem.grid[tab] == true) }
    var focused by remember(tab) { mutableStateOf<Item?>(null) }
    var focusIdx by remember(tab) { mutableIntStateOf(VodMem.idx[tab] ?: 0) }
    val restoreReq = remember { FocusRequester() }
    LaunchedEffect(tab) {
        if (gridMode) {
            runCatching { gridState.scrollToItem(focusIdx) }
            kotlinx.coroutines.delay(60)
            if (runCatching { restoreReq.requestFocus() }.isFailure) gridMode = false
        }
    }
    SideEffect { VodMem.grid[tab] = gridMode; VodMem.idx[tab] = focusIdx }
    var menu by remember { mutableIntStateOf(0) }   // 1 Sortierung, 2 Suche
    var longFor by remember { mutableStateOf<Item?>(null) }
    val firstCard = remember { FocusRequester() }
    val groupReq = remember { FocusRequester() }
    val groupState = rememberLazyListState()
    val sideFocus = remember { FocusRequester() }
    var inSidebar by remember { mutableStateOf(false) }
    BackHandler(enabled = !gridMode && !inSidebar) { runCatching { sideFocus.requestFocus() } }
    BackHandler(enabled = gridMode) {
        gridMode = false
        runCatching { groupReq.requestFocus() }
    }
    LaunchedEffect(selKey) {
        Tm.set(ctx, if (kind == Kind.MOVIE) Tm.VOD_LAST_M else Tm.VOD_LAST_S, selKey)
        if (!gridMode) { focused = items.firstOrNull(); runCatching { gridState.scrollToItem(0) } }
    }
    val shown = focused ?: items.firstOrNull()

    Box(Modifier.fillMaxSize().background(Bg)) {
        // Szenenbild rechts oben, nach links und unten in den Hintergrund auslaufend
        if (shown != null) VodBackdrop(shown, Modifier.align(Alignment.TopEnd).fillMaxWidth(0.62f).fillMaxHeight(0.66f))
        Row(Modifier.fillMaxSize().padding(start = DS.SidebarW)) {
            if (!gridMode) {
                LazyColumn(
                    state = groupState,
                    modifier = Modifier.width(250.dp).fillMaxHeight().padding(start = 14.dp),
                    contentPadding = PaddingValues(top = 180.dp, bottom = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listItemsIndexed(groups, key = { _, g -> g.key }) { _, g ->
                        VodGroupRow(g.name, g.raw, g.key == selKey,
                            if (g.key == selKey) Modifier.focusRequester(groupReq).focusRequester(ContentFocus.req) else Modifier,
                            onFocus = { selKey = g.key; query = "" },
                            onRight = { if (items.isNotEmpty()) { runCatching { firstCard.requestFocus() }; true } else false }
                        ) { if (items.isNotEmpty()) runCatching { firstCard.requestFocus() } }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (gridMode) 26.dp else 30.dp, end = 0.dp)) {
                Box(Modifier.fillMaxWidth().height(250.dp).padding(top = 16.dp, end = 24.dp)) {
                    if (shown != null) VodInfo(shown, Modifier.fillMaxWidth(if (gridMode) 0.5f else 0.72f))
                    else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        AppIcon(if (kind == Kind.MOVIE) AIcon.Grid else AIcon.Layers, Muted, size = 52.dp)
                        Text(if (kind == Kind.MOVIE) "Keine Filme" else "Keine Serien", color = Tok.TextHi, fontSize = 22.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    if (gridMode) Row(
                        Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VodIconButton(AIcon.Search) { menu = 2 }
                        VodIconButton(AIcon.Sliders) { menu = 1 }
                    }
                }
                if (sel != null && items.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(end = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sel.name + (if (query.isNotBlank()) "  ·  „$query“" else ""), raw = sel.raw || query.isNotBlank(), color = Tok.TextHi, fontSize = 17.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (gridMode) Text(raw = true, text = "${focusIdx + 1} / ${items.size}", color = Tok.TextMid, fontSize = 16.sp)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(118.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().padding(end = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 40.dp)
                    ) {
                        if (grouped) {
                            var idx = 0
                            groupedSections.forEach { (g, sl) ->
                                item(key = "h$g", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(raw = true, text = g, color = Tok.TextMid, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                                }
                                val start = idx
                                itemsIndexed(sl, key = { _, it -> "g$g" + it.id }) { i, it ->
                                    VodPosterCard(it, if (start + i == 0) Modifier.focusRequester(firstCard) else Modifier,
                                        onFocus = { focused = it; focusIdx = start + i; gridMode = true },
                                        onLeftEdge = { false }, onLong = { longFor = it }) { onOpen(it) }
                                }
                                idx += sl.size
                            }
                        } else itemsIndexed(items, key = { _, it -> it.id }) { i, it ->
                            VodPosterCard(it, (if (i == 0) Modifier.focusRequester(firstCard) else Modifier).then(if (i == focusIdx) Modifier.focusRequester(restoreReq) else Modifier),
                                onFocus = { focused = it; focusIdx = i; gridMode = true },
                                onLeftEdge = {
                                    val col = gridState.layoutInfo.visibleItemsInfo.firstOrNull { v -> v.index == i }?.column ?: -1
                                    if (col == 0) { gridMode = false; true } else false
                                },
                                onLong = { longFor = it }) { onOpen(it) }
                        }
                    }
                }
            }
        }
        Sidebar(tab, { id -> if (id == MenuLogic.SETTINGS) SettingsOpen.value = true else onTab(id) }, Modifier.align(Alignment.CenterStart), sideFocus) { inSidebar = it }
        // Einstellungen öffnen sich wie auf den anderen Seiten als Leiste rechts
        if (SettingsOpen.value) {
            val scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
            SettingsPanel(onAdd, { PlaylistStore.savedUrl(ctx)?.let { u -> scope.launch { PlaylistStore.load(ctx, u, false) } } }) { SettingsOpen.value = false; runCatching { sideFocus.requestFocus() } }
        }
        // Nach dem Verlassen des Rasters die Gruppe wieder markieren
        // Gruppen der Playlist kommen etwas später: so lange versuchen, bis die gewählte Gruppe da ist
        LaunchedEffect(gridMode, providerGroups.size) {
            if (!gridMode && !inSidebar) repeat(12) { kotlinx.coroutines.delay(60); if (runCatching { groupReq.requestFocus() }.isSuccess) return@LaunchedEffect }
        }
    }
    when (menu) {
        1 -> {
            val names = listOf("Anordnung in der Wiedergabeliste", "nach Name", "nach Bewertung", "nach Datum zuletzt hinzugefügt")
            val e = names.mapIndexed { i, n -> SideEntry(n, if (i == sort) AIcon.Check else null) { VodPrefs.setSort(ctx, kind, i) } } +
                SideEntry(Lang.tr("Gruppiere") + " „" + Lang.tr("Alle $noun") + "“ " + Lang.tr("nach Kategorien") + (if (VodPrefs.groupAll(kind)) "  ✓" else ""), raw = true) {
                    VodPrefs.setGroupAll(ctx, kind, !VodPrefs.groupAll(kind))
                }
            SideMenu(listOf(SideSection(Lang.tr("Sortierung"), e)), { menu = 0 })
        }
        2 -> NameDialog(Lang.tr("Suchen"), query, onDismiss = { menu = 0 }) { query = it.trim(); menu = 0 }
    }
    longFor?.let { it0 ->
        val inList = Library.isWatch(it0.id)
        val e = arrayListOf(
            SideEntry("Wiedergeben", AIcon.Play) { onPlay(it0) },
            SideEntry("Details", AIcon.Info) { onOpen(it0) },
            SideEntry(if (inList) "Von Meiner Liste entfernen" else "Zu Meiner Liste hinzufügen", AIcon.Plus) { Library.toggleWatch(ctx, it0.id) }
        )
        if (selKey == K_HIST) e.add(SideEntry("Aus Verlauf entfernen", AIcon.Close) { History.remove(ctx, it0.id); WatchStore.remove(ctx, it0.id) })
        SideMenu(listOf(SideSection(it0.title, e)), { longFor = null })
    }
}

@Composable
private fun VodGroupRow(name: String, raw: Boolean, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onRight: () -> Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (f) TvFocusFill else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) onRight() else false }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(name, raw = raw, color = if (f) Color(0xFF15171A) else if (selected) Accent2 else Tok.TextHi, fontSize = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VodIconButton(icon: AIcon, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(if (f) TvFocusFill else Color.Transparent)
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { AppIcon(icon, if (f) Color(0xFF15171A) else Tok.TextHi, size = 20.dp) }
}

/** Poster wie in TiviMate: Wertung unten links (blau), Titel darunter; markiert mit hellem Rahmen und hellem Titelfeld. */
@Composable
private fun VodPosterCard(item: Item, modifier: Modifier, onFocus: () -> Unit, onLeftEdge: () -> Boolean, onLong: () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val st = rememberMetaState(item)
    val m = st.meta
    val img = m?.poster?.let { cardPoster(it) } ?: item.logo
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier.clip(shape).background(Tok.Card)
            .border(2.dp, if (f) Color.White else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) onLeftEdge() else false
            }
            .menuKeys(onMenu = onLong, onLong = onLong)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color(0xFF2B2E33))) {
            if (img != null) AsyncImage(img, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            (m?.rating ?: VodMeta.rating(item))?.takeIf { it > 0 }?.let { r ->
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E88E5)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(raw = true, text = String.format(java.util.Locale.US, "%.1f", r), color = Color.White, fontSize = 12.sp)
                }
            }
            if (Library.isWatch(item.id)) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { AppIcon(AIcon.Check, Color.White, size = 16.dp) }
        }
        Box(Modifier.fillMaxWidth().background(if (f) TvFocusFill else Color.Transparent).padding(horizontal = 6.dp, vertical = 5.dp)) {
            Text(raw = true, text = item.title, color = if (f) Color(0xFF15171A) else Tok.TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Infos zum markierten Titel wie in TiviMate. */
@Composable
private fun VodInfo(item: Item, modifier: Modifier) {
    val ctx = LocalContext.current
    val base = rememberMeta(item)
    val m = rememberFullMeta(item, base)
    val credits by produceState<Credits?>(null, m?.tmdbId) { value = m?.let { CreditsRepo.credits(ctx, it) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val year = m?.year ?: item.year?.toString()
        val title = item.title.let { t -> if (year != null && !t.contains("($year)")) "$t ($year)" else t }
        Text(raw = true, text = title, color = Tok.TextHi, fontSize = 28.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (m?.rating ?: VodMeta.rating(item))?.takeIf { it > 0 }?.let { r ->
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFBDBDBD)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(raw = true, text = String.format(java.util.Locale.US, "%.1f", r), color = Color(0xFF15171A), fontSize = 12.sp)
                }
            }
            val parts = ArrayList<String>()
            year?.let { parts.add(it) }
            if (item.kind == Kind.SERIES) seasonCount(item).takeIf { it > 0 }?.let { parts.add(if (it == 1) "1 Staffel" else "$it " + Lang.tr("Staffeln")) }
            else m?.runtime?.takeIf { it > 0 }?.let { parts.add("${it / 60}h ${it % 60}m") }
            m?.genres?.takeIf { it.isNotEmpty() }?.let { parts.add(it.take(4).joinToString(", ")) }
            if (parts.isNotEmpty()) Text(raw = true, text = parts.joinToString("  •  "), color = Tok.TextMid, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        credits?.cast?.takeIf { it.isNotEmpty() }?.let { c ->
            Row { Text("Besetzung:", color = Muted, fontSize = 14.sp); Text(raw = true, text = " " + c.take(4).joinToString(", ") { it.name }, color = Tok.TextMid, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        credits?.directors?.takeIf { it.isNotEmpty() }?.let { d ->
            Row { Text("Regisseur:", color = Muted, fontSize = 14.sp); Text(raw = true, text = " " + d.take(2).joinToString(", ") { it.name }, color = Tok.TextMid, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        m?.overview?.takeIf { it.isNotBlank() }?.let {
            Text(raw = true, text = it, color = Tok.TextMid, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VodBackdrop(item: Item, modifier: Modifier) {
    val m = rememberMeta(item)
    val url = m?.backdrop?.let { cardBackdrop(it) } ?: return
    Box(modifier) {
        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopEnd)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Bg, 0.35f to Bg.copy(alpha = .55f), 0.7f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Bg.copy(alpha = .35f), 1f to Bg)))
    }
}

/** Stand je Reiter über die Detailseite hinweg (Raster aktiv, markierter Titel). */
private object VodMem {
    val grid = HashMap<Int, Boolean>()
    val idx = HashMap<Int, Int>()
}

/** Filme bzw. Serien einer Playlist mit ihren Gruppen (einmal berechnet). */
class VodData(val items: List<Item>, val groups: List<Pair<String, List<Item>>>, val byId: Map<String, Item>) {
    companion object { val EMPTY = VodData(emptyList(), emptyList(), emptyMap()) }
}

/** Hält die Daten je Art, solange sich die Playlist nicht ändert – der Reiter öffnet dann ohne Neuberechnung. */
object VodCache {
    private var src: List<Item>? = null
    private val byKind = HashMap<Kind, VodData>()

    private var ord: Map<String, Int>? = null

    @Synchronized fun get(all: List<Item>, kind: Kind): VodData? = if (src === all && ord === PlaylistStore.groupOrder()) byKind[kind] else null

    fun build(all: List<Item>, kind: Kind): VodData {
        val order = PlaylistStore.groupOrder()
        val items = all.filter { it.kind == kind }
        val g = LinkedHashMap<String, MutableList<Item>>()
        items.forEach { g.getOrPut(it.group) { ArrayList() }.add(it) }
        // Ordner in der Reihenfolge des Anbieters (TiviMate); unbekannte dahinter in der Reihenfolge der Liste
        val groups = g.entries.map { it.key to it.value.toList() }.sortedBy { order[it.first] ?: Int.MAX_VALUE }
        val d = VodData(items, groups, items.associateBy { it.id })
        synchronized(this) {
            if (src !== all || ord !== order) { src = all; ord = order; byKind.clear() }
            byKind[kind] = d
        }
        return d
    }
}

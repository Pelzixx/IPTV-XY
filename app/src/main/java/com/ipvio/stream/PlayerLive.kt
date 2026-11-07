package com.ipvio.stream

import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Sender-Infoblock: Logo, Nummer, Name, laufende Sendung mit Fortschritt und "Danach". */
@Composable
fun LiveInfoBlock(item: Item?, title: String, number: Int, epg: List<EpgEntry>, modifier: Modifier = Modifier, switching: Boolean = false, media: String? = null) {
    // Runde 20 (TV-Stil): Logo frei links, daneben Sendungstitel groß; darunter Zeit, Fortschritt, Restminuten und Sendername,
    // dann Beschreibung und die nächste Sendung
    val nowE = Epg.current(epg)
    val nextE = if (nowE != null) epg.firstOrNull { it.start >= nowE.end } else null
    val logo = Logos.of(item)
    val chName = item?.let { ChannelEdit.title(it) } ?: title
    // Optik > Player > Infofeld (TiviMate): Kartenstil, Beschreibung (immer oder nur beim Senderwechsel), Medieneigenschaften
    val card = Tm.b(Tm.IF_CARD, false)
    val showDesc = Tm.b(Tm.IF_DESC, true) && (switching || !Tm.b(Tm.IF_DESC_SWITCH, true))
    Row(
        modifier.then(if (card) Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF202328).copy(alpha = uiAlpha(0.95f))).padding(20.dp) else Modifier),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Box(Modifier.width(140.dp).height(90.dp), contentAlignment = Alignment.Center) {
            if (logo != null) AsyncImage(logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(raw = true, text = chName.take(3).uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(raw = true, text = nowE?.title ?: chName, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (nowE != null) {
                    val nowS = System.currentTimeMillis() / 1000
                    val frac = ((nowS - nowE.start).toFloat() / (nowE.end - nowE.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    Text(raw = true, text = hhmm(nowE.start) + " — " + hhmm(nowE.end), color = PT.T2, fontSize = 18.sp, style = TNUM, maxLines = 1)
                    Box(Modifier.width(70.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DS.Overlay3)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
                    }
                    Text(raw = true, text = "${((nowE.end - nowS) / 60).coerceAtLeast(0)} Min", color = PT.T2, fontSize = 18.sp, style = TNUM, maxLines = 1)
                }
                if (number > 0) Text("$number", color = Accent2, fontSize = 18.sp, fontWeight = FontWeight.Medium, style = TNUM)
                Text(raw = true, text = chName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (media != null && Tm.b(Tm.IF_MEDIA, true)) PChip(media, raw = true)
            }
            if (nowE != null) {
                if (showDesc && nowE.desc.isNotBlank()) Text(raw = true, text = nowE.desc, color = PT.T2, fontSize = 17.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (nextE != null) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(raw = true, text = hhmm(nextE.start) + " — " + hhmm(nextE.end), color = PT.T2, fontSize = 18.sp, style = TNUM, maxLines = 1)
                    Text(raw = true, text = nextE.title, color = PT.T2, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                val supported = item != null && Epg.supported(item)
                Text(if (supported) "Kein Programm für diesen Sender" else "Kein Programm verfügbar", color = PT.T2, fontSize = 17.sp, maxLines = 1)
            }
        }
    }
}

/**
 * Senderliste beim Fernsehen (Taste links): links die Sender mit laufender Sendung, in der Mitte das Programm
 * des markierten Senders (Heute/Morgen), rechts oben die Einzelheiten zur laufenden Sendung. Das Bild läuft rechts weiter.
 * Kategorien: noch einmal links.
 */
@Composable
fun ChannelOverlay(
    liveAll: List<Item>, curId: String, curItem: Item?,
    chanCat: String, onChanCat: (String) -> Unit,
    showCats: Boolean, onShowCats: (Boolean) -> Unit,
    focusedCh: Item?, onFocusedCh: (Item) -> Unit,
    inHeader: Boolean, onInHeader: (Boolean) -> Unit,
    chanFocus: FocusRequester, catsFocus: FocusRequester, headerFocus: FocusRequester,
    onClose: () -> Unit, onArchive: (Item, EpgEntry) -> Unit, onPick: (Item, List<Item>) -> Unit
) {
    // TiviMate: Links im Player = Senderliste über dem Bild. Nochmal Links = Gruppen.
    // Rechts = Programm des markierten Senders (Sendungen wählbar, laufende mit „LIVE“), daneben die Tage, ganz rechts Details.
    val ctx = LocalContext.current
    val byId = remember(liveAll) { liveAll.associateBy { it.id } }
    val groups = remember(liveAll) { liveAll.groupBy { it.group } }
    val favItems = Library.favs.mapNotNull { byId[it] }
    val favCats = CatFavs.list.filter { it in groups }
    val catNames = favCats + groups.keys.filter { it !in favCats }
    val myLists = ChannelLists.lists.toList()
    val curList = ChannelLists.byKey(chanCat)
    val chanList = when {
        chanCat == FAV_CAT -> favItems
        chanCat == "ALL" -> liveAll
        chanCat == "RECENT" -> History.ids.mapNotNull { byId[it] }
        curList != null -> ChannelListLogic.resolve(curList, byId)
        PlGroups.isPl(chanCat) -> PlGroups.resolve(chanCat, liveAll).orEmpty()
        else -> groups[chanCat].orEmpty()
    }
    val startIdx = chanList.indexOfFirst { it.id == curId }
    val ls = rememberLazyListState()
    LaunchedEffect(chanCat) { ls.scrollToItem(maxOf(startIdx - 2, 0)) }
    val fc = focusedCh ?: curItem
    val fe by produceState(emptyList<EpgEntry>(), fc?.id) { value = if (fc != null) EpgFull.get(fc) else emptyList() }
    val nowSec = System.currentTimeMillis() / 1000
    val catLabel = when {
        chanCat == FAV_CAT -> Lang.tr("Favoriten"); chanCat == "ALL" -> Lang.tr("Alle Sender"); chanCat == "RECENT" -> Lang.tr("Verlauf")
        curList != null -> curList.name; else -> PlGroups.label(chanCat) ?: chanCat
    }
    val plName = PlGroups.playlistOfKey(chanCat) ?: PlaylistStore.lists.getOrNull(PlaylistStore.active)?.name
    val header = listOfNotNull(plName, catLabel.takeIf { !chanCat.startsWith(PlGroups.P) }).joinToString("  •  ")
    // Programmansicht (nach Rechts): markierte Sendung bestimmt die Detailkarte
    var progMode by remember { mutableStateOf(false) }
    var selE by remember(fc?.id) { mutableStateOf<EpgEntry?>(null) }
    val progFocus = remember { FocusRequester() }
    LaunchedEffect(progMode) {
        val target = if (progMode) progFocus else chanFocus
        repeat(12) { kotlinx.coroutines.delay(40); if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect }
    }
    val panelBg = Color(0xFF1B1E22).copy(alpha = uiAlpha(0.95f))
    val detailE = selE ?: Epg.current(fe)

    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xCC000000), 0.6f to Color(0x99000000), 1f to Color(0x33000000)))) {
        Row(Modifier.fillMaxSize()) {
            // ---- Gruppen (zweites Links)
            if (showCats) {
                Column(
                    Modifier.fillMaxHeight().width(280.dp).background(Color(0xF0181A1E)).padding(horizontal = 16.dp, vertical = 12.dp)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) false
                            else if (e.key == Key.DirectionRight) { onShowCats(false); true }
                            else e.key == Key.DirectionLeft
                        }
                ) {
                    LazyColumn {
                        item { PCatRow("Favoriten", favItems.size, chanCat == FAV_CAT, true, if (chanCat == FAV_CAT) Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat(FAV_CAT) }) { onShowCats(false) } }
                        item { PCatRow("Verlauf", History.ids.count { it in byId }, chanCat == "RECENT", false, if (chanCat == "RECENT") Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat("RECENT") }, icon = AIcon.History) { onShowCats(false) } }
                        items(myLists) { l ->
                            val k = ChannelListLogic.key(l)
                            PCatRow(l.name, ChannelListLogic.resolve(l, byId).size, chanCat == k, false, if (chanCat == k) Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat(k) }, icon = AIcon.Lists, raw = true) { onShowCats(false) }
                        }
                        item { PCatRow("Alle Sender", liveAll.size, chanCat == "ALL", false, if (chanCat == "ALL") Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat("ALL") }) { onShowCats(false) } }
                        if (PlaylistStore.multiPlaylist) {
                            // TiviMate: je Wiedergabeliste ein Abschnitt mit ihren Ordnern
                            PlGroups.sections(liveAll).forEach { s ->
                                val pk = PlGroups.keyP(s.playlist)
                                item(key = pk) { PCatRow(s.playlist, s.count, chanCat == pk, false, if (chanCat == pk) Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat(pk) }, raw = true,
                                    expanded = !PlGroups.collapsed(s.playlist)) { PlGroups.toggle(ctx, s.playlist) } }
                                if (!PlGroups.collapsed(s.playlist)) items(Tm.sorted(s.groups, Tm.i(Tm.GRP_SORT, 0)) { it.first }, key = { pk + "0001" + it.first }) { (g, c) ->
                                    val gk = PlGroups.keyG(s.playlist, g)
                                    PCatRow("   " + g, c, chanCat == gk, false, if (chanCat == gk) Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat(gk) }, raw = true) { onShowCats(false) }
                                }
                            }
                        } else items(catNames) { g ->
                            PCatRow(g, groups[g]?.size ?: 0, chanCat == g, g in CatFavs.list, if (chanCat == g) Modifier.focusRequester(catsFocus) else Modifier, { CatFavs.toggle(ctx, g) }, { onChanCat(g) }, raw = true) { onShowCats(false) }
                        }
                    }
                }
            }
            // ---- Sender (in der Programmansicht ausgeblendet)
            if (!progMode) Column(
                Modifier.fillMaxHeight().width(360.dp).background(panelBg)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) false
                        else if (e.key == Key.DirectionRight && !showCats) { progMode = true; true }
                        else if (e.key == Key.DirectionLeft && !showCats) { onShowCats(true); true }
                        else false
                    }
            ) {
                Box(Modifier.fillMaxWidth().height(46.dp).background(Color(0x33000000)).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                    Text(raw = true, text = header, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LazyColumn(state = ls, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    itemsIndexed(chanList) { i, it ->
                        ChannelRow(
                            it, it.id == curId, i + 1,
                            if (i == startIdx || (startIdx < 0 && i == 0)) Modifier.focusRequester(chanFocus) else Modifier,
                            onFocus = { onFocusedCh(it) }
                        ) { onPick(it, chanList) }
                    }
                }
            }
            // ---- Programm des markierten Senders (in der Programmansicht wählbar, mit Tagesleiste)
            if (fc != null && !showCats && (progMode || Tm.b(Tm.CL_OV_PROGS, true))) {
                Column(Modifier.fillMaxHeight().width(if (progMode) 470.dp else 320.dp).background(if (progMode) panelBg else Color.Transparent)) {
                    Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                            if (Logos.of(fc) != null) AsyncImage(Logos.of(fc), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        Column {
                            Text(raw = true, text = ChannelEdit.title(fc), color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(raw = true, text = listOfNotNull(PlaylistStore.playlistOf(fc.id) ?: plName, fc.group.takeIf { it.isNotBlank() }).joinToString("  •  "), color = Tok.TextMid, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    ProgPane(
                        (if (Tm.b(Tm.CL_HIDE_PAST, false)) fe.filter { it.end > nowSec } else fe), nowSec, progMode, progFocus, Modifier.weight(1f),
                        canArchive = Catchup.version >= 0 && Catchup.has(fc),
                        onSel = { selE = it },
                        onLeft = { progMode = false; selE = null },
                        onPick = { e ->
                            when {
                                nowSec in e.start until e.end -> onPick(fc, chanList)
                                e.end <= nowSec && Catchup.has(fc) -> onArchive(fc, e)
                                else -> {}
                            }
                        }
                    )
                }
            }
            // ---- Details der markierten bzw. laufenden Sendung
            if (fc != null && !showCats && Tm.b(Tm.CL_OV_DESC, true)) Box(Modifier.weight(1f).fillMaxHeight().padding(top = 26.dp, start = 16.dp, end = 26.dp), contentAlignment = Alignment.TopStart) {
                val e = detailE
                if (e != null) {
                    Column(
                        Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xE62A2D33)).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(raw = true, text = e.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(raw = true, text = hhmm(e.start) + " — " + hhmm(e.end), color = Tok.TextMid, fontSize = 16.sp, style = TNUM)
                            if (nowSec in e.start until e.end) {
                                val frac = ((nowSec - e.start).toFloat() / (e.end - e.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                                Box(Modifier.width(48.dp).height(3.dp).background(DS.Overlay3)) { Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White)) }
                                Text(raw = true, text = ((e.end - nowSec) / 60).coerceAtLeast(0).toString() + " Min", color = Tok.TextMid, fontSize = 16.sp, style = TNUM)
                            } else Text(raw = true, text = dayLabel(e.start), color = Tok.TextMid, fontSize = 16.sp)
                        }
                        if (e.desc.isNotBlank()) Text(raw = true, text = e.desc, color = Tok.TextMid, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Programmliste im Overlay. Normal nur zum Lesen (laufende Sendung blau); in der Programmansicht wählbar:
 * markierte Sendung hell, laufende mit „LIVE“, rechts eine Tagesleiste. Links = zurück zu den Sendern.
 */
@Composable
private fun ProgPane(
    epg: List<EpgEntry>, nowSec: Long, active: Boolean, firstReq: FocusRequester, modifier: Modifier,
    canArchive: Boolean, onSel: (EpgEntry) -> Unit, onLeft: () -> Unit, onPick: (EpgEntry) -> Unit
) {
    val leftKey = Modifier.onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { onLeft(); true } else false }
    if (epg.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Kein Programm für diesen Sender", color = Muted, fontSize = 15.sp,
                modifier = if (active) Modifier.focusRequester(firstReq).then(leftKey).focusable() else Modifier)
        }
        return
    }
    val zone = epgZone()
    fun dayOf(sec: Long): Int = java.util.Calendar.getInstance(zone).run { timeInMillis = sec * 1000; get(java.util.Calendar.YEAR) * 1000 + get(java.util.Calendar.DAY_OF_YEAR) }
    val curIdx = epg.indexOfFirst { nowSec in it.start until it.end }.let { if (it < 0) epg.indexOfFirst { e -> e.start > nowSec }.coerceAtLeast(0) else it }
    val st = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(epg) { st.scrollToItem(maxOf(curIdx - 5, 0)) }
    var selIdx by remember(epg) { mutableIntStateOf(curIdx) }
    val days = remember(epg) { epg.map { dayOf(it.start) to it.start }.distinctBy { it.first } }
    val selDay = dayOf(epg.getOrNull(selIdx)?.start ?: nowSec)
    val dayFmt = remember { java.text.SimpleDateFormat("EE,", java.util.Locale.getDefault()).apply { timeZone = zone } }
    val dateFmt = remember { java.text.SimpleDateFormat("d. MMM", java.util.Locale.getDefault()).apply { timeZone = zone } }
    Row(modifier.fillMaxWidth()) {
        LazyColumn(
            state = st, modifier = Modifier.weight(1f).fillMaxHeight().then(leftKey),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            itemsIndexed(epg) { i, e ->
                var f by remember { mutableStateOf(false) }
                val running = nowSec in e.start until e.end
                val past = e.end <= nowSec
                Row(
                    Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (f) TvFocusFill else Color.Transparent)
                        .then(if (active && i == curIdx) Modifier.focusRequester(firstReq) else Modifier)
                        .onFocusChanged { f = it.isFocused; if (it.isFocused) { selIdx = i; onSel(e) } }
                        .then(if (active) Modifier.clickable { onPick(e) } else Modifier)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val c = when { f -> DS.FocusInk; running -> Accent2; past && !canArchive -> Tok.TextMid; else -> Color.White }
                    Text(raw = true, text = hhmm(e.start), color = c, fontSize = 17.sp, style = TNUM)
                    Text(raw = true, text = e.title, color = c, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (running) {
                        PlayerIcon(PIcon.Play, if (f) DS.FocusInk else Accent2, size = 16.dp)
                        Box(Modifier.border(1.dp, if (f) DS.FocusInk else Tok.TextMid, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)) {
                            Text("LIVE", color = if (f) DS.FocusInk else Tok.TextMid, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (past && canArchive) AppIcon(AIcon.Catchup, if (f) DS.FocusInk else Accent2, size = 15.dp)
                }
            }
        }
        // Tagesleiste (nur in der Programmansicht): springt zum ersten Eintrag des Tages
        if (active) LazyColumn(
            Modifier.width(84.dp).fillMaxHeight().padding(vertical = 4.dp)
                .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { runCatching { firstReq.requestFocus() }; true } else false }
        ) {
            items(days) { (d, startSec) ->
                var f by remember { mutableStateOf(false) }
                val sel = d == selDay
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (f) TvFocusFill else if (sel) Color(0x26FFFFFF) else Color.Transparent)
                        .onFocusChanged { f = it.isFocused }
                        .clickable {
                            val i = epg.indexOfFirst { dayOf(it.start) == d }
                            if (i >= 0) { selIdx = i; scope.launch { st.scrollToItem(i) } }
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val c = if (f) DS.FocusInk else if (sel) Color.White else Tok.TextMid
                    Text(raw = true, text = dayFmt.format(java.util.Date(startSec * 1000)), color = c, fontSize = 14.sp)
                    Text(raw = true, text = dateFmt.format(java.util.Date(startSec * 1000)), color = c, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Programm eines Senders als Liste: Uhrzeit und Titel, Trenner „Heute“/„Morgen“/Datum, laufende Sendung farbig. */
@Composable
private fun ProgrammeList(epg: List<EpgEntry>, nowSec: Long) {
    if (epg.isEmpty()) { Text("Kein Programm für diesen Sender", color = Muted, fontSize = 15.sp); return }
    val zone = epgZone()
    fun dayOf(sec: Long): Int = java.util.Calendar.getInstance(zone).run { timeInMillis = sec * 1000; get(java.util.Calendar.YEAR) * 1000 + get(java.util.Calendar.DAY_OF_YEAR) }
    val today = dayOf(nowSec)
    val tomorrow = dayOf(nowSec + 86400)
    // Zeilen: Trenner bei Tageswechsel (nicht vor dem ersten Eintrag des heutigen Tages)
    val rows = remember(epg, today) {
        val out = ArrayList<Pair<String?, EpgEntry?>>()
        var last = -1
        for (e in epg) {
            val d = dayOf(e.start)
            if (d != last && !(last == -1 && d == today)) out.add(Pair(when (d) { today -> "Heute"; tomorrow -> "Morgen"; else -> dayLabel(e.start) }, null))
            last = d
            out.add(Pair(null, e))
        }
        out
    }
    val cur = rows.indexOfFirst { it.second?.let { e -> nowSec in e.start until e.end } == true }
    val st = rememberLazyListState()
    LaunchedEffect(epg) { if (cur > 2) st.scrollToItem(cur - 2) }
    LazyColumn(state = st, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(rows.size) { i ->
            val (sep, e) = rows[i]
            if (sep != null) Text(sep, color = Accent2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            else if (e != null) {
                val running = nowSec in e.start until e.end
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(hhmm(e.start), color = if (running) Accent2 else Tok.TextMid, fontSize = 16.sp, style = TNUM)
                    Text(raw = true, text = e.title, color = if (running) Accent2 else Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PCatRow(
    label: String, count: Int, selected: Boolean, fav: Boolean, modifier: Modifier,
    onFav: (() -> Unit)?, onFocus: () -> Unit, icon: AIcon? = null, raw: Boolean = false, expanded: Boolean? = null, onClick: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusS)
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape)
            .background(if (f) Color.White else if (selected) DS.Overlay2 else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .favKeys { onFav?.invoke() }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val ic = if (fav) AIcon.Star else icon
            if (ic != null) AppIcon(ic, if (f) PT.InkSoft else if (fav) PT.Gold else Color.White, size = 17.dp)
            Text(
                label, color = if (f) PT.Ink else Color.White, fontSize = 16.sp,
                fontWeight = if (selected || f) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, raw = raw
            )
        }
        if (expanded != null) AppIcon(if (expanded) AIcon.Up else AIcon.Down, if (f) PT.InkSoft else Muted, size = 17.dp)
        else Text("$count", color = if (f) PT.InkSoft else Muted, fontSize = 13.sp, style = TNUM)
    }
}

@Composable
private fun ChannelRow(item: Item, current: Boolean, num: Int, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusS)
    val epg by produceState(emptyList<EpgEntry>(), item.id) { value = Epg.now(item) }
    val nowE = Epg.current(epg)
    // Menü-Taste (falls vorhanden): Favorit und eigene Senderlisten. OK gedrückt halten = sofort Favorit (Wunsch des Nutzers).
    var menu by remember { mutableStateOf(false) }
    if (menu) Box { ChannelMenu(item, null, null) { menu = false } }
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
            .background(if (f) Color.White else if (current) DS.Overlay2 else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .menuKeys(onMenu = { menu = true }, onLong = {
                // Mit eigenen Senderlisten: Auswahl (Favoriten oder eine Liste); sonst sofort Favorit an/aus
                if (ChannelLists.lists.isNotEmpty()) { menu = true; return@menuKeys }
                val on = !Library.isFav(item.id)
                Library.toggleFav(ctx, item.id)
                android.widget.Toast.makeText(ctx, Lang.tr(if (on) "Zu Favoriten hinzugefügt" else "Aus Favoriten entfernt"), android.widget.Toast.LENGTH_SHORT).show()
            })
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp), contentAlignment = Alignment.Center) {
            if (Logos.of(item) != null) AsyncImage(Logos.of(item), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title, color = if (f) PT.Ink else Color.White, fontSize = 17.sp,
                fontWeight = if (current || f) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (nowE != null) {
                val frac = ((System.currentTimeMillis() / 1000 - nowE.start).toFloat() / (nowE.end - nowE.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                Text(raw = true, text = nowE.title, color = if (f) PT.InkSoft else if (Tm.b(Tm.CL_HL_NOW, true)) Accent2 else Tok.TextMid, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(2.dp).background(if (f) Color(0x26000000) else DS.Overlay3)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(if (f) PT.Ink else Color.White))
                }
            } else Text(raw = true, text = item.group, color = if (f) PT.InkSoft else Muted, fontSize = 13.sp, maxLines = 1)
        }
        if (Catchup.version >= 0 && Catchup.has(item)) AppIcon(AIcon.Catchup, if (f) PT.InkSoft else Accent2, size = 18.dp)
        if (DeadLinks.isDead(item.url)) AppIcon(AIcon.Warning, if (f) PT.InkSoft else PT.Gold, size = 18.dp)
        if (Library.isFav(item.id)) AppIcon(AIcon.Star, if (f) PT.InkSoft else PT.Gold, size = 18.dp)
        if (f) AppIcon(AIcon.Play, PT.Ink, size = 18.dp)
    }
}

/** Obere Leiste im Live-Player (TV-Stil): links „Wiedergabeliste • Gruppe“, rechts Datum und Uhrzeit. */
@Composable
fun LiveTopBar(modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val plName = ZapContext.key?.let { PlGroups.playlistOfKey(it) } ?: PlaylistStore.lists.getOrNull(PlaylistStore.active)?.name
    val group = ZapContext.key?.let { k ->
        when {
            k.startsWith(PlGroups.P) -> null
            k.startsWith(PlGroups.G) -> PlGroups.label(k)
            k == FAV_CAT -> Lang.tr("Favoriten")
            k == "ALL" -> Lang.tr("Alle Sender")
            else -> ChannelLists.byKey(k)?.name ?: k
        }
    }
    val nowS by produceState(System.currentTimeMillis() / 1000) {
        while (true) { kotlinx.coroutines.delay(15_000); value = System.currentTimeMillis() / 1000 }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Infofeld: Wiedergabeliste/Gruppe, Uhr und Datum je nach Einstellung
        Text(raw = true, text = if (Tm.b(Tm.IF_PLGROUP, true)) listOfNotNull(plName, group).joinToString("  •  ") else "", color = Color.White, fontSize = 20.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (Tm.b(Tm.IF_CLOCK, true)) Text(raw = true, text = (if (Tm.b(Tm.IF_DATE, true)) longDay(nowS) + ", " else "") + hhmm(nowS), color = Color.White, fontSize = 20.sp, maxLines = 1)
        trailing()
    }
}

/**
 * TiviMate: Zeile unter der Senderinfo (OK im Live-Player): „EPG“, „Verlauf“ und die zuletzt gesehenen Sender
 * mit Logo, laufender Sendung und Fortschritt. Runter öffnet darunter die Menüleiste.
 */
@Composable
fun LiveRecentRow(
    firstReq: FocusRequester, recent: List<Item>, modifier: Modifier = Modifier,
    onEpg: () -> Unit, onHistory: () -> Unit, onPick: (Item) -> Unit
) {
    val st = rememberLazyListState()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(state = st, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (Tm.b(Tm.HI_EPG_BTN, true)) item(key = "epg") {
                RecentTile(Modifier.focusRequester(firstReq), onEpg) { f ->
                    PlayerIcon(PIcon.Guide, Color.White, size = 40.dp)
                    Text("EPG", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (Tm.b(Tm.HI_HIST_BTN, true)) item(key = "hist") {
                RecentTile(Modifier, onHistory) { f ->
                    AppIcon(AIcon.History, Color.White, size = 40.dp)
                    Text("Verlauf", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(recent, key = { it.id }) { ch ->
                RecentTile(Modifier, { onPick(ch) }, pad = false) { f ->
                    val epg by produceState(emptyList<EpgEntry>(), ch.id) { value = Epg.now(ch) }
                    val nowE = Epg.current(epg)
                    Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        if (Logos.of(ch) != null) AsyncImage(Logos.of(ch), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Text(raw = true, text = ch.title, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(raw = true, text = if (Tm.b(Tm.RC_NAMES, false)) ChannelEdit.title(ch) else nowE?.title ?: ch.title, color = Accent2, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp))
                    val frac = if (nowE != null) ((System.currentTimeMillis() / 1000 - nowE.start).toFloat() / (nowE.end - nowE.start).coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).background(DS.Overlay3)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
                    }
                }
            }
        }
        AppIcon(AIcon.Down, Tok.TextMid, size = 22.dp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RecentTile(modifier: Modifier, onClick: () -> Unit, pad: Boolean = true, content: @Composable androidx.compose.foundation.layout.ColumnScope.(Boolean) -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.size(width = 140.dp, height = 106.dp).clip(shape)
            .background(if (f) Color(0xFF2C3036) else Color(0xFF1C1F23).copy(alpha = uiAlpha(0.95f)))
            .border(2.dp, if (f) Color.White else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused }
            .clickable(onClick = onClick)
            .padding(if (pad) 8.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (pad) Arrangement.Center else Arrangement.Top
    ) { content(f) }
}

/**
 * TiviMate: Fortschrittsbalken der laufenden Sendung im Live-Player (über die ganze Breite).
 * Blau bis zur Wiedergabeposition (bei Pause/Zeitversatz hinter „jetzt“), heller Rest bis „jetzt“, Punkt = Position.
 * Ohne Programm gilt die laufende halbe Stunde. Markiert: OK = Pause/Weiter, Links/Rechts = spulen (falls möglich).
 */
@Composable
fun LiveSeekBar(
    epg: List<EpgEntry>, behindSec: Long, paused: Boolean, modifier: Modifier = Modifier,
    onToggle: () -> Unit, onSeek: (Int) -> Unit, onDown: () -> Unit, onUp: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val nowS by produceState(System.currentTimeMillis() / 1000) {
        while (true) { kotlinx.coroutines.delay(1000); value = System.currentTimeMillis() / 1000 }
    }
    val e = Epg.current(epg)
    val start = e?.start ?: (nowS / 1800 * 1800)
    val end = e?.end ?: (start + 1800)
    val span = (end - start).coerceAtLeast(1).toFloat()
    val live = ((nowS - start) / span).coerceIn(0f, 1f)
    val pos = ((nowS - behindSec - start) / span).coerceIn(0f, 1f)
    Row(
        modifier.fillMaxWidth().height(34.dp)
            .onFocusChanged { f = it.isFocused }
            .onPreviewKeyEvent { k ->
                if (k.type != KeyEventType.KeyDown) false
                else when (k.key) {
                    Key.DirectionLeft -> { onSeek(-1); true }
                    Key.DirectionRight -> { onSeek(1); true }
                    Key.DirectionDown -> { onDown(); true }
                    Key.DirectionUp -> { onUp(); true }
                    else -> false
                }
            }
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bei Pause (oder markiertem Balken) steht vorne das Symbol, wie bei TiviMate
        if (paused || f) Box(Modifier.size(30.dp).clip(CircleShape).background(if (f) Color.White else Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            PlayerIcon(if (paused) PIcon.Play else PIcon.Pause, if (f) PT.Ink else Color.White, size = 20.dp)
        }
        BoxWithConstraints(Modifier.weight(1f).height(16.dp), contentAlignment = Alignment.CenterStart) {
            val w = maxWidth
            val h = if (f) 5.dp else 3.dp
            Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF)))
            Box(Modifier.fillMaxWidth(live).height(h).clip(RoundedCornerShape(2.dp)).background(Color(0x80FFFFFF)))
            Box(Modifier.fillMaxWidth(pos).height(h).clip(RoundedCornerShape(2.dp)).background(Accent2))
            val d = if (f) 16.dp else 12.dp
            Box(Modifier.offset(x = (w * pos - d / 2).coerceAtLeast(0.dp)).size(d).clip(CircleShape).background(if (f) Color.White else Color(0xFFD0D3D8)))
        }
        if (behindSec >= 3) Text(raw = true, text = "−" + (behindSec / 60).toString() + ":" + (behindSec % 60).toString().padStart(2, '0'),
            color = Color.White, fontSize = 15.sp, style = TNUM)
    }
}

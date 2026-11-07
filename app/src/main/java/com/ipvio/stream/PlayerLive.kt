package com.ipvio.stream

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
fun LiveInfoBlock(item: Item?, title: String, number: Int, epg: List<EpgEntry>, modifier: Modifier = Modifier) {
    // Runde 20 (TV-Stil): Logo frei links, daneben Sendungstitel groß; darunter Zeit, Fortschritt, Restminuten und Sendername,
    // dann Beschreibung und die nächste Sendung
    val nowE = Epg.current(epg)
    val nextE = if (nowE != null) epg.firstOrNull { it.start >= nowE.end } else null
    val logo = item?.logo
    val chName = item?.let { ChannelEdit.title(it) } ?: title
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
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
            }
            if (nowE != null) {
                if (nowE.desc.isNotBlank()) Text(raw = true, text = nowE.desc, color = PT.T2, fontSize = 17.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
    onClose: () -> Unit, onPick: (Item, List<Item>) -> Unit
) {
    val ctx = LocalContext.current
    val byId = remember(liveAll) { liveAll.associateBy { it.id } }
    val groups = remember(liveAll) { liveAll.groupBy { it.group } }
    val favItems = Library.favs.mapNotNull { byId[it] }
    val favCats = CatFavs.list.filter { it in groups }
    val catNames = favCats + groups.keys.filter { it !in favCats }
    // Eigene Senderlisten (angelegt im Live-TV-Bereich mit der Menü-Taste)
    val myLists = ChannelLists.lists.toList()
    val curList = ChannelLists.byKey(chanCat)
    val chanList = when {
        chanCat == FAV_CAT -> favItems
        chanCat == "ALL" -> liveAll
        curList != null -> ChannelListLogic.resolve(curList, byId)
        else -> groups[chanCat].orEmpty()
    }
    val startIdx = chanList.indexOfFirst { it.id == curId }
    val ls = rememberLazyListState()
    LaunchedEffect(chanCat) { ls.scrollToItem(maxOf(startIdx - 2, 0)) }
    val fc = focusedCh ?: curItem
    val fe by produceState(emptyList<EpgEntry>(), fc?.id) { value = if (fc != null) EpgFull.get(fc) else emptyList() }
    val nowSec = System.currentTimeMillis() / 1000
    val catLabel = when { chanCat == FAV_CAT -> "Favoriten"; chanCat == "ALL" -> "Alle Sender"; curList != null -> curList.name; else -> chanCat }

    // Dunkler Verlauf von links, rechts bleibt das Bild sichtbar
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF2050608), 0.55f to Color(0xD9050608), 0.8f to Color(0x66050608), 1f to Color.Transparent))) {
    Row(Modifier.fillMaxSize()) {
        if (showCats) {
            Column(
                Modifier.fillMaxHeight().width(300.dp).background(Color(0xF50C0D16)).padding(16.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) false
                        else if (e.key == Key.DirectionRight) { onShowCats(false); true }
                        else if (e.key == Key.DirectionLeft) true
                        else false
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Kategorien", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                LazyColumn {
                    item {
                        PCatRow("Favoriten", favItems.size, chanCat == FAV_CAT, true, if (chanCat == FAV_CAT) Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat(FAV_CAT) }) { onShowCats(false) }
                    }
                    items(myLists) { l ->
                        val k = ChannelListLogic.key(l)
                        PCatRow(l.name, ChannelListLogic.resolve(l, byId).size, chanCat == k, false, if (chanCat == k) Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat(k) }, icon = AIcon.Lists, raw = true) { onShowCats(false) }
                    }
                    item {
                        PCatRow("Alle Sender", liveAll.size, chanCat == "ALL", false, if (chanCat == "ALL") Modifier.focusRequester(catsFocus) else Modifier, null, { onChanCat("ALL") }) { onShowCats(false) }
                    }
                    items(catNames) { g ->
                        PCatRow(g, groups[g]?.size ?: 0, chanCat == g, g in CatFavs.list, if (chanCat == g) Modifier.focusRequester(catsFocus) else Modifier, { CatFavs.toggle(ctx, g) }, { onChanCat(g) }) { onShowCats(false) }
                    }
                }
            }
        }
        // Links: Sender
        Column(
            Modifier.fillMaxHeight().width(420.dp).padding(start = 16.dp, top = 20.dp, end = 8.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) false
                    else if (e.key == Key.DirectionRight && !showCats) { onClose(); true }
                    else if (e.key == Key.DirectionLeft && !showCats) { onShowCats(true); true }
                    else false
                },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(catLabel, color = Tok.TextMid, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                raw = curList != null || (chanCat != FAV_CAT && chanCat != "ALL"), modifier = Modifier.padding(start = 6.dp))
            LazyColumn(state = ls) {
                itemsIndexed(chanList) { i, it ->
                    ChannelRow(
                        it, it.id == curId, i + 1,
                        if (i == startIdx || (startIdx < 0 && i == 0)) Modifier.focusRequester(chanFocus) else Modifier,
                        onFocus = { onFocusedCh(it) }
                    ) { onPick(it, chanList) }
                }
            }
        }
        // Mitte: Programm des markierten Senders
        if (fc != null) Column(Modifier.fillMaxHeight().width(380.dp).padding(start = 12.dp, top = 20.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(width = 64.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                    if (fc.logo != null) AsyncImage(fc.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Column {
                    Text(raw = true, text = fc.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(raw = true, text = fc.group, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ProgrammeList(fe, nowSec)
        }
        // Rechts oben: laufende Sendung
        Box(Modifier.weight(1f).fillMaxHeight().padding(top = 20.dp, end = 24.dp), contentAlignment = Alignment.TopEnd) {
            val nowE = Epg.current(fe)
            if (fc != null && nowE != null) {
                val shape = RoundedCornerShape(DS.RadiusM)
                Column(
                    Modifier.widthIn(max = 520.dp).clip(shape).background(Color(0xE6202228)).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(raw = true, text = nowE.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val frac = ((nowSec - nowE.start).toFloat() / (nowE.end - nowE.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    val left = ((nowE.end - nowSec) / 60).coerceAtLeast(0)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${hhmm(nowE.start)} – ${hhmm(nowE.end)}", color = Tok.TextMid, fontSize = 16.sp, style = TNUM)
                        Box(Modifier.width(90.dp).height(3.dp).background(DS.Overlay3)) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
                        }
                        Text("$left Min", color = Tok.TextMid, fontSize = 16.sp, style = TNUM)
                    }
                    if (nowE.desc.isNotBlank()) Text(raw = true, text = nowE.desc, color = Tok.TextMid, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
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
    onFav: (() -> Unit)?, onFocus: () -> Unit, icon: AIcon? = null, raw: Boolean = false, onClick: () -> Unit
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
        Text("$count", color = if (f) PT.InkSoft else Muted, fontSize = 13.sp, style = TNUM)
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
        Text(raw = true, text = num.toString(), color = if (f) PT.InkSoft else Tok.TextMid, fontSize = 16.sp, style = TNUM,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(34.dp))
        Box(Modifier.size(width = 64.dp, height = 44.dp), contentAlignment = Alignment.Center) {
            if (item.logo != null) AsyncImage(item.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title, color = if (f) PT.Ink else Color.White, fontSize = 17.sp,
                fontWeight = if (current || f) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (nowE != null) {
                val frac = ((System.currentTimeMillis() / 1000 - nowE.start).toFloat() / (nowE.end - nowE.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                Text(raw = true, text = nowE.title, color = if (f) PT.InkSoft else Accent2, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val plName = PlaylistStore.lists.getOrNull(PlaylistStore.active)?.name
    val group = ZapContext.key?.let { k ->
        when {
            k == FAV_CAT -> Lang.tr("Favoriten")
            k == "ALL" -> Lang.tr("Alle Sender")
            else -> ChannelLists.byKey(k)?.name ?: k
        }
    }
    val nowS by produceState(System.currentTimeMillis() / 1000) {
        while (true) { kotlinx.coroutines.delay(15_000); value = System.currentTimeMillis() / 1000 }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(raw = true, text = listOfNotNull(plName, group).joinToString("  •  "), color = Color.White, fontSize = 20.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(raw = true, text = longDay(nowS) + ", " + hhmm(nowS), color = Color.White, fontSize = 20.sp, maxLines = 1)
        trailing()
    }
}

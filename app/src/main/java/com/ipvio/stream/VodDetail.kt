package com.ipvio.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Detailseite für Filme und Serien im Aufbau von TiviMate:
 *  - oben links Titel (Jahr), Wertung, Jahr • Länge/Staffeln • Genres, Besetzung, Regisseur, Beschreibung
 *  - rechts das Szenenbild, nach links und unten in den Hintergrund auslaufend
 *  - darunter „Abspielen“ (Serie: „Abspielen S1 E1“ bzw. die nächste Folge) und runde Knöpfe: Extern, Trailer, Meine Liste
 *  - Serien: je Staffel eine Reihe mit Folgenbildern, rechts „1 / 6“
 */

@Composable
fun VodDetail(item0: Item, all: List<Item>, onPlay: (String, String, Long) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val item by produceState(item0, item0.id) { value = SeriesLazy.full(item0) }
    BackHandler(onBack = onBack)
    var meta by remember(item.id) { mutableStateOf(MetaRepo.cached(item)) }
    LaunchedEffect(item.id) { MetaRepo.extended(ctx, item)?.let { meta = it } }
    val credits by produceState<Credits?>(null, meta?.tmdbId) { value = meta?.let { if (it.tmdbId > 0) CreditsRepo.credits(ctx, it) else null } }
    val isSeries = item.kind == Kind.SERIES
    val seasons = remember(item) { item.episodes.map { it.season }.distinct().sorted() }
    val saved = remember(item.id) { VariantPref.get(ctx, item.id) }
    val qCap = remember { PlaybackPrefs.cap(ctx) }
    fun pick(ep: Episode) = pickVariant(ep, saved.first, saved.second, saved.third, qCap)
    val progress = remember(item.id) { EpProgress.of(ctx, item.id) }
    val resume = WatchStore.entries.firstOrNull { it.itemId == item.id }
    // Film: dieselbe Quelle wie auf der bisherigen Detailseite (gewählte Variante, sonst die Adresse des Eintrags)
    val movieAlts by produceState(listOf(item), item, all) {
        if (item.kind == Kind.MOVIE) value = withContext(Dispatchers.Default) { Sources.alternatives(item, all) }
    }
    val movieUrl = Sources.asVariants(movieAlts).firstOrNull { it.label == saved.third }?.url ?: item.url
    // Serie: nächste Folge = Fortsetzen-Punkt, sonst die erste
    val nextEp: Episode? = remember(item, resume) {
        val r = resume
        val se = r?.let { if (it.season > 0 || it.ep > 0) it.season to it.ep else ResumeLogic.seasonEp(it.title) }
        (se?.let { s -> item.episodes.firstOrNull { it.season == s.first && it.num == s.second } }) ?: item.episodes.firstOrNull()
    }
    fun playEp(ep: Episode) {
        val p = progress[ResumeLogic.key(ep.season, ep.num)]
        val pos = if (p != null && !p.done) p.pos * 1000L else 0L
        onPlay(pick(ep).url, item.title + " S" + ep.season + "E" + ep.num, pos)
    }
    val playReq = remember { FocusRequester() }
    LaunchedEffect(item.id) { delay(80); runCatching { playReq.requestFocus() } }
    var compact by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        meta?.backdrop?.let { b ->
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.7f).fillMaxHeight(0.72f)) {
                AsyncImage(cardBackdrop(b), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopEnd)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Bg, 0.3f to Bg.copy(alpha = .5f), 0.65f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Bg.copy(alpha = .4f), 1f to Bg)))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 40.dp)) {
            item(key = "info") {
                Column(Modifier.fillMaxWidth(0.5f).heightIn(min = if (compact) 0.dp else 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val year = meta?.year ?: item.year?.toString()
                    Text(raw = true, text = item.title.let { t -> if (year != null && !t.contains("($year)")) "$t ($year)" else t },
                        color = Tok.TextHi, fontSize = 28.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        meta?.rating?.takeIf { it > 0 }?.let { r ->
                            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFBDBDBD)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text(raw = true, text = String.format(java.util.Locale.US, "%.1f", r), color = Color(0xFF15171A), fontSize = 12.sp)
                            }
                        }
                        val parts = ArrayList<String>()
                        year?.let { parts.add(it) }
                        if (isSeries) seasons.size.takeIf { it > 0 }?.let { parts.add(if (it == 1) "1 Staffel" else "$it " + Lang.tr("Staffeln")) }
                        else meta?.runtime?.takeIf { it > 0 }?.let { parts.add("${it / 60}h ${it % 60}m") }
                        meta?.genres?.takeIf { it.isNotEmpty() }?.let { parts.add(it.take(4).joinToString(", ")) }
                        if (parts.isNotEmpty()) Text(raw = true, text = parts.joinToString("  •  "), color = Tok.TextMid, fontSize = 15.sp, maxLines = 1)
                    }
                    credits?.cast?.takeIf { it.isNotEmpty() }?.let { c ->
                        Row { Text("Besetzung:", color = Muted, fontSize = 14.sp); Text(raw = true, text = " " + c.take(4).joinToString(", ") { it.name }, color = Tok.TextMid, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    credits?.directors?.takeIf { it.isNotEmpty() }?.let { d ->
                        Row { Text("Regisseur:", color = Muted, fontSize = 14.sp); Text(raw = true, text = " " + d.take(2).joinToString(", ") { it.name }, color = Tok.TextMid, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    meta?.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(raw = true, text = it, color = Tok.TextMid, fontSize = 14.sp, lineHeight = 19.sp, maxLines = if (compact) 5 else 8, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(key = "buttons") {
                Row(Modifier.padding(top = 18.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val label = when {
                        isSeries -> nextEp?.let { "Abspielen S" + it.season + " E" + it.num } ?: "Abspielen"
                        resume != null -> Lang.tr("Fortsetzen") + " · " + (resume.pos / 60000) + " Min"
                        else -> "Abspielen"
                    }
                    VodPlayPill(label, Modifier.focusRequester(playReq).onFocusChanged { if (it.isFocused) compact = false }) {
                        if (isSeries) nextEp?.let { playEp(it) }
                        else onPlay(movieUrl, item.title, resume?.pos ?: 0L)
                    }
                    VodRoundButton(AIcon.External) {
                        val u = if (isSeries) nextEp?.let { pick(it).url } else movieUrl
                        u?.let { openExternal(ctx, it, item.title) }
                    }
                    VodRoundButton(AIcon.Play) { openTrailer(ctx, meta?.trailerKey, item.title) }
                    val inList = Library.isWatch(item.id)
                    VodRoundButton(if (inList) AIcon.Check else AIcon.Plus) { Library.toggleWatch(ctx, item.id) }
                    if (resume != null && !isSeries) VodRoundButton(AIcon.Rewind) { onPlay(movieUrl, item.title, 0L) }
                }
            }
            if (isSeries) items(seasons, key = { "s$it" }) { s ->
                SeasonRow(item, meta, s, item.episodes.filter { it.season == s }, progress, onFocusIn = { compact = true }) { playEp(it) }
            }
        }
    }
}

@Composable
private fun SeasonRow(
    item: Item, meta: Meta?, season: Int, eps: List<Episode>, progress: Map<Long, ResumeLogic.EpPos>,
    onFocusIn: () -> Unit, onPlay: (Episode) -> Unit
) {
    var focusIdx by remember { mutableIntStateOf(0) }
    var rowFocus by remember { mutableStateOf(false) }
    val tmdb = meta?.tmdbId?.takeIf { it > 0 && meta.isTv }
    val epMeta by produceState(emptyList<EpMeta>(), tmdb, season) { if (tmdb != null) value = MetaRepo.season(tmdb, season, meta.imdbId) }
    Column(Modifier.padding(bottom = 18.dp).onFocusChanged { rowFocus = it.hasFocus; if (it.hasFocus) onFocusIn() }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Lang.tr("Staffel") + " " + season, raw = true, color = if (rowFocus) Tok.TextHi else Tok.TextLow, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(raw = true, text = "${focusIdx + 1} / ${eps.size}", color = Tok.TextLow, fontSize = 16.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(eps, key = { _, e -> e.season * 10000 + e.num }) { i, ep ->
                val em = epMeta.firstOrNull { it.num == ep.num }
                val p = progress[ResumeLogic.key(ep.season, ep.num)]
                EpisodeCard(ep, em, p, onFocus = { focusIdx = i }) { onPlay(ep) }
            }
        }
    }
}

@Composable
private fun EpisodeCard(ep: Episode, em: EpMeta?, p: ResumeLogic.EpPos?, onFocus: () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.width(200.dp).clip(shape).background(Tok.Card)
            .border(2.dp, if (f) Color.White else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF2B2E33))) {
            em?.still?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (p != null && p.fraction > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                Box(Modifier.fillMaxWidth(p.fraction).fillMaxHeight().background(Accent2))
            }
        }
        val t = em?.name?.takeIf { it.isNotBlank() } ?: ep.title
        Box(Modifier.fillMaxWidth().background(if (f) TvFocusFill else Color.Transparent).padding(horizontal = 6.dp, vertical = 5.dp)) {
            Text(raw = true, text = "E" + ep.num + ". " + t, color = if (f) Color(0xFF15171A) else Tok.TextHi, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VodPlayPill(text: String, modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (f) TvFocusFill else Color(0x33FFFFFF))
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIcon(AIcon.Play, if (f) Color(0xFF15171A) else Tok.TextHi, size = 18.dp)
        Text(text, raw = text.contains(" S"), color = if (f) Color(0xFF15171A) else Tok.TextHi, fontSize = 16.sp)
    }
}

@Composable
private fun VodRoundButton(icon: AIcon, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (f) TvFocusFill else Color(0x33FFFFFF))
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { AppIcon(icon, if (f) Color(0xFF15171A) else Tok.TextHi, size = 20.dp) }
}

/*
 * XY IPTV – Sport-Guide: Sportübertragungen aus dem TV-Programm, eigene Top-Spiele und Live-Spielstände,
 * jeweils mit den Sendern, auf denen es läuft. Eigene Umsetzung für XY IPTV (basierend auf OpenTV).
 * Lizenz: GNU GPL v3.0 oder später.
 */
package app.opentv.ui.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.xy.SportEvent
import app.opentv.xy.SportGuideLogic
import app.opentv.xy.SportKind
import app.opentv.xy.SportLogic
import app.opentv.xy.SportScore
import app.opentv.xy.SportScores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Eigene Top-Spiele (Datei im Releases-Repo, änderbar ohne neue App-Version). */
private const val SPORTS_URL = "https://raw.githubusercontent.com/Pelzixx/IPTV-XY-Releases/main/sports.json"

/** Ein Eintrag der Liste: Ereignis plus die passenden Sender (je Sender nur die beste Qualität). */
private data class SportRow(val event: SportEvent, val channels: List<Channel>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportScreen(onPlayChannel: (Channel) -> Unit) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    var rows by remember { mutableStateOf<List<SportRow>?>(null) }
    var scores by remember { mutableStateOf<List<SportScore>>(emptyList()) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Liste laden und alle 5 Minuten neu aufbauen (Programm läuft weiter)
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            rows = withContext(Dispatchers.IO) { runCatching { load(graph, now) }.getOrDefault(emptyList()) }
            delay(5 * 60_000L)
        }
    }
    // Spielstände jede Minute, solange die Seite offen ist und Spiele mit Mannschaften anstehen
    LaunchedEffect(rows != null) {
        while (true) {
            val list = rows
            val t = System.currentTimeMillis()
            if (list != null && list.any { it.event.home != null && it.event.startMillis < t + 6 * 3_600_000L }) {
                scores = withContext(Dispatchers.IO) { fetchScores(graph.httpClient) }
            }
            now = t
            delay(60_000L)
        }
    }

    val all = rows
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 28.dp)) {
        Text(stringResource(R.string.nav_sport), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (all == null) {
            Text(stringResource(R.string.sport_loading), modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val kinds = all.map { it.event.kind }.distinct().sortedBy { it.ordinal }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.sport_all)) })
            }
            items(kinds) { k ->
                FilterChip(selected = filter == k.name, onClick = { filter = k.name }, label = { Text(stringResource(kindLabel(k))) })
            }
        }
        val shown = all.filter { filter == null || it.event.kind.name == filter }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.sport_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(shown, key = { it.event.title + it.event.startMillis + it.event.featured }) { r ->
                EventCard(r, SportScores.find(r.event, scores), now, onPlayChannel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(r: SportRow, score: SportScore?, now: Long, onPlayChannel: (Channel) -> Unit) {
    val e = r.event
    val live = score?.state == "in" || (score == null && SportGuideLogic.isLive(e, now))
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .then(if (e.featured) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(110.dp)) {
                if (live) Badge(stringResource(R.string.sport_live), Color(0xFFD32F2F))
                else Text(timeLabel(e.startMillis, now), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (score != null && score.detail.isNotEmpty() && score.state != "pre")
                    Text(score.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (e.featured) Badge(stringResource(R.string.sport_top), MaterialTheme.colorScheme.primary)
                    Text(
                        listOfNotNull(stringResource(kindLabel(e.kind)), e.league).distinct().joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (score != null && score.state != "pre" && e.home != null && e.away != null) {
                    Text(
                        "${e.home}  ${score.scoreFor(e)}  ${e.away}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (e.featured && !e.description.isNullOrBlank())
                    Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        if (r.channels.isEmpty()) {
            Text(stringResource(R.string.sport_no_channel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            r.channels.take(8).forEach { ch ->
                OutlinedButton(onClick = { onPlayChannel(ch) }) { Text(ch.shownName, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Stand aus Sicht des Ereignisses (falls Heim/Gast in der Quelle vertauscht sind). */
private fun SportScore.scoreFor(e: SportEvent): String {
    val swapped = e.home != null && !SportScores.teamMatches(e.home, home) && SportScores.teamMatches(e.home, away)
    return if (swapped) "$awayScore : $homeScore" else "$homeScore : $awayScore"
}

private fun timeLabel(start: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(start).atZone(zone)
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
    val time = t.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (t.toLocalDate()) {
        today -> time
        today.plusDays(1) -> t.format(DateTimeFormatter.ofPattern("EEE")) + " " + time
        else -> t.format(DateTimeFormatter.ofPattern("EEE dd.MM.")) + " " + time
    }
}

private fun kindLabel(k: SportKind): Int = when (k) {
    SportKind.FOOTBALL -> R.string.sport_kind_football
    SportKind.BASKETBALL -> R.string.sport_kind_basketball
    SportKind.MOTORSPORT -> R.string.sport_kind_motorsport
    SportKind.TENNIS -> R.string.sport_kind_tennis
    SportKind.ICE_HOCKEY -> R.string.sport_kind_ice_hockey
    SportKind.AMERICAN_FOOTBALL -> R.string.sport_kind_american_football
    SportKind.COMBAT -> R.string.sport_kind_combat
    SportKind.OTHER -> R.string.sport_kind_other
}

private suspend fun load(graph: ServiceLocator.Graph, now: Long): List<SportRow> {
    val channels = graph.database.channels().allForMatching().filter { !it.hidden }
    // Guide-Kennung → Sender; je logischem Sender (groupKey) nur die beste Qualität
    val byEpg = HashMap<String, MutableList<Channel>>()
    for (c in channels) for (id in c.epgCandidates) byEpg.getOrPut(id) { mutableListOf() }.add(c)

    val shows = graph.database.programmes().sportsCandidates(now - 3 * 3_600_000L, now + 2 * 86_400_000L).map {
        SportGuideLogic.Show(it.epgChannelId, it.title, it.category, it.description, it.startUtcMillis, it.endUtcMillis)
    }
    val guide = SportGuideLogic.fromShows(shows, now).filter { ev -> ev.epgIds.any { byEpg.containsKey(it) } }

    val featured = runCatching {
        graph.httpClient.newCall(Request.Builder().url(SPORTS_URL).header("Cache-Control", "no-cache").build())
            .execute().use { if (it.isSuccessful) SportLogic.parseCustom(it.body?.string().orEmpty()) else emptyList() }
    }.getOrDefault(emptyList())

    return SportGuideLogic.merge(featured, guide, now).map { ev ->
        val list = if (ev.featured) channels.filter { c -> ev.channelNames.any { SportLogic.nameMatches(it, c.shownName) || SportLogic.nameMatches(it, c.name) } }
        else ev.epgIds.flatMap { byEpg[it].orEmpty() }
        SportRow(ev, best(list))
    }
}

private fun best(list: List<Channel>): List<Channel> =
    list.groupBy { it.groupKey.ifEmpty { it.name } }.values
        .map { g -> g.maxBy { it.qualityRank } }
        .sortedWith(compareByDescending<Channel> { it.favourite }.thenBy { it.shownName })

private suspend fun fetchScores(http: OkHttpClient): List<SportScore> = coroutineScope {
    SportScores.LEAGUES.map { l ->
        async {
            runCatching {
                http.newCall(Request.Builder().url(SportScores.url(l)).build()).execute()
                    .use { if (it.isSuccessful) SportScores.parseEspn(it.body?.string().orEmpty()) else emptyList() }
            }.getOrDefault(emptyList())
        }
    }.awaitAll().flatten()
}

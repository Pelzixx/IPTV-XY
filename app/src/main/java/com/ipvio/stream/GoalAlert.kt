package com.ipvio.stream

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Meldung „Tor“ oder „Abpfiff“ zu einem markierten Spiel. */
data class GoalNotice(val event: SportEvent, val kind: String, val text: String)

/** Reine Logik (Unit-Tests): Was hat sich zwischen zwei Ständen eines Spiels geändert? */
object GoalLogic {
    /** null = nichts Neues; sonst "Tor" oder "Abpfiff". */
    fun change(old: SportEvent?, new: SportEvent): String? {
        if (old == null) return null
        if (!old.finished && new.finished) return "Abpfiff"
        val o = (old.scoreHome ?: 0) + (old.scoreAway ?: 0)
        val n = (new.scoreHome ?: 0) + (new.scoreAway ?: 0)
        return if (n > o) "Tor" else null
    }

    fun line(e: SportEvent): String = "${e.home} ${e.scoreHome ?: 0}:${e.scoreAway ?: 0} ${e.away}"
}

/**
 * Tor-Alarm (Wunsch des Nutzers, 2026-11-09): Für markierte Spiele (Sport Guide → „Tor-Alarm“) erscheint bei einem Tor
 * oder Abpfiff eine kleine Einblendung – auch während ein anderer Sender läuft. Spielstände kommen von OpenLigaDB,
 * solange ein markiertes Spiel läuft, wird jede Minute nachgesehen.
 */
object GoalAlert {
    val watched = mutableStateListOf<String>()
    var pending by mutableStateOf<GoalNotice?>(null)
    private val last = HashMap<String, SportEvent>()
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ipvio_goals", Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (watched.isEmpty()) watched.addAll(prefs(c).getString("keys", "").orEmpty().split("\n").filter { it.isNotBlank() })
    }

    fun isOn(key: String) = key in watched

    fun toggle(c: Context, e: SportEvent): Boolean {
        val on = if (watched.remove(e.key)) false else { watched.add(e.key); last[e.key] = e; true }
        prefs(c).edit().putString("keys", watched.joinToString("\n")).apply()
        return on
    }

    /** Nur Spiele mit Spielstand (OpenLigaDB) können gemeldet werden. */
    fun supported(e: SportEvent) = e.source == "OpenLigaDB" && !e.finished

    suspend fun watch(c: Context) {
        init(c)
        while (true) {
            val now = System.currentTimeMillis() / 1000
            val mine = SportsRepo.events.filter { it.key in watched }
            if (mine.any { it.live(now) || (now >= it.start - 120 && !it.finished) }) {
                runCatching { SportsRepo.refreshScores() }
                for (e in SportsRepo.events.filter { it.key in watched }) {
                    val kind = GoalLogic.change(last[e.key], e)
                    last[e.key] = e
                    if (kind != null && pending == null) {
                        val min = SportsLogic.gameMinute(e, System.currentTimeMillis() / 1000)
                        pending = GoalNotice(e, kind, GoalLogic.line(e) + (if (kind == "Tor" && min != null) " · $min" else ""))
                    }
                }
                // Beendete Spiele aus der Liste nehmen
                val done = SportsRepo.events.filter { it.key in watched && it.finished }.map { it.key }
                if (done.isNotEmpty()) { watched.removeAll(done); prefs(c).edit().putString("keys", watched.joinToString("\n")).apply() }
            } else for (e in mine) last[e.key] = e
            delay(60_000)
        }
    }
}

/** Einblendung oben: „⚽ Tor! Bayern 2:1 Dortmund · 67'“ mit „Umschalten“ (zum Sender des Spiels) und „Schließen“. */
@Composable
fun GoalCard(n: GoalNotice, channel: Item?, modifier: Modifier, onClose: () -> Unit, onSwitch: () -> Unit) {
    val fr = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(n) { delay(150); runCatching { fr.requestFocus() }; delay(15_000); onClose() }
    val shape = RoundedCornerShape(DS.RadiusL)
    Row(
        modifier.widthIn(max = 760.dp).clip(shape).background(Tok.GlassStrong).border(1.dp, Accent2, shape).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(raw = true, text = "⚽", fontSize = 28.sp, color = Color.White)
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (n.kind == "Tor") "Tor!" else "Abpfiff", color = Accent2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(raw = true, text = n.text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(raw = true, text = n.event.league, color = Muted, fontSize = 14.sp)
        }
        if (channel != null) PillButton("Umschalten", Modifier.focusRequester(fr), light = true) { onSwitch() }
        PillButton("Schließen", if (channel == null) Modifier.focusRequester(fr) else Modifier) { onClose() }
    }
}

package com.ipvio.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Spielstand im Player (Wunsch des Nutzers, 2026-11-11; Optik > Player > „Spielstand einblenden“, Standard aus):
 * Läuft auf dem Sender gerade ein Spiel mit Live-Stand (OpenLigaDB), steht oben links „Heim 1 : 0 Gast · 67'“.
 * Nach einem Tor leuchtet die Anzeige einige Sekunden mit „Tor!“. Der Stand wird jede Minute neu geholt.
 */
@Composable
fun ScoreOverlay(chId: String?, liveAll: List<Item>, modifier: Modifier = Modifier) {
    if (chId == null || !Tm.b(Tm.PL_SCORE, false)) return
    // Sportdaten bereitstellen, falls der Sport Guide noch nie geöffnet wurde
    LaunchedEffect(Unit) {
        if (SportsRepo.events.isEmpty() && !SportsRepo.loading && liveAll.isNotEmpty()) runCatching { SportsRepo.refresh(liveAll) }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() / 1000 } }
    val ev = SportsRepo.events.firstOrNull { chId in it.channels && it.liveScore(now) } ?: return
    // Stand jede Minute aktualisieren, solange das Spiel hier läuft
    LaunchedEffect(ev.key) { while (true) { delay(60_000); runCatching { SportsRepo.refreshScores() } } }
    val score = "${ev.scoreHome ?: 0} : ${ev.scoreAway ?: 0}"
    var shown by remember(ev.key) { mutableStateOf(score) }
    var goal by remember { mutableStateOf(false) }
    LaunchedEffect(score) {
        if (score == shown) return@LaunchedEffect
        shown = score; goal = true
        delay(10_000); goal = false
    }
    val min = SportsLogic.gameMinute(ev, now)
    Row(
        modifier.widthIn(max = 620.dp).clip(RoundedCornerShape(DS.RadiusM))
            .background(if (goal) Accent2.copy(alpha = 0.92f) else Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (goal) Text("Tor!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(raw = true, text = ev.home ?: "", color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Text(raw = true, text = score, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(raw = true, text = ev.away ?: "", color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        if (min != null) Text(raw = true, text = min, color = if (goal) Color.White else Accent2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

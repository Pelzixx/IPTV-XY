package com.ipvio.stream

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Eine Meldung im Laufband. [error] = Störung (rot). */
data class TickerItem(val id: String, val text: String, val error: Boolean, val ts: Long)

/** Reine Logik ohne Android (Unit-Tests). */
object TickerLogic {
    fun parse(body: String): List<TickerItem> = runCatching {
        val a = JSONObject(body).optJSONArray("items") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text", "").replace(Regex("""\s+"""), " ").trim().take(400)
            if (text.isEmpty()) null
            else TickerItem(o.optString("id", i.toString()), text, o.optString("level", "info").equals("error", true), o.optLong("ts", 0L))
        }.take(10)
    }.getOrDefault(emptyList())

    /** Alle Meldungen hintereinander, neueste zuerst. */
    fun line(items: List<TickerItem>): String = items.sortedByDescending { it.ts }.joinToString("   +++   ") { it.text }
}

/**
 * Laufband (Wunsch des Nutzers, 2026-11-09): News und Server-Störungen, die der Betreiber in einem Element-Raum postet.
 * Das Panel liest den Raum und liefert die aktuellen Meldungen (Adresse „ticker“ in der Konfiguration, nur XY TV und
 * Township). Abruf beim Start und alle 2 Minuten; leer = kein Band.
 */
object Ticker {
    var items by mutableStateOf<List<TickerItem>>(emptyList())
        private set

    suspend fun watch() {
        while (true) {
            Presets.tickerUrl?.let { url ->
                val body = withContext(Dispatchers.IO) {
                    runCatching {
                        Net.withTimeouts(10, 15).newCall(okhttp3.Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute()
                            .use { if (it.isSuccessful) it.body?.string() else null }
                    }.getOrNull()
                }
                // Netzfehler: letzten Stand behalten
                if (body != null) items = TickerLogic.parse(body)
            }
            delay(if (Presets.tickerUrl == null) 15_000L else 120_000L)
        }
    }
}

/** Schmales Band unten mit durchlaufendem Text; rot, sobald eine Störung dabei ist. */
@Composable
fun TickerBar(modifier: Modifier = Modifier) {
    val list = Ticker.items
    if (list.isEmpty()) return
    val error = list.any { it.error }
    val line = remember(list) { TickerLogic.line(list) }
    Row(
        modifier.fillMaxWidth().height(40.dp)
            .background(if (error) Color(0xE6B3261E) else Color(0xE6101522)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.fillMaxHeight().background(if (error) Color(0xFF8C1D18) else Accent1).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(if (error) "STÖRUNG" else "NEWS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            raw = true, text = line, color = Color.White, fontSize = 17.sp, maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                .basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately, initialDelayMillis = 800, velocity = 70.dp)
        )
    }
}

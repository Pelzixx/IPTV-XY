package com.ipvio.stream

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Favoriten vom Handy übernehmen (Wunsch des Nutzers, 2026-11-09, statt TiviMate-Backup):
 * Sendernamen einfügen, einer pro Zeile; die App ordnet sie den Sendern der Playlist zu, auch bei
 * abweichenden Zusätzen wie „HD“, „raw“, „DE:“ oder „(1080p)“.
 */
object FavImportLogic {
    private val PREFIX = Regex("""^\s*([a-z]{2,3})\s*[:|\-]\s*""", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("""[\(\[\{][^\)\]\}]*[\)\]\}]""")
    private val TAGS = Regex("""\b(uhd|fhd|hd|sd|hevc|h265|4k|8k|raw|backup|vip|plus|\d{3,4}p|50fps|60fps)\b""", RegexOption.IGNORE_CASE)

    /** Vergleichsform: klein, ohne Länderkürzel vorne, ohne Klammern, Qualitäts-Zusätze und Satzzeichen. */
    fun norm(s: String): String {
        var t = s.trim()
        t = PREFIX.replace(t, "")
        t = BRACKETS.replace(t, " ")
        t = t.lowercase().replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace("ß", "ss")
        t = t.replace(Regex("""[ᴴᴰᶠʳᵃʷ]+"""), " ")
        t = TAGS.replace(t, " ")
        return t.replace(Regex("""[^a-z0-9]+"""), "")
    }

    /**
     * Ordnet Namen den Sendern zu. Ergebnis: gefundene IDs (Reihenfolge wie eingegeben, je Name ein Sender)
     * und die Namen ohne Treffer. Bei mehreren Treffern gewinnt der erste der Playlist (Reihenfolge des Anbieters).
     */
    fun match(lines: List<String>, channels: List<Pair<String, String>>): Pair<List<String>, List<String>> {
        val byNorm = LinkedHashMap<String, String>()
        for ((id, title) in channels) { val n = norm(title); if (n.isNotEmpty()) byNorm.putIfAbsent(n, id) }
        val found = ArrayList<String>(); val missing = ArrayList<String>()
        for (raw in lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            val n = norm(raw)
            if (n.isEmpty()) continue
            val id = byNorm[n]
                ?: byNorm.entries.firstOrNull { it.key.startsWith(n) && it.key.length - n.length <= 3 }?.value
            if (id != null) { if (id !in found) found.add(id) } else missing.add(raw)
        }
        return found to missing
    }
}

/**
 * Dialog „Favoriten vom Handy“: QR-Code/Adresse/Code wie beim Hinzufügen; empfangene Liste wird sofort zugeordnet.
 */
@Composable
fun FavPhoneDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    val receiver = remember {
        PhoneImport(favMode = true, onFavs = { text ->
            Handler(Looper.getMainLooper()).post {
                val live = PlaylistStore.channels.filter { it.kind == Kind.LIVE }.map { it.id to it.title }
                val (ids, miss) = FavImportLogic.match(text.lines(), live)
                Library.init(ctx)
                var added = 0
                for (id in ids.reversed()) if (!Library.isFav(id)) { Library.toggleFav(ctx, id); added++ }
                result = Lang.tr("Übernommen:") + " $added  ·  " + Lang.tr("schon Favorit:") + " ${ids.size - added}  ·  " + Lang.tr("nicht gefunden:") + " ${miss.size}"
                missing = miss
            }
        }) {}
    }
    var serving by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { serving = receiver.start(); onDispose { receiver.stop() } }
    val ip = remember { PhoneImport.localIp() }
    val address = if (serving && ip != null) "http://$ip:${receiver.port}" else null
    BackHandler { onClose() }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(DS.RadiusL)
        Column(
            Modifier.widthIn(max = 760.dp).clip(shape).background(Surf).border(1.dp, Surf2, shape).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Favoriten vom Handy übernehmen", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Sendernamen auf dem Handy eintragen, einen pro Zeile – so wie sie z. B. in TiviMate heißen. Zusätze wie HD, raw oder DE: sind egal.",
                color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
            if (address == null) Text("Die Übertragung vom Handy ist gerade nicht verfügbar. Das Gerät muss mit dem WLAN verbunden sein.", color = Color(0xFFFFB86B), fontSize = 15.sp)
            else Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val qr = remember(address) { HomeInfo.qrBitmap("$address/?c=${receiver.code}", 300)?.asImageBitmap() }
                if (qr != null) Box(Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp)) {
                    Image(qr, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mit dem Handy scannen – oder im Browser öffnen:", color = Color.White, fontSize = 15.sp)
                    Text(raw = true, text = address, color = Accent2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(raw = true, text = Lang.tr("Code") + ": " + receiver.code, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            result?.let { Text(raw = true, text = it, color = Accent2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            if (missing.isNotEmpty()) Text(raw = true, text = Lang.tr("Nicht gefunden:") + " " + missing.take(15).joinToString(", ") + if (missing.size > 15) " …" else "",
                color = Muted, fontSize = 14.sp, maxLines = 3)
            PillButton("Fertig", Modifier.focusRequester(fr), light = true) { onClose() }
        }
    }
}

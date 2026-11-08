package com.ipvio.stream

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Angaben zum Xtream-Zugang (player_api.php → user_info). Zeiten in Sekunden; null = unbegrenzt bzw. unbekannt. */
data class AccountInfo(val status: String, val expSec: Long?, val maxCons: Int?, val activeCons: Int?, val trial: Boolean)

/** Mitteilung für die Startseite aus news.json (alles optional; ohne Inhalt wird nichts angezeigt). */
data class NewsInfo(val title: String, val text: String, val image: String?, val qr: String?, val qrLabel: String)

/** Reine Logik ohne Android (Unit-Tests). */
object HomeInfoLogic {
    fun parseAccount(body: String): AccountInfo? = runCatching {
        val u = JSONObject(body).optJSONObject("user_info") ?: return null
        fun num(k: String): Long? = u.opt(k)?.toString()?.trim()?.toLongOrNull()
        AccountInfo(
            status = u.optString("status", "").trim(),
            expSec = num("exp_date")?.takeIf { it > 0 },
            maxCons = num("max_connections")?.toInt(),
            activeCons = num("active_cons")?.toInt(),
            trial = u.opt("is_trial")?.toString()?.trim() == "1"
        )
    }.getOrNull()

    /** Ganze Tage bis zum Ablauf (aufgerundet); negativ = abgelaufen; null = unbegrenzt. */
    fun daysLeft(expSec: Long?, nowSec: Long): Long? {
        if (expSec == null) return null
        val d = expSec - nowSec
        return if (d <= 0) -1 else (d + 86399) / 86400
    }

    fun parseNews(body: String): NewsInfo? = runCatching {
        val o = JSONObject(body)
        fun s(k: String) = o.optString(k, "").trim()
        val n = NewsInfo(s("title"), s("text"), s("image").ifEmpty { null }?.takeIf { it.startsWith("http") },
            s("qr").ifEmpty { null }, s("qrLabel"))
        if (n.title.isEmpty() && n.text.isEmpty() && n.image == null && n.qr == null) null else n
    }.getOrNull()
}

/** Lädt Zugang und Mitteilung; beides wird 30 Minuten zwischengespeichert. */
object HomeInfo {
    /** Mitteilung, QR-Link und Bild: im öffentlichen Releases-Repo änderbar, ohne neue App-Version. */
    const val NEWS_URL = "https://raw.githubusercontent.com/Pelzixx/IPTV-XY-Releases/main/news.json"
    private const val TTL = 30 * 60 * 1000L
    /** Zuletzt abgefragte Zugänge je Playlist-Adresse (mehrere Server: Laufzeit je Zugang auf der Startseite). */
    private val accs = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, AccountInfo>>()
    /** Grund, warum keine Zugangsdaten kamen (für die Anzeige), oder null. */
    var accountError by mutableStateOf<String?>(null)
        private set
    private var news: Pair<Long, NewsInfo?>? = null

    suspend fun account(playlistUrl: String?): AccountInfo? = withContext(Dispatchers.IO) {
        val parts = playlistUrl?.let { LoadLogic.xtreamParts(it) } ?: return@withContext null
        val now = System.currentTimeMillis()
        accs[playlistUrl]?.let { if (now - it.first < TTL) return@withContext it.second }
        val (host, user, pass) = parts
        val url = "$host/player_api.php?username=${URLEncoder.encode(user, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}"
        // Gleiche Kennung wie beim Laden der Playlist: manche Server lehnen unbekannte Programme ab
        val r = runCatching {
            Net.base.newCall(Request.Builder().url(url).header("User-Agent", "IPTV-XY/1.0").build()).execute().use {
                if (!it.isSuccessful) { accountError = "HTTP ${it.code}"; null }
                else HomeInfoLogic.parseAccount(it.body?.string().orEmpty()).also { a -> accountError = if (a == null) "keine Angaben" else null }
            }
        }.getOrElse { e -> accountError = e.javaClass.simpleName; null }
        // Nur Erfolge merken: ein Fehler (z. B. Netz beim Start noch nicht da) wird beim nächsten Anzeigen erneut versucht
        if (r != null) accs[playlistUrl] = now to r
        r
    }

    suspend fun news(): NewsInfo? = if (!BuildConfig.NEWS) null else withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        news?.let { if (now - it.first < TTL) return@withContext it.second }
        val r = runCatching {
            Net.base.newCall(Request.Builder().url(NEWS_URL).header("Cache-Control", "no-cache").build()).execute()
                .use { if (it.isSuccessful) HomeInfoLogic.parseNews(it.body?.string().orEmpty()) else null }
        }.getOrNull()
        news = now to r
        r
    }

    fun qrBitmap(text: String, size: Int = 360): Bitmap? = runCatching {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}

/** Info-Zeile unter dem Kopfbild: Zugang (links), Mitteilung (Mitte), QR-Code (rechts). Ohne Inhalte unsichtbar. */
@Composable
fun HomeInfoRow(modifier: Modifier = Modifier) {
    val url = PlaylistStore.lists.getOrNull(PlaylistStore.active)?.url
    val account by produceState<AccountInfo?>(null, url) {
        value = HomeInfo.account(url)
        // Beim Start ist das Netz manchmal noch nicht bereit: zweimal nachfassen
        repeat(2) { if (value == null && LoadLogic.xtreamParts(url ?: "") != null) { kotlinx.coroutines.delay(15_000); value = HomeInfo.account(url) } }
    }
    val isXtream = url?.let { LoadLogic.xtreamParts(it) } != null
    // Mehrere Xtream-Zugänge (Wunsch des Nutzers, 2026-11-08): Laufzeit jedes Zugangs untereinander
    val xtLists = PlaylistStore.lists.filter { LoadLogic.xtreamParts(it.url) != null }
    val multi by produceState<List<Pair<String, AccountInfo?>>>(emptyList(), xtLists.map { it.url }) {
        if (xtLists.size < 2) { value = emptyList(); return@produceState }
        value = xtLists.map { it.name to null }
        value = xtLists.map { pl -> pl.name to HomeInfo.account(pl.url) }
    }
    val news by produceState<NewsInfo?>(null) { value = HomeInfo.news() }
    val a = account
    val n = news
    val hasNews = n != null && (n.title.isNotEmpty() || n.text.isNotEmpty() || n.image != null)
    val qr = n?.qr
    if (a == null && !isXtream && !hasNews && qr == null) return
    val shape = RoundedCornerShape(DS.RadiusM)
    val card = Modifier.fillMaxHeight().clip(shape).background(Color(0x14FFFFFF)).padding(16.dp)
    Row(modifier.fillMaxWidth().height(150.dp).padding(horizontal = DS.PageX), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (xtLists.size >= 2) Column(card.width(380.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Meine Zugänge", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val nowSec = System.currentTimeMillis() / 1000
            multi.take(4).forEach { (name, ai) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(raw = true, text = name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val (txt, col) = AccountLine.short(ai, nowSec)
                    Text(raw = true, text = txt, color = col, fontSize = 14.sp, maxLines = 1)
                }
            }
            if (multi.size > 4) Text(raw = true, text = "+ " + (multi.size - 4), color = Muted, fontSize = 13.sp)
        }
        // Xtream-Zugang, aber (noch) keine Angaben vom Server: Karte trotzdem zeigen, mit Grund
        else if (a == null && isXtream) Column(card.width(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mein Zugang", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val err = HomeInfo.accountError
            Text(if (err == null) "Wird abgefragt …" else "Keine Angaben vom Server", color = Tok.TextMid, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (err != null) Text(raw = true, text = err, color = Muted, fontSize = 13.sp)
        }
        if (xtLists.size < 2 && a != null) Column(card.width(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (a.trial) "Testzugang" else "Mein Zugang", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val days = HomeInfoLogic.daysLeft(a.expSec, System.currentTimeMillis() / 1000)
            val active = a.status.equals("Active", true) && (days == null || days >= 0)
            when {
                !active && days != null && days < 0 -> Text("Abgelaufen", color = DS.Danger, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                !active -> Text("Status: " + a.status.ifEmpty { "?" }, color = DS.Danger, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                days == null -> Text("Unbegrenzt", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                else -> {
                    val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { timeZone = epgZone() }.format(Date(a.expSec!! * 1000))
                    Text("Aktiv bis $date", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (days <= 14) "Läuft in $days Tagen ab" else "noch $days Tage", color = if (days <= 14) DS.Gold else Accent2, fontSize = 15.sp)
                }
            }
            if (a.maxCons != null) Text("Verbindungen: ${a.activeCons ?: 0} von ${a.maxCons}", color = Tok.TextMid, fontSize = 15.sp)
        }
        if (hasNews && n != null) Row(card.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (n.image != null) AsyncImage(n.image, null, Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(DS.RadiusS)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.title.isNotEmpty()) Text(raw = true, text = n.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotEmpty()) Text(raw = true, text = n.text, color = Tok.TextMid, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        } else Box(Modifier.weight(1f))
        if (qr != null) {
            val bmp = remember(qr) { HomeInfo.qrBitmap(qr)?.asImageBitmap() }
            if (bmp != null) Row(card, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(4.dp)) {
                    Image(bmp, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                }
                if (n?.qrLabel.orEmpty().isNotEmpty()) Text(raw = true, text = n?.qrLabel.orEmpty(), color = Color.White, fontSize = 15.sp, lineHeight = 20.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
            }
        }
    }
}

/** Kurze Zeile je Zugang für „Meine Zugänge“: „bis 04.03.27 · 116 T.“, abgelaufen rot, bald ablaufend gelb. */
object AccountLine {
    fun short(a: AccountInfo?, nowSec: Long): Pair<String, Color> {
        if (a == null) return "…" to Muted
        val days = HomeInfoLogic.daysLeft(a.expSec, nowSec)
        val active = a.status.equals("Active", true) && (days == null || days >= 0)
        return when {
            !active && days != null && days < 0 -> Lang.tr("Abgelaufen") to DS.Danger
            !active -> a.status.ifEmpty { "?" } to DS.Danger
            days == null -> Lang.tr("Unbegrenzt") to Color.White
            else -> {
                val date = SimpleDateFormat("dd.MM.yy", Locale.GERMANY).apply { timeZone = epgZone() }.format(Date(a.expSec!! * 1000))
                (Lang.tr("bis") + " $date · $days " + Lang.tr("T.")) to (if (days <= 14) DS.Gold else Accent2)
            }
        }
    }
}

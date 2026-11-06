/*
 * XY IPTV – Info-Startseite (Zugang, Mitteilungen/Angebote, QR-Code).
 * Teil von XY IPTV, basierend auf OpenTV. Lizenz: GNU GPL v3.0 oder später.
 */
package app.opentv.ui.info

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.pairing.QrCodes
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mitteilung aus news.json (alle Felder optional). */
data class News(val title: String, val text: String, val image: String?, val qr: String?, val qrLabel: String)

/** Stand eines Zugangs für die Anzeige. */
private sealed interface Access {
    data object Loading : Access
    data class Ok(val expiryMillis: Long?, val max: Int?, val active: Int?) : Access
    data class Error(val message: String) : Access
}

object InfoHome {
    /** Mitteilung, Bild und QR-Link: im öffentlichen Releases-Repo änderbar, ohne neue App-Version. */
    const val NEWS_URL = "https://raw.githubusercontent.com/Pelzixx/IPTV-XY-Releases/main/news.json"

    fun parseNews(body: String): News? = runCatching {
        val o = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        fun s(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
        val n = News(s("title"), s("text"), s("image").takeIf { it.startsWith("http") }, s("qr").ifEmpty { null }, s("qrLabel"))
        if (n.title.isEmpty() && n.text.isEmpty() && n.image == null && n.qr == null) null else n
    }.getOrNull()

    /** Ganze Tage bis zum Ablauf (aufgerundet); negativ = abgelaufen; null = unbegrenzt. */
    fun daysLeft(expiryMillis: Long?, nowMillis: Long): Long? {
        if (expiryMillis == null || expiryMillis <= 0) return null
        val d = expiryMillis - nowMillis
        return if (d <= 0) -1 else (d + 86_399_999) / 86_400_000
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoHomeScreen(hasSources: Boolean, onAddSource: () -> Unit) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val sources by produceState<List<Source>>(emptyList()) {
        value = runCatching { graph.sourceRepository.enabled() }.getOrDefault(emptyList())
    }
    val news by produceState<News?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                graph.httpClient.newCall(Request.Builder().url(InfoHome.NEWS_URL).header("Cache-Control", "no-cache").build())
                    .execute().use { if (it.isSuccessful) InfoHome.parseNews(it.body?.string().orEmpty()) else null }
            }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Image(painterResource(R.drawable.ic_xy_logo), contentDescription = null, modifier = Modifier.size(88.dp))
            Column {
                Text(stringResource(R.string.info_welcome), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.info_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        news?.let { n -> NewsCard(n) }

        Text(stringResource(R.string.info_access_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!hasSources || sources.isEmpty()) {
            InfoCard {
                Text(stringResource(R.string.info_no_source), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onAddSource) { Text(stringResource(R.string.info_add_source)) }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                sources.forEach { s -> AccessCard(s) }
            }
        }
    }
}

@Composable
private fun AccessCard(source: Source) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val state by produceState<Access>(Access.Loading, source.id) {
        value = if (source.kind != SourceKind.XTREAM) Access.Ok(null, null, null)
        else runCatching { graph.xtreamApi.authenticate(source) }
            .fold({ Access.Ok(it.expiryMillis, it.maxConnections, it.activeConnections) }, { Access.Error(it.message ?: it.javaClass.simpleName) })
    }
    InfoCard(Modifier.width(320.dp)) {
        Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when (val s = state) {
            Access.Loading -> Text(stringResource(R.string.info_checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
            is Access.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, maxLines = 3)
            is Access.Ok -> {
                val days = InfoHome.daysLeft(s.expiryMillis, System.currentTimeMillis())
                when {
                    source.kind != SourceKind.XTREAM -> Text(stringResource(R.string.info_no_details), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    days == null -> Text(stringResource(R.string.info_unlimited), style = MaterialTheme.typography.titleLarge)
                    days < 0 -> Text(stringResource(R.string.info_expired), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    else -> {
                        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(s.expiryMillis!!))
                        Text(stringResource(R.string.info_active_until, date), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(if (days <= 14) R.string.info_expires_in else R.string.info_days_left, days),
                            color = if (days <= 14) Color(0xFFFFC107) else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (s.max != null) Text(stringResource(R.string.info_connections, s.active ?: 0, s.max), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NewsCard(n: News) {
    val qr: Bitmap? = remember(n.qr) { n.qr?.let { QrCodes.render(it, 400) } }
    InfoCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (n.image != null) AsyncImage(
                n.image, null,
                Modifier.width(280.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (n.title.isNotEmpty()) Text(n.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (n.text.isNotEmpty()) Text(n.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (qr != null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp)) {
                    Image(qr.asImageBitmap(), contentDescription = n.qrLabel, modifier = Modifier.fillMaxSize())
                }
                if (n.qrLabel.isNotEmpty()) Text(n.qrLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 180.dp))
            }
        }
    }
}

/** Karte, die mit der Fernbedienung angesteuert werden kann (damit man auf der Seite scrollen kann). */
@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

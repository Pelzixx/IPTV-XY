package com.ipvio.stream

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Kleiner Player für die Vorschau in Live TV. Ohne Ton, ein einziger Player für die ganze Seite.
 * Wichtig: Viele Anbieter erlauben nur eine Verbindung gleichzeitig – vor dem Öffnen des großen Players wird [stop] gerufen,
 * beim Verlassen der Seite [release].
 */
class LivePreviewPlayer(ctx: Context) {
    val player: ExoPlayer = run {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        ExoPlayer.Builder(ctx, SoftAudio.factory(ctx))
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .build()
            .also { it.volume = 0f }
    }
    private var url: String? = null

    fun play(u: String) {
        if (u == url) return
        url = u
        player.setMediaItem(MediaItem.fromUri(u))
        player.prepare()
        player.playWhenReady = true
    }

    fun stop() {
        url = null
        player.stop()
        player.clearMediaItems()
    }

    fun release() { url = null; player.release() }
}

/** Vorschaufenster: spielt den markierten Sender nach kurzer Pause ab; bis dahin (und bei Fehlern) das Senderlogo. */
@Composable
fun LivePreview(p: LivePreviewPlayer, ch: Item?, modifier: Modifier = Modifier, fallback: Item? = null) {
    var showVideo by remember { mutableStateOf(false) }
    DisposableEffect(p) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { showVideo = true }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { showVideo = false }
        }
        p.player.addListener(l)
        onDispose { p.player.removeListener(l) }
    }
    LaunchedEffect(ch?.id) {
        showVideo = false
        if (ch == null || !AppSettings.livePreview) { p.stop(); return@LaunchedEffect }
        // Beim Durchblättern nicht jeden Sender anspielen
        delay(700)
        p.play(ch.url)
    }
    val shape = RoundedCornerShape(DS.RadiusS)
    Box(modifier.clip(shape).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = p.player
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Kein Sender markiert (Fokus links in den Kategorien): statt einer schwarzen Fläche das Logo des oberen Senders
        val logoCh = ch ?: fallback
        if (!showVideo && logoCh != null) {
            Box(Modifier.fillMaxSize().background(Surf), contentAlignment = Alignment.Center) {
                if (Logos.of(logoCh) != null) AsyncImage(Logos.of(logoCh), null, Modifier.fillMaxSize().padding(28.dp), contentScale = ContentScale.Fit)
                else Text(raw = true, text = logoCh.title, color = Muted, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Oben links in Live TV: Sender, laufende Sendung mit Zeit und Fortschritt, Beschreibung und was danach kommt. */
@Composable
fun LiveInfo(ch: Item?, modifier: Modifier = Modifier, prog: EpgEntry? = null) {
    // TiviMate: Titel (mit Stern bei Favoriten), darunter Zeit · Balken · Restminuten und rechts „Wiedergabeliste • Gruppe“,
    // dann die Beschreibung über mehrere Zeilen
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ch == null) return@Column
        @Suppress("UNUSED_VARIABLE") val editVersion = ChannelEdit.version
        val epg by produceState(emptyList<EpgEntry>(), ch.id) { value = Epg.now(ch) }
        // Im Guide gewählte Sendung zeigen, sonst die laufende
        val nowE = prog ?: Epg.current(epg)
        val plName = PlaylistStore.playlistOf(ch.id) ?: PlaylistStore.lists.getOrNull(PlaylistStore.active)?.name
        val where = listOfNotNull(plName, ch.group.takeIf { it.isNotBlank() }).joinToString("  •  ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(raw = true, text = nowE?.title ?: ChannelEdit.title(ch), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            AppIcon(if (Library.isFav(ch.id)) AIcon.Star else AIcon.StarOutline, if (Library.isFav(ch.id)) DS.Gold else Tok.TextMid, size = 22.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (nowE != null) {
                val nowS = System.currentTimeMillis() / 1000
                val frac = ((nowS - nowE.start).toFloat() / (nowE.end - nowE.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                // Künftige Sendung (im Guide gewählt): Dauer statt Restzeit
                val leftMin = (if (nowS < nowE.start) (nowE.end - nowE.start) / 60 else (nowE.end - nowS) / 60).coerceAtLeast(0)
                Text(raw = true, text = hhmm(nowE.start) + " — " + hhmm(nowE.end), color = Tok.TextMid, fontSize = 17.sp)
                Box(Modifier.size(width = 48.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(DS.Overlay3)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
                }
                Text(raw = true, text = "$leftMin Min", color = Tok.TextMid, fontSize = 17.sp)
            } else Text(raw = true, text = ChannelEdit.title(ch), color = Tok.TextMid, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(raw = true, text = where, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val desc = nowE?.desc?.takeIf { it.isNotBlank() }
        if (desc != null) Text(raw = true, text = desc, color = Tok.TextMid, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
        else if (epg.isEmpty()) Text("Für diesen Sender gibt es kein Programm.", color = Muted, fontSize = 17.sp)
    }
}

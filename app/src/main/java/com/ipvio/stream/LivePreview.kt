package com.ipvio.stream

import android.content.Context
import androidx.compose.foundation.background
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
fun LivePreview(p: LivePreviewPlayer, ch: Item?, modifier: Modifier = Modifier) {
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
        if (!showVideo && ch != null) {
            Box(Modifier.fillMaxSize().background(Surf), contentAlignment = Alignment.Center) {
                if (ch.logo != null) AsyncImage(ch.logo, null, Modifier.fillMaxSize().padding(28.dp), contentScale = ContentScale.Fit)
                else Text(raw = true, text = ch.title, color = Muted, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Oben links in Live TV: Sender, laufende Sendung mit Zeit und Fortschritt, Beschreibung und was danach kommt. */
@Composable
fun LiveInfo(ch: Item?, number: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ch == null) return@Column
        val epg by produceState(emptyList<EpgEntry>(), ch.id) { value = Epg.now(ch) }
        val nowE = Epg.current(epg)
        val next = if (nowE != null) epg.firstOrNull { it.start >= nowE.end } else epg.firstOrNull()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (number > 0) Text(raw = true, text = number.toString(), color = Muted, fontSize = 16.sp)
            Text(raw = true, text = ch.title, color = Tok.TextMid, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(raw = true, text = nowE?.title ?: ch.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (nowE != null) {
            val frac = ((System.currentTimeMillis() / 1000 - nowE.start).toFloat() / (nowE.end - nowE.start).coerceAtLeast(1)).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(raw = true, text = hhmm(nowE.start) + " – " + hhmm(nowE.end), color = Muted, fontSize = 14.sp)
                Box(Modifier.size(width = 160.dp, height = 3.dp).background(DS.Overlay3)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent2))
                }
            }
            nowE.desc?.takeIf { it.isNotBlank() }?.let {
                Text(raw = true, text = it, color = Tok.TextMid, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        } else if (epg.isEmpty()) Text("Für diesen Sender gibt es kein Programm.", color = Muted, fontSize = 14.sp)
        if (next != null) Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Danach", color = Accent2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(raw = true, text = hhmm(next.start) + "  " + next.title, color = Tok.TextMid, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

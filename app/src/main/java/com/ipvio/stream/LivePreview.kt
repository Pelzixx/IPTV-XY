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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import kotlinx.coroutines.delay

/**
 * Player für die Vorschau in Live TV, ein einziger für die ganze Seite. Wie TiviMate mit Ton (abschaltbar).
 * Viele Anbieter erlauben nur eine Verbindung gleichzeitig: OK auf den laufenden Sender übergibt diesen Player an den
 * Vollbild-Player ([handover]) – kein Neuladen, keine zweite Verbindung. Sonst wird vor dem Vollbild [stop] gerufen.
 */
/** [startUrl] ≠ null: vom Vollbild zurückgegebener Player, der diesen Sender schon spielt (kein Neuladen). */
class LivePreviewPlayer internal constructor(private val built: XyPlayer.Built, startUrl: String?) {
    constructor(ctx: Context) : this(XyPlayer.build(ctx, null), null)
    val player: ExoPlayer = built.player
    private var url: String? = startUrl
    private var released = false
    /** An den Vollbild-Player übergeben: gehört dann nicht mehr der Vorschau (stop/release wirken nicht mehr). */
    private var handedOver = false

    fun play(u: String, itemId: String? = null) {
        if (u == url || released || handedOver) return
        url = u
        runCatching {
            built.http.setUserAgent(TmNet.userAgentFor(itemId))
            player.volume = if (Tm.b(Tm.CV_SOUND, true)) 1f else 0f
            player.setMediaItem(MediaItem.fromUri(TmNet.playUrl(u)))
            player.prepare()
            player.playWhenReady = true
        }.onFailure { url = null; Diag.report("Vorschau: " + (it.message ?: it.javaClass.simpleName)) }
    }

    /** Läuft gerade dieser Sender (Adresse wie im Vollbild)? */
    fun isOn(u: String): Boolean = !released && !handedOver && url == u

    /** Für den Vollbild-Player: Player samt Verbindung übernehmen, wenn er gerade [u] spielt. */
    fun handover(u: String): XyPlayer.Built? {
        if (!isOn(u) || player.playbackState == androidx.media3.common.Player.STATE_IDLE) return null
        handedOver = true
        return built
    }

    fun stop() {
        url = null
        if (released || handedOver) return
        runCatching { player.stop(); player.clearMediaItems() }
    }

    fun release() { url = null; if (!released && !handedOver) { released = true; player.release() } }
    val isReleased: Boolean get() = released || handedOver
    /** Endgültig weg (nicht nur gerade im Vollbild ausgeliehen). */
    val isDead: Boolean get() = released
    fun owns(p: androidx.media3.common.Player): Boolean = player === p
    /** Vollbild gibt denselben Player zurück: wieder Vorschau, spielt [u] weiter. */
    fun takeBack(u: String) { handedOver = false; url = u; runCatching { player.volume = if (Tm.b(Tm.CV_SOUND, true)) 1f else 0f } }
    /** Vollbild hat den ausgeliehenen Player selbst freigegeben. */
    fun lost() { handedOver = false; released = true; url = null }
}

/**
 * Vorschau-Player und seine Videofläche bleiben über Reiterwechsel erhalten (TiviMate behält seine Ansichten):
 * beim Öffnen des TV-Bereichs muss weder ExoPlayer noch PlayerView neu angelegt werden. Beim Verlassen wird nur gestoppt.
 */
object PreviewHolder {
    private var p: LivePreviewPlayer? = null
    private var v: PlayerView? = null
    /** Zählt jede Rückgabe aus dem Vollbild: Live TV bleibt unter dem Vollbild bestehen und meldet die Videofläche dann neu an. */
    var gen by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    // Ausgeliehen ans Vollbild zählt nicht als weg: Live TV darunter behält denselben Vorschau-Player
    fun player(ctx: Context): LivePreviewPlayer = p?.takeIf { !it.isDead } ?: LivePreviewPlayer(ctx.applicationContext).also { p = it }

    /** Videofläche der Vorschau wieder an den (zurückgegebenen) Player hängen, ohne die Ansicht neu zu bauen. */
    fun reattach(pl: LivePreviewPlayer) {
        val cur = v ?: return
        if (attached && cur.player === pl.player) return
        cur.player = null; cur.player = pl.player; attached = true
    }

    /** Videofläche der Vorschau hängt schon am laufenden Player (vor dem Schließen des Vollbilds umgehängt). */
    private var attached = true

    /**
     * Vollbild schließt gleich: Bild schon jetzt in die Vorschau umhängen, solange die Vollbild-Fläche noch darüber liegt –
     * so ist beim Zurück sofort das laufende Bild da (kein altes Standbild, kein Logo).
     */
    fun attachNow(pl: androidx.media3.common.Player) {
        val cur = v ?: return
        cur.player = null; cur.player = pl; attached = true
    }

    /** Vollbild hat den Player freigegeben statt zurückzugeben (z. B. anderer Reiter): Vorschau baut beim nächsten Mal neu. */
    fun lost(pl: androidx.media3.common.Player) {
        val cur = p ?: return
        if (cur.owns(pl)) { cur.lost(); p = null; gen++ }
    }

    fun view(c: Context, pl: LivePreviewPlayer): PlayerView {
        // Erst lösen, dann neu setzen: kam der Player aus dem Vollbild zurück, ist es dasselbe Objekt, und die Videofläche
        // muss trotzdem neu angemeldet werden (sonst bleibt die Vorschau schwarz)
        v?.let { cur -> if (cur.context === c) { (cur.parent as? android.view.ViewGroup)?.removeView(cur); cur.player = null; cur.player = pl.player; return cur } }
        return PlayerView(c).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player = pl.player
        }.also { v = it }
    }

    fun releaseAll() { p?.release(); p = null; v = null }
    /** App verlassen: Vorschau sofort stoppen. */
    fun stopAll() { p?.stop() }
    /** Lage der Vorschau im Fenster: das Vollbild wächst von hier aus auf (TiviMate). */
    var rect: androidx.compose.ui.geometry.Rect? = null

    /**
     * Zurück aus dem Vollbild in TV (Wunsch des Nutzers, wie TiviMate): der laufende Player wird wieder zur Vorschau,
     * das Bild läuft ohne Unterbrechung weiter.
     */
    fun giveBack(b: XyPlayer.Built, url: String) {
        val cur = p
        if (cur != null && cur.owns(b.player)) cur.takeBack(url)
        else {
            cur?.release()
            b.player.volume = if (Tm.b(Tm.CV_SOUND, true)) 1f else 0f
            p = LivePreviewPlayer(b, url)
        }
        gen++
    }

    /** Vollbild-Player: laufenden Vorschau-Player übernehmen, wenn er gerade diesen Sender spielt; sonst Vorschau stoppen. */
    fun takeFor(url: String): XyPlayer.Built? {
        val cur = p ?: return null
        // Der Vorschau-Player bleibt hier eingetragen (ausgeliehen) und kommt beim Zurück wieder ([giveBack])
        val b = cur.handover(url)
        if (b != null) attached = false
        if (b == null) cur.stop()
        return b
    }
}

/** Vorschaufenster: spielt den markierten Sender nach kurzer Pause ab; bis dahin (und bei Fehlern) das Senderlogo. */
@Composable
fun LivePreview(p: LivePreviewPlayer, ch: Item?, modifier: Modifier = Modifier, fallback: Item? = null) {
    // Läuft der Sender schon (zurück aus dem Vollbild): gleich das Bild, nicht erst für einen Moment das Logo
    var showVideo by remember { mutableStateOf(ch != null && p.isOn(ch.url) && p.player.playbackState == Player.STATE_READY) }
    // Zurück aus dem Vollbild (Live TV blieb darunter bestehen): Videofläche wieder anmelden, Bild läuft ohne Neuladen weiter
    val gen = PreviewHolder.gen
    LaunchedEffect(gen) {
        if (gen == 0) return@LaunchedEffect
        PreviewHolder.reattach(p)
        if (ch != null && p.isOn(ch.url)) showVideo = true
    }
    DisposableEffect(p) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { showVideo = true }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { showVideo = false }
        }
        p.player.addListener(l)
        onDispose { p.player.removeListener(l) }
    }
    // App im Hintergrund (Home-Taste, App geschlossen): Vorschau anhalten, sonst läuft der Ton weiter und die Verbindung bleibt belegt;
    // beim Zurückkommen den markierten Sender wieder starten
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) { started = false; p.stop() }
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_START) started = true
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(ch?.id, started, p, gen) {
        // Läuft der Sender schon (aus dem Vollbild zurückgegeben): Bild sofort zeigen statt erst das Logo
        val on = ch != null && started && p.isOn(ch.url) && p.player.playbackState == Player.STATE_READY
        // Vorschau gerade ans Vollbild ausgeliehen (Live TV liegt verdeckt darunter): nichts ändern
        if (!on && p.isReleased && !p.isDead) return@LaunchedEffect
        showVideo = on
        if (!started) return@LaunchedEffect
        if (ch == null || !AppSettings.livePreview) { p.stop(); return@LaunchedEffect }
        // Beim Durchblättern nicht jeden Sender anspielen
        delay(700)
        p.play(if (Stalker.isPlay(ch.url)) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Stalker.playable(ch.url) } else ch.url, ch.id)
    }
    val shape = RoundedCornerShape(DS.RadiusS)
    Box(modifier.onGloballyPositioned { PreviewHolder.rect = it.boundsInWindow() }.clip(shape).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c -> PreviewHolder.view(c, p) },
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

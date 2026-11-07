package com.ipvio.stream

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Multi-View (TiviMate): mehrere Sender gleichzeitig. Bis zu 9 Bildschirme, Aufteilung je nach Anzahl (1, 2, 2×2, 3×3).
 * Ton nur vom markierten Bildschirm. OK öffnet das Menü des Bildschirms.
 */
object MultiView {
    const val MAX = 9
    /** Multi-View sichtbar? (statt des normalen Players) */
    var open by mutableStateOf(false)
    val screens = mutableStateListOf<Item>()
    var active by mutableIntStateOf(0)

    fun start(first: Item?) {
        screens.clear()
        if (first != null) screens.add(first)
        active = 0
        open = true
    }
}

/** Ein Player je Bildschirm; Ton nur, wenn markiert. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MultiCell(item: Item, audio: Boolean, focused: Boolean, modifier: Modifier, onOk: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(item.id) {
        val http = DefaultHttpDataSource.Factory().setUserAgent(TmNet.userAgentFor(item.id))
            .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000)
        ExoPlayer.Builder(ctx, SoftAudio.factory(ctx))
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .build()
    }
    // Adresse (bei Stalker-Portalen erst über das Portal) setzen und starten
    LaunchedEffect(player) {
        val real = if (Stalker.isPlay(item.url)) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Stalker.playable(item.url) } else item.url
        player.setMediaItem(MediaItem.fromUri(TmNet.playUrl(real)))
        player.prepare()
        player.playWhenReady = true
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(audio) { player.volume = if (audio) 1f else 0f }
    var err by remember(item.id) { mutableStateOf(false) }
    DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { err = true }
            override fun onRenderedFirstFrame() { err = false }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.clip(shape).background(Color.Black)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFE6E7E8) else Color(0xFF2A2D31), shape)
            .clickable { onOk() },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize().padding(3.dp)
        )
        if (err) Text("Wiedergabe nicht möglich", color = Muted, fontSize = 15.sp)
        // Sendername unten links (wie TiviMate), Lautsprecher beim Ton-Bildschirm
        Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (audio) AppIcon(AIcon.Music, Accent2, size = 16.dp)
            Text(raw = true, text = ChannelEdit.title(item), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Leerer Platz: „Bildschirm hinzufügen“. */
@Composable
private fun AddCell(modifier: Modifier, onOk: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.clip(shape).background(if (f) Color(0xFF2A2D31) else Color(0xFF141619))
            .border(if (f) 3.dp else 1.dp, if (f) Color(0xFFE6E7E8) else Color(0xFF2A2D31), shape)
            .onFocusChanged { f = it.isFocused }.clickable { onOk() },
        contentAlignment = Alignment.Center
    ) {
        IconLabel(AIcon.Plus, "Bildschirm hinzufügen", if (f) Color.White else Muted, 17.sp, FontWeight.SemiBold)
    }
}

@Composable
fun MultiViewScreen(liveAll: List<Item>, onExit: (Item?) -> Unit) {
    val ctx = LocalContext.current
    val screens = MultiView.screens
    var focusIdx by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableIntStateOf(-2) }      // -2 = zu, -1 = Hinzufügen, sonst Bildschirm
    var pickFor by remember { mutableIntStateOf(-2) }      // Kanalauswahl: -1 = neu, sonst ersetzen
    var pickGroup by remember { mutableStateOf<String?>(null) }
    // Das Seitenmenü schließt vor der Auswahl: erst danach entscheiden, ob „Zurück“ gedrückt wurde (nichts gewählt)
    var pickClosing by remember { mutableStateOf(false) }
    var closingFrom by remember { mutableStateOf("") }
    LaunchedEffect(pickClosing) {
        if (!pickClosing) return@LaunchedEffect
        pickClosing = false
        if (pickFor != -2 && (pickGroup ?: "") == closingFrom) {
            if (pickGroup == null) { pickFor = -2; if (screens.isEmpty()) onExit(null) } else pickGroup = null
        }
    }
    val firstReq = remember { FocusRequester() }
    BackHandler(enabled = menuFor == -2 && pickFor == -2) { onExit(screens.getOrNull(MultiView.active)) }
    LaunchedEffect(Unit) { delay(150); runCatching { firstReq.requestFocus() } }
    LaunchedEffect(Unit) { if (screens.isEmpty()) pickFor = -1 }
    // Mehr Bildschirme als die Liste Verbindungen erlaubt: Hinweis (TiviMate warnt ebenso)
    fun checkLimit(add: Item) {
        val list = PlaylistStore.playlistOf(add.id)
        val max = PlOpts.maxConn(list)
        val used = screens.count { PlaylistStore.playlistOf(it.id) == list }
        if (max in 1..used) Toast.makeText(ctx, Lang.tr("Hinweis: Die Wiedergabeliste erlaubt nur") + " $max " + Lang.tr("Verbindungen."), Toast.LENGTH_LONG).show()
    }

    val n = screens.size + if (screens.size < MultiView.MAX) 1 else 0
    val cols = when { n <= 1 -> 1; n <= 4 -> 2; else -> 3 }
    val rows = (n + cols - 1) / cols
    Box(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
        // Jeder Bildschirm im Format 16:9, das Raster mittig (TiviMate)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        val cellMod = Modifier.weight(1f).aspectRatio(16f / 9f).then(if (i == 0) Modifier.focusRequester(firstReq) else Modifier)
                        when {
                            i < screens.size -> {
                                val it = screens[i]
                                key(it.id, i) {
                                    MultiCell(it, i == MultiView.active, focusIdx == i,
                                        cellMod.onFocusChanged { s -> if (s.isFocused) { focusIdx = i; MultiView.active = i } }) { menuFor = i }
                                }
                            }
                            i == screens.size && screens.size < MultiView.MAX -> AddCell(cellMod) { pickFor = -1; pickGroup = null }
                            else -> Spacer(Modifier.weight(1f).aspectRatio(16f / 9f))
                        }
                    }
                }
            }
        }
    }

    // Menü eines Bildschirms
    if (menuFor >= 0) {
        val i = menuFor
        val it = screens.getOrNull(i)
        if (it == null) menuFor = -2 else {
            val e = ArrayList<SideEntry>()
            e.add(SideEntry("Vollbild", AIcon.Play) { onExit(it) })
            e.add(SideEntry("Kanal ändern", AIcon.Lists) { pickFor = i; pickGroup = null })
            if (screens.size < MultiView.MAX) e.add(SideEntry("Bildschirm hinzufügen", AIcon.Plus) { pickFor = -1; pickGroup = null })
            e.add(SideEntry("Bildschirm entfernen", AIcon.Close) {
                screens.removeAt(i)
                MultiView.active = MultiView.active.coerceIn(0, maxOf(screens.size - 1, 0))
                if (screens.isEmpty()) onExit(null)
            })
            SideMenu(listOf(SideSection(ChannelEdit.title(it), e)), { menuFor = -2 })
        }
    }

    // Kanalauswahl: erst Gruppe, dann Sender (wie die Senderliste)
    if (pickFor != -2) {
        val groups = remember(liveAll) { liveAll.map { it.group }.distinct() }
        val g = pickGroup
        if (g == null) {
            SideMenu(listOf(SideSection(Lang.tr("Gruppe wählen"), groups.map { gr -> SideEntry(gr, raw = true) { pickGroup = gr } })), {
                closingFrom = ""; pickClosing = true
            })
        } else {
            val chans = liveAll.filter { it.group == g }
            SideMenu(listOf(SideSection(g, chans.map { ch ->
                SideEntry(ChannelEdit.title(ch), raw = true) {
                    if (ChLock.needsPinItem(ch)) {
                        Toast.makeText(ctx, Lang.tr("Dieser Kanal ist gesperrt."), Toast.LENGTH_SHORT).show()
                    } else {
                        checkLimit(ch)
                        if (pickFor >= 0 && pickFor < screens.size) screens[pickFor] = ch else { screens.add(ch); MultiView.active = screens.lastIndex }
                    }
                    pickFor = -2; pickGroup = null
                }
            })), { closingFrom = g; pickClosing = true })
        }
    }
}

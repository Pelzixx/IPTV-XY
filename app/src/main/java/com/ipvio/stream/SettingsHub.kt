package com.ipvio.stream

import androidx.compose.foundation.focusable
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

private class Cat(val icon: AIcon, val title: String, val sub: String)

/** Ausgeblendete Bereiche (Index in CATS): 3 Design, 10 Profile, 11 API-Schlüssel. */
private val HIDDEN_CATS = setOf(3, 10, 11)

private val CATS = listOf(
    Cat(AIcon.Gear, "Allgemein", "Start, Beenden, Autostart"),
    Cat(AIcon.Globe, "Sprache & Region", "Sprache, Uhrzeit, Zeitzone"),
    Cat(AIcon.Grid, "Erscheinungsbild", "Kartengröße, Raster"),
    Cat(AIcon.Sparkle, "Design", "Farben, Schrift, Animationen"),
    Cat(AIcon.Lists, "Menü", "Reihenfolge und Einträge"),
    Cat(AIcon.Play, "Wiedergabe", "Spulen, Intro, Qualität, defekte Links"),
    Cat(AIcon.Music, "Ton & Untertitel", "Sprachen, CC, Untertitel-Stil"),
    Cat(AIcon.Tag, "Genres & Kategorien", "Automatische Genres, Kategorien"),
    Cat(AIcon.Live, "EPG & TV-Guide", "TV-Programm, Senderlisten, Erinnerungen"),
    Cat(AIcon.Download, "Wiedergabelisten", "Verwalten, EPG-Quellen, Aktualisieren"),
    Cat(AIcon.Person, "Profile", "Verlauf, Favoriten, Jugendschutz je Person"),
    Cat(AIcon.Star, "API-Schlüssel", "TMDB, IMDb und Rotten Tomatoes"),
    Cat(AIcon.Lock, "Kindersicherung", "PIN, Inhalte ab 18"),
    Cat(AIcon.Storage, "Speicher", "Cache, Verlauf, Fortsetzen"),
    Cat(AIcon.Pulse, "Diagnose", "Status und Fehlersuche"),
    Cat(AIcon.Info, "Über IPTV-XY", "Version, Quellen, Zurücksetzen")
)

/**
 * Reihenfolge der Übersicht (Index in CATS): Playlists, Live-TV & Guide, Ansicht, Player, Ton & Untertitel, Jugendschutz,
 * Menü, Allgemein, Sprache & Region, Genres, Speicher, Diagnose, Über.
 */
private val CAT_ORDER = listOf(9, 8, 2, 5, 6, 12, 4, 0, 1, 7, 13, 14, 15, 3, 10, 11)

/** Bereich links in den Einstellungen: Symbol und Titel; gewählt = leicht hinterlegt, markiert = hell mit dunkler Schrift. */
@Composable
private fun SettingsRow(cat: Cat, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val ink = if (f) DS.FocusInk else Color.White
    Row(
        modifier.fillMaxWidth().heightIn(min = dpx(58)).clip(RoundedCornerShape(DS.RadiusS))
            .background(if (f) TvFocusFill else if (selected) Color(0x1AFFFFFF) else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .trackFocus()
            .clickable(onClick = onClick)
            .padding(horizontal = dpx(20), vertical = dpx(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dpx(18))
    ) {
        AppIcon(cat.icon, if (f) DS.FocusInk else Tok.TextMid, size = dpx(24))
        Text(cat.title, color = ink, fontSize = spx(19), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** Direkt eine Seite der Einstellungen öffnen (z. B. Profil-Kreis oben bei nur einem Profil: Seite „Profile“). */
object SettingsNav {
    const val PROFILES = 10
    var openPage by androidx.compose.runtime.mutableIntStateOf(-1)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsHub(onAdd: () -> Unit, reload: () -> Unit) {
    val ctx = LocalContext.current
    // Runde 20 (TV-Aufbau): links die Bereiche, rechts die Einträge des markierten Bereichs. Markieren zeigt den Bereich
    // rechts an, OK oder Rechts geht hinein, Zurück wieder links.
    var page by remember { mutableIntStateOf(CAT_ORDER.first()) }
    LaunchedEffect(SettingsNav.openPage) {
        val p = SettingsNav.openPage
        if (p >= 0) { SettingsNav.openPage = -1; page = p }
    }
    val cardFocus = remember { List(CATS.size) { if (it == CAT_ORDER.first()) ContentFocus.req else FocusRequester() } }
    var inRight by remember { mutableStateOf(false) }
    val fm = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = inRight) { runCatching { cardFocus[page].requestFocus() } }
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

    Row(Modifier.fillMaxWidth().height(screenH).padding(start = dpx(24), top = dpx(24)), horizontalArrangement = Arrangement.spacedBy(dpx(32))) {
        // Links: Bereiche
        Column(Modifier.width(dpx(400)).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dpx(2))) {
            Text("Einstellungen", color = Color.White, fontSize = spx(30), fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = dpx(20), bottom = dpx(14)))
            CAT_ORDER.filter { it !in HIDDEN_CATS }.forEach { idx ->
                SettingsRow(CATS[idx], idx == page, Modifier.focusRequester(cardFocus[idx]), onFocus = { page = idx }) {
                    page = idx
                    scope.launch { kotlinx.coroutines.delay(60); fm.moveFocus(FocusDirection.Right) }
                }
            }
            Spacer(Modifier.height(dpx(40)))
        }
        // Rechts: Einträge des Bereichs
        Column(
            Modifier.weight(1f).fillMaxHeight().onFocusChanged { inRight = it.hasFocus }
                .verticalScroll(rememberScrollState()).padding(end = dpx(40), bottom = dpx(60)),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(CATS[page].title, color = Tok.TextLow, fontSize = spx(18), modifier = Modifier.padding(start = 20.dp, top = dpx(12), bottom = dpx(10)))
            when (page) {
                0 -> GeneralPage(ctx)
                1 -> RegionPage(ctx)
                2 -> ViewPage(ctx)
                3 -> DesignPage(ctx)
                4 -> MenuPage(ctx)
                5 -> PlayerPage(ctx)
                6 -> AudioPage(ctx)
                7 -> FilterPage(ctx) { page = 9 }
                8 -> LivePage(ctx)
                9 -> PlaylistPage(ctx, onAdd, reload)
                10 -> ProfilesPage(ctx) { page = CAT_ORDER.first() }
                11 -> ApiKeysPage(ctx)
                12 -> ParentalPage(ctx) { page = CAT_ORDER.first() }
                13 -> StoragePage(ctx)
                14 -> DiagPage(ctx, reload)
                else -> AboutPage(ctx) { _, _ -> }
            }
        }
    }
}

/** Farbe je Bereich: gleicher Aufbau wie die Kacheln der Startseite (Farbverlauf, Symbol in der Bereichsfarbe, Pfeil). */
private fun catColors(i: Int): Triple<Color, Color, Color> {
    val h = (i * 360f / CATS.size + 18f) % 360f
    return Triple(Color.hsv(h, 0.62f, 0.34f), Color.hsv(h, 0.70f, 0.14f), Color.hsv(h, 0.45f, 1f))
}

@Composable
private fun SettingsCard(cat: Cat, idx: Int, modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) focusScale(1.04f) else 1f, label = "sc")
    val shape = RoundedCornerShape(dpx(14))
    val (c1, c2, ink) = catColors(idx)
    Row(
        modifier.height(dpx(96)).graphicsLayer { scaleX = s; scaleY = s }.clip(shape)
            .background(Brush.horizontalGradient(listOf(c1, c2)))
            .border(if (f) 3.dp else 1.dp, if (f) Color.White else ink.copy(alpha = .22f), shape)
            .onFocusChanged { f = it.isFocused }
            .trackFocus()
            .clickable(onClick = onClick)
            .padding(horizontal = dpx(20)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dpx(16))
    ) {
        Box(Modifier.size(dpx(48)).clip(CircleShape).background(ink.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            AppIcon(cat.icon, ink, size = dpx(28))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(3))) {
            Text(cat.title, color = Color.White, fontSize = spx(20), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cat.sub, color = Tok.TextMid, fontSize = spx(13), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AppIcon(AIcon.Right, Color.White, size = dpx(18))
    }
}

/** Zurück-Knopf der Unterseiten: runder Knopf wie in der Kopfzeile. */
@Composable
private fun SettingsBack(modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier.size(dpx(52)).clip(CircleShape)
            .background(if (f) DS.FocusFill else Tok.Glass)
            .border(1.dp, if (f) DS.FocusFill else Color(0x26FFFFFF), CircleShape)
            .onFocusChanged { f = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { AppIcon(AIcon.Left, if (f) DS.FocusInk else Color.White, size = dpx(24)) }
}

@Composable
internal fun Hint(text: String) {
    Text(text, color = Muted, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.widthIn(max = 900.dp).padding(horizontal = 25.dp))
}

@Composable
internal fun SectionTitle(text: String) {
    // Runde 20: Abschnittsüberschrift wie in TV-Playern – klein und blau
    Text(text, color = Accent2, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 25.dp, top = 14.dp, bottom = 2.dp))
}

/** Auswahl-Chip mit optionalen Farbpunkten. Der gewählte Eintrag trägt einen Haken und einen hellen Rand. */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, dots: List<Color> = emptyList(), modifier: Modifier = Modifier, onFocus: ((Boolean) -> Unit)? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) focusScale(1.05f) else 1f, label = "chip")
    val shape = RoundedCornerShape(50)
    // Fokus wie alle Knöpfe: weiße Fläche, dunkle Schrift. Ausgewählt (ohne Fokus): hellere Fläche, heller Rand und Haken.
    Row(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc }.height(46.dp).clip(shape)
            .background(if (f) DS.FocusFill else if (selected) DS.Overlay3 else Surf)
            .border(DS.Border, if (f) DS.FocusFill else if (selected) Color(0x99FFFFFF) else DS.Hairline, shape)
            .onFocusChanged { if (f != it.isFocused) { f = it.isFocused; onFocus?.invoke(it.isFocused) } }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dots.forEach { Box(Modifier.size(18.dp).clip(CircleShape).background(it).border(1.dp, if (f) DS.FocusInk.copy(alpha = .3f) else DS.Overlay3, CircleShape)) }
        Text(label, color = if (f) DS.FocusInk else Color.White, fontSize = DS.TextBody, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        if (selected) AppIcon(AIcon.Check, if (f) DS.FocusInk else Accent2, size = 18.dp)
    }
}

/** Rundes Farbfeld für die eigene Farbe. */
@Composable
private fun Swatch(rgb: Int, selected: Boolean, onFocus: ((Boolean) -> Unit)? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) focusScale(1.18f) else 1f, label = "sw")
    val col = Color(0xFF000000.toInt() or rgb)
    Box(
        Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.size(44.dp).clip(CircleShape).background(col)
            .border(if (f) 3.dp else 1.dp, if (f) Color.White else Color(0x44FFFFFF), CircleShape)
            .onFocusChanged { if (f != it.isFocused) { f = it.isFocused; onFocus?.invoke(it.isFocused) } }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) AppIcon(AIcon.Check, if (ThemeMath.luminance(rgb) > 0.45) Color.Black else Color.White, size = 22.dp)
    }
}

/** Schalter-Zeile wie in Standard-Apps: Titel, Erklärung und ein An/Aus-Schalter. OK schaltet um. */
@Composable
internal fun ToggleRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    // Runde 20 (TV-Stil): Titel, darunter Erklärung bzw. Zustand; markiert hell mit dunkler Schrift, Schalter rechts
    SettingRow(title, desc, onClick = { onChange(!checked) }) { f ->
        Box(
            Modifier.width(58.dp).height(32.dp).clip(RoundedCornerShape(50))
                .background(if (checked) Accent1 else if (f) Color(0x33000000) else Color(0x44FFFFFF)).padding(3.dp)
        ) {
            Box(Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).size(26.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/**
 * Einstellungs-Zeile im TV-Stil: Titel (weiß), darunter der Wert oder eine Erklärung (grau); ohne Rahmen,
 * markiert = helle Fläche mit dunkler Schrift. [trailing] = z. B. Schalter rechts.
 */
@Composable
internal fun SettingRow(
    title: String, value: String? = null, modifier: Modifier = Modifier, rawValue: Boolean = false, rawTitle: Boolean = false,
    onClick: () -> Unit, leading: (@Composable (Boolean) -> Unit)? = null, trailing: (@Composable (Boolean) -> Unit)? = null
) {
    var f by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().widthIn(max = 900.dp).heightIn(min = 58.dp).clip(RoundedCornerShape(DS.RadiusS))
            .background(if (f) TvFocusFill else Color.Transparent)
            .onFocusChanged { f = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 25.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        leading?.invoke(f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (f) DS.FocusInk else Color.White, fontSize = 17.sp, raw = rawTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!value.isNullOrEmpty()) Text(value, color = if (f) DS.FocusInk.copy(alpha = .65f) else Tok.TextLow, fontSize = 13.sp,
                lineHeight = 18.sp, raw = rawValue, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(f)
    }
}

/** Gefährliche Aktion mit Rückfrage: erster Druck fragt nach, zweiter führt aus (nach 4 s verfällt die Rückfrage). */
@Composable
internal fun ConfirmPill(label: String, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { kotlinx.coroutines.delay(4000); armed = false } }
    PillButton(if (armed) "Wirklich? OK zum Bestätigen" else label, light = armed) {
        if (armed) { armed = false; onConfirm() } else armed = true
    }
}

private fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

/** Auswahl-Zeile im selben Stil wie ToggleRow: Titel links, aktueller Wert rechts, OK öffnet die Liste. */
@Composable
internal fun SelectRow(label: String, value: String, options: List<String>, selected: Int, active: Boolean = false, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // TV-Stil: Titel, darunter der gewählte Wert; die Auswahl öffnet sich als Leiste am rechten Rand
    SettingRow(label, value, onClick = { open = true })
    if (open) SideMenu(
        listOf(SideSection(Lang.tr(label), options.mapIndexed { i, o -> SideEntry(o, if (i == selected) AIcon.Check else null) { onPick(i) } })),
        { open = false }
    )
}

/** Ziffernblock für die PIN: Pfeiltasten + OK oder direkt die Zifferntasten der Fernbedienung. */
@Composable
internal fun PinPad(title: String, hint: String?, error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }
    LaunchedEffect(pin) { if (pin.length == 4) { val p = pin; pin = ""; onSubmit(p) } }
    BackHandler { onCancel() }
    fun add(d: Char) { if (pin.length < 4) pin += d }
    Column(
        Modifier.onPreviewKeyEvent { e ->
            val ne = e.nativeKeyEvent
            if (ne.action != android.view.KeyEvent.ACTION_DOWN) false
            else {
                val kc = ne.keyCode
                val d = when {
                    kc in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> '0' + (kc - android.view.KeyEvent.KEYCODE_0)
                    kc in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (kc - android.view.KeyEvent.KEYCODE_NUMPAD_0)
                    else -> null
                }
                if (d != null) { add(d); true } else false
            }
        },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (hint != null) Hint(hint)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { i ->
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (i < pin.length) Accent2 else DS.Overlay3))
            }
            if (error != null) Text(error, color = DS.Danger, fontSize = 15.sp)
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "ESC")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k ->
                    val ic = when (k) { "DEL" -> AIcon.Backspace; "ESC" -> AIcon.Close; else -> null }
                    SmallPill(if (ic != null) "" else k, Modifier.width(78.dp).then(if (k == "5") Modifier.focusRequester(start) else Modifier), icon = ic) {
                        when (k) {
                            "DEL" -> pin = pin.dropLast(1)
                            "ESC" -> onCancel()
                            else -> add(k[0])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionPage(ctx: Context) {
    val langOpts = listOf("Automatisch (Gerät)") + I18nCore.NATIVE
    val li = if (General.lang.isBlank()) 0 else I18nCore.CODES.indexOf(General.lang) + 1
    SectionTitle("Sprache")
    SelectRow("Sprache der App", if (li == 0) "Automatisch (Gerät)" else I18nCore.NATIVE[li - 1], langOpts, li) {
        General.setLang(ctx, if (it == 0) "" else I18nCore.CODES[it - 1])
    }
    Hint("Alle Menüs und Hinweise erscheinen in dieser Sprache. Beschreibungen, Besetzung und Titel-Infos kommen von TMDB und werden in derselben Sprache geladen, sobald du einen Titel öffnest. Namen von Sendern, Filmen und Kategorien stammen von deinem Anbieter und bleiben unverändert.")
    SectionTitle("Uhrzeit")
    val fmts = listOf("Automatisch (Gerät)", "24 Stunden (14:30)", "12 Stunden (2:30 PM)")
    SelectRow("Zeitformat", fmts[General.timeFmt], fmts, General.timeFmt) { General.setTimeFmt(ctx, it) }
    val zoneOpts = SettingsLogic.ZONES.map { if (it.id.isEmpty()) "Systemzeit (Gerät: " + java.util.TimeZone.getDefault().id + ")" else it.label }
    val zi = SettingsLogic.ZONES.indexOfFirst { it.id == General.tzId }.coerceAtLeast(0)
    SelectRow("Zeitzone", zoneOpts[zi], zoneOpts, zi) { General.setTz(ctx, SettingsLogic.ZONES[it].id) }
    Text("Uhrzeit jetzt: " + hhmm(System.currentTimeMillis() / 1000), color = Accent2, fontSize = 16.sp)
    Hint("Gilt für alle Uhrzeiten, zum Beispiel im TV-Guide und in der Live-TV-Infoleiste. „Systemzeit“ nutzt die Einstellung deines Geräts. Zeigt die App Uhrzeiten falsch an, steht dein Fernseher vermutlich auf UTC; wähle dann hier deine Zeitzone.")
}

@Composable
private fun ParentalPage(ctx: Context, onExit: () -> Unit) {
    // Schritte der PIN-Eingabe: 0 = keine, 1 = Entsperren, 2 = neue PIN, 3 = neue PIN bestätigen, 4 = alte PIN bei Entfernen
    var step by remember { mutableIntStateOf(if (Parental.pinSet && !Parental.unlocked) 1 else 0) }
    var first by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { Parental.unlocked = false } }
    // Zählen per Regex über alle Titel: im Hintergrund, nicht beim Zeichnen
    val src = PlaylistStore.channels
    val hideNow = Parental.hideAdult
    val hiddenCount by produceState(0, Parental.version, src, hideNow) {
        value = if (hideNow) 0 else withContext(Dispatchers.Default) { src.count { ParentalLogic.isAdult(it.group, it.title) } }
    }
    when (step) {
        1 -> PinPad("PIN eingeben", "Die Jugendschutz-Einstellungen sind mit einer PIN geschützt.", err,
            {
                if (Parental.verify(ctx, it)) { Parental.unlocked = true; err = null; step = 0 }
                else err = Parental.pinError()
            },
            { onExit() })
        2 -> PinPad("Neue PIN festlegen", "Wähle eine 4-stellige PIN.", err, { first = it; err = null; step = 3 }, { step = 0 })
        3 -> PinPad("PIN bestätigen", "Gib die PIN noch einmal ein.", err,
            { if (it == first) { Parental.setPin(ctx, it); Parental.unlocked = true; err = null; step = 0 } else { err = "Die PINs stimmen nicht überein"; step = 2 } },
            { step = 0 })
        else -> {
            ToggleRow(
                "Inhalte für Erwachsene ausblenden",
                "Blendet Kategorien und Titel mit Begriffen wie XXX, Adult oder 18+ in der ganzen App aus: Startseite, Suche, Listen und Live-TV.",
                Parental.hideAdult
            ) { Parental.setHide(ctx, it) }
            if (Parental.hideAdult) Text("Der Schutz ist aktiv.", color = Accent2, fontSize = 15.sp)
            else Text(if (hiddenCount > 0) "$hiddenCount Einträge in deiner Playlist würden ausgeblendet." else "Kein Erwachsenen-Inhalt erkannt.", color = Muted, fontSize = 15.sp)
            SectionTitle("PIN")
            Text(if (Parental.pinSet) "PIN ist gesetzt" else "Keine PIN gesetzt", color = if (Parental.pinSet) Accent2 else Muted, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(if (Parental.pinSet) "PIN ändern" else "PIN festlegen", light = !Parental.pinSet) { err = null; step = 2 }
                if (Parental.pinSet) ConfirmPill("PIN entfernen") { Parental.clearPin(ctx) }
            }
            Hint("Mit einer PIN lässt sich der Schutz nur nach Eingabe der PIN ändern. Die Erkennung arbeitet mit Stichwörtern in Kategorie- und Titelnamen und ist kein vollständiger Filter. Beim Zurücksetzen aller Einstellungen bleibt die PIN erhalten.")
        }
    }
}

@Composable
private fun GeneralPage(ctx: Context) {
    var tick by remember { mutableIntStateOf(0) }
    SectionTitle("Start")
    ToggleRow(
        "Beim Einschalten des Geräts öffnen",
        "IPTV-XY startet automatisch, sobald der Fernseher oder die Box hochgefahren ist.",
        General.autoStart
    ) { on ->
        General.setAutoStart(ctx, on)
        if (on && !canStartFromBoot(ctx)) openOverlaySettings(ctx)
    }
    val granted = remember(tick, General.autoStart) { canStartFromBoot(ctx) }
    if (General.autoStart) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconLabel(
                if (granted) AIcon.Check else AIcon.Warning,
                if (granted) "Berechtigung erteilt" else "Berechtigung „Über anderen Apps einblenden“ fehlt",
                if (granted) Accent2 else Color(0xFFFFB454), 15.sp, iconSize = 16.dp, maxLines = 2
            )
            if (!granted) SmallPill("Einstellung öffnen") { openOverlaySettings(ctx) }
            SmallPill("Prüfen") { tick++ }
        }
        Hint("Ab Android 10 erlaubt das System den Autostart nur mit dieser Berechtigung. Manche Geräte (z. B. einzelne Fire-TV-Modelle) blockieren den Autostart trotzdem; das kann die App nicht ändern.")
    }
    val tabs = listOf("Start", "Filme", "Serien", "Live TV", "Suche")
    val shown = SettingsLogic.resolveStartTab(General.startTab, AppSettings.menuOrder.toList(), AppSettings.menuHidden.toList())
    SelectRow("Startseite", tabs[shown], tabs, shown) { General.setStartTab(ctx, it) }
    Hint("Diese Seite öffnet sich beim Start der App. Versteckte Menüpunkte sind nicht wählbar.")
    ToggleRow("Letzten Sender beim Start abspielen", "Wenn du zuletzt Live TV gesehen hast, läuft dieser Sender direkt nach dem Start.", General.resumeLive) { General.setResumeLive(ctx, it) }
    ToggleRow("Letzten Film / letzte Serie beim Start fortsetzen", "Wenn du zuletzt einen Film oder eine Serie gesehen hast, läuft sie nach dem Start an der gemerkten Stelle weiter (bei einer fertigen Folge mit der nächsten).", General.resumeVod) { General.setResumeVod(ctx, it) }

    SectionTitle("Bedienung")
    ToggleRow("Beenden bestätigen", "Auf der Startseite muss „Zurück“ zweimal gedrückt werden, damit die App nicht aus Versehen schließt.", General.confirmExit) { General.setConfirmExit(ctx, it) }
}

private fun canStartFromBoot(ctx: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 29 || android.provider.Settings.canDrawOverlays(ctx)

private fun openOverlaySettings(ctx: Context) {
    try {
        ctx.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        )
    } catch (e: Exception) {
        android.widget.Toast.makeText(ctx, Lang.tr("Dieses Gerät bietet die Einstellung nicht an. Autostart funktioniert evtl. trotzdem."), android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun StoragePage(ctx: Context) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    // Größen und Löschen laufen im Hintergrund: der Metadaten-Ordner kann zehntausende Dateien enthalten
    var sizes by remember { mutableStateOf(List(General.AREAS.size) { 0L }) }
    LaunchedEffect(tick) { sizes = withContext(Dispatchers.IO) { General.AREAS.map { General.areaSize(ctx, it) } } }
    SectionTitle("Zwischenspeicher")
    General.AREAS.forEachIndexed { i, a ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(420.dp)) {
                Text(a.name + "  ·  " + SettingsLogic.fmtBytes(sizes[i]), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(a.note, color = Muted, fontSize = 13.sp)
            }
            ConfirmPill("Leeren") { scope.launch { withContext(Dispatchers.IO) { General.clearArea(ctx, a) }; tick++ } }
        }
    }
    Text("Gesamt: " + SettingsLogic.fmtBytes(sizes.sum()), color = Accent2, fontSize = 15.sp)
    Hint("Geleerte Daten lädt die App bei Bedarf neu. Titel-Infos und Bilder erscheinen danach kurz später. Der Playlist-Zwischenspeicher macht den Start schnell; ohne ihn wartet die App auf den Download.")
    SectionTitle("Verlauf")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfirmPill("Suchverlauf löschen") { AppSettings.clearSearch(ctx) }
        ConfirmPill("Zuletzt geschaut löschen") { General.clearHistory(ctx) }
        ConfirmPill("Weiterschauen löschen") { General.clearResume(ctx) }
    }
    Hint("Meine Liste und Favoriten bleiben immer erhalten.")
    SectionTitle("Sicherung")
    var backupMsg by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Sicherung erstellen") {
            scope.launch {
                val f = withContext(Dispatchers.IO) { Backup.export(ctx) }
                backupMsg = if (f != null) "Gespeichert: " + f.absolutePath else "Sicherung fehlgeschlagen."
            }
        }
        ConfirmPill("Sicherung wiederherstellen") {
            scope.launch {
                val n = withContext(Dispatchers.IO) { Backup.restore(ctx) }
                if (n >= 0) Profiles.restart(ctx) else backupMsg = "Keine gültige Sicherung gefunden."
            }
        }
    }
    backupMsg?.let { Text(it, color = Accent2, fontSize = 14.sp, modifier = Modifier.widthIn(max = 680.dp)) }
    Hint("Gesichert werden Einstellungen, Favoriten, Verlauf, Weiterschauen und Profile. Playlists und Zugangsdaten sind nicht enthalten. Beim Wiederherstellen startet die App neu.")
}

@Composable
private fun ViewPage(ctx: Context) {
    CatPrefs.init(ctx); BrowsePrefs.init(ctx)
    SectionTitle("Filme, Serien und Sender")
    val views = listOf("Raster", "Kino")
    SelectRow("Standard-Ansicht", views[if (BrowsePrefs.detail) 1 else 0], views, if (BrowsePrefs.detail) 1 else 0) { BrowsePrefs.set(ctx, it == 1) }
    Hint("Raster zeigt viele Titel auf einmal. Kino zeigt oben die Details zum markierten Titel und unten eine Reihe zum Durchblättern.")
    StepRow("Spalten (Raster)", AppSettings.gridCols.toString(),
        { AppSettings.setGrid(ctx, AppSettings.gridCols - 1, AppSettings.gridRows) },
        { AppSettings.setGrid(ctx, AppSettings.gridCols + 1, AppSettings.gridRows) })
    StepRow("Zeilen (Raster)", AppSettings.gridRows.toString(),
        { AppSettings.setGrid(ctx, AppSettings.gridCols, AppSettings.gridRows - 1) },
        { AppSettings.setGrid(ctx, AppSettings.gridCols, AppSettings.gridRows + 1) })
    SectionTitle("Startseite")
    val heroOpts = listOf(0, 6, 9, 15)
    SelectRow("Banner wechselt", if (General.heroSec == 0) "nie" else "alle ${General.heroSec} s", heroOpts.map { if (it == 0) "nie" else "alle $it s" }, heroOpts.indexOf(General.heroSec).coerceAtLeast(0)) {
        General.setHeroSec(ctx, heroOpts[it])
    }
    StepRow("Kartengröße (Reihen)", AppSettings.cardW.toString(),
        { AppSettings.setCardW(ctx, AppSettings.cardW - 10) },
        { AppSettings.setCardW(ctx, AppSettings.cardW + 10) })
    SectionTitle("Kategorien-Ansicht")
    val cv = listOf("Liste mit Vorschau", "Kacheln")
    SelectRow("Ansicht", cv[if (CatPrefs.listView) 0 else 1], cv, if (CatPrefs.listView) 0 else 1) { CatPrefs.set(ctx, it == 0) }
    Hint("Das Raster kannst du zusätzlich direkt in der Rasteransicht mit dem Raster-Knopf ändern.")
}

@Composable
private fun MenuPage(ctx: Context) {
    Hint("Verschiebe Einträge mit den Pfeil-Knöpfen oder blende sie aus. Start und Einstellungen bleiben immer sichtbar.")
    AppSettings.menuOrder.forEach { id ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(MENU_LABELS[id], color = if (id in AppSettings.menuHidden) Muted else Color.White,
                fontSize = 17.sp, modifier = Modifier.width(190.dp))
            SmallPill("", icon = AIcon.Up) { AppSettings.moveMenu(ctx, id, -1) }
            SmallPill("", icon = AIcon.Down) { AppSettings.moveMenu(ctx, id, 1) }
            if (id != 0 && id != 5) { val hidden = id in AppSettings.menuHidden; SmallPill(if (hidden) "versteckt" else "sichtbar", icon = if (hidden) AIcon.EyeOff else AIcon.Eye) { AppSettings.toggleMenu(ctx, id) } }
        }
    }
    PillButton("Standard-Reihenfolge wiederherstellen") { AppSettings.setMenuDefault(ctx) }
}

@Composable
private fun PlayerPage(ctx: Context) {
    val steps = listOf(5, 10, 15, 30, 60)
    SelectRow("Sprung links/rechts", "${AppSettings.seekStep} s", steps.map { "$it s" }, steps.indexOf(AppSettings.seekStep).coerceAtLeast(0)) {
        AppSettings.setPlayer(ctx, AppSettings.skipIntro, AppSettings.showInfo, steps[it])
    }
    val asp = General.ASPECT_NAMES
    SelectRow("Standard-Bildformat", asp[General.defaultAspect], asp, General.defaultAspect) { General.setAspect(ctx, it) }
    ToggleRow("Intro überspringen anbieten", "Zeigt bei Serien einen Knopf „Intro überspringen“, wenn Intro-Zeiten bekannt sind.", AppSettings.skipIntro) {
        AppSettings.setPlayer(ctx, it, AppSettings.showInfo, AppSettings.seekStep)
    }
    ToggleRow("Stream-Info am Start", "Zeigt kurz Auflösung, Ton und Format beim Abspielen von Filmen und Serien.", AppSettings.showInfo) {
        AppSettings.setPlayer(ctx, AppSettings.skipIntro, it, AppSettings.seekStep)
    }
    val hides = listOf(3, 4, 5, 6, 8, 10, 15)
    SelectRow("Steuerleiste ausblenden nach", "${AppSettings.hideSec} s", hides.map { "$it s" }, hides.indexOf(AppSettings.hideSec).coerceAtLeast(0)) {
        AppSettings.setPlayerExtra(ctx, AppSettings.autoNext, hides[it], AppSettings.showEndTime, AppSettings.autoSkipIntro)
    }
    ToggleRow("Nächste Folge automatisch", "Startet die nächste Folge nach dem Abspann von selbst.", AppSettings.autoNext) {
        AppSettings.setPlayerExtra(ctx, it, AppSettings.hideSec, AppSettings.showEndTime, AppSettings.autoSkipIntro)
    }
    ToggleRow("Abspann automatisch überspringen", "Startet die nächste Folge, sobald der Abspann beginnt. Geht nur, wenn für die Serie Abspann-Zeiten bekannt sind (TheIntroDB); sonst bleibt es beim Hinweis in den letzten 30 Sekunden.", AppSettings.autoSkipOutro) {
        AppSettings.setAutoOutro(ctx, it)
    }
    ToggleRow("Endzeit anzeigen", "Zeigt, um wie viel Uhr der Film voraussichtlich endet.", AppSettings.showEndTime) {
        AppSettings.setPlayerExtra(ctx, AppSettings.autoNext, AppSettings.hideSec, it, AppSettings.autoSkipIntro)
    }
    ToggleRow("Intro automatisch überspringen", "Überspringt bekannte Intros ohne Rückfrage.", AppSettings.autoSkipIntro) {
        AppSettings.setPlayerExtra(ctx, AppSettings.autoNext, AppSettings.hideSec, AppSettings.showEndTime, it)
    }
    ToggleRow("Uhrzeit im Bild", "Zeigt die aktuelle Uhrzeit beim Abspielen in einer Ecke (nicht, solange die Steuerleiste offen ist).", AppSettings.clockOn) {
        AppSettings.setClock(ctx, it, AppSettings.clockPos, AppSettings.clockOpacity, AppSettings.clockSize)
    }
    if (AppSettings.clockOn) {
        val poss = listOf("Oben rechts", "Oben links", "Unten rechts", "Unten links")
        SelectRow("Uhrzeit: Ort", poss[AppSettings.clockPos], poss, AppSettings.clockPos) {
            AppSettings.setClock(ctx, true, it, AppSettings.clockOpacity, AppSettings.clockSize)
        }
        val ops = listOf(20, 30, 40, 50, 60, 70, 80, 90, 100)
        SelectRow("Uhrzeit: Schrift-Deckkraft", "${AppSettings.clockOpacity} %", ops.map { "$it %" }, ops.indexOf(AppSettings.clockOpacity).let { if (it < 0) 5 else it }) {
            AppSettings.setClock(ctx, true, AppSettings.clockPos, ops[it], AppSettings.clockSize)
        }
        val sizes = listOf("Klein", "Mittel", "Groß")
        SelectRow("Uhrzeit: Größe", sizes[AppSettings.clockSize], sizes, AppSettings.clockSize) {
            AppSettings.setClock(ctx, true, AppSettings.clockPos, AppSettings.clockOpacity, it)
        }
        ToggleRow("Uhrzeit: Hintergrund", "Dunkler Kasten hinter der Uhr. Aus = nur die Zahlen.", AppSettings.clockBg) {
            AppSettings.setClockBg(ctx, it, AppSettings.clockBgOpacity)
        }
        if (AppSettings.clockBg) {
            val bops = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
            SelectRow("Uhrzeit: Hintergrund-Deckkraft", "${AppSettings.clockBgOpacity} %", bops.map { "$it %" }, bops.indexOf(AppSettings.clockBgOpacity).let { if (it < 0) 3 else it }) {
                AppSettings.setClockBg(ctx, true, bops[it])
            }
        }
        Text("Vorschau", color = Muted, fontSize = 14.sp)
        ClockSample()
        Hint("Die Uhr folgt der Zeitzone und dem 12/24-Stunden-Format aus den Einstellungen. Im Player geht es auch über Einstellungen > Uhrzeit.")
    }
    val bufNames = listOf("Automatisch", "Normal", "Groß", "Sehr groß")
    SelectRow("Puffer (bei langsamem Internet)", bufNames[AppSettings.bufferMode], bufNames, AppSettings.bufferMode) { AppSettings.setBufferMode(ctx, it) }
    Hint("Ein größerer Puffer lädt mehr Film im Voraus und verhindert Ruckeln bei schwacher Leitung; dafür dauert der Start etwas länger und es wird mehr Speicher gebraucht. „Automatisch“ beginnt normal und vergrößert den Puffer von selbst, wenn es mehrfach ruckelt (aktuell Stufe ${AppSettings.bufferLearned}). Gilt ab dem nächsten gestarteten Titel.")
    ToggleRow("Bildrate anpassen", "Passt die Bildwiederholrate des Fernsehers an den Film an (z. B. 24 Bilder), wenn der Fernseher das ohne Bildaussetzer kann. Bei Fire TV zusätzlich in den Geräteeinstellungen „Originale Bildrate anpassen“ prüfen.", AppSettings.autoFps) {
        AppSettings.setAutoFps(ctx, it)
    }
    Hint("Intro-Zeiten stammen aus der Community-Datenbank TheIntroDB und gibt es nicht für jede Serie. Die Stream-Info erscheint nur bei Filmen und Serien.")
    SourceSettingsSection(ctx)
    DeadLinksSection(ctx)
}

@Composable
private fun DesignPage(ctx: Context) {
    fun save(accent: Int = Look.accentIdx, custom: Int = Look.custom, bg: Int = Look.bgIdx, motion: Int = Look.motion, scale: Int = Look.uiScale, custom2: Int = Look.custom2) =
        AppSettings.setLook(ctx, accent, custom, bg, motion, scale, custom2)
    fun col(rgb: Int) = Color(0xFF000000.toInt() or rgb)
    // Vorschau oben rechts einblenden (steht fest, scrollt nicht mit); markierte Farben nur dort zeigen
    DisposableEffect(Unit) {
        DesignPreview.clear(); DesignPreview.visible = true
        onDispose { DesignPreview.visible = false; DesignPreview.clear() }
    }
    // Beim Weiterwandern kommt „Fokus weg“ evtl. erst nach „Fokus da“ des nächsten Eintrags: nur die eigene Vorschau löschen
    fun hover(on: Boolean, label: String, set: () -> Unit) {
        if (on) { DesignPreview.clear(); set(); DesignPreview.label = label }
        else if (DesignPreview.label == label) DesignPreview.clear()
    }

    // Rechts bleibt Platz für die Vorschau
    Column(Modifier.padding(end = DesignPreview.WIDTH + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    SectionTitle("Farbthema")
    val ids = Look.ACCENTS.indices.toList() + Look.CUSTOM
    ids.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { i ->
                if (i == Look.CUSTOM) {
                    val c2 = if (Look.custom2 >= 0) Look.custom2 else Look.autoHighRgb()
                    ChoiceChip("Eigene Farben", Look.accentIdx == i, listOf(col(Look.custom), col(c2)),
                        onFocus = { hover(it, Lang.tr("Eigene Farben")) { DesignPreview.accent = i } }) { save(accent = i) }
                } else {
                    val p = Look.ACCENTS[i]
                    ChoiceChip(p.name, Look.accentIdx == i, listOf(col(p.fill), col(p.highlight)),
                        onFocus = { hover(it, Lang.tr(p.name)) { DesignPreview.accent = i } }) { save(accent = i) }
                }
            }
        }
    }

    if (Look.accentIdx == Look.CUSTOM) {
        SectionTitle("Eigene Farben")
        // Beide Farben frei wählbar: erst die Farbe oben auswählen, dann Farbfeld oder Hex-Code
        var slot by remember { mutableIntStateOf(0) }
        val c2 = if (Look.custom2 >= 0) Look.custom2 else Look.autoHighRgb()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("Farbe 1 · Flächen", slot == 0, listOf(col(Look.custom))) { slot = 0 }
            ChoiceChip(if (Look.custom2 >= 0) "Farbe 2 · Hervorhebung" else "Farbe 2 · automatisch", slot == 1, listOf(col(c2))) { slot = 1 }
        }
        val current = if (slot == 0) Look.custom else c2
        fun pick(rgb: Int) { if (slot == 0) save(custom = rgb) else save(custom2 = rgb) }
        Look.SWATCHES.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    Swatch(c, c == current, onFocus = { on ->
                        hover(on, ThemeMath.hexOf(c)) { if (slot == 0) DesignPreview.custom = c else DesignPreview.custom2 = c }
                    }) { pick(c) }
                }
            }
        }
        var hex by remember(slot, current) { mutableStateOf(ThemeMath.hexOf(current)) }
        val parsed = ThemeMath.parseHex(hex)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(240.dp)) { Field(hex, { hex = it.take(7) }, "#RRGGBB") }
            PillButton(if (parsed != null) "Übernehmen" else "Ungültig", light = parsed != null) { parsed?.let { pick(it) } }
            if (slot == 1 && Look.custom2 >= 0) PillButton("Automatisch") { save(custom2 = -1) }
        }
        if (Look.custom2 >= 0 && ThemeMath.contrast(Look.custom2, Look.BGS[Look.bgIdx.coerceIn(0, Look.BGS.lastIndex)].bg) < 3.0) {
            Text("Farbe 2 ist auf diesem Hintergrund schwer lesbar.", color = Color(0xFFFFB454), fontSize = 15.sp)
        }
        Hint("Farbe 1 färbt Knöpfe, Balken und markierte Flächen – die Schrift darauf wird automatisch hell oder dunkel, damit sie lesbar bleibt. Farbe 2 färbt Hervorhebungen, Rahmen und farbige Schrift. „Automatisch“ berechnet Farbe 2 passend zu Farbe 1.")
    }

    SectionTitle("Hintergrund")
    Look.BGS.indices.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { i ->
                val b = Look.BGS[i]
                ChoiceChip(b.name, Look.bgIdx == i, listOf(Color(0xFF000000.toInt() or b.surf2)),
                    onFocus = { hover(it, Lang.tr(b.name)) { DesignPreview.bg = i } }) { save(bg = i) }
            }
        }
    }

    // Runde 14: Auswahl als Felder (wie die Farben) – markieren zeigt die Wirkung in der Vorschau, OK übernimmt
    SectionTitle("Animationen")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Look.MOTION.forEachIndexed { i, n ->
            ChoiceChip(n, Look.motion == i, onFocus = { hover(it, Lang.tr(n)) { DesignPreview.motion = i } }) { save(motion = i) }
        }
    }
    SectionTitle("Anzeigegröße")
    Look.SCALES.indices.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { i ->
                val n = Look.SCALES[i]
                ChoiceChip(n, Look.uiScale == i, onFocus = { hover(it, Lang.tr(n)) { DesignPreview.scale = i } }) { save(scale = i) }
            }
        }
    }
    val cfg = LocalConfiguration.current
    val factor = ThemeMath.uiFactor(Look.uiScale, cfg.screenWidthDp)
    Hint("Oberfläche aktuell ×" + String.format(java.util.Locale.US, "%.2f", factor) + " (Bildschirm " + cfg.screenWidthDp + " dp breit).")
    Hint("Standard ist 85 %: mehr Inhalt, Elemente bleiben gut lesbar. „Automatisch“ vergrößert die App auf sehr breiten Bildschirmen.")
    Hint("Animationen „Aus“ schaltet die Vergrößerung beim Fokus ab, „Dezent“ macht sie schwächer. Das schont schwache Geräte.")

    SectionTitle("Schriftart")
    General.FONTS.indices.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { i ->
                val n = General.FONTS[i]
                ChoiceChip(n, General.fontIdx == i, onFocus = { hover(it, Lang.tr(n)) { DesignPreview.font = i } }) { General.setFont(ctx, i) }
            }
        }
    }
    Hint("Outfit (Standard) und Inter sind in die App eingebaut, die übrigen Schriften stammen von deinem Gerät.")
    SectionTitle("Schriftgröße")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        General.TEXT_SCALES.forEachIndexed { i, n ->
            ChoiceChip(n, General.textScaleIdx == i, onFocus = { hover(it, Lang.tr(n)) { DesignPreview.textScale = i } }) { General.setTextScale(ctx, i) }
        }
    }
    Hint("Fünf klare Systemschriften, die auf jedem Gerät vorhanden sind. Die Schriftgröße wirkt zusätzlich zur Anzeigegröße.")
    PillButton("Design zurücksetzen") { Look.reset(); save() }
    }
}

@Composable
private fun DiagPage(ctx: Context, reload: () -> Unit) {
    val cfg = LocalConfiguration.current
    val items = PlaylistStore.channels
    val counts = remember(items) { Triple(items.count { it.kind == Kind.LIVE }, items.count { it.kind == Kind.MOVIE }, items.count { it.kind == Kind.SERIES }) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    val dens = ctx.resources.displayMetrics.density
    val err = Diag.lastError?.let { it + "  (" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(Diag.lastErrorAt)) + ")" } ?: "keiner seit dem Start"
    val lines = listOf(
        "Version" to version,
        "Installiert" to runCatching { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime)) }.getOrDefault("?"),
        "Sprache" to (Lang.code() + " · Index " + Lang.index),
        "Bildschirm" to "${cfg.screenWidthDp} × ${cfg.screenHeightDp} dp · Dichte ${"%.2f".format(java.util.Locale.US, dens)}",
        "Playlist" to (PlaylistStore.lists.getOrNull(PlaylistStore.active)?.name ?: "keine"),
        "Inhalte" to "${counts.first} Sender · ${counts.second} Filme · ${counts.third} Serien",
        "Ladezustand" to (if (PlaylistStore.loading) "lädt …" else PlaylistStore.error ?: "bereit"),
        "Programm (EPG)" to "${Xmltv.status} · ${Xmltv.channelCount} Sender",
        "Metadaten" to (if (MetaRepo.enabled()) (if (Classifier.running) "lädt ${Classifier.progress}/${Classifier.total}" else "aktiv") else "kein TMDB-Schlüssel"),
        "Speicher" to (Mem.totalMb(ctx).toString() + " MB · " + listOf("wenig", "mittel", "viel")[Mem.tier(ctx)]),
        "Letzter Fehler" to err,
        "Fehler seit Start" to Diag.errorCount.toString(),
        "Bildschirm wach" to (if (ScreenAwake.isOn) Lang.tr("an") + " (" + ScreenAwake.reasons + ")" else Lang.tr("aus")) +
            ScreenAwake.history.joinToString("") {
                "\n" + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it.first)) + "  " +
                    (if (it.second == "aus") Lang.tr("aus") else Lang.tr("an") + it.second.removePrefix("an"))
            },
        "Weiterschauen" to (ResumeLog.also { it.init(ctx) }.lines.take(12).joinToString("\n").ifBlank { "noch nichts gemerkt" }),
        "Letzter Absturz" to (CrashLog.last(ctx)?.let { it.first + "  (" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.second)) + ")" } ?: "keiner")
    )
    lines.forEach { (k, v) ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(k, color = Muted, fontSize = 16.sp, modifier = Modifier.width(170.dp))
            Text(v, color = Color.White, fontSize = 16.sp, modifier = Modifier.widthIn(max = 640.dp))
        }
    }
    // Hinweis vor den Knöpfen: Text unter dem letzten Knopf scrollt die Liste nie ins Bild (nichts darunter ist anwählbar)
    Hint("Der Bericht enthält keine Adressen, Benutzernamen oder Passwörter. Er wird nur in die Zwischenablage kopiert und nirgendwohin gesendet.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Playlist neu laden", light = true, icon = AIcon.Refresh) { reload() }
        PillButton("Bericht kopieren") {
            val text = lines.joinToString("\n") { it.first + ": " + it.second }
            runCatching {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("IPTV-XY Diagnose", text))
            }
        }
    }
}

@Composable
private fun AudioPage(ctx: Context) {
    TrackSettingsSection(ctx)
    // Runde 14: Untertitel-Stil mit Live-Vorschau. Markieren zeigt die Wirkung sofort im Bild oben, OK übernimmt.
    SectionTitle("Untertitel-Stil")
    var pSize by remember { mutableStateOf<Int?>(null) }
    var pColor by remember { mutableStateOf<Int?>(null) }
    var pBg by remember { mutableStateOf<Int?>(null) }
    var pEdge by remember { mutableStateOf<Int?>(null) }
    SubtitlePreview(pSize ?: AppSettings.subSize, pColor ?: AppSettings.subColor, pBg ?: AppSettings.subBg, pEdge ?: AppSettings.subEdge)
    fun setSub(size: Int = AppSettings.subSize, color: Int = AppSettings.subColor, bg: Int = AppSettings.subBg, edge: Int = AppSettings.subEdge) =
        AppSettings.setSub(ctx, size, color, bg, edge)
    SubChoiceRow("Größe", SubStyle.SIZES.map { it.first }, AppSettings.subSize, { i -> pSize = i }) { setSub(size = it) }
    SubChoiceRow("Farbe", SubStyle.COLORS.map { it.first }, AppSettings.subColor, { i -> pColor = i }) { setSub(color = it) }
    SubChoiceRow("Hintergrund", SubStyle.BGS.map { it.first }, AppSettings.subBg, { i -> pBg = i }) { setSub(bg = it) }
    SubChoiceRow("Rand", SubStyle.EDGES, AppSettings.subEdge, { i -> pEdge = i }) { setSub(edge = it) }
}

/** Eine Reihe Auswahlfelder; [onProbe] bekommt den markierten Eintrag (null = keiner markiert). */
@Composable
private fun SubChoiceRow(title: String, names: List<String>, selected: Int, onProbe: (Int?) -> Unit, onPick: (Int) -> Unit) {
    Text(title, color = Muted, fontSize = 15.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        names.forEachIndexed { i, n ->
            ChoiceChip(n, selected == i, onFocus = { on -> if (on) onProbe(i) else onProbe(null) }) { onPick(i) }
        }
    }
}

/** Kleines „Fernsehbild“ mit einer Untertitelzeile im gewählten Stil. */
@Composable
private fun SubtitlePreview(size: Int, color: Int, bg: Int, edge: Int) {
    val shape = RoundedCornerShape(DS.RadiusM)
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3B4A5C), Color(0xFF1B2330), Color(0xFF52412F))))
            .border(1.dp, DS.Overlay3, shape),
        contentAlignment = Alignment.BottomCenter
    ) {
        AppIcon(AIcon.Play, Color(0x33FFFFFF), Modifier.align(Alignment.Center), 46.dp)
        val shadow = when (edge) {
            1 -> androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(2f, 2f), 6f)
            2 -> androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 0f), 3f)
            else -> null
        }
        Text(
            "So sehen deine Untertitel aus", color = Color(SubStyle.COLORS[color.coerceIn(0, SubStyle.COLORS.lastIndex)].second.toInt()),
            fontSize = (20 * SubStyle.SIZES[size.coerceIn(0, SubStyle.SIZES.lastIndex)].second).sp,
            style = androidx.compose.ui.text.TextStyle(shadow = shadow),
            modifier = Modifier.padding(bottom = 16.dp).background(Color(SubStyle.BGS[bg.coerceIn(0, SubStyle.BGS.lastIndex)].second.toInt()))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FilterPage(ctx: Context, onLanguages: () -> Unit) {
    ToggleRow("Automatische Genres", "Aus = Kategorien deines Anbieters (Standard). Umschalten geht auch direkt in der Ansicht „Kategorien“.", AppSettings.ipvioFilter) { AppSettings.set(ctx, it) }
    val all = PlaylistStore.channels
    val vod = remember(all) { all.count { it.kind != Kind.LIVE } }
    if (!Classifier.supported(vod)) Hint("Deine Bibliothek ist für automatische Genres zu groß (über 60.000 Filme und Serien). Es werden die Kategorien deines Anbieters verwendet. Blende unter „Playlists“ > „Sprachen der Inhalte“ Sprachen aus, die du nicht brauchst – dann passt es wieder.")
    Hint("Automatische Genres sortieren Titel nach Genre (Anime = Animation aus Japan). Beim ersten Mal dauert das einige Minuten, danach ist alles gespeichert. Aus = die Kategorien deines Anbieters in der Reihenfolge deines Anbieters. In der Ansicht „Kategorien“ kannst du jederzeit zwischen beiden wechseln.")
    if (!MetaRepo.enabled()) Text("Automatische Genres brauchen einen TMDB-Schlüssel (Einstellungen > API-Schlüssel).", color = DS.Danger, fontSize = 15.sp)
    if (Classifier.running) Text("${Classifier.phase} werden geladen … ${Classifier.progress} / ${Classifier.total}", color = Accent2, fontSize = 15.sp)
    SectionTitle("Sprachen der Inhalte")
    Hint("Welche Sprachen geladen werden, stellst du unter „Playlists“ ein – das bestimmt, was aus der Playlist geladen wird.")
    PillButton("Sprachen der Inhalte öffnen", icon = AIcon.Globe) { onLanguages() }
}

/**
 * Sprachfilter: Sprachen der Playlist mit Anzahl. Auswahl erst als Entwurf (mehrere Sprachen an-/abhaken),
 * geladen wird erst mit „Übernehmen“ – sonst startete nach jedem Haken ein neues Einlesen.
 */
@Composable
private fun LanguageFilterSection(ctx: Context) {
    val counts = PlaylistStore.langCounts
    val list = remember(counts) {
        counts.entries.sortedWith(compareBy<Map.Entry<String, Int>>({ it.key == LangTagLogic.UNKNOWN }, { -it.value })).map { it.key to it.value }
    }
    val hidden = LangFilter.hidden
    var draft by remember(hidden) { mutableStateOf(hidden) }
    var warn by remember { mutableStateOf(false) }
    var big by remember { mutableStateOf(false) }
    val changed = draft != hidden
    fun edit(h: Set<String>) { draft = h; warn = false }
    fun apply() {
        // Nie alles ausblenden: dann wäre die Playlist leer und man käme kaum zurück
        if (list.isNotEmpty() && list.all { it.first in draft }) { warn = true; big = false; return }
        warn = false
        // Sehr große Auswahl: erlaubt, aber mit Hinweis (wird der Speicher zu knapp, bleibt die alte Auswahl aktiv)
        big = !PlaylistStore.fits(draft)
        LangFilter.setAndApply(ctx, draft)
    }
    SectionTitle("Sprachen der Inhalte")
    Hint("Blende Sprachen aus, die du nicht brauchst – sie verschwinden dann überall: Startseite, Filme, Serien, Live-TV und Suche. Erkannt wird die Kennzeichnung deines Anbieters (z. B. „DE |“, „[TR]“, „GERMAN“). Das ist bei IPTV-Anbietern die Sprache der Tonspur, nicht das Herkunftsland des Films.")
    if (list.isEmpty()) { Hint("Noch keine Playlist geladen."); return }
    Hint("Ausgeblendete Sprachen werden gar nicht erst geladen. Das spart viel Arbeitsspeicher und macht große Playlists schneller.")
    if (PlaylistStore.loading) {
        val t = PlaylistStore.totalBytes
        val pct = if (t > 0) "  " + (PlaylistStore.bytesRead * 100 / t).coerceIn(0, 100) + " %" else ""
        Text(Lang.tr("Sprachauswahl wird angewendet …") + pct, color = Accent2, fontSize = 15.sp, raw = true)
    } else if (PlaylistStore.error == PlaylistStore.MSG_TOO_BIG) Text(PlaylistStore.MSG_TOO_BIG, color = DS.Danger, fontSize = 15.sp)
    if (big) Text("Diese Auswahl ist sehr groß für dieses Gerät – das Laden kann lange dauern.", color = DS.Gold, fontSize = 15.sp)
    val mine = Lang.code()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Nur meine Sprache") { edit(list.map { it.first }.filter { it != mine && it != LangTagLogic.UNKNOWN }.toSet()) }
        PillButton("Alle an") { edit(emptySet()) }
        PillButton("Alle aus") { edit(list.map { it.first }.toSet()) }
    }
    @Composable
    fun ApplyRow() {
        if (warn) Text("Mindestens eine Sprache muss sichtbar bleiben.", color = DS.Danger, fontSize = 15.sp)
        if (changed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Übernehmen und laden", light = true, icon = AIcon.Check) { apply() }
            PillButton("Verwerfen") { edit(hidden) }
            val n = list.count { it.first !in draft }
            Text("$n von ${list.size} Sprachen gewählt", color = Muted, fontSize = 14.sp)
        }
    }
    ApplyRow()
    for ((code, n) in list) {
        ToggleRow(
            if (code == LangTagLogic.UNKNOWN) "Ohne Kennung" else LangTagLogic.name(code),
            n.toString() + " Titel",
            code !in draft
        ) { edit(if (code in draft) draft - code else draft + code) }
    }
    // Nach langer Liste: Übernehmen auch unten, damit man nicht zurückscrollen muss
    if (list.size > 4) ApplyRow()
}

/** Übersicht der EPG-Quellen: welche Quelle, für welche Playlist, wie viele Sender, Größe, Stand. */
@Composable
private fun EpgSourceList() {
    val list = Xmltv.sources
    if (list.isEmpty()) return
    SectionTitle("EPG-Quellen")
    list.forEach { s ->
        val kind = when (s.kind) { EpgSrc.FIXED -> "Fest eingebaut"; EpgSrc.SERVER -> "Fest für Server"; EpgSrc.OWN -> "Eigene Quelle"; EpgSrc.PROVIDER -> "Vom Anbieter"; EpgSrc.PL_EPG -> "EPG der Playlist"; else -> "Aus der Playlist" }
        val state = when (s.state) {
            EpgSourceStat.LOADING -> Lang.tr("Wird geladen …") + if (s.mb > 0) " ${s.mb} MB" else ""
            EpgSourceStat.DONE -> "${s.channels} " + Lang.tr("Sender") + " · ${s.mb} MB · " + hhmm(s.at)
            EpgSourceStat.ERROR -> Lang.tr("Fehler") + ": " + (s.error ?: "?") + (if (s.channels > 0) " · ${s.channels} " + Lang.tr("Sender") else "")
            else -> Lang.tr("Wartet")
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(DS.RadiusS)).background(Color(0x12FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(raw = true, text = s.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(raw = true, text = Lang.tr(kind) + " · " + (s.forName ?: Lang.tr("alle Playlists")), color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(raw = true, text = state, fontSize = 15.sp, maxLines = 1,
                color = when (s.state) { EpgSourceStat.DONE -> Accent2; EpgSourceStat.ERROR -> DS.Danger; else -> Tok.TextMid })
        }
    }
}

@Composable
private fun LivePage(ctx: Context) {
    var url by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(Xmltv.customUrls(ctx)) }
    val all = PlaylistStore.channels
    val chCount = Xmltv.channelCount
    val counts by produceState(0 to 0, all, chCount) {
        value = withContext(Dispatchers.Default) { val live = all.filter { it.kind == Kind.LIVE }; live.size to live.count { Epg.supported(it) } }
    }
    val xtream = counts.second
    SectionTitle("TV-Programm (EPG)")
    Text("Programm bei $xtream von ${counts.first} Sendern möglich", color = Muted, fontSize = 15.sp)
    Text("XMLTV: ${Xmltv.status}", color = Accent2, fontSize = 15.sp)
    ToggleRow("Vorschau in Live TV", "Spielt den markierten Sender oben rechts klein ab (ohne Ton). Ausschalten, wenn dein Anbieter nur eine Verbindung erlaubt oder die Box langsam ist.", AppSettings.livePreview) {
        AppSettings.setLivePreview(ctx, it)
    }
    Hint("Die App sucht die Programmdaten automatisch (Xtream-Schnittstelle oder XMLTV-Adresse aus der Playlist). Weitere XMLTV-Quellen kannst du hier hinzufügen – alle werden zusammen geladen.")
    EpgSourceList()
    // Eigene Quellen: Liste mit Entfernen-Knopf
    custom.forEach { u ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(u, raw = true, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            PillButton("Entfernen") {
                custom = custom - u
                Xmltv.setCustomUrls(ctx, custom)
                Xmltv.start(ctx, PlaylistStore.epgUrls, PlaylistStore.channels)
            }
        }
    }
    Field(url, { url = it }, "https://…/xmltv.php?username=…&password=…")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Hinzufügen & laden", light = true) {
            val add = PresetLogic.parseUrls(url)
            if (add.isNotEmpty()) {
                custom = (custom + add).distinct()
                Xmltv.setCustomUrls(ctx, custom)
                url = ""
            }
            Xmltv.start(ctx, PlaylistStore.epgUrls, PlaylistStore.channels)
        }
        PillButton("Neu laden") { Xmltv.start(ctx, PlaylistStore.epgUrls, PlaylistStore.channels, force = true) }
    }
    Spacer(Modifier.height(4.dp))
    Text("Favoriten: ${Library.favs.size} Sender · ${CatFavs.list.size} Kategorien", color = Color.White, fontSize = 17.sp)
    Hint("Sender als Favorit: auf dem Sender OK gedrückt halten > „Zu Favoriten“. Kategorie als Favorit: auf der Kategorie OK gedrückt halten.")
    ChannelListsSection(ctx)
    CatchupSection()
    ReminderSettingsSection(ctx)
}
/** Texteingabe als eigenes Fenster (Bildschirmtastatur): Speichern übernimmt, Zurück/Abbrechen verwirft. */
@Composable
private fun TextAskDialog(title: String, initial: String, hint: String, password: Boolean = false, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        // Fokus auf das Feld: die Tastatur geht auf; Runter führt zu Speichern
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); runCatching { fr.requestFocus() } }
        Column(
            Modifier.width(720.dp).clip(RoundedCornerShape(DS.RadiusL)).background(Surf).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, raw = true)
            Field(text, { text = it }, hint, inner = Modifier.focusRequester(fr), password = password, onDone = { onSave(text.trim()) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Speichern", light = true) { onSave(text.trim()) }
                PillButton("Abbrechen") { onDismiss() }
            }
        }
    }
}

/** Was gerade abgefragt wird (Texteingabe auf der Seite einer Wiedergabeliste). */
private class Ask(val title: String, val initial: String, val hint: String, val password: Boolean = false, val onSave: (String) -> Unit)

/**
 * Eigene Senderlisten (TiviMate: „Eigene Gruppen“): „Neue Senderliste“ und je Liste Umbenennen/Löschen.
 * Immer mindestens ein wählbarer Eintrag, damit die Seite nie ohne Fokus ist (sonst reagierte keine Taste mehr).
 */
@Composable
private fun TmChannelLists(ctx: Context) {
    ChannelLists.init(ctx)
    var ask by remember { mutableStateOf<Ask?>(null) }
    var menuFor by remember { mutableStateOf<ChList?>(null) }
    var confirmDel by remember { mutableStateOf<ChList?>(null) }
    val ls = ChannelLists.lists.toList()
    SettingRow("Neue Senderliste", Lang.tr("Sender danach im TV-Guide mit OK gedrückt halten hinzufügen"), leading = { f ->
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { AppIcon(AIcon.Plus, if (f) DS.FocusInk else Color.White, size = 22.dp) }
    }, onClick = {
        ask = Ask("Neue Senderliste", Lang.tr("Senderliste " + (ls.size + 1)), "Name der Liste") { n ->
            if (n.isNotBlank()) ChannelLists.create(ctx, n.trim().take(ChannelListLogic.MAX_NAME), null)
        }
    })
    if (ls.isNotEmpty()) PanelSection(Lang.tr("Eigene Senderlisten"))
    ls.forEach { l ->
        SettingRow(l.name, l.ids.size.toString() + " " + Lang.tr("Sender"), rawTitle = true, rawValue = true, onClick = { menuFor = l })
    }
    menuFor?.let { l ->
        SideMenu(listOf(SideSection(l.name, listOf(
            SideEntry("Umbenennen", AIcon.Edit) {
                ask = Ask("Umbenennen", l.name, "Name der Liste") { n -> if (n.isNotBlank()) ChannelLists.rename(ctx, l.id, n.trim().take(ChannelListLogic.MAX_NAME)) }
            },
            SideEntry("Liste löschen", AIcon.Close) { confirmDel = l }
        ))), { menuFor = null })
    }
    confirmDel?.let { l ->
        SideMenu(listOf(SideSection(l.name, listOf(
            SideEntry("Löschen", AIcon.Close) { ChannelLists.delete(ctx, l.id) },
            SideEntry("Abbrechen", AIcon.Left) {}
        ))), { confirmDel = null })
    }
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
}

/** Wiedergabeliste ändern und speichern; neue Adresse lädt die aktive Liste neu, neue EPG-Quellen das TV-Programm. */
private fun savePlaylist(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, idx: Int, name: String? = null, url: String? = null, epg: String? = null) {
    val pl = PlaylistStore.lists.getOrNull(idx) ?: return
    val newEpg = epg ?: pl.epg
    val urlChanged = PlaylistStore.update(ctx, idx, name ?: pl.name, url ?: pl.url, newEpg)
    if (urlChanged && EpgPrefs.onPlaylistChange) EpgPrefs.forceNext = true
    if (urlChanged) scope.launch { PlaylistStore.load(ctx, url ?: pl.url, false) }
    else if (idx == PlaylistStore.active && newEpg != pl.epg) Xmltv.start(ctx, PlaylistStore.epgUrls, PlaylistStore.channels, force = true)
}

private fun saveXtream(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, idx: Int, host: String, user: String, pass: String) {
    val url = PlaylistStore.xtreamUrl(host, user, pass)
    if (url == null) android.widget.Toast.makeText(ctx, Lang.tr("Angaben unvollständig: Server, Benutzername und Passwort nötig."), android.widget.Toast.LENGTH_SHORT).show()
    else savePlaylist(ctx, scope, idx, url = url)
}

/** Blaue Abschnittsüberschrift im Einstellungs-Panel (z. B. „Update-Optionen“). */
@Composable
internal fun PanelSection(text: String) {
    Text(text, color = Accent2, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 25.dp, top = 14.dp, bottom = 2.dp))
}

/**
 * Seite einer Wiedergabeliste (Aufbau wie in TV-Playern üblich): Aktivieren, Name, Adresse bzw. Xtream Codes Parameter,
 * EPG Quellen, Update-Optionen (Aktualisieren, Löschen).
 */
@Composable
private fun PlaylistMainPage(ctx: Context, idx: Int, onXtream: () -> Unit, onEpg: () -> Unit, onDeleted: () -> Unit, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pl = PlaylistStore.lists.getOrNull(idx) ?: return
    val isActive = idx == PlaylistStore.active
    val xtream = LoadLogic.xtreamParts(pl.url) != null
    var ask by remember { mutableStateOf<Ask?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val epgs = PresetLogic.parseUrls(pl.epg)
    // TiviMate: mehrere Playlists gleichzeitig aktiv. Die erste aktive ist die Haupt-Playlist, weitere kommen dazu.
    ToggleRow("Aktivieren", if (isActive) Lang.tr("Haupt-Wiedergabeliste") else null, isActive || pl.on) { on ->
        if (isActive) {
            // Haupt-Playlist aus: eine andere eingeschaltete übernimmt; ist keine an, bleibt sie an (eine ist immer aktiv)
            val other = PlaylistStore.lists.indexOfFirst { it.on }
            if (!on && other >= 0) {
                PlaylistStore.setOn(ctx, other, false)
                PlaylistStore.activate(ctx, other)
                scope.launch { PlaylistStore.load(ctx, PlaylistStore.lists[other].url, false) }
            }
        } else {
            PlaylistStore.setOn(ctx, idx, on)
            PlaylistStore.loadExtrasInBackground(ctx.applicationContext)
        }
    }
    SettingRow("Playlist Name", pl.name, rawValue = true, onClick = { ask = Ask("Playlist Name", pl.name, "Name der Wiedergabeliste") { if (it.isNotEmpty()) savePlaylist(ctx, scope, idx, name = it) } })
    if (xtream) SettingRow("Xtream Codes Parameter", null, onClick = onXtream)
    else SettingRow("Playlist URL", pl.url, rawValue = true, onClick = {
        ask = Ask("Playlist URL", pl.url, "http://…/playlist.m3u") { if (it.startsWith("http", true)) savePlaylist(ctx, scope, idx, url = it) }
    })
    SettingRow("EPG Quellen", if (epgs.isEmpty()) Lang.tr("EPG vom Anbieter") else epgs.size.toString() + " " + Lang.tr("Quellen") + " + " + Lang.tr("Anbieter"),
        rawValue = true, onClick = onEpg)
    PanelSection(Lang.tr("Update-Optionen"))
    SettingRow("Aktualisieren", if (isActive) Lang.tr("Sender und TV-Programm neu laden") else null, onClick = {
        if (isActive) reload() else { PlaylistStore.activate(ctx, idx); scope.launch { PlaylistStore.load(ctx, pl.url, false) } }
    })
    SettingRow("Löschen", null, onClick = { confirmDelete = true })
    if (confirmDelete) SideMenu(
        listOf(SideSection(pl.name, listOf(
            SideEntry("Löschen", AIcon.Close) {
                PlaylistStore.remove(ctx, idx)
                if (isActive && PlaylistStore.lists.isNotEmpty()) {
                    val u = PlaylistStore.lists[PlaylistStore.active].url
                    scope.launch { PlaylistStore.load(ctx, u, false) }
                }
                onDeleted()
            },
            SideEntry("Abbrechen", AIcon.Left) {}
        ))),
        { confirmDelete = false }
    )
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
}

/** Xtream Codes Parameter einer Wiedergabeliste: Server Adresse, Benutzername, Passwort, Ablaufdatum. */
@Composable
private fun PlaylistXtreamPage(ctx: Context, idx: Int) {
    val scope = rememberCoroutineScope()
    val pl = PlaylistStore.lists.getOrNull(idx) ?: return
    val parts = LoadLogic.xtreamParts(pl.url) ?: return
    val host = parts.first; val user = parts.second; val pass = parts.third
    val servers = Presets.servers
    // Mehrere feste Server können dieselbe Adresse haben: zuerst über den Namen der Liste zuordnen
    val sel = servers.indexOfFirst { it.name.equals(pl.name, ignoreCase = true) && PresetLogic.indexOf(host, listOf(it)) == 0 }
        .takeIf { it >= 0 } ?: PresetLogic.indexOf(host, servers)
    var ask by remember { mutableStateOf<Ask?>(null) }
    var serverMenu by remember { mutableStateOf(false) }
    // Feste XY-Server zeigen ihren Namen statt der Adresse
    SettingRow("Server Adresse", if (sel >= 0) servers[sel].name else host, rawValue = true, onClick = {
        if (servers.isNotEmpty()) serverMenu = true
        else ask = Ask("Server Adresse", host, "http://server.tld:8080") { saveXtream(ctx, scope, idx, it, user, pass) }
    })
    SettingRow("Benutzername", user, rawValue = true, onClick = { ask = Ask("Benutzername", user, "Benutzername") { saveXtream(ctx, scope, idx, host, it, pass) } })
    SettingRow("Passwort", "•".repeat(pass.length.coerceIn(4, 12)), rawValue = true, onClick = {
        ask = Ask("Passwort", pass, "Passwort", password = true) { saveXtream(ctx, scope, idx, host, user, it) }
    })
    // Ablaufdatum vom Server (wie „Mein Zugang“ auf Home)
    val acc by produceState<AccountInfo?>(null, pl.url) { value = HomeInfo.account(pl.url) }
    acc?.let { a ->
        val exp = a.expSec?.let { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).apply { timeZone = epgZone() }.format(java.util.Date(it * 1000)) }
        SettingRow("Ablaufdatum", exp ?: Lang.tr("Unbegrenzt"), rawValue = true, onClick = {})
    }
    if (serverMenu) SideMenu(
        listOf(SideSection(Lang.tr("Server Adresse"), servers.mapIndexed { i, s ->
            SideEntry(s.name, if (i == sel) AIcon.Check else null, raw = true) { saveXtream(ctx, scope, idx, s.url, user, pass) }
        } + SideEntry("Eigener Server …", if (sel < 0) AIcon.Check else AIcon.Edit) {
            ask = Ask("Server Adresse", if (sel < 0) host else "", "http://server.tld:8080") { saveXtream(ctx, scope, idx, it, user, pass) }
        })),
        { serverMenu = false }
    )
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
}

/** EPG Quellen einer Wiedergabeliste: zugewiesene Quellen in Reihenfolge (Vorrang), hinzufügen, verschieben, entfernen. */
@Composable
private fun PlaylistEpgPage(ctx: Context, idx: Int) {
    val scope = rememberCoroutineScope()
    val pl = PlaylistStore.lists.getOrNull(idx) ?: return
    val epgs = PresetLogic.parseUrls(pl.epg)
    var ask by remember { mutableStateOf<Ask?>(null) }
    var epgMenu by remember { mutableIntStateOf(-1) }
    fun saveList(l: List<String>) = savePlaylist(ctx, scope, idx, epg = PresetLogic.joinUrls(l.distinct()))
    SettingRow("Quelle hinzufügen", null, onClick = {
        ask = Ask("EPG-Quelle hinzufügen", "", "https://…/epg.xml.gz") { u ->
            if (u.startsWith("http://", true) || u.startsWith("https://", true)) saveList(epgs + u)
            else android.widget.Toast.makeText(ctx, Lang.tr("Ungültige Adresse."), android.widget.Toast.LENGTH_SHORT).show()
        }
    })
    // Stand einer Quelle aus dem letzten Laden (Anzahl Sender oder Fehler)
    fun stateOf(label: String, kind: Int): String? = Xmltv.sources.firstOrNull { it.kind == kind && (kind == EpgSrc.PROVIDER || it.label == label) }?.let { s ->
        when (s.state) {
            EpgSourceStat.DONE -> "${s.channels} " + Lang.tr("Sender")
            EpgSourceStat.ERROR -> Lang.tr("Fehler") + (s.error?.let { ": $it" } ?: "")
            EpgSourceStat.LOADING -> Lang.tr("Wird geladen …")
            else -> null
        }
    }
    // TiviMate: feste Quelle (Panel) und die Quelle aus dem Xtream-Zugang sind immer dabei, eigene kommen dazu
    val server = Presets.servers.firstOrNull { it.name.equals(pl.name, ignoreCase = true) }
    val fixed = (server?.epg.orEmpty().map { it to EpgSrc.SERVER } + Presets.epg.map { it to EpgSrc.FIXED }).distinctBy { it.first }
    if (fixed.isNotEmpty() || LoadLogic.xtreamParts(pl.url) != null) {
        PanelSection(Lang.tr("Automatische Quellen"))
        fixed.forEach { (u, k) ->
            val label = EpgSources.label(EpgSrc(u, k, null))
            SettingRow(label, listOfNotNull(Lang.tr("Feste Quelle"), stateOf(label, k)).joinToString(" · "), rawTitle = true, rawValue = true, onClick = {})
        }
        if (LoadLogic.xtreamParts(pl.url) != null) SettingRow("Anbieter-EPG",
            listOfNotNull(Lang.tr("Aus dem Xtream-Zugang"), stateOf("", EpgSrc.PROVIDER)).joinToString(" · "), rawValue = true, onClick = {})
    }
    PanelSection(Lang.tr("Zugewiesene Quellen"))
    epgs.forEachIndexed { i, u ->
        SettingRow(EpgSources.label(EpgSrc(u, EpgSrc.PL_EPG, null)), u, rawTitle = true, rawValue = true, onClick = { epgMenu = i })
    }
    if (epgs.isEmpty()) Hint("Weitere Quellen mit „Quelle hinzufügen“.")
    epgs.getOrNull(epgMenu)?.let { u ->
        val i = epgMenu
        fun move(d: Int) { val m = epgs.toMutableList(); val j = i + d; if (j in m.indices) { m.add(j, m.removeAt(i)); saveList(m) } }
        SideMenu(
            listOf(SideSection(u, listOfNotNull(
                if (i > 0) SideEntry("Priorität erhöhen", AIcon.Up) { move(-1) } else null,
                if (i < epgs.lastIndex) SideEntry("Priorität senken", AIcon.Down) { move(1) } else null,
                SideEntry("Bearbeiten", AIcon.Edit) {
                    ask = Ask("EPG-Quelle", u, "https://…/epg.xml.gz") { n -> if (n.startsWith("http", true)) saveList(epgs.map { if (it == u) n else it }) }
                },
                SideEntry("Entfernen", AIcon.Close) { saveList(epgs - u) }
            ))),
            { epgMenu = -1 }
        )
    }
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
}

@Composable
private fun PlaylistPage(ctx: Context, onAdd: () -> Unit, reload: () -> Unit, onOpen: (Int) -> Unit = {}, onLang: () -> Unit = {}, onUpdate: () -> Unit = {}) {
    PlaylistStore.savedUrl(ctx)
    // Übersicht wie in TV-Playern: Haken = aktive Liste, darunter Kanäle bzw. Art; OK öffnet ihre Seite
    PlaylistStore.lists.forEachIndexed { i, pl ->
        val active = i == PlaylistStore.active
        // TiviMate: bei jeder Liste ihre eigene Senderzahl (bzw. warum eine zugeschaltete Liste nicht geladen ist)
        val info = if (active && PlaylistStore.primaryLiveCount > 0) Lang.tr("Kanäle") + ": " + PlaylistStore.primaryLiveCount
            else if (!active && pl.on) PlaylistStore.extraStatus[pl.name]?.let { s -> if (s.startsWith("Kanäle: ")) Lang.tr("Kanäle") + ": " + s.removePrefix("Kanäle: ") else s }
                ?: Lang.tr("Wird geladen …")
            else if (LoadLogic.xtreamParts(pl.url) != null) "Xtream Codes" else "M3U"
        // Haken links wie in TV-Playern (aktive Liste); sonst gleicher Einzug
        SettingRow(pl.name, info, rawTitle = true, rawValue = true, onClick = { onOpen(i) }, leading = { f ->
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (active || pl.on) Box(Modifier.size(30.dp).clip(CircleShape).background(if (f) DS.FocusInk else Accent2), contentAlignment = Alignment.Center) {
                    AppIcon(AIcon.Check, if (f) TvFocusFill else Color.White, size = 20.dp)
                }
            }
        })
    }
    SettingRow("Hinzufügen", Lang.tr("Xtream Codes, M3U-Link oder vom Handy"), onClick = { onAdd() })
    SettingRow("Aktualisiere alle Wiedergabelisten", null, onClick = { reload() })
    // Eigene Funktionen von XY (Sprachfilter, Zeitplan) als eigene Einträge mit Unterseite
    SettingRow("Sprachen der Inhalte", Lang.tr("Ausgeblendete Sprachen werden gar nicht erst geladen"), onClick = onLang)
    val modes = listOf("Bei jedem Start", "Alle paar Stunden", "Täglich", "Wöchentlich", "Nur von Hand")
    SettingRow("Automatisch aktualisieren", Lang.tr(modes[General.refreshMode]), onClick = onUpdate)
}

/** Zeitplan für das automatische Aktualisieren der Wiedergabelisten. */
@Composable
private fun PlaylistUpdatePage(ctx: Context) {
    val modes = listOf("Bei jedem Start", "Alle paar Stunden", "Täglich", "Wöchentlich", "Nur von Hand")
    SelectRow("Playlist aktualisieren", modes[General.refreshMode], modes, General.refreshMode) { General.setPlan(ctx, mode = it) }
    if (General.refreshMode == 1) {
        val ivs = listOf(3, 6, 12, 24, 48)
        SelectRow("Abstand", "alle ${General.refreshInterval} Stunden", ivs.map { "alle $it Stunden" }, ivs.indexOf(General.refreshInterval).coerceAtLeast(0)) {
            General.setPlan(ctx, interval = ivs[it])
        }
    }
    if (General.refreshMode == 2 || General.refreshMode == 3) {
        if (General.refreshMode == 3) {
            val names = java.text.DateFormatSymbols(Lang.locale()).weekdays
            val dows = listOf(java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY,
                java.util.Calendar.FRIDAY, java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)
            SelectRow("Wochentag", names[General.refreshDow], dows.map { names[it] }, dows.indexOf(General.refreshDow).coerceAtLeast(0)) {
                General.setPlan(ctx, dow = dows[it])
            }
        }
        val hours = (0..23).map { String.format("%02d", it) }
        SelectRow("Stunde", hours[General.refreshHour], hours, General.refreshHour) { General.setPlan(ctx, hour = it) }
        val mins = listOf(0, 15, 30, 45)
        SelectRow("Minute", String.format("%02d", General.refreshMinute), mins.map { String.format("%02d", it) }, mins.indexOf(General.refreshMinute).coerceAtLeast(0)) {
            General.setPlan(ctx, minute = mins[it])
        }
    }
    val nowMs = System.currentTimeMillis()
    SettingRow("Zuletzt aktualisiert",
        if (General.lastRefresh > 0) dayLabel(General.lastRefresh / 1000) + " " + hhmm(General.lastRefresh / 1000) else Lang.tr("noch nie"),
        rawValue = true, onClick = {})
    SettingsLogic.nextDue(General.plan(), General.lastRefresh, nowMs, General.zone())?.let {
        SettingRow("Nächste Aktualisierung", dayLabel(it / 1000) + " " + hhmm(it / 1000), rawValue = true, onClick = {})
    }
    Hint("„Bei jedem Start“ lädt die Playlist bei jedem Öffnen der App. Die geplanten Varianten laufen auch im Hintergrund, solange das Gerät eingeschaltet und online ist, und holen verpasste Termine beim nächsten Start nach. „Nur von Hand“ nutzt immer den gespeicherten Stand; Neu laden geht jederzeit mit dem Knopf oben. Die Uhrzeit richtet sich nach deiner Zeitzone unter „Sprache & Region“.")
    Hint("Läuft gerade ein Film oder Sender, wartet die Aktualisierung, bis du fertig bist – so ruckelt nichts.")
}

@Composable
private fun ProfilesPage(ctx: Context, onExit: () -> Unit) {
    var editing by remember { mutableIntStateOf(-1) }
    var name by remember { mutableStateOf("") }
    Profiles.init(ctx)
    // Gesperrtes Profil (Jugendschutz mit PIN): Wechseln und Bearbeiten der Profile nur nach PIN-Eingabe
    var unlocked by remember { mutableStateOf(!Parental.profileLocked(ctx, Profiles.active)) }
    var pinErr by remember { mutableStateOf<String?>(null) }
    if (!unlocked) {
        PinPad(
            "PIN eingeben", "Dieses Profil ist durch den Jugendschutz gesperrt. Profile wechseln und bearbeiten geht nur mit der PIN.", pinErr,
            { if (Parental.verifyFor(ctx, Profiles.active, it)) { unlocked = true; pinErr = null } else pinErr = Parental.pinError() },
            { onExit() }
        )
        return
    }
    Text("Aktiv: " + Profiles.activeName(), color = Accent2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Hint("Jedes Profil hat eigenen Verlauf, eigene Favoriten, Ansicht, Player-Einstellungen und eigenen Jugendschutz. Playlist, Sprache und Aktualisierung sind für alle gleich.")
    Profiles.list.toList().forEach { p ->
        val isActive = p.id == Profiles.active
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(DS.RadiusM)).background(if (isActive) DS.Overlay2 else Color(0x12FFFFFF)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.text.BasicText(
                Profiles.displayName(p) + if (isActive) "  ·  " + Lang.tr("aktiv") else "",
                modifier = Modifier.weight(1f),
                style = androidx.compose.ui.text.TextStyle(color = if (isActive) Accent2 else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!isActive) SmallPill("Wechseln") { Profiles.switchTo(ctx, p.id) }
            SmallPill("Umbenennen") { editing = p.id; name = p.name }
            if (!isActive && p.id != 0) ConfirmPill("Löschen") { Profiles.remove(ctx, p.id) }
        }
    }
    if (editing >= 0) {
        Field(name, { name = it }, "Name des Profils")
        PillButton("Name speichern", light = true) { Profiles.rename(ctx, editing, name); editing = -1 }
    }
    if (Profiles.list.size < Profiles.MAX) PillButton("Profil hinzufügen") { Profiles.add(ctx) }
    ToggleRow("Beim Start fragen: Wer schaut?", "Zeigt beim Öffnen der App die Profilauswahl, sobald es mehr als ein Profil gibt.", Profiles.askAtStart) { Profiles.setAsk(ctx, it) }
    Hint("Beim Wechseln startet die App kurz neu.")
}

/**
 * Eigene API-Schlüssel (Open Source: es ist kein Schlüssel eingebaut). Ohne Schlüssel funktioniert die App, zeigt aber
 * nur, was die Playlist liefert.
 */
@Composable
private fun ApiKeysPage(ctx: Context) {
    val scope = rememberCoroutineScope()
    var tmdb by remember { mutableStateOf(Config.ownTmdb(ctx)) }
    var omdb by remember { mutableStateOf(Config.ownOmdb(ctx)) }
    var tmdbStatus by remember { mutableStateOf("") }
    var omdbStatus by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Hint("Die App enthält keine eigenen Schlüssel. Mit deinen eigenen, kostenlosen Schlüsseln zeigt sie Poster, Beschreibungen, Besetzung, Trailer, automatische Genres sowie IMDb- und Rotten-Tomatoes-Wertungen. Ohne Schlüssel übernimmt sie Beschreibung, Cover und Bewertung von deinem Anbieter (bei Xtream-Zugängen).")

    SectionTitle("TMDB (Film-Infos)")
    Field(tmdb, { tmdb = it; tmdbStatus = "" }, "TMDB API-Schlüssel (v3)")
    Hint("Kostenlos unter themoviedb.org: Konto anlegen, anmelden, dann Einstellungen > API > API-Schlüssel anfordern und den 32-stelligen „API Key“ eintragen. Dieses Produkt verwendet die TMDB-API, wird aber nicht von TMDB unterstützt oder zertifiziert.")
    if (tmdbStatus.isNotBlank()) Text(tmdbStatus, color = if (tmdbStatus == "Schlüssel gültig") Accent2 else Muted, fontSize = 16.sp)

    SectionTitle("OMDb (IMDb- und Rotten-Tomatoes-Wertung)")
    Field(omdb, { omdb = it; omdbStatus = "" }, "OMDb API-Schlüssel")
    Hint("Kostenlos unter omdbapi.com > API Key (FREE). Danach den Aktivierungslink in der E-Mail anklicken, sonst funktioniert der Schlüssel nicht. Geht auch ohne TMDB-Schlüssel.")
    if (omdbStatus.isNotBlank()) Text(omdbStatus, color = if (omdbStatus == "Schlüssel gültig") Accent2 else Muted, fontSize = 16.sp)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton(if (busy) "Bitte warten …" else "Speichern und prüfen", light = true, icon = AIcon.Check) {
            if (busy) return@PillButton
            busy = true
            Config.save(ctx, tmdb, omdb)
            tmdb = Config.ownTmdb(ctx); omdb = Config.ownOmdb(ctx)
            scope.launch {
                tmdbStatus = if (tmdb.isEmpty()) "" else KeyCheck.tmdb(tmdb) ?: "Schlüssel gültig"
                omdbStatus = if (omdb.isEmpty()) "" else KeyCheck.omdb(omdb) ?: "Schlüssel gültig"
                busy = false
            }
        }
        if (tmdb.isNotEmpty() || omdb.isNotEmpty()) ConfirmPill("Schlüssel entfernen") {
            tmdb = ""; omdb = ""; tmdbStatus = ""; omdbStatus = ""; Config.save(ctx, "", "")
        }
    }
    Hint("Die Schlüssel bleiben nur auf diesem Gerät. Nach dem Eintragen die App einmal neu starten, damit alle Bereiche die Infos laden. Intro- und Abspann-Zeiten kommen von TheIntroDB und brauchen keinen Schlüssel.")
}

@Composable
private fun AboutPage(ctx: Context, open: (String, String) -> Unit) {
    // TiviMate-Aufbau: schlichte Zeilen (Titel, darunter klein der Wert), Info-Zeilen grau und nicht wählbar, kurzer Hinweis unten
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun check() {
        busy = true; status = Lang.tr("Suche …"); info = null; apk = null
        scope.launch {
            AppUpdate.check(ctx)
                .onSuccess { r -> info = r; status = if (r == null) Lang.tr("Du hast die neueste Version.") else Lang.tr("Neue Version") + " " + r.name }
                .onFailure { status = it.message ?: Lang.tr("Fehler") }
            busy = false
        }
    }
    fun install(f: java.io.File) {
        status = Lang.tr(if (AppUpdate.install(ctx, f)) "Installation gestartet" else "Erlaube der App einmal „Apps installieren“ und tippe dann erneut.")
    }
    LaunchedEffect(Unit) { check() }
    val u = info
    SettingRow(if (u != null) "Jetzt aktualisieren" else "Nach Updates suchen", status.ifBlank { null }, rawValue = true, onClick = {
        if (busy) return@SettingRow
        if (u == null) { check(); return@SettingRow }
        apk?.let { install(it); return@SettingRow }
        busy = true
        scope.launch {
            AppUpdate.download(ctx, u) { p -> status = if (p >= 0) Lang.tr("Lade …") + " $p %" else Lang.tr("Lade …") }
                .onSuccess { f -> apk = f; install(f) }
                .onFailure { status = it.message ?: Lang.tr("Fehler") }
            busy = false
        }
    })
    SettingRow("Systemeinstellungen der App", null, onClick = {
        runCatching {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            )
        }
    })
    SettingRow("Alle Einstellungen zurücksetzen", Lang.tr("Playlists, Favoriten und Verlauf bleiben erhalten"), onClick = { confirmReset = true })
    InfoRow("Gerätename", (android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + android.os.Build.MODEL).trim())
    InfoRow("Version", appVersion(ctx))
    // Hinweise und Lizenzen direkt auf der Seite (Wunsch des Nutzers), jede Zeile mit Hoch/Runter erreichbar
    AboutLegal()
    if (confirmReset) SideMenu(
        listOf(SideSection(Lang.tr("Alle Einstellungen zurücksetzen"), listOf(
            SideEntry("Zurücksetzen", AIcon.Refresh) { AppSettings.resetAll(ctx); General.resetAll(ctx) },
            SideEntry("Abbrechen", AIcon.Left) {}
        ))),
        { confirmReset = false }
    )
}

/** Nicht wählbare Info-Zeile wie bei TiviMate (Gerätename, Version): Titel und Wert grau. */
@Composable
private fun InfoRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 25.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Text(title, color = Tok.TextMid, fontSize = 17.sp)
        Text(raw = true, text = value, color = Tok.TextMid, fontSize = 13.sp)
    }
}

/** Rechtliche Hinweise (von „Über“ ausgelagert, damit die Seite übersichtlich bleibt). */
@Composable
private fun AboutLegal() {
    // Jede Zeile ist wählbar: so lässt sich die lange Seite mit Hoch/Runter durchblättern (nicht wählbarer Text wäre nicht erreichbar)
    PanelSection(Lang.tr("Rechtlicher Hinweis"))
    LegalText("IPTV-XY ist ein Player und liefert selbst keine Inhalte. Nutze nur Quellen, für die du eine Berechtigung hast.")
    LegalText("IPTV-XY ist nicht mit den Anbietern von Wiedergabelisten, Sendern oder Programmdaten verbunden. Für Inhalte und deren Rechte sind die jeweiligen Anbieter verantwortlich.")
    PanelSection(Lang.tr("Datenschutz"))
    LegalText("Zugangsdaten von Wiedergabelisten werden verschlüsselt nur auf diesem Gerät gespeichert.")
    LegalText("Die App ruft nur die Adressen ab, die du einträgst (Wiedergabelisten, EPG), sowie die Dienste für Film- und Serieninfos, Wertungen und Updates.")
    PanelSection(Lang.tr("Lizenz"))
    LegalText("IPTV-XY ist freie Software unter der GNU General Public License v3 (GPL-3.0) und basiert auf IPVIO Stream. Es gibt keine Gewährleistung.")
    PanelSection(Lang.tr("Bibliotheken"))
    listOf(
        "AndroidX (Activity, Compose, Work)" to "Apache License 2.0",
        "AndroidX Media3 (ExoPlayer, HLS)" to "Apache License 2.0",
        "Jellyfin Media3 FFmpeg-Decoder (FFmpeg)" to "GNU GPL v3",
        "OkHttp (Square)" to "Apache License 2.0",
        "Coil" to "Apache License 2.0",
        "Kotlin, Kotlin Coroutines (JetBrains)" to "Apache License 2.0"
    ).forEach { (n, l) -> SettingRow(n, l, rawTitle = true, rawValue = true, onClick = {}) }
    PanelSection(Lang.tr("Schriften"))
    listOf("Outfit (The Outfit Project Authors)", "Inter (The Inter Project Authors)").forEach {
        SettingRow(it, "SIL Open Font License 1.1", rawTitle = true, rawValue = true, onClick = {})
    }
    PanelSection(Lang.tr("Online-Dienste"))
    SettingRow("The Movie Database (TMDB)", Lang.tr("Film- und Serieninfos, Bilder"), rawTitle = true, rawValue = true, onClick = {})
    LegalText("Dieses Produkt verwendet die TMDB-API, wird aber nicht von TMDB unterstützt oder zertifiziert.")
    SettingRow("OMDb API", Lang.tr("Wertungen von IMDb und Rotten Tomatoes"), rawTitle = true, rawValue = true, onClick = {})
    SettingRow("TheIntroDB", Lang.tr("Zeiten für Intro, Rückblick und Abspann"), rawTitle = true, rawValue = true, onClick = {})
    PanelSection(Lang.tr("Marken"))
    LegalText("IMDb, Rotten Tomatoes, TMDB, Android TV, Google TV und Fire TV sind Marken ihrer jeweiligen Inhaber. IPTV-XY ist mit ihnen nicht verbunden.")
}

/** Wählbarer Textabsatz (für lange Hinweise im Einstellungs-Panel). */
@Composable
private fun LegalText(text: String) {
    var f by remember { mutableStateOf(false) }
    Text(text, color = if (f) DS.FocusInk else Tok.TextMid, fontSize = 15.sp, lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(DS.RadiusS)).background(if (f) TvFocusFill else Color.Transparent)
            .onFocusChanged { f = it.isFocused }.focusable().padding(horizontal = 25.dp, vertical = 10.dp))
}


// ---------------------------------------------------------------- Einstellungs-Panel (Runde 20)

/** Seiten im Einstellungs-Panel; Zurück nimmt die oberste vom Stapel. */
private sealed class SP {
    object Root : SP()
    class Cat(val i: Int) : SP()
    class Pl(val idx: Int) : SP()
    class PlX(val idx: Int) : SP()
    class PlE(val idx: Int) : SP()
    object Lang : SP()
    object Upd : SP()
    class Sub(val key: String, val title: String) : SP()
}

/** Bereich im Panel mit den Seiten (Index in CATS), die er zeigt. */
private class PanelCat(val title: String, val pages: List<Int>)

/** Aufbau wie in TV-Playern üblich: Allgemein, Wiedergabelisten, EPG, Optik, Wiedergabe, Kindersicherung, Sonstiges, Über. */
private val PANEL = listOf(
    PanelCat("Allgemein", listOf(0, 1)),
    PanelCat("Wiedergabelisten", listOf(9)),
    PanelCat("EPG", listOf(8)),
    PanelCat("Optik", listOf(2, 4)),
    PanelCat("Wiedergabe", listOf(5, 6)),
    PanelCat("Fernbedienung", emptyList()),
    PanelCat("Kindersicherung", listOf(12)),
    PanelCat("Sonstiges", listOf(7, 13, 14)),
    PanelCat("Über", listOf(15))
)

/** Am rechten Fensterrand, volle Höhe. */
private val PanelRight = object : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize
    ) = androidx.compose.ui.unit.IntOffset(windowSize.width - popupContentSize.width, 0)
}

/**
 * Einstellungen als Leiste am rechten Rand über dem aktuellen Bildschirm (wie in TV-Playern üblich): oben ein hellerer Kopf
 * mit dem Titel, darunter die Einträge; OK öffnet eine Unterseite, Zurück geht eine Ebene hoch bzw. schließt.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun SettingsPanel(onAdd: () -> Unit, reload: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf<SP>(SP.Root) }
    val top = stack.last()
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }
    val title = when (top) {
        SP.Root -> Lang.tr("Einstellungen")
        is SP.Cat -> Lang.tr(PANEL[top.i].title)
        is SP.Pl -> PlaylistStore.lists.getOrNull(top.idx)?.name.orEmpty()
        is SP.PlX -> Lang.tr("Xtream Codes Parameter")
        is SP.PlE -> Lang.tr("EPG Quellen") + " (" + PlaylistStore.lists.getOrNull(top.idx)?.name.orEmpty() + ")"
        SP.Lang -> Lang.tr("Sprachen der Inhalte")
        SP.Upd -> Lang.tr("Automatisch aktualisieren")
        is SP.Sub -> Lang.tr(top.title)
    }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = PanelRight, onDismissRequest = onClose,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true, dismissOnBackPress = false)
    ) {
        val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.375f
        Column(
            Modifier.fillMaxHeight().width(w).background(Surf)
                .onPreviewKeyEvent { e -> if (e.key == Key.Back) { if (e.type == KeyEventType.KeyUp) back(); true } else false }
        ) {
            Box(Modifier.fillMaxWidth().height(72.dp).background(androidx.compose.ui.graphics.lerp(Surf, Color.White, 0.06f)).padding(start = 28.dp, end = 20.dp),
                contentAlignment = Alignment.CenterStart) {
                Text(raw = true, text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            key(stack.size, top) {
                // Ersten Eintrag markieren (wie ein erster Druck auf „runter“): beim Öffnen und bei jeder Unterseite
                val pfm = LocalFocusManager.current
                val rootReq = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    repeat(15) {
                        kotlinx.coroutines.delay(80)
                        val ok = if (top == SP.Root) runCatching { rootReq.requestFocus() }.isSuccess else pfm.moveFocus(FocusDirection.Down)
                        if (ok) return@LaunchedEffect
                    }
                }
                // Beim Wechsel der Einträge sofort scrollen (keine Animation – fühlt sich mit der Fernbedienung flinker an)
                val dens = androidx.compose.ui.platform.LocalDensity.current
                // Alles im Panel einheitlich auf TV-Player-Größe (0,8): auch ältere, größere Bauteile werden so kompakt
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides InstantSpec,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density * PANEL_SCALE, dens.fontScale)
                ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).focusGroup()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (top) {
                        SP.Root -> PANEL.forEachIndexed { i, c ->
                            SettingRow(c.title, null, modifier = if (i == 0) Modifier.focusRequester(rootReq) else Modifier, onClick = { stack.add(SP.Cat(i)) })
                        }
                        is SP.Cat -> {
                            val open: (String, String) -> Unit = { k, t -> stack.add(SP.Sub(k, t)) }
                            when (top.i) {
                                0 -> TmGeneral(ctx)
                                1 -> PlaylistPage(ctx, onAdd = { onClose(); onAdd() }, reload = reload, onOpen = { stack.add(SP.Pl(it)) },
                                    onLang = { stack.add(SP.Lang) }, onUpdate = { stack.add(SP.Upd) })
                                2 -> TmEpg(ctx) { open("epg_src", "EPG Quellen") }
                                3 -> TmLook(ctx, open)
                                4 -> TmPlayback(ctx, open)
                                5 -> TmRemote(ctx, open)
                                6 -> ParentalPage(ctx) { back() }
                                7 -> TmMisc(open)
                                else -> AboutPage(ctx, open)
                            }
                        }
                        is SP.Sub -> { val open: (String, String) -> Unit = { k, t -> stack.add(SP.Sub(k, t)) }
                        when (top.key) {
                            "epg_src" -> TmEpgSources(ctx)
                            "look_epg" -> TmLookEpg(ctx)
                            "look_player" -> TmLookPlayer(ctx, open)
                            "lp_list" -> TmLpList(ctx)
                            "lp_info" -> TmLpInfo(ctx)
                            "lp_hist" -> TmLpHist(ctx)
                            "lp_menu" -> TmLpMenu(ctx)
                            "play_steps" -> TmSteps(ctx)
                            "look_logos" -> TmLogos(ctx)
                            "rc_player" -> TmRemotePlayer(ctx)
                            "look_groups" -> TmLookGroups(ctx) { stack.add(SP.Lang) }
                            "look_vod", "misc_vod" -> ViewPage(ctx)
                            "look_menu" -> MenuPage(ctx)
                            "play_audio" -> AudioPage(ctx)
                            "misc_search" -> SettingRow("Suchverlauf löschen", null, onClick = { AppSettings.clearSearch(ctx) })
                            "misc_rem" -> ReminderSettingsSection(ctx)
                            "about_legal" -> AboutLegal()
                            "misc_lists" -> TmChannelLists(ctx)
                            "misc_storage" -> StoragePage(ctx)
                            "misc_diag" -> DiagPage(ctx, reload)
                        }
                        }
                        is SP.Pl -> PlaylistMainPage(ctx, top.idx, onXtream = { stack.add(SP.PlX(top.idx)) }, onEpg = { stack.add(SP.PlE(top.idx)) },
                            onDeleted = { back() }, reload = reload)
                        is SP.PlX -> PlaylistXtreamPage(ctx, top.idx)
                        is SP.PlE -> PlaylistEpgPage(ctx, top.idx)
                        SP.Lang -> LanguageFilterSection(ctx)
                        SP.Upd -> PlaylistUpdatePage(ctx)
                    }
                    Spacer(Modifier.height(40.dp))
                }
                }
            }
        }
    }
}

/** Ist das Einstellungs-Panel offen? (Menü „Einstellungen“, Profil-Kreis oben.) */
object SettingsOpen {
    var value by androidx.compose.runtime.mutableStateOf(false)
}

/** Ins Bild holen ohne Animation (Einstellungs-Panel). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal val InstantSpec = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = androidx.compose.animation.core.snap()
}

// ---------------------------------------------------------------- Bereiche im TV-Aufbau (Runde 20)
// Einträge und Reihenfolge wie in TV-Playern üblich; XY-eigene Funktionen stehen jeweils am Ende.

/** Allgemein: Autostart, letzter Sender, Zurück doppelt bestätigen, Daten sichern/wiederherstellen; dazu Startseite, Region. */
@Composable
private fun TmGeneral(ctx: Context) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    ToggleRow("Automatisch nach dem Booten starten", null, General.autoStart) { General.setAutoStart(ctx, it) }
    val granted = remember(tick, General.autoStart) { canStartFromBoot(ctx) }
    if (General.autoStart && !granted) SettingRow("Berechtigung fehlt", Lang.tr("„Über anderen Apps einblenden“ erlauben – hier öffnen"),
        onClick = { openOverlaySettings(ctx); tick++ })
    ToggleRow("Automatisch aus dem Sleep Mode starten", Lang.tr("Funktioniert nicht auf allen Geräten"), Tm.b(Tm.GEN_SLEEP_START, false)) { Tm.set(ctx, Tm.GEN_SLEEP_START, it) }
    ToggleRow("Zuletzt angesehenen Sender beim Start anzeigen", null, General.resumeLive) { General.setResumeLive(ctx, it) }
    ToggleRow("Doppelt bestätigen, zum Verlassen mit der Zurück-Taste", null, General.confirmExit) { General.setConfirmExit(ctx, it) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    val ua = Tm.s(Tm.GEN_UA)
    SettingRow("User-Agent", ua.ifBlank { Lang.tr("Nicht festgelegt") }, rawValue = true, onClick = {
        ask = Ask("User-Agent", ua, "Mozilla/5.0 …") { Tm.set(ctx, Tm.GEN_UA, it.trim()) }
    })
    val udp = Tm.s(Tm.GEN_UDP)
    SettingRow("UDP Proxy (Adresse:Port)", udp.ifBlank { Lang.tr("Nicht festgelegt") }, rawValue = true, onClick = {
        ask = Ask("UDP Proxy (Adresse:Port)", udp, "192.168.1.1:4022") { Tm.set(ctx, Tm.GEN_UDP, it.trim()) }
    })
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
    SettingRow("Daten sichern", msg, rawValue = true, onClick = {
        scope.launch {
            val f = withContext(Dispatchers.IO) { Backup.export(ctx) }
            msg = if (f != null) Lang.tr("Gespeichert") + ": " + f.absolutePath else Lang.tr("Sicherung fehlgeschlagen.")
        }
    })
    SettingRow("Daten wiederherstellen", null, onClick = {
        scope.launch {
            val n = withContext(Dispatchers.IO) { Backup.restore(ctx) }
            if (n >= 0) Profiles.restart(ctx) else msg = Lang.tr("Keine gültige Sicherung gefunden.")
        }
    })
    PanelSection(Lang.tr("Start"))
    val tabs = listOf("Start", "Filme", "Serien", "Live TV", "Suche")
    val shown = SettingsLogic.resolveStartTab(General.startTab, AppSettings.menuOrder.toList(), AppSettings.menuHidden.toList())
    SelectRow("Startseite", tabs[shown], tabs, shown) { General.setStartTab(ctx, it) }
    ToggleRow("Letzten Film / letzte Serie beim Start fortsetzen", null, General.resumeVod) { General.setResumeVod(ctx, it) }
    PanelSection(Lang.tr("Uhrzeit"))
    val fmts = listOf("Wie das Gerät", "24 Stunden", "12 Stunden")
    SelectRow("Zeitformat", fmts[General.timeFmt], fmts, General.timeFmt) { General.setTimeFmt(ctx, it) }
    val zones = SettingsLogic.ZONES
    val zi = zones.indexOfFirst { it.id == General.tzId }.coerceAtLeast(0)
    SelectRow("Zeitzone", zones[zi].label, zones.map { it.label }, zi) { General.setTz(ctx, zones[it].id) }
}

/** EPG: Quellen, Update-Optionen (Aktualisieren, EPG löschen), letzter Status. */
@Composable
private fun TmEpg(ctx: Context, onSources: () -> Unit) {
    val custom = Xmltv.customUrls(ctx)
    SettingRow("EPG Quellen", if (Xmltv.sources.isNotEmpty()) Xmltv.sources.size.toString() + " " + Lang.tr("Quellen aktiv") else if (custom.isEmpty()) Lang.tr("Nur von den Wiedergabelisten") else custom.size.toString() + " " + Lang.tr("Quellen"),
        rawValue = true, onClick = onSources)
    val days = EpgPrefs.DAY_OPTS
    SelectRow("Vergangene Tage, um EPG zu behalten", EpgPrefs.keepDays.toString(), days.map { it.toString() }, days.indexOf(EpgPrefs.keepDays).coerceAtLeast(0)) {
        EpgPrefs.set(ctx, keep = days[it])
    }
    ToggleRow("Programmbeschreibungen speichern", null, EpgPrefs.saveDesc) { EpgPrefs.set(ctx, desc = it) }
    PanelSection(Lang.tr("Update-Optionen"))
    val hours = EpgPrefs.HOUR_OPTS
    SelectRow("Aktualisierungsintervall (Stunden)", EpgPrefs.intervalH.toString(), hours.map { it.toString() }, hours.indexOf(EpgPrefs.intervalH).coerceAtLeast(0)) {
        EpgPrefs.set(ctx, hours = hours[it])
    }
    ToggleRow("Aktualisieren beim Starten der App", null, EpgPrefs.onStart) { EpgPrefs.set(ctx, start = it) }
    ToggleRow("Aktualisieren bei Playlisten-Änderung", null, EpgPrefs.onPlaylistChange) { EpgPrefs.set(ctx, plChange = it) }
    SettingRow("Aktualisieren", null, onClick = { Xmltv.start(ctx, PlaylistStore.epgUrls, PlaylistStore.channels, force = true) })
    SettingRow("EPG löschen", Lang.tr("Gespeichertes TV-Programm verwerfen und neu laden"), onClick = {
        runCatching { java.io.File(ctx.cacheDir, "xmltv.bin").delete() }
        Xmltv.start(ctx, PlaylistStore.epgUrls, PlaylistStore.channels, force = true)
    })
    SettingRow("Letzter Aktualisierungsstatus", Xmltv.status, rawValue = false, onClick = {})
}

/** EPG Quellen für alle Wiedergabelisten (zusätzlich zu den Quellen je Liste). */
@Composable
private fun TmEpgSources(ctx: Context) {
    var urls by remember { mutableStateOf(Xmltv.customUrls(ctx)) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var menu by remember { mutableIntStateOf(-1) }
    fun save(l: List<String>) {
        urls = l.distinct(); Xmltv.setCustomUrls(ctx, urls)
        Xmltv.start(ctx, (PlaylistStore.epgUrls + PlaylistStore.extraEpg).distinct(), PlaylistStore.channels, force = true)
    }
    // TiviMate: alle genutzten Quellen auf einen Blick – feste Quelle (Panel), EPG aus dem Xtream-Zugang, eigene – mit Stand
    val active = Xmltv.sources
    if (active.isNotEmpty()) {
        PanelSection(Lang.tr("Aktive Quellen"))
        active.forEach { s ->
            val kind = Lang.tr(when (s.kind) {
                EpgSrc.FIXED -> "Feste Quelle"
                EpgSrc.SERVER -> "Quelle des Servers"
                EpgSrc.PROVIDER -> "Aus dem Xtream-Zugang"
                EpgSrc.PL_EPG -> "Quelle der Wiedergabeliste"
                EpgSrc.PLAYLIST -> "Aus der Wiedergabeliste"
                else -> "Eigene Quelle"
            })
            val state = when (s.state) {
                EpgSourceStat.LOADING -> Lang.tr("Wird geladen …") + if (s.mb > 0) " ${s.mb} MB" else ""
                EpgSourceStat.DONE -> "${s.channels} " + Lang.tr("Sender")
                EpgSourceStat.ERROR -> Lang.tr("Fehler") + (s.error?.let { ": $it" } ?: "")
                else -> Lang.tr("Wartet")
            }
            SettingRow(if (s.kind == EpgSrc.PROVIDER) Lang.tr("Anbieter-EPG") else s.label, "$kind · $state", rawTitle = true, rawValue = true, onClick = {})
        }
    }
    SettingRow("Quelle hinzufügen", null, onClick = {
        ask = Ask("EPG-Quelle hinzufügen", "", "https://…/epg.xml.gz") { u ->
            if (u.startsWith("http://", true) || u.startsWith("https://", true)) save(urls + u)
            else android.widget.Toast.makeText(ctx, Lang.tr("Ungültige Adresse."), android.widget.Toast.LENGTH_SHORT).show()
        }
    })
    PanelSection(Lang.tr("Quellen für alle Wiedergabelisten"))
    urls.forEachIndexed { i, u -> SettingRow(EpgSources.label(EpgSrc(u, EpgSrc.OWN, null)), u, rawTitle = true, rawValue = true, onClick = { menu = i }) }
    Hint("Quellen einzelner Wiedergabelisten stehen unter Wiedergabelisten > Liste > EPG Quellen.")
    urls.getOrNull(menu)?.let { u ->
        val i = menu
        fun move(d: Int) { val m = urls.toMutableList(); val j = i + d; if (j in m.indices) { m.add(j, m.removeAt(i)); save(m) } }
        SideMenu(listOf(SideSection(u, listOfNotNull(
            if (i > 0) SideEntry("Priorität erhöhen", AIcon.Up) { move(-1) } else null,
            if (i < urls.lastIndex) SideEntry("Priorität senken", AIcon.Down) { move(1) } else null,
            SideEntry("Entfernen", AIcon.Close) { save(urls - u) }
        ))), { menu = -1 })
    }
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
}

/** Optik: Unterseiten EPG, Player, Gruppen, Logos; Sprache, Schriftgröße, Farbthema. */
@Composable
private fun TmLook(ctx: Context, open: (String, String) -> Unit) {
    SettingRow("EPG", null, onClick = { open("look_epg", "EPG") })
    SettingRow("Player", null, onClick = { open("look_player", "Player") })
    SettingRow("Gruppen", null, onClick = { open("look_groups", "Gruppen") })
    SettingRow("Logos", null, onClick = { open("look_logos", "Logos") })
    val langs = listOf("System") + I18nCore.NATIVE
    val li = if (General.lang.isEmpty()) 0 else (I18nCore.CODES.indexOf(General.lang) + 1).coerceAtLeast(0)
    SelectRow("Sprache", langs.getOrElse(li) { "System" }, langs, li) { General.setLang(ctx, if (it == 0) "" else I18nCore.CODES[it - 1]) }
    SelectRow("Schriftgröße", General.TEXT_SCALES[General.textScaleIdx], General.TEXT_SCALES, General.textScaleIdx) { General.setTextScale(ctx, it) }
    val themes = Look.ACCENTS.map { it.name }
    val ti = Look.accentIdx.coerceIn(0, themes.lastIndex)
    SelectRow("Farbthema", Lang.tr("Dunkel") + "  •  " + themes[ti], themes, ti) {
        AppSettings.setLook(ctx, it, Look.custom, Look.bgIdx, Look.motion, Look.uiScale)
    }
    val alphas = listOf(0, 10, 20, 30, 40, 50, 60, 70)
    SelectRow("Transparenz der Benutzeroberfläche (%)", Tm.i(Tm.UI_ALPHA, 20).toString(), alphas.map { "$it" }, alphas.indexOf(Tm.i(Tm.UI_ALPHA, 20)).coerceAtLeast(0)) { Tm.set(ctx, Tm.UI_ALPHA, alphas[it]) }
    // XY-eigene Bereiche
    PanelSection("IPTV-XY")
    SettingRow("Filme & Serien", null, onClick = { open("look_vod", "Filme & Serien") })
    SettingRow("Menü", null, onClick = { open("look_menu", "Menü") })
}

/** Optik > Logos (TiviMate). */
@Composable
private fun TmLogos(ctx: Context) {
    var ask by remember { mutableStateOf<Ask?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val pr = Logos.PRIO_NAMES.map { Lang.tr(it) }
    SelectRow("Logo Priorität", pr[Tm.i(Tm.LOGO_PRIO, 0)], pr, Tm.i(Tm.LOGO_PRIO, 0)) { Tm.set(ctx, Tm.LOGO_PRIO, it) }
    val dir = Tm.s(Tm.LOGO_DIR)
    SettingRow("Ordner für Logos", dir.ifBlank { "–" }, rawValue = true, onClick = {
        ask = Ask("Ordner für Logos", dir, "/storage/emulated/0/Logos  oder  https://…/logos") { Tm.set(ctx, Tm.LOGO_DIR, it.trim()) }
    })
    ToggleRow("Übereinstimmung bei ungenauer Bezeichnung für Logodateien", null, Tm.b(Tm.LOGO_FUZZY, false)) { Tm.set(ctx, Tm.LOGO_FUZZY, it) }
    SettingRow("Logo Cache löschen", msg, rawValue = true, onClick = {
        runCatching { coil.Coil.imageLoader(ctx).memoryCache?.clear(); @OptIn(coil.annotation.ExperimentalCoilApi::class) coil.Coil.imageLoader(ctx).diskCache?.clear() }
        msg = Lang.tr("Gelöscht")
    })
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
}

/** Optik > EPG: Darstellung des TV-Guides. */
@Composable
private fun TmLookEpg(ctx: Context) {
    // Aufbau und Reihenfolge wie TiviMate (Optik > EPG)
    var ask by remember { mutableStateOf<Ask?>(null) }
    val sorts = Tm.SORT_NAMES.map { Lang.tr(it) }
    SelectRow("Sender Sortierung", sorts[Tm.i(Tm.EPG_CH_SORT, 0)], sorts, Tm.i(Tm.EPG_CH_SORT, 0)) { Tm.set(ctx, Tm.EPG_CH_SORT, it) }
    val prev = listOf(Lang.tr("Einschalten"), Lang.tr("Ausschalten"))
    SelectRow("Vorschau", prev[if (AppSettings.livePreview) 0 else 1], prev, if (AppSettings.livePreview) 0 else 1) { AppSettings.setLivePreview(ctx, it == 0) }
    val ed = Tm.s(Tm.EPG_NAME_EDIT)
    SettingRow("Sendernamen-Editor", ed.ifBlank { Lang.tr("Aus") }, rawValue = true, onClick = {
        ask = Ask("Sendernamen-Editor", ed, "DE| , HD , FHD") { Tm.set(ctx, Tm.EPG_NAME_EDIT, it) }
    })
    ToggleRow("Zeige Kanalnummern", null, Tm.b(Tm.EPG_NUMBERS, false)) { Tm.set(ctx, Tm.EPG_NUMBERS, it) }
    ToggleRow("Zeige Sendernamen", null, Tm.b(Tm.EPG_NAMES, true)) { Tm.set(ctx, Tm.EPG_NAMES, it) }
    ToggleRow("Zweizeilige Kanalnamen", null, Tm.b(Tm.EPG_TWO_LINE_NAMES, false)) { Tm.set(ctx, Tm.EPG_TWO_LINE_NAMES, it) }
    ToggleRow("Catchup Symbol in Kanalliste anzeigen", null, Tm.b(Tm.EPG_CATCHUP_ICON, true)) { Tm.set(ctx, Tm.EPG_CATCHUP_ICON, it) }
    ToggleRow("Aktuellen Sender farbig hervorheben", null, Tm.b(Tm.EPG_HL_CURRENT, true)) { Tm.set(ctx, Tm.EPG_HL_CURRENT, it) }
    ToggleRow("Zweizeilige Programmtitel", null, Tm.b(Tm.EPG_TWO_LINE_TITLES, false)) { Tm.set(ctx, Tm.EPG_TWO_LINE_TITLES, it) }
    ToggleRow("Markiere laufendes Programm", null, Tm.b(Tm.EPG_MARK_RUNNING, true)) { Tm.set(ctx, Tm.EPG_MARK_RUNNING, it) }
    ToggleRow("Markiere nur den aktuellen Fortschritt", null, Tm.b(Tm.EPG_MARK_PROGRESS, true)) { Tm.set(ctx, Tm.EPG_MARK_PROGRESS, it) }
    ToggleRow("Aktuelle Zeitanzeige in voller Höhe anzeigen", null, Tm.b(Tm.EPG_NOW_FULL, true)) { Tm.set(ctx, Tm.EPG_NOW_FULL, it) }
    ToggleRow("Kanäle scrollen animieren", null, Tm.b(Tm.EPG_SCROLL_ANIM, false)) { Tm.set(ctx, Tm.EPG_SCROLL_ANIM, it) }
    ToggleRow("Bleib im EPG, wenn du im Overlay-Modus die Sender wechselst", null, Tm.b(Tm.EPG_STAY_OVERLAY, false)) { Tm.set(ctx, Tm.EPG_STAY_OVERLAY, it) }
    ToggleRow("Verwende Zurück, um zu den aktuellen Programmen zurückzukehren", null, Tm.b(Tm.EPG_BACK_NOW, true)) { Tm.set(ctx, Tm.EPG_BACK_NOW, it) }
    CatchupSection()
    ask?.let { a -> TextAskDialog(Lang.tr(a.title), a.initial, a.hint, a.password, onDismiss = { ask = null }) { ask = null; a.onSave(it) } }
}

/** Optik > Gruppen (TiviMate): Sortierung und welche Kategorien oben stehen; darunter die XY-Gruppenfilter. */
@Composable
private fun TmLookGroups(ctx: Context, onLang: () -> Unit) {
    val sorts = Tm.SORT_NAMES.map { Lang.tr(it) }
    SelectRow("Gruppen Sortierung", sorts[Tm.i(Tm.GRP_SORT, 0)], sorts, Tm.i(Tm.GRP_SORT, 0)) { Tm.set(ctx, Tm.GRP_SORT, it) }
    ToggleRow("Zeige \"alle Playlists\" Kategorie", null, Tm.b(Tm.GRP_ALL_PLAYLISTS, true)) { Tm.set(ctx, Tm.GRP_ALL_PLAYLISTS, it) }
    ToggleRow("Zeige \"Favoriten\" Kategorie", null, Tm.b(Tm.GRP_FAVS, true)) { Tm.set(ctx, Tm.GRP_FAVS, it) }
    ToggleRow("Zeige \"alle Kanäle\" Kategorie", null, Tm.b(Tm.GRP_ALL, true)) { Tm.set(ctx, Tm.GRP_ALL, it) }
    FilterPage(ctx) { onLang() }
}

/** Optik > Player: Infos und Uhrzeit im Bild. */
@Composable
private fun TmLookPlayer(ctx: Context, open: (String, String) -> Unit) {
    // Aufbau wie TiviMate (Optik > Player)
    SettingRow("Senderliste", null, onClick = { open("lp_list", "Senderliste") })
    SettingRow("Infofeld", null, onClick = { open("lp_info", "Infofeld") })
    SettingRow("Historie / Letzte Sender", null, onClick = { open("lp_hist", "Historie / Letzte Sender") })
    SettingRow("Menü", null, onClick = { open("lp_menu", "Menü") })
    val clocks = listOf("Aus", "Oben links", "Oben rechts", "Unten links", "Unten rechts").map { Lang.tr(it) }
    val ci = if (AppSettings.clockOn) AppSettings.clockPos + 1 else 0
    SelectRow("Uhr", clocks[ci.coerceIn(0, 4)], clocks, ci.coerceIn(0, 4)) {
        AppSettings.setClock(ctx, it > 0, if (it > 0) it - 1 else AppSettings.clockPos, AppSettings.clockOpacity, AppSettings.clockSize)
    }
    val hides = listOf(3, 4, 5, 6, 8, 10, 15)
    SelectRow("Feld Zeitüberschreitung (Sekunden)", "${AppSettings.hideSec}", hides.map { "$it" }, hides.indexOf(AppSettings.hideSec).coerceAtLeast(0)) {
        AppSettings.setPlayerExtra(ctx, AppSettings.autoNext, hides[it], AppSettings.showEndTime, AppSettings.autoSkipIntro)
    }
    ToggleRow("Zeige schwarzes Bild beim Senderwechsel", null, Tm.b(Tm.PL_BLACK_SWITCH, false)) { Tm.set(ctx, Tm.PL_BLACK_SWITCH, it) }
    PanelSection(Lang.tr("Filme & Serien"))
    ToggleRow("Stream-Info am Start", null, AppSettings.showInfo) { AppSettings.setPlayer(ctx, AppSettings.skipIntro, it, AppSettings.seekStep) }
    ToggleRow("Endzeit anzeigen", null, AppSettings.showEndTime) { AppSettings.setPlayerExtra(ctx, AppSettings.autoNext, AppSettings.hideSec, it, AppSettings.autoSkipIntro) }
}

/** Optik > Player > Senderliste (TiviMate). */
@Composable
private fun TmLpList(ctx: Context) {
    ToggleRow("Aktuelles Programm farbig hervorheben", null, Tm.b(Tm.CL_HL_NOW, true)) { Tm.set(ctx, Tm.CL_HL_NOW, it) }
    ToggleRow("Vergangenes Programm ausblenden", null, Tm.b(Tm.CL_HIDE_PAST, false)) { Tm.set(ctx, Tm.CL_HIDE_PAST, it) }
    PanelSection(Lang.tr("Überlagerungsmodus"))
    ToggleRow("Zeige Programme", null, Tm.b(Tm.CL_OV_PROGS, true)) { Tm.set(ctx, Tm.CL_OV_PROGS, it) }
    ToggleRow("Zeige Programmbeschreibung", null, Tm.b(Tm.CL_OV_DESC, true)) { Tm.set(ctx, Tm.CL_OV_DESC, it) }
    ToggleRow("Kanäle automatisch abspielen", null, Tm.b(Tm.CL_OV_AUTOPLAY, false)) { Tm.set(ctx, Tm.CL_OV_AUTOPLAY, it) }
    ToggleRow("Bleib in der Liste beim Sender wechseln", null, Tm.b(Tm.CL_OV_STAY, false)) { Tm.set(ctx, Tm.CL_OV_STAY, it) }
}

/** Optik > Player > Infofeld (TiviMate). */
@Composable
private fun TmLpInfo(ctx: Context) {
    ToggleRow("Kartenstil", null, Tm.b(Tm.IF_CARD, false)) { Tm.set(ctx, Tm.IF_CARD, it) }
    ToggleRow("Zeige die Uhr", null, Tm.b(Tm.IF_CLOCK, true)) { Tm.set(ctx, Tm.IF_CLOCK, it) }
    ToggleRow("Datum neben der Uhr anzeigen", null, Tm.b(Tm.IF_DATE, true)) { Tm.set(ctx, Tm.IF_DATE, it) }
    ToggleRow("Zeige Wiedergabeliste und Gruppenname", null, Tm.b(Tm.IF_PLGROUP, true)) { Tm.set(ctx, Tm.IF_PLGROUP, it) }
    ToggleRow("Zeige Programmbeschreibung", null, Tm.b(Tm.IF_DESC, true)) { Tm.set(ctx, Tm.IF_DESC, it) }
    ToggleRow("Beschreibung nur beim Senderwechsel anzeigen", null, Tm.b(Tm.IF_DESC_SWITCH, true)) { Tm.set(ctx, Tm.IF_DESC_SWITCH, it) }
    ToggleRow("Medieneigenschaften anzeigen", null, Tm.b(Tm.IF_MEDIA, true)) { Tm.set(ctx, Tm.IF_MEDIA, it) }
    ToggleRow("Videoauflösung anstelle von Bezeichnung anzeigen", Lang.tr("Zum Beispiel \"1920x1080\" statt \"FHD\""), Tm.b(Tm.IF_RES_NUM, false)) { Tm.set(ctx, Tm.IF_RES_NUM, it) }
}

/** Optik > Player > Historie / Letzte Sender (TiviMate). */
@Composable
private fun TmLpHist(ctx: Context) {
    ToggleRow("Schaltfläche \"EPG\" zeigen", null, Tm.b(Tm.HI_EPG_BTN, true)) { Tm.set(ctx, Tm.HI_EPG_BTN, it) }
    ToggleRow("Zeige \"Verlauf\" Button", null, Tm.b(Tm.HI_HIST_BTN, true)) { Tm.set(ctx, Tm.HI_HIST_BTN, it) }
    PanelSection(Lang.tr("Verlauf"))
    val days = listOf(1, 2, 3, 5, 7, 14, 30)
    SelectRow("Verlauf Tageszähler", Tm.i(Tm.HI_DAYS, 7).toString(), days.map { "$it" }, days.indexOf(Tm.i(Tm.HI_DAYS, 7)).coerceAtLeast(0)) { Tm.set(ctx, Tm.HI_DAYS, days[it]) }
    val delays = listOf(0, 5, 10, 15, 30, 60)
    SelectRow("Verzögerung vor dem Hinzufügen zum Verlauf, Sekunden", Tm.i(Tm.HI_DELAY, 10).toString(), delays.map { "$it" }, delays.indexOf(Tm.i(Tm.HI_DELAY, 10)).coerceAtLeast(0)) { Tm.set(ctx, Tm.HI_DELAY, delays[it]) }
    ToggleRow("Aktuelles Programm anzeigen", null, Tm.b(Tm.HI_NOW, true)) { Tm.set(ctx, Tm.HI_NOW, it) }
    ToggleRow("Vergangene Sendungen anzeigen", null, Tm.b(Tm.HI_PAST, false)) { Tm.set(ctx, Tm.HI_PAST, it) }
    PanelSection(Lang.tr("Zuletzt angesehene Sender"))
    val counts = listOf(5, 10, 15, 20, 30)
    SelectRow("Anzahl der letzten Sender", Tm.i(Tm.RC_COUNT, 10).toString(), counts.map { "$it" }, counts.indexOf(Tm.i(Tm.RC_COUNT, 10)).coerceAtLeast(0)) { Tm.set(ctx, Tm.RC_COUNT, counts[it]) }
    SelectRow("Verzögerung vor dem Hinzufügen zu den letzten Sendern, Sekunden", Tm.i(Tm.RC_DELAY, 0).toString(), delays.map { "$it" }, delays.indexOf(Tm.i(Tm.RC_DELAY, 0)).coerceAtLeast(0)) { Tm.set(ctx, Tm.RC_DELAY, delays[it]) }
    ToggleRow("Zeige Sendernamen", null, Tm.b(Tm.RC_NAMES, false)) { Tm.set(ctx, Tm.RC_NAMES, it) }
}

/** Optik > Player > Menü (TiviMate): Knöpfe ein-/ausblenden und neu anordnen. */
@Composable
private fun TmLpMenu(ctx: Context) {
    var reorder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<MBtn?>(null) }
    SettingRow(if (reorder) "Fertig" else "Buttons neu anordnen", if (reorder) Lang.tr("OK auf einem Button: verschieben") else null, onClick = { reorder = !reorder })
    val order = PlayerMenu.order()
    if (reorder) {
        order.filter { PlayerMenu.visible(it) }.forEach { b -> SettingRow(b.label, null, onClick = { moving = b }) }
    } else {
        PanelSection(Lang.tr("Sichtbare Buttons"))
        order.filter { PlayerMenu.visible(it) }.forEach { b -> ToggleRow(b.label, null, true) { PlayerMenu.setVisible(ctx, b, it) } }
        PanelSection(Lang.tr("Versteckte Buttons"))
        order.filter { !PlayerMenu.visible(it) }.forEach { b -> ToggleRow(b.label, null, false) { PlayerMenu.setVisible(ctx, b, it) } }
        SettingRow("Auf Standard zurücksetzen", null, onClick = { PlayerMenu.reset(ctx) })
    }
    moving?.let { b ->
        SideMenu(listOf(SideSection(Lang.tr(b.label), listOf(
            SideEntry("Nach vorne", AIcon.Up) { PlayerMenu.move(ctx, b, -1) },
            SideEntry("Nach hinten", AIcon.Down) { PlayerMenu.move(ctx, b, 1) }
        ))), { moving = null })
    }
}

/** Wiedergabe: Puffergröße, AFR, Schritte überspringen, Bildformat; dazu Intro/Abspann, Ton & Untertitel. */
@Composable
private fun TmPlayback(ctx: Context, open: (String, String) -> Unit) {
    val bufNames = listOf("Automatisch", "Normal", "Groß", "Sehr groß")
    SelectRow("Puffergröße", bufNames[AppSettings.bufferMode], bufNames, AppSettings.bufferMode) { AppSettings.setBufferMode(ctx, it) }
    // Reihenfolge wie TiviMate (Wiedergabe); Änderungen an Decoder/Passthrough/Tunneling gelten ab dem nächsten Sender
    val dec = listOf(Lang.tr("Hardware"), Lang.tr("Software"))
    SelectRow("Audio Decoder", dec[Tm.i(Tm.PB_AUDIO_DEC, 0)], dec, Tm.i(Tm.PB_AUDIO_DEC, 0)) { Tm.set(ctx, Tm.PB_AUDIO_DEC, it) }
    SelectRow("Video Decoder", dec[Tm.i(Tm.PB_VIDEO_DEC, 0)], dec, Tm.i(Tm.PB_VIDEO_DEC, 0)) { Tm.set(ctx, Tm.PB_VIDEO_DEC, it) }
    val afr = listOf("Aus", "An")
    SelectRow("Automatische Anpassung der Framerate (AFR)", afr[if (AppSettings.autoFps) 1 else 0], afr, if (AppSettings.autoFps) 1 else 0) {
        AppSettings.setAutoFps(ctx, it == 1)
    }
    ToggleRow("Wähle standardmäßig die Surround-Audiospur aus", null, Tm.b(Tm.PB_SURROUND, false)) { Tm.set(ctx, Tm.PB_SURROUND, it) }
    ToggleRow("Audio-Passthrough", null, Tm.b(Tm.PB_PASSTHROUGH, false)) { Tm.set(ctx, Tm.PB_PASSTHROUGH, it) }
    ToggleRow("Getunnelte Wiedergabe", null, Tm.b(Tm.PB_TUNNEL, false)) { Tm.set(ctx, Tm.PB_TUNNEL, it) }
    val ext = listOf(Lang.tr("Nein"), Lang.tr("Ja"), Lang.tr("Nur für Filme und Serien"))
    SelectRow("Externen Player nutzen", ext[Tm.i(Tm.PB_EXTERNAL, 0)], ext, Tm.i(Tm.PB_EXTERNAL, 0)) { Tm.set(ctx, Tm.PB_EXTERNAL, it) }
    SettingRow("Schritte überspringen", null, onClick = { open("play_steps", "Schritte überspringen") })
    SelectRow("Bildformat", General.ASPECT_NAMES[General.defaultAspect], General.ASPECT_NAMES, General.defaultAspect) { General.setAspect(ctx, it) }
    SettingRow("Audio & Untertitel", Lang.tr("Sprachen, Untertitel-Stil"), onClick = { open("play_audio", "Audio & Untertitel") })
    PanelSection(Lang.tr("Filme & Serien"))
    ToggleRow("Nächste Folge automatisch", null, AppSettings.autoNext) { AppSettings.setPlayerExtra(ctx, it, AppSettings.hideSec, AppSettings.showEndTime, AppSettings.autoSkipIntro) }
    ToggleRow("Intro überspringen anbieten", null, AppSettings.skipIntro) { AppSettings.setPlayer(ctx, it, AppSettings.showInfo, AppSettings.seekStep) }
    ToggleRow("Intro automatisch überspringen", null, AppSettings.autoSkipIntro) { AppSettings.setPlayerExtra(ctx, AppSettings.autoNext, AppSettings.hideSec, AppSettings.showEndTime, it) }
    ToggleRow("Abspann automatisch überspringen", null, AppSettings.autoSkipOutro) { AppSettings.setAutoOutro(ctx, it) }
    PanelSection(Lang.tr("Störungen"))
    DeadLinksSection(ctx)
    SourceSettingsSection(ctx)
}

/** Sonstiges: Suchen, Erinnerungen, VOD; dazu Senderlisten, Speicher, Diagnose. */
@Composable
private fun TmMisc(open: (String, String) -> Unit) {
    SettingRow("Suchen", null, onClick = { open("misc_search", "Suchen") })
    SettingRow("Erinnerungen", null, onClick = { open("misc_rem", "Erinnerungen") })
    SettingRow("VOD", null, onClick = { open("misc_vod", "VOD") })
    SettingRow("Senderlisten", null, onClick = { open("misc_lists", "Senderlisten") })
    SettingRow("Speicher", null, onClick = { open("misc_storage", "Speicher") })
    SettingRow("Diagnose", null, onClick = { open("misc_diag", "Diagnose") })
}
private const val PANEL_SCALE = 0.8f

/** Wiedergabe > Schritte überspringen (TiviMate): getrennte Sprungweiten für Zeitleiste und RW-/FF-Taste. */
@Composable
private fun TmSteps(ctx: Context) {
    val opts = listOf(5, 10, 15, 30, 60, 120, 300, 600)
    fun label(s: Int) = if (s >= 60) "${s / 60} Min" else "$s Sek"
    @Composable fun row(title: String, key: String, def: Int) {
        val v = Tm.i(key, def)
        SelectRow(title, label(v), opts.map { label(it) }, opts.indexOf(v).coerceAtLeast(0)) { Tm.set(ctx, key, opts[it]) }
    }
    row("Sprungschritt für Zeitleiste (rückwärts)", Tm.PB_STEP_TL_BACK, AppSettings.seekStep)
    row("Sprungschritt für Zeitleiste (vorwärts)", Tm.PB_STEP_TL_FWD, AppSettings.seekStep)
    row("Überspringe den Schritt für die RW-Taste", Tm.PB_STEP_RW, 10)
    row("Überspringe den Schritt für die FF-Taste", Tm.PB_STEP_FF, 10)
}

/** Fernbedienung (TiviMate): Tastenbelegung im Player und Spuloptionen. */
@Composable
private fun TmRemote(ctx: Context, open: (String, String) -> Unit) {
    SettingRow("Player", Lang.tr("Aktion für jede Taste und jeden langen Druck"), onClick = { open("rc_player", "Player") })
    PanelSection(Lang.tr("Spuloptionen"))
    ToggleRow("Verwende RW / FF / Pause zum Suchen / Pausieren, während du anschaust", null, Tm.b(Tm.SP_RWFF, true)) { Tm.set(ctx, Tm.SP_RWFF, it) }
    ToggleRow("Verwende RW, um den Live-Stream zurückzuspulen", null, Tm.b(Tm.SP_RW_LIVE, false)) { Tm.set(ctx, Tm.SP_RW_LIVE, it) }
    ToggleRow("Verwende Links/Rechts zum Spulen, während du Catchup schaust", null, Tm.b(Tm.SP_LR_CATCHUP, false)) { Tm.set(ctx, Tm.SP_LR_CATCHUP, it) }
    ToggleRow("Verwende Down / Up zum Suchen, während du den Catchup schaust", null, Tm.b(Tm.SP_UD_CATCHUP, false)) { Tm.set(ctx, Tm.SP_UD_CATCHUP, it) }
}

/** Fernbedienung > Player (TiviMate): jede Taste mit eigener Aktion. */
@Composable
private fun TmRemotePlayer(ctx: Context) {
    val acts = PAct.entries
    val labels = acts.map { Lang.tr(it.label) }
    PKey.entries.forEach { k ->
        val a = PlayerKeys.action(k)
        SelectRow(k.label, Lang.tr(a.label), labels, a.ordinal) { PlayerKeys.set(ctx, k, acts[it]) }
    }
    SettingRow("Auf Standard zurücksetzen", null, onClick = { PlayerKeys.reset(ctx) })
}

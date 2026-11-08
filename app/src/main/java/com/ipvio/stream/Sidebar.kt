package com.ipvio.stream

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MenuIcon(id: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val s = size.minDimension
        // Strichstärke im Verhältnis zur Größe (wie die Umriss-Symbole im Entwurf)
        val sw = Stroke(width = maxOf(1.4.dp.toPx(), s * 0.085f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = sw.width
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = w, cap = StrokeCap.Round)
        fun rect(x: Float, y: Float, rw: Float, rh: Float, r: Float) =
            drawRoundRect(color, topLeft = Offset(x * s, y * s), size = Size(rw * s, rh * s), cornerRadius = CornerRadius(r * s), style = sw)
        // Symbole wie im Entwurf (Umriss-Stil)
        when (id) {
            0 -> { // Home: Haus
                val p = Path().apply {
                    moveTo(.12f * s, .5f * s); lineTo(.5f * s, .15f * s); lineTo(.88f * s, .5f * s)
                    moveTo(.22f * s, .44f * s); lineTo(.22f * s, .86f * s); lineTo(.78f * s, .86f * s); lineTo(.78f * s, .44f * s)
                }
                drawPath(p, color, style = sw)
            }
            1 -> { // Filme: Filmstreifen
                rect(.14f, .14f, .72f, .72f, .1f)
                line(.32f, .14f, .32f, .86f); line(.68f, .14f, .68f, .86f)
                line(.14f, .38f, .32f, .38f); line(.14f, .62f, .32f, .62f)
                line(.68f, .38f, .86f, .38f); line(.68f, .62f, .86f, .62f)
            }
            2 -> { // Serien: Filmklappe
                rect(.12f, .38f, .76f, .48f, .08f)
                val p = Path().apply {
                    moveTo(.12f * s, .38f * s); lineTo(.16f * s, .2f * s); lineTo(.86f * s, .12f * s); lineTo(.88f * s, .3f * s); close()
                }
                drawPath(p, color, style = sw)
                line(.36f, .17f, .42f, .34f); line(.6f, .14f, .66f, .32f)
            }
            3 -> { // Live TV: Fernseher mit Antenne
                rect(.1f, .3f, .8f, .56f, .1f)
                line(.34f, .12f, .5f, .3f); line(.66f, .12f, .5f, .3f)
            }
            4 -> { // Suche: Lupe
                drawCircle(color, radius = .26f * s, center = Offset(.44f * s, .44f * s), style = sw)
                line(.63f, .63f, .86f, .86f)
            }
            5 -> { // Einstellungen: Zahnrad
                val c = Offset(.5f * s, .5f * s)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val cx = kotlin.math.cos(a).toFloat(); val cy = kotlin.math.sin(a).toFloat()
                    drawLine(color, Offset(c.x + cx * .3f * s, c.y + cy * .3f * s), Offset(c.x + cx * .4f * s, c.y + cy * .4f * s), strokeWidth = w * 1.5f, cap = StrokeCap.Round)
                }
                drawCircle(color, radius = .27f * s, center = c, style = sw)
                drawCircle(color, radius = .1f * s, center = c, style = sw)
            }
            6 -> { // EPG: Liste mit Punkten
                for (y in listOf(.26f, .5f, .74f)) {
                    drawCircle(color, radius = .055f * s, center = Offset(.18f * s, y * s))
                    line(.34f, y, .88f, y)
                }
            }
            7 -> { // Favoriten: Herz
                val p = Path().apply {
                    moveTo(.5f * s, .84f * s)
                    cubicTo(.08f * s, .56f * s, .06f * s, .2f * s, .3f * s, .18f * s)
                    cubicTo(.42f * s, .17f * s, .48f * s, .26f * s, .5f * s, .32f * s)
                    cubicTo(.52f * s, .26f * s, .58f * s, .17f * s, .7f * s, .18f * s)
                    cubicTo(.94f * s, .2f * s, .92f * s, .56f * s, .5f * s, .84f * s)
                    close()
                }
                drawPath(p, color, style = sw)
            }
            8 -> { // Meine Liste: Lesezeichen
                val p = Path().apply {
                    moveTo(.26f * s, .12f * s); lineTo(.74f * s, .12f * s); lineTo(.74f * s, .88f * s)
                    lineTo(.5f * s, .68f * s); lineTo(.26f * s, .88f * s); close()
                }
                drawPath(p, color, style = sw)
            }
            9 -> { // Verlauf: Uhr mit Zeigern
                drawCircle(color, radius = .38f * s, center = Offset(.5f * s, .5f * s), style = sw)
                line(.5f, .5f, .5f, .28f)
                line(.5f, .5f, .66f, .6f)
            }
            10 -> { // Aufnahmen: Kreis mit Punkt
                drawCircle(color, radius = .36f * s, center = Offset(.5f * s, .5f * s), style = sw)
                drawCircle(color, radius = .16f * s, center = Offset(.5f * s, .5f * s))
            }
            11 -> { // Sport: Ball (Kreis mit Fünfeck und Nähten)
                val c = Offset(.5f * s, .5f * s)
                drawCircle(color, radius = .36f * s, center = c, style = sw)
                val pent = Path().apply {
                    for (k in 0 until 5) {
                        val a = Math.toRadians(-90.0 + k * 72.0)
                        val x = (.5f + .13f * Math.cos(a).toFloat()) * s; val y = (.5f + .13f * Math.sin(a).toFloat()) * s
                        if (k == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(pent, color)
                for (k in 0 until 5) {
                    val a = Math.toRadians(-90.0 + k * 72.0)
                    line(.5f + .13f * Math.cos(a).toFloat(), .5f + .13f * Math.sin(a).toFloat(), .5f + .34f * Math.cos(a).toFloat(), .5f + .34f * Math.sin(a).toFloat())
                }
            }
            else -> {
                for (y in listOf(.26f, .5f, .74f)) line(.12f, y, .88f, y)
            }
        }
    }
}

// Seitenleiste (Runde 14): eingeklappt nur Symbole (immer sichtbar), mit Fokus klappt sie über den Inhalt auf und zeigt die Texte
// Runde 20: heller als der Inhalt (wie bei TV-Playern üblich), neutral grau
private val SidebarBg: Color get() = Look.surf
private val SidebarInk = Color(0xFFA9AEB4)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Sidebar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier, activeFocus: FocusRequester, onFocusIn: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }   // = Seitenleiste hat den Fokus
    val fm = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val visible = AppSettings.menuOrder.filter { it !in AppSettings.menuHidden }
    val activeVisible = tab in visible
    val w by animateDpAsState(if (expanded) DS.SidebarOpenW else DS.SidebarW, if (Look.motion == 0) snap() else tween(220), label = "sideW")
    Box(modifier.fillMaxHeight()) {
        // Aufgeklappt: Inhalt dahinter abdunkeln, damit die Texte gut lesbar sind
        if (expanded) Box(
            Modifier.width(w + dpx(260)).fillMaxHeight()
                .background(Brush.horizontalGradient(0f to Color(0xF2000000), 0.55f to Color(0xB3000000), 1f to Color.Transparent))
        )
        Column(
            Modifier.width(w).fillMaxHeight()
                .background(SidebarBg)
                .drawBehind { drawLine(Color(0x1AFFFFFF), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1f) }
                .focusProperties { enterTo { if (activeVisible) activeFocus else FocusRequester.Default } }
                // Rechts: zurück zum zuletzt markierten Element im Inhalt (nicht zum Element neben dem Menüpunkt)
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight) {
                        ContentMemory.restore() || runCatching { ContentFocus.req.requestFocus() }.getOrDefault(false)
                    } else false
                }
                .focusGroup()
                .onFocusChanged { expanded = it.hasFocus; onFocusIn(it.hasFocus) }
                .padding(start = dpx(16), end = dpx(16), top = dpx(22))
        ) {
            // Logo mittig über die ganze Breite: eingeklappt so breit wie die Leiste, aufgeklappt groß.
            // Feste Höhe, damit die Menüpunkte beim Aufklappen nicht springen.
            Box(Modifier.fillMaxWidth().height(dpx(130)), contentAlignment = Alignment.Center) { BrandMark(Modifier.size(if (expanded) dpx(96) else dpx(52))) }
            Spacer(Modifier.height(dpx(20)))
            Column(verticalArrangement = Arrangement.spacedBy(dpx(10))) {
                visible.forEach { id ->
                    SideItem(id, MENU_LABELS[id], id == tab, expanded, if (id == tab) Modifier.focusRequester(activeFocus) else Modifier) {
                        AppNav.focusTab = id
                        HeaderFocus.returnTo = null
                        onTab(id)
                        scope.launch {
                            delay(200)
                            // Solange die Seitenleiste den Fokus noch hat, weiter versuchen: erst das Ziel des Inhalts,
                            // dann "nach rechts". Ein einzelner Versuch scheitert, wenn die Liste noch lädt.
                            var n = 0
                            while (expanded && n < 14) {
                                val ok = ContentFocus.tab == id && runCatching { ContentFocus.req.requestFocus() }.getOrDefault(false)
                                if (ok) AppNav.focusTab = -1
                                if (!ok && n >= 4) runCatching { fm.moveFocus(FocusDirection.Right) }
                                delay(100)
                                n++
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideItem(id: Int, label: String, active: Boolean, expanded: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(dpx(12))
    // Aktiv: Symbol in der Designfarbe auf getönter Fläche. Fokus: weiße Fläche, dunkle Schrift (eindeutig „markiert“).
    val ink = if (f) DS.FocusInk else if (active) Color.White else SidebarInk
    Row(
        modifier.fillMaxWidth().height(dpx(56)).clip(shape)
            .background(
                if (f) SolidColor(DS.FocusFill)
                else if (active) SolidColor(Color(0x1FFFFFFF))
                else SolidColor(Color.Transparent)
            )
            .onFocusChanged { f = it.isFocused }
            .clickable(onClick = onClick)
            .padding(start = dpx(17)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MenuIcon(id, ink, Modifier.size(dpx(26)))
        if (expanded) {
            Spacer(Modifier.width(dpx(18)))
            Text(label, color = if (f) DS.FocusInk else Color.White, fontSize = spx(19),
                fontWeight = if (active || f) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

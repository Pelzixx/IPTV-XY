package com.ipvio.stream

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bildrate anpassen wie TiviMate: den Anzeigemodus des Fernsehers auf eine Bildwiederholrate umstellen, die zum Video passt
 * (24p → 24/48 Hz, 25p → 50 Hz, 30p → 60 Hz …). Beim Verlassen des Players gilt wieder der Standardmodus des Geräts.
 *
 * „Amlogic Fix“ (TiviMate: Auflösungskorrektur): Amlogic-Geräte melden Modi mit anderer Auflösung als passend; dann
 * springt der Fernseher z. B. von 4K auf 1080p. Mit dem Fix kommen nur Modi mit der aktuellen Auflösung in Frage.
 */
/**
 * Bildrate eines Streams schnell bestimmen (Wunsch des Nutzers, 2026-11-08): Live-TS-Streams geben sie meist nicht an.
 * Statt 3,5 s Bilder zu zählen, werden die Zeitstempel der ersten 16 Bilder ausgewertet (bei 25 fps nach gut 0,6 s).
 * Läuft im Wiedergabe-Thread; das Ergebnis kommt über [onFps] im Hauptthread an. [reset] bei jedem Senderwechsel.
 */
class FpsProbe : androidx.media3.exoplayer.video.VideoFrameMetadataListener {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val pts = LongArray(16)
    @Volatile private var n = 0
    @Volatile private var done = false
    var onFps: ((Int) -> Unit)? = null

    fun reset() { n = 0; done = false }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long, releaseTimeNs: Long, format: androidx.media3.common.Format, mediaFormat: android.media.MediaFormat?
    ) {
        if (done) return
        val f = if (format.frameRate > 1f) FpsLogic.snap(format.frameRate) else {
            if (n < pts.size) pts[n++] = presentationTimeUs
            if (n < pts.size) return
            FpsLogic.fromPts(pts)
        }
        done = true
        if (f > 0) main.post { onFps?.invoke(f) }
    }
}

/** Reine Rechenlogik (Unit-Tests). */
object FpsLogic {
    private val STD = intArrayOf(24, 25, 30, 48, 50, 60)

    /** Auf eine übliche Bildrate runden (23,976 → 24, 29,97 → 30), sonst ganzzahlig. */
    fun snap(f: Float): Int {
        val s = STD.minByOrNull { abs(it - f) } ?: return f.roundToInt()
        return if (abs(s - f) < 1.5f) s else f.roundToInt()
    }

    /** Bildrate aus Zeitstempeln (µs): Median der Abstände, unempfindlich gegen einzelne Sprünge. 0 = unklar. */
    fun fromPts(p: LongArray): Int {
        val d = (1 until p.size).map { p[it] - p[it - 1] }.filter { it in 1_000..200_000 }.sorted()
        if (d.size < 5) return 0
        return snap(1_000_000f / d[d.size / 2])
    }
}

object DisplayAfr {
    private var changed = false

    private fun display(a: Activity): Display? =
        if (Build.VERSION.SDK_INT >= 30) a.display else @Suppress("DEPRECATION") a.windowManager.defaultDisplay

    /** Passt die Bildwiederholrate [r] zu [fps]? (ganzzahliges Vielfaches, 23,976 ≈ 24 usw.) */
    fun fits(r: Float, fps: Float): Boolean {
        if (fps <= 1f) return false
        val k = (r / fps).roundToInt()
        return k in 1..4 && abs(r - fps * k) < 0.06f * k
    }

    /** Bester Modus: passende Rate, bei Gleichstand die niedrigste passende (24 vor 48), bevorzugt aktuelle Auflösung. */
    fun pick(modes: List<Display.Mode>, cur: Display.Mode, fps: Float, sameRes: Boolean): Display.Mode? {
        val cand = modes.filter { fits(it.refreshRate, fps) }
            .filter { !sameRes || (it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight) }
        if (cand.isEmpty()) return null
        val same = cand.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
        val pool = same.ifEmpty { cand.sortedByDescending { it.physicalWidth * it.physicalHeight } }
        return pool.minByOrNull { it.refreshRate }
    }

    fun apply(a: Activity, fps: Float) {
        if (Build.VERSION.SDK_INT < 23 || fps <= 1f) return
        val d = display(a) ?: return
        val cur = d.mode
        if (fits(cur.refreshRate, fps)) return
        val m = pick(d.supportedModes.toList(), cur, fps, Tm.b(Tm.PB_AFR_AMLOGIC, false)) ?: return
        val w = a.window ?: return
        w.attributes = w.attributes.also { it.preferredDisplayModeId = m.modeId }
        changed = true
    }

    fun restore(a: Activity?) {
        if (!changed || a == null || Build.VERSION.SDK_INT < 23) return
        val w = a.window ?: return
        w.attributes = w.attributes.also { it.preferredDisplayModeId = 0 }
        changed = false
    }
}

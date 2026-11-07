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

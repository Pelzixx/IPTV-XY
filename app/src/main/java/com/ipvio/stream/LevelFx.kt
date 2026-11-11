package com.ipvio.stream

import android.media.audiofx.DynamicsProcessing
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.WeakHashMap

/**
 * Lautstärke-Ausgleich (Wunsch des Nutzers, 2026-11-11; Wiedergabe > „Lautstärke-Ausgleich“, Standard aus):
 * Kompressor + Begrenzer auf der Tonspur des Players (Android 9+). Leise Stellen werden lauter, laute (Werbung,
 * Explosionen) gedämpft – angenehm nachts. Bei Audio-Passthrough (Ton geht unbearbeitet an den Verstärker) ohne Wirkung.
 */
object LevelFx {
    private val active = WeakHashMap<Player, DynamicsProcessing>()

    fun attach(p: ExoPlayer) {
        if (android.os.Build.VERSION.SDK_INT < 28) return
        p.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { apply(p, audioSessionId) }
        })
        apply(p, p.audioSessionId)
    }

    /** Einstellung geändert: auf den laufenden Player anwenden. */
    fun refresh(p: ExoPlayer?) { if (p != null && android.os.Build.VERSION.SDK_INT >= 28) apply(p, p.audioSessionId) }

    private fun apply(p: Player, session: Int) {
        if (android.os.Build.VERSION.SDK_INT < 28) return
        active.remove(p)?.let { runCatching { it.release() } }
        if (!Tm.b(Tm.PB_LEVEL, false) || session == C.AUDIO_SESSION_ID_UNSET || session == 0) return
        runCatching {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0, true, 1, false, 0, true
            ).build()
            // Ein Band über alles: ab -32 dB mit 3:1 zusammendrücken, danach um 8 dB anheben
            val band = DynamicsProcessing.MbcBand(true, 20000f, 8f, 250f, 3f, -32f, 0f, -90f, 1f, 0f, 8f)
            for (ch in 0 until 2) cfg.setMbcBandByChannelIndex(ch, 0, band)
            // Begrenzer: nichts über -1 dB (keine Übersteuerung nach dem Anheben)
            cfg.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
            val dp = DynamicsProcessing(0, session, cfg)
            dp.enabled = true
            active[p] = dp
        }.onFailure { Diag.report("Lautstärke-Ausgleich: " + (it.message ?: it.javaClass.simpleName)) }
    }
}

package com.ipvio.stream

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Ein Player-Aufbau für Vorschau und Vollbild. So kann der Vollbild-Player den Player der Vorschau einfach übernehmen
 * (TiviMate: der Sender läuft in der Vorschau schon mit Ton und wird beim OK ohne Neuladen groß).
 */
object XyPlayer {
    class Built(val player: ExoPlayer, val http: OkHttpDataSource.Factory)

    fun build(ctx: Context, itemId: String?): Built {
        // Über den gemeinsamen Client (sichere DNS-Abfrage): folgt Weiterleitungen auch von http nach https,
        // gibt nach 10 s Verbindungsaufbau bzw. 15 s Lesezeit auf
        val http = OkHttpDataSource.Factory(Net.player)
            .setUserAgent(TmNet.userAgentFor(itemId))
        // Wiedergabe (TiviMate): getunnelte Wiedergabe, Surround-Tonspur bevorzugen (aus = höchstens Stereo, wenn vorhanden)
        val ts = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(ctx).apply {
            setParameters(buildUponParameters().setTunnelingEnabled(Tm.b(Tm.PB_TUNNEL, false))
                .apply { if (!Tm.b(Tm.PB_SURROUND, false)) setMaxAudioChannelCount(2) })
        }
        val p = ExoPlayer.Builder(ctx, SoftAudio.factory(ctx))
            .setTrackSelector(ts)
            .setLoadControl(Mem.loadControl(ctx))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(SmbAwareFactory(DefaultDataSource.Factory(ctx, http))))
            .build()
            .also { LevelFx.attach(it) }
            .also {
                // Bildrate: nur wechseln, wenn der Fernseher das nahtlos kann (kein Aufblitzen); abschaltbar unter Einstellungen > Player
                it.videoChangeFrameRateStrategy = if (AppSettings.autoFps && Tm.afrMode() == 0) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            }
        return Built(p, http)
    }
}

package com.ipvio.stream

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * Software-Tondecoder (FFmpeg): spielt Tonformate wie AC3, E-AC3, DTS oder TrueHD auch dann, wenn das Gerät sie nicht selbst
 * decodieren kann – typisch mit Bluetooth-Kopfhörern (AirPods), wo es keinen Durchreich-Ausgang an den Receiver gibt.
 * Bleibt wie bisher, wenn die Bibliothek fehlt oder nicht zur Media3-Version passt: der Test erzeugt einmal probeweise den
 * Decoder, bevor er eingeschaltet wird, und fängt jeden Fehler ab (kein Absturz beim Abspielen).
 *
 * TiviMate-Einstellungen (Wiedergabe): Audio-/Video-Decoder Hardware oder Software, Audio-Passthrough.
 */
object SoftAudio {
    private var checked = false
    private var ok = false

    fun available(ctx: Context): Boolean {
        if (checked) return ok
        checked = true
        ok = try {
            val c = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
                .getConstructor(Handler::class.java, AudioRendererEventListener::class.java, AudioSink::class.java)
            c.newInstance(null, null, DefaultAudioSink.Builder(ctx.applicationContext).build())
            true
        } catch (t: Throwable) {
            false
        }
        return ok
    }

    @Suppress("DEPRECATION")
    fun factory(ctx: Context): DefaultRenderersFactory {
        Tm.init(ctx)
        val audioSoft = Tm.i(Tm.PB_AUDIO_DEC, 0) == 1
        val videoSoft = Tm.i(Tm.PB_VIDEO_DEC, 0) == 1
        val passthrough = Tm.b(Tm.PB_PASSTHROUGH, false)
        AudioOffset.init()
        val f = object : DefaultRenderersFactory(ctx) {
            // Passthrough aus: Ton immer selbst decodieren (PCM) statt ihn roh an Receiver/Soundbar zu geben
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
                val sink = if (passthrough) super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                else DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                return sink?.let { AudioOffset.wrap(it) }
            }
        }
        f.setExtensionRendererMode(
            when {
                !available(ctx) -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                audioSoft -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
        )
        // Video-Decoder „Software“: Software-Decoder des Geräts zuerst (z. B. bei Bildfehlern des Hardware-Decoders)
        if (videoSoft) f.setMediaCodecSelector(MediaCodecSelector { mime, secure, tunneling ->
            MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { if (it.softwareOnly) 0 else 1 }
        })
        f.setEnableDecoderFallback(true)
        return f
    }
}

/**
 * Audioversatz (TiviMate: Lippensynchronität). Das Bild richtet sich nach der Audio-Uhr; wird sie um den Versatz
 * verschoben, kommt der Ton entsprechend früher oder später zum Bild. Positiv = Ton später.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioOffset {
    /** Versatz in Millisekunden (gilt sofort, auch während der Wiedergabe). */
    var ms by androidx.compose.runtime.mutableIntStateOf(0)

    fun init() { ms = Tm.i(Tm.PB_AUDIO_DELAY, 0) }
    fun set(c: Context, v: Int) { ms = v.coerceIn(-2000, 2000); Tm.set(c, Tm.PB_AUDIO_DELAY, ms) }

    fun wrap(sink: AudioSink): AudioSink = object : androidx.media3.exoplayer.audio.ForwardingAudioSink(sink) {
        override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
            val p = super.getCurrentPositionUs(sourceEnded)
            if (p == AudioSink.CURRENT_POSITION_NOT_SET || ms == 0) return p
            return (p + ms * 1000L).coerceAtLeast(0L)
        }
    }
}

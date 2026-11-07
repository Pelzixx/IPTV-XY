package com.ipvio.stream

import java.io.File

/**
 * Optik > Logos (TiviMate): welches Senderlogo angezeigt wird – aus der Playlist, aus dem EPG oder aus einem eigenen
 * Ordner (lokaler Pfad oder Webadresse, Dateiname = Sendername + .png; „ungenau“ = vereinheitlichter Name).
 */
object Logos {
    val PRIO_NAMES = listOf("Logos aus der Playlist bevorzugen", "Logos aus dem EPG bevorzugen", "Logos aus dem Ordner bevorzugen")

    fun of(item: Item?): String? {
        if (item == null) return null
        val pl = item.logo?.takeIf { it.isNotBlank() }
        val epg = Xmltv.iconFor(item)
        val dir = folder(item)
        return when (Tm.i(Tm.LOGO_PRIO, 0)) {
            1 -> epg ?: pl ?: dir
            2 -> dir ?: pl ?: epg
            else -> pl ?: epg ?: dir
        }
    }

    private fun folder(item: Item): String? {
        val base = Tm.s(Tm.LOGO_DIR).trim().trimEnd('/')
        if (base.isEmpty()) return null
        val title = ChannelEdit.title(item)
        val name = if (Tm.b(Tm.LOGO_FUZZY, false)) EpgNames.norm(title) else title
        if (base.startsWith("http://", true) || base.startsWith("https://", true)) return "$base/" + android.net.Uri.encode(name) + ".png"
        val f = File(base, "$name.png")
        return if (runCatching { f.exists() }.getOrDefault(false)) "file://" + f.absolutePath else null
    }
}

package com.ipvio.stream

/**
 * Zuordnung von Sendern zum XMLTV-Programm. Mit EPG-Kennung (tvg-id) über diese, sonst über den Namen –
 * wie TiviMate: viele Xtream-Listen liefern keine Kennung. Der Name wird vereinheitlicht
 * („DE| DAS ERSTE HD“ -> „daserste“), ebenso die Anzeigenamen in der EPG-Datei.
 */
object EpgNames {
    const val PFX = "n:"
    private val BRACKETS = Regex("""\[[^\]]*]|\([^)]*\)""")
    private val PREFIX = Regex("""^[a-z]{2,3}\s*[|:]\s*""")
    private val TAGS = Regex("""\b(uhd|fhd|hd|sd|4k|8k|hevc|h265|h264|raw|backup|multi|\d{3,4}p|\d{2}fps)\b""")

    fun norm(s: String): String {
        var t = s.lowercase().replace("&amp;", "&")
        t = BRACKETS.replace(t, " ").trim()
        t = PREFIX.replace(t, "")
        t = TAGS.replace(t, " ")
        return t.replace("+", "plus").filter { it.isLetterOrDigit() }
    }

    /** Schlüssel eines Senders: Kennung (klein) oder „n:“ + vereinheitlichter Name; null, wenn beides fehlt. */
    fun key(item: Item): String? =
        item.tvgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: norm(item.title).takeIf { it.length >= 2 }?.let { PFX + it }

    /** Alle Schlüssel eines Senders in Reihenfolge: zuerst die Kennung, dann der Name (falls die Kennung im EPG fehlt). */
    fun keys(item: Item): List<String> {
        val id = item.tvgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val n = norm(item.title).takeIf { it.length >= 2 }?.let { PFX + it }
        return listOfNotNull(id, n).distinct()
    }
}

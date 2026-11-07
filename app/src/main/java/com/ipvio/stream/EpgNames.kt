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
        // Von Hand zugewiesener EPG-Kanal zuerst
        val own = runCatching { EpgAssign.get(item.id) }.getOrNull()
        return listOfNotNull(own, id, n).distinct()
    }
}

/** EPG je Kanal zuweisen (TiviMate: Kanaloptionen > „EPG zuweisen“). Hat Vorrang vor tvg-id und Name. */
object EpgAssign {
    private const val KEY = "epg_assign"
    @Volatile private var raw: String? = null
    @Volatile private var map: Map<String, String> = emptyMap()

    private fun all(): Map<String, String> {
        val r = Tm.s(KEY)
        if (r !== raw) {
            map = r.split('\n').mapNotNull { l -> val i = l.indexOf('\t'); if (i > 0) l.substring(0, i) to l.substring(i + 1) else null }.toMap()
            raw = r
        }
        return map
    }

    fun get(itemId: String): String? = all()[itemId]

    fun set(c: android.content.Context, itemId: String, epgId: String?) {
        val m = all().toMutableMap()
        if (epgId.isNullOrBlank()) m.remove(itemId) else m[itemId] = epgId.lowercase()
        Tm.set(c, KEY, m.entries.joinToString("\n") { it.key + "\t" + it.value })
    }
}

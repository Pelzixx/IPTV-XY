package com.ipvio.stream

/** Ein fest eingebauter Xtream-Server: angezeigter Name und Adresse (z. B. http://server.tld:8080). */
data class PresetServer(val name: String, val url: String)

/**
 * Fest eingebaute Server und EPG-Quellen. Die Werte kommen beim Bauen aus den GitHub-Secrets
 * PRESET_SERVERS und PRESET_EPG (oder aus secrets.properties), stehen also nicht im öffentlichen Code.
 * Ohne Werte verhält sich die App wie bisher (freie Eingabe von M3U-Link oder Xtream-Server).
 */
object Presets {
    val servers: List<PresetServer> by lazy { PresetLogic.parseServers(BuildConfig.PRESET_SERVERS) }
    val epg: List<String> by lazy { PresetLogic.parseUrls(BuildConfig.PRESET_EPG) }
}

/** Reine Logik ohne Android (Unit-Tests). */
object PresetLogic {
    private val SEP = Regex("""[;\n\r]+""")

    /**
     * Einträge getrennt durch ";" oder Zeilenumbruch, jeweils "Name|Adresse" oder nur "Adresse".
     * Ohne Namen heißt der Eintrag "Server 1", "Server 2" …; Einträge ohne gültige Adresse werden übergangen.
     */
    fun parseServers(raw: String): List<PresetServer> {
        val out = ArrayList<PresetServer>()
        for (part in raw.split(SEP)) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val name = if ('|' in p) p.substringBefore('|').trim() else ""
            val url = normalize(if ('|' in p) p.substringAfter('|') else p) ?: continue
            out.add(PresetServer(name.ifEmpty { "Server " + (out.size + 1) }, url))
        }
        return out
    }

    /** Adressen getrennt durch ";" oder Zeilenumbruch; nur http(s), doppelte nur einmal. */
    fun parseUrls(raw: String): List<String> =
        raw.split(SEP).map { it.trim() }.filter { it.startsWith("http://", true) || it.startsWith("https://", true) }.distinct()

    /** Server-Adresse vereinheitlichen: ohne Schema wird http:// ergänzt, ohne Schrägstrich am Ende. */
    fun normalize(host: String): String? {
        var h = host.trim().trimEnd('/')
        if (h.isEmpty() || h.any { it.isWhitespace() }) return null
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        return h
    }

    /** Index des fest eingebauten Servers, zu dem [host] gehört, sonst -1 (z. B. um eine gespeicherte Eingabe wiederzuerkennen). */
    fun indexOf(host: String, servers: List<PresetServer>): Int {
        val h = normalize(host)?.lowercase() ?: return -1
        return servers.indexOfFirst { it.url.lowercase() == h }
    }

    /** Eigene EPG-Adressen werden zeilenweise gespeichert. */
    fun joinUrls(urls: List<String>): String = urls.joinToString("\n")
}

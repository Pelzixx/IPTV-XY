package com.ipvio.stream

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Ein fest eingebauter Xtream-Server: angezeigter Name, Adresse (z. B. http://server.tld:8080), eigene EPG-Quellen. */
data class PresetServer(val name: String, val url: String, val epg: List<String> = emptyList())

/**
 * Fest eingebaute Server und EPG-Quellen. Die Werte kommen beim Bauen aus den GitHub-Secrets
 * PRESET_SERVERS und PRESET_EPG (oder aus secrets.properties), stehen also nicht im öffentlichen Code.
 * Ohne Werte verhält sich die App wie bisher (freie Eingabe von M3U-Link oder Xtream-Server).
 */
object Presets {
    private val builtServers: List<PresetServer> by lazy { PresetLogic.parseServers(BuildConfig.PRESET_SERVERS) }
    private val builtEpg: List<String> by lazy { PresetLogic.parseUrls(BuildConfig.PRESET_EPG) }
    /** Zuletzt geladene Fernkonfiguration (config.json, Adresse aus dem Secret PRESET_CONFIG_URL) oder null. */
    private var remote by mutableStateOf<RemoteConfig?>(null)

    /** Server: aus der Fernkonfiguration, sonst fest eingebaut. */
    val servers: List<PresetServer> get() = remote?.servers?.takeIf { it.isNotEmpty() } ?: builtServers
    /** EPG-Quellen: aus der Fernkonfiguration, sonst fest eingebaut. */
    val epg: List<String> get() = remote?.epg?.takeIf { it.isNotEmpty() } ?: builtEpg
    /** Mitteilung, Diagnose- und Sicherungsadresse aus der Panel-Konfiguration (null = nicht eingerichtet). */
    val news: String? get() = remote?.news
    val diagUrl: String? get() = remote?.diag
    val backupUrl: String? get() = remote?.backup

    private fun prefs(c: Context) = c.getSharedPreferences("ipvio_config", Context.MODE_PRIVATE)

    /** Beim Start: zuletzt geladene Fernkonfiguration sofort verwenden (ohne Netz). */
    fun init(c: Context) {
        if (remote == null) remote = prefs(c).getString("remote", null)?.let { PresetLogic.parseConfig(it) }
    }

    /**
     * Fernkonfiguration neu laden. Haben sich Server-Adressen geändert, werden gespeicherte Playlists dieser Server
     * auf die neue Adresse umgestellt (Benutzername und Passwort bleiben); die aktive wird neu geladen.
     */
    suspend fun refresh(c: Context) {
        val url = BuildConfig.PRESET_CONFIG_URL
        if (url.isBlank()) return
        val body = withContext(Dispatchers.IO) {
            runCatching {
                Net.base.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute()
                    .use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull()
        } ?: return
        val cfg = PresetLogic.parseConfig(body) ?: return
        val old = servers
        val oldEpg = epg
        prefs(c).edit().putString("remote", body).apply()
        remote = cfg
        PlaylistStore.retarget(c, old, servers)
        // Feste EPG-Quellen aus dem Panel kamen erst nach dem Start des EPG an (z. B. erste Installation): jetzt mitladen
        if (epg != oldEpg && PlaylistStore.hasData) withContext(Dispatchers.Main) {
            Xmltv.start(c, (PlaylistStore.epgUrls + PlaylistStore.extraEpg).distinct(), PlaylistStore.channels)
        }
    }
}

/** Inhalt von config.json: {"servers":[{"name":"XY TV","url":"http://…:8080"}], "epg":["https://…/epg.xml.gz"]} */
/** [news] = Mitteilung für die Startseite (JSON wie news.json), [diag]/[backup] = Adressen der Panel-Endpunkte (docs/panel-schnittstelle.md). */
data class RemoteConfig(val servers: List<PresetServer>, val epg: List<String>, val news: String? = null, val diag: String? = null, val backup: String? = null)

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

    /** config.json lesen; null, wenn die Datei kaputt ist (dann bleibt die bisherige Konfiguration). */
    fun parseConfig(body: String): RemoteConfig? = runCatching {
        val o = org.json.JSONObject(body)
        val servers = ArrayList<PresetServer>()
        o.optJSONArray("servers")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                val url = normalize(s.optString("url", "")) ?: continue
                val sEpg = ArrayList<String>()
                s.optJSONArray("epg")?.let { e -> for (j in 0 until e.length()) sEpg.add(e.optString(j, "")) }
                servers.add(PresetServer(s.optString("name", "").trim().ifEmpty { "Server " + (servers.size + 1) }, url, parseUrls(sEpg.joinToString("\n"))))
            }
        }
        val epg = ArrayList<String>()
        o.optJSONArray("epg")?.let { a -> for (i in 0 until a.length()) epg.add(a.optString(i, "")) }
        fun url(k: String) = o.optString(k, "").trim().takeIf { it.startsWith("https://") || it.startsWith("http://") }
        RemoteConfig(servers, parseUrls(epg.joinToString("\n")), o.optJSONObject("news")?.toString(), url("diag"), url("backup"))
    }.getOrNull()

    /**
     * Neue Adresse einer gespeicherten Xtream-Playlist, wenn ihr Server umgezogen ist, sonst null.
     * Zuordnung über den Namen der Playlist (= Name des Servers) oder über die bisherige Server-Adresse.
     */
    fun retarget(url: String, name: String, old: List<PresetServer>, new: List<PresetServer>): String? {
        val i = url.indexOf("/get.php")
        if (i <= 0) return null
        val host = normalize(url.substring(0, i))?.lowercase() ?: return null
        val byName = new.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        val byOld = old.firstOrNull { it.url.lowercase() == host }?.let { o -> new.firstOrNull { it.name.equals(o.name, ignoreCase = true) } }
        val target = byName ?: byOld ?: return null
        if (target.url.lowercase() == host) return null
        return target.url + url.substring(i)
    }

    /** Eigene EPG-Adressen werden zeilenweise gespeichert. */
    fun joinUrls(urls: List<String>): String = urls.joinToString("\n")
}

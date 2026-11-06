/*
 * XY IPTV – fest hinterlegte Server (XY TV, XY Backup, XY Radio) als Vorlage beim Hinzufügen eines Anbieters.
 * Teil von XY IPTV, basierend auf OpenTV. Lizenz: GNU GPL v3.0 oder später.
 */
package app.opentv.xy

import android.content.Context
import app.opentv.BuildConfig
import app.opentv.core.ServiceLocator
import app.opentv.data.model.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Ein fest hinterlegter Server: Name, Adresse (wird nicht angezeigt) und optional eine EPG-Adresse. */
data class XyServer(val name: String, val url: String, val epg: String? = null)

object XyPresets {
    private val built: List<XyServer> by lazy { XyPresetLogic.parseServers(BuildConfig.PRESET_SERVERS) }
    private val _servers = MutableStateFlow<List<XyServer>>(emptyList())
    /** Aktuelle Server: aus der Fernkonfiguration (Gist), sonst aus dem Secret PRESET_SERVERS. */
    val servers: StateFlow<List<XyServer>> = _servers

    private fun prefs(c: Context) = c.getSharedPreferences("xy_config", Context.MODE_PRIVATE)

    /** Beim Start: zuletzt geladene Fernkonfiguration sofort, sonst die fest eingebauten Server. */
    fun init(c: Context) {
        val saved = prefs(c).getString("remote", null)?.let { XyPresetLogic.parseConfig(it) }
        _servers.value = saved?.takeIf { it.isNotEmpty() } ?: built
    }

    /**
     * Fernkonfiguration (config.json, Adresse im Secret PRESET_CONFIG_URL) laden. Hat ein Server eine neue Adresse,
     * werden gespeicherte Anbieter mit demselben Namen umgestellt (Benutzername und Passwort bleiben).
     */
    suspend fun refresh(c: Context) {
        val url = BuildConfig.PRESET_CONFIG_URL
        if (url.isBlank()) return
        val graph = ServiceLocator.get(c)
        val body = withContext(Dispatchers.IO) {
            runCatching {
                graph.httpClient.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute()
                    .use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull()
        } ?: return
        val list = XyPresetLogic.parseConfig(body)?.takeIf { it.isNotEmpty() } ?: return
        prefs(c).edit().putString("remote", body).apply()
        _servers.value = list
        runCatching {
            for (s in graph.sourceRepository.enabled()) {
                if (s.kind != SourceKind.XTREAM) continue
                val target = list.firstOrNull { it.name.equals(s.name.trim(), ignoreCase = true) } ?: continue
                if (XyPresetLogic.sameHost(target.url, s.url)) continue
                graph.sourceRepository.save(s.copy(url = target.url))
            }
        }
    }
}

/** Reine Logik ohne Android (Unit-Tests). */
object XyPresetLogic {
    private val SEP = Regex("""[;\n\r]+""")

    /** „Name|Adresse;Name|Adresse“ (Secret PRESET_SERVERS). */
    fun parseServers(raw: String): List<XyServer> {
        val out = ArrayList<XyServer>()
        for (part in raw.split(SEP)) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val name = if ('|' in p) p.substringBefore('|').trim() else ""
            val url = normalize(if ('|' in p) p.substringAfter('|') else p) ?: continue
            out.add(XyServer(name.ifEmpty { "Server " + (out.size + 1) }, url))
        }
        return out
    }

    /** config.json: {"servers":[{"name":"XY TV","url":"http://…","epg":["https://…"]}]} */
    fun parseConfig(body: String): List<XyServer>? = runCatching {
        val a = (kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject)["servers"]
            as? kotlinx.serialization.json.JsonArray ?: return null
        val out = ArrayList<XyServer>()
        for (e in a) {
            val o = e as? kotlinx.serialization.json.JsonObject ?: continue
            fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
            val url = normalize(str("url")) ?: continue
            val epg = ((o["epg"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.trim()?.takeIf { it.startsWith("http") }
            out.add(XyServer(str("name").ifEmpty { "Server " + (out.size + 1) }, url, epg))
        }
        out
    }.getOrNull()

    /** Adresse vereinheitlichen: ohne Schema http://, ohne Schrägstrich am Ende. */
    fun normalize(host: String): String? {
        var h = host.trim().trimEnd('/')
        if (h.isEmpty() || h.any { it.isWhitespace() }) return null
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        return h
    }

    fun sameHost(a: String, b: String): Boolean = normalize(a)?.lowercase() == normalize(b)?.lowercase()
}

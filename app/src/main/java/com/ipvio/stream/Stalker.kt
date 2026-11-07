package com.ipvio.stream

import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Stalker-/Ministra-Portal mit MAC-Adresse (TiviMate: Playlist-Typ „Stalker Portal“), Live-TV.
 *
 * Wiedergabeliste: `stalker://<portal-adresse>?mac=<MAC>` – beim Laden werden Genres und Sender geholt und als M3U
 * abgelegt. Jeder Sender hat die Adresse `stalker-play://<portal>?mac=…&cmd=…`; erst beim Abspielen liefert das Portal
 * mit `create_link` die echte Stream-Adresse (die Links verfallen).
 */
object Stalker {
    const val LIST = "stalker://"
    const val PLAY = "stalker-play://"
    private const val UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    private val tokens = ConcurrentHashMap<String, String>()
    private val client by lazy { Net.withTimeouts(15, 30) }

    fun isList(u: String) = u.startsWith(LIST, true)
    fun isPlay(u: String) = u.startsWith(PLAY, true)

    /** Portal-Adresse und MAC aus einer stalker://- oder stalker-play://-Adresse. */
    data class Ref(val portal: String, val mac: String, val cmd: String?)

    fun parse(u: String): Ref? {
        val rest = u.substringAfter("://")
        val host = rest.substringBefore('?')
        val q = rest.substringAfter('?', "").split('&').mapNotNull { p ->
            val i = p.indexOf('='); if (i <= 0) null else p.substring(0, i) to URLDecoder.decode(p.substring(i + 1), "UTF-8")
        }.toMap()
        val mac = q["mac"] ?: return null
        val portal = (if (host.startsWith("http", true)) host else "http://$host").let { URLDecoder.decode(it, "UTF-8") }
        return Ref(portal, mac.uppercase(), q["cmd"])
    }

    fun listUrl(portal: String, mac: String): String =
        LIST + URLEncoder.encode(portal.trim().trimEnd('/'), "UTF-8") + "?mac=" + URLEncoder.encode(mac.trim().uppercase(), "UTF-8")

    /** Adresse der Schnittstelle: …/portal.php bzw. …/stalker_portal/server/load.php. */
    fun api(portal: String): String {
        val p = portal.trimEnd('/')
        return when {
            p.endsWith(".php", true) -> p
            p.contains("stalker_portal", true) -> p.substringBefore("/stalker_portal") + "/stalker_portal/server/load.php"
            p.endsWith("/c", true) -> p.removeSuffix("/c").removeSuffix("/C") + "/portal.php"
            else -> "$p/portal.php"
        }
    }

    private fun call(ref: Ref, query: String, token: String?): JSONObject {
        val b = Request.Builder().url(api(ref.portal) + "?" + query + "&JsHttpRequest=1-xml")
            .header("User-Agent", UA)
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", "mac=" + URLEncoder.encode(ref.mac, "UTF-8") + "; stb_lang=de; timezone=Europe%2FBerlin")
            .header("Referer", ref.portal.trimEnd('/') + "/c/")
        if (token != null) b.header("Authorization", "Bearer $token")
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Portal: HTTP " + r.code)
            val body = r.body?.string().orEmpty()
            return JSONObject(body)
        }
    }

    private fun token(ref: Ref, fresh: Boolean = false): String {
        val key = ref.portal + "|" + ref.mac
        if (!fresh) tokens[key]?.let { return it }
        val t = call(ref, "type=stb&action=handshake&token=", null).optJSONObject("js")?.optString("token").orEmpty()
        if (t.isEmpty()) throw java.io.IOException("Portal: keine Anmeldung möglich (MAC freigeschaltet?)")
        runCatching { call(ref, "type=stb&action=get_profile", t) }
        tokens[key] = t
        return t
    }

    /** Senderliste als M3U (Gruppen = Genres des Portals). */
    fun m3u(listUrl: String, out: java.io.Writer, onCount: (Int) -> Unit): Int {
        val ref = parse(listUrl) ?: throw java.io.IOException("Ungültige Portal-Adresse")
        val t = token(ref, fresh = true)
        val genres = HashMap<String, String>()
        runCatching {
            val a = call(ref, "type=itv&action=get_genres", t).optJSONArray("js")
            if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let { g -> genres[g.optString("id")] = g.optString("title") }
        }
        val data = call(ref, "type=itv&action=get_all_channels", t).optJSONObject("js")?.optJSONArray("data")
            ?: throw java.io.IOException("Portal: keine Sender erhalten")
        out.write("#EXTM3U\n")
        var n = 0
        for (i in 0 until data.length()) {
            val c = data.optJSONObject(i) ?: continue
            val name = c.optString("name").trim().ifEmpty { continue }
            val cmd = c.optString("cmd").trim().ifEmpty { continue }
            val group = genres[c.optString("tv_genre_id")]?.takeIf { it.isNotBlank() } ?: "Portal"
            val logo = c.optString("logo").takeIf { it.startsWith("http") } ?: ""
            val play = PLAY + URLEncoder.encode(ref.portal, "UTF-8") + "?mac=" + URLEncoder.encode(ref.mac, "UTF-8") + "&cmd=" + URLEncoder.encode(cmd, "UTF-8")
            out.write("#EXTINF:-1 tvg-name=\"" + name.replace("\"", "'") + "\" tvg-logo=\"" + logo + "\" group-title=\"" + group.replace("\"", "'") + "\"," + name + "\n" + play + "\n")
            n++
            if (n % 200 == 0) onCount(n)
        }
        out.flush()
        return n
    }

    /** Echte Stream-Adresse für einen Portal-Sender (create_link); einmal neu anmelden, wenn das Token abgelaufen ist. */
    fun resolve(u: String): String {
        val ref = parse(u) ?: return u
        val cmd = ref.cmd ?: return u
        fun link(t: String): String {
            val js = call(ref, "type=itv&action=create_link&cmd=" + URLEncoder.encode(cmd, "UTF-8") + "&series=&forced_storage=undefined&disable_ad=0&download=0", t).optJSONObject("js")
            return js?.optString("cmd").orEmpty().trim().removePrefix("ffmpeg ").removePrefix("auto ").trim()
        }
        val first = runCatching { link(token(ref)) }.getOrDefault("")
        if (first.startsWith("http")) return first
        val again = link(token(ref, fresh = true))
        if (again.startsWith("http")) return again
        // Manche Portale liefern die Adresse direkt im cmd
        return cmd.removePrefix("ffmpeg ").trim().takeIf { it.startsWith("http") } ?: throw java.io.IOException("Portal: kein Stream-Link")
    }

    /** Für den Player: Portal-Adressen auflösen, andere unverändert. */
    fun playable(u: String): String = if (isPlay(u)) runCatching { resolve(u) }.getOrDefault(u) else u
}

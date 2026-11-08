package com.ipvio.stream

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * API keys for the optional film information services. No key is shipped with the app: every user enters their own
 * (Settings > API keys). Developers can additionally put keys into "secrets.properties" (not in Git); those are only
 * used as a fallback when nothing was entered in the app.
 *
 *   TMDB   https://www.themoviedb.org/settings/api  – posters, descriptions, cast, trailers, automatic genres
 *   OMDb   https://www.omdbapi.com/apikey.aspx      – IMDb and Rotten Tomatoes ratings
 *
 * Intro/credits times come from TheIntroDB, which needs no key for reading.
 * Without keys the app works normally and only hides the extra information.
 */
object Config {
    /** Film-Extras (TMDB/OMDb: Bewertungen, Trailer, Darsteller, Top 10 …). Am 2026-11-06 aus („schlanke App“). */
    // Wieder an (Wunsch des Nutzers, 2026-11-08): fester TMDB-Schlüssel aus dem GitHub-Secret TMDB_KEY (OMDb optional: OMDB_KEY)
    const val EXTRAS = true
    /** Massenabgleich aller Filme/Serien mit TMDB im Hintergrund (automatische Genres/Länder): aus, bremst bei großen Listen. */
    const val BULK = false
    @Volatile var TMDB_KEY: String = if (EXTRAS) clean(BuildConfig.TMDB_KEY) else ""
        private set
    @Volatile var OMDB_KEY: String = if (EXTRAS) clean(BuildConfig.OMDB_KEY) else ""
        private set
    /** true, wenn der Schlüssel in der App eingetragen wurde (nicht aus secrets.properties). */
    var tmdbOwn by androidx.compose.runtime.mutableStateOf(false)
        private set
    var omdbOwn by androidx.compose.runtime.mutableStateOf(false)
        private set
    private var inited = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ipvio_keys", Context.MODE_PRIVATE)

    /** Nur Buchstaben und Ziffern (ein Schlüssel darf nie Sonderzeichen in Adressen einschleusen). */
    fun clean(s: String): String = s.trim().filter { it.isLetterOrDigit() }.take(64)

    @Synchronized
    fun init(c: Context) {
        if (inited) return
        inited = true
        if (!EXTRAS) return
        val p = prefs(c)
        val t = clean(p.getString("tmdb", "").orEmpty())
        val o = clean(p.getString("omdb", "").orEmpty())
        if (t.isNotEmpty()) { TMDB_KEY = t; tmdbOwn = true }
        if (o.isNotEmpty()) { OMDB_KEY = o; omdbOwn = true }
    }

    /** Speichert die eingetragenen Schlüssel; leer = Schlüssel entfernen (dann gilt ggf. wieder secrets.properties). */
    fun save(c: Context, tmdb: String, omdb: String) {
        if (!EXTRAS) return
        val t = clean(tmdb); val o = clean(omdb)
        prefs(c).edit().putString("tmdb", t).putString("omdb", o).apply()
        TMDB_KEY = t.ifEmpty { clean(BuildConfig.TMDB_KEY) }; tmdbOwn = t.isNotEmpty()
        OMDB_KEY = o.ifEmpty { clean(BuildConfig.OMDB_KEY) }; omdbOwn = o.isNotEmpty()
        inited = true
    }

    /** Eingetragener Schlüssel (für das Eingabefeld); Schlüssel aus secrets.properties werden nicht angezeigt. */
    fun ownTmdb(c: Context): String = clean(prefs(c).getString("tmdb", "").orEmpty())
    fun ownOmdb(c: Context): String = clean(prefs(c).getString("omdb", "").orEmpty())
}

/** Prüft einen Schlüssel mit einer kleinen Abfrage beim Dienst. */
object KeyCheck {
    /** null = in Ordnung, sonst Fehlertext (deutsch, wird übersetzt). */
    suspend fun tmdb(key: String): String? = check("https://api.themoviedb.org/3/configuration?api_key=" + Config.clean(key)) { code, body ->
        if (code == 200) null else if (code == 401) "Schlüssel ungültig" else "Fehler $code"
    }

    suspend fun omdb(key: String): String? = check("https://www.omdbapi.com/?apikey=" + Config.clean(key) + "&i=tt0111161") { code, body ->
        if (code == 200 && body.contains("\"Response\":\"True\"")) null
        else if (code == 401 || body.contains("Invalid API key")) "Schlüssel ungültig" else "Fehler $code"
    }

    private suspend fun check(url: String, eval: (Int, String) -> String?): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Net.base.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r -> eval(r.code, r.body?.string().orEmpty()) }
            } catch (e: java.net.UnknownHostException) {
                "Server nicht gefunden (DNS)"
            } catch (e: javax.net.ssl.SSLException) {
                "Sichere Verbindung fehlgeschlagen. Datum und Uhrzeit des Geräts prüfen"
            } catch (e: java.net.SocketTimeoutException) {
                "Zeitüberschreitung, Server antwortet nicht"
            } catch (e: Exception) {
                "Keine Verbindung: " + e.javaClass.simpleName
            }
        }
}

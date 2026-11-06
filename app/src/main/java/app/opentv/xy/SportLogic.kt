/*
 * XY IPTV – Sport-Guide: Sportsendungen aus dem TV-Programm erkennen und eigene Top-Spiele (sports.json) lesen.
 * Eigene Umsetzung für XY IPTV (basierend auf OpenTV). Lizenz: GNU GPL v3.0 oder später.
 */
package app.opentv.xy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.OffsetDateTime

/** Sportarten für die Filter oben. */
enum class SportKind { FOOTBALL, BASKETBALL, MOTORSPORT, TENNIS, ICE_HOCKEY, AMERICAN_FOOTBALL, COMBAT, OTHER }

/** Ein Sport-Ereignis: aus dem TV-Programm (mehrere Sender zusammengefasst) oder aus der eigenen Liste. */
data class SportEvent(
    val kind: SportKind,
    val league: String?,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    /** Guide-Kennungen der Sender (aus dem TV-Programm) … */
    val epgIds: List<String> = emptyList(),
    /** … oder Sendernamen (eigene Liste). */
    val channelNames: List<String> = emptyList(),
    val home: String? = null,
    val away: String? = null,
    val description: String? = null,
    /** Aus der eigenen Liste (Top-Spiel). */
    val featured: Boolean = false,
)

/** Reine Logik ohne Android (Unit-Tests). */
object SportLogic {
    private class Rule(val kind: SportKind, val words: List<String>, val league: String? = null)

    // Ligen zuerst (liefern auch den Namen der Liga), dann allgemeine Stichworte je Sportart
    private val RULES = listOf(
        Rule(SportKind.FOOTBALL, listOf("2. bundesliga"), "2. Bundesliga"),
        Rule(SportKind.FOOTBALL, listOf("bundesliga"), "Bundesliga"),
        Rule(SportKind.FOOTBALL, listOf("champions league"), "Champions League"),
        Rule(SportKind.FOOTBALL, listOf("europa league"), "Europa League"),
        Rule(SportKind.FOOTBALL, listOf("conference league"), "Conference League"),
        Rule(SportKind.FOOTBALL, listOf("dfb-pokal", "dfb pokal"), "DFB-Pokal"),
        Rule(SportKind.FOOTBALL, listOf("premier league"), "Premier League"),
        Rule(SportKind.FOOTBALL, listOf("laliga", "la liga"), "LaLiga"),
        Rule(SportKind.FOOTBALL, listOf("serie a"), "Serie A"),
        Rule(SportKind.FOOTBALL, listOf("ligue 1"), "Ligue 1"),
        Rule(SportKind.FOOTBALL, listOf("süper lig", "super lig"), "Süper Lig"),
        Rule(SportKind.FOOTBALL, listOf("3. liga"), "3. Liga"),
        Rule(SportKind.MOTORSPORT, listOf("formel 1", "formula 1", "formel-1", " f1 "), "Formel 1"),
        Rule(SportKind.MOTORSPORT, listOf("motogp"), "MotoGP"),
        Rule(SportKind.BASKETBALL, listOf("nba"), "NBA"),
        Rule(SportKind.BASKETBALL, listOf("euroleague"), "EuroLeague"),
        Rule(SportKind.AMERICAN_FOOTBALL, listOf("nfl"), "NFL"),
        Rule(SportKind.ICE_HOCKEY, listOf("nhl"), "NHL"),
        Rule(SportKind.ICE_HOCKEY, listOf("del ", "penny del"), "DEL"),
        Rule(SportKind.TENNIS, listOf("atp"), "ATP"),
        Rule(SportKind.TENNIS, listOf("wta"), "WTA"),
        Rule(SportKind.COMBAT, listOf("ufc"), "UFC"),
        Rule(SportKind.FOOTBALL, listOf("fußball", "fussball", "football", "soccer", "länderspiel")),
        Rule(SportKind.BASKETBALL, listOf("basketball")),
        Rule(SportKind.MOTORSPORT, listOf("motorsport", "grand prix", "nascar", "rallye", "dtm")),
        Rule(SportKind.TENNIS, listOf("tennis")),
        Rule(SportKind.ICE_HOCKEY, listOf("eishockey", "ice hockey", "hockey")),
        Rule(SportKind.AMERICAN_FOOTBALL, listOf("american football", "super bowl")),
        Rule(SportKind.COMBAT, listOf("boxen", "boxing", "kampfsport", "mma", "wrestling", "wwe")),
        Rule(SportKind.OTHER, listOf("darts", "handball", "golf", "biathlon", "ski", "volleyball", "snooker", "radsport", "leichtathletik")),
    )

    /** Ausschluss: Magazine, Talk und Nachrichten über Sport sind keine Übertragung. */
    private val EXCLUDE = listOf("magazin", "nachrichten", "news", "talk", "highlights", "doku", "wiederholung", "rückblick", "quiz")

    /** Sportart und Liga einer Sendung, oder null, wenn es keine Sportübertragung ist. */
    fun classify(title: String, category: String?, description: String? = null): Pair<SportKind, String?>? {
        val t = " " + title.lowercase() + " "
        val c = category.orEmpty().lowercase()
        if (EXCLUDE.any { it in t }) return null
        for (r in RULES) if (r.words.any { it in t }) return r.kind to r.league
        val sportCategory = "sport" in c
        if (!sportCategory) return null
        for (r in RULES) if (r.words.any { it in c }) return r.kind to r.league
        // Kategorie „Sport“ und Begegnung im Titel („A - B“, „A vs. B“): als Sport zählen
        if (Regex("""\S\s(-|–|vs\.?|gegen)\s\S""").containsMatchIn(title.lowercase())) return SportKind.OTHER to null
        val d = description.orEmpty().lowercase()
        for (r in RULES) if (r.words.any { it in d }) return r.kind to r.league
        return null
    }

    /** Heim/Gast aus „FC Bayern - Borussia Dortmund“ oder „Lakers vs. Celtics“. */
    fun teams(title: String): Pair<String, String>? {
        val m = Regex("""^(?:[^:]*:\s*)?(.+?)\s+(?:-|–|vs\.?|gegen)\s+(.+?)(?:\s*\(.*\))?$""", RegexOption.IGNORE_CASE).find(title.trim()) ?: return null
        val a = m.groupValues[1].trim(); val b = m.groupValues[2].trim()
        if (a.length < 2 || b.length < 2 || a.length > 40 || b.length > 40) return null
        return a to b
    }

    /** Gleiche Sendung auf mehreren Sendern zusammenfassen: gleicher Titel, Beginn innerhalb von 15 Minuten. */
    fun key(title: String, startMillis: Long): String =
        title.lowercase().filter { it.isLetterOrDigit() } + "@" + (startMillis / (15 * 60_000L))

    /** Sendername vereinfachen zum Vergleichen: „DE| Sky Sport Bundesliga 1 HD“ → „skysportbundesliga1“. */
    fun norm(name: String): String {
        var n = name.lowercase()
        n = n.replace(Regex("""^\s*[a-z]{2,3}\s*[|:]\s*"""), "")
        n = n.replace(Regex("""\b(uhd|fhd|hd|sd|4k|hevc|raw|h265|50fps|60fps)\b"""), "")
        return n.filter { it.isLetterOrDigit() }
    }

    /** Passt ein gewünschter Sendername zu einem Sender? */
    fun nameMatches(wanted: String, channelName: String): Boolean {
        val w = norm(wanted); val c = norm(channelName)
        return w.isNotEmpty() && (c == w || (w.length >= 4 && c.startsWith(w)))
    }

    /**
     * Eigene Liste (sports.json):
     * {"events":[{"sport":"football","league":"Bundesliga","title":"FC Bayern – BVB","home":"FC Bayern","away":"BVB",
     *   "start":"2026-11-08T18:30:00+01:00","end":"2026-11-08T20:30:00+01:00","channels":["Sky Sport Bundesliga 1","DAZN 1"]}]}
     */
    fun parseCustom(body: String): List<SportEvent> = runCatching {
        val arr = (Json.parseToJsonElement(body) as JsonObject)["events"] as? JsonArray ?: return emptyList()
        arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.trim().orEmpty()
            val title = s("title").ifEmpty { return@mapNotNull null }
            val start = runCatching { OffsetDateTime.parse(s("start")).toInstant().toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { OffsetDateTime.parse(s("end")).toInstant().toEpochMilli() }.getOrNull() ?: (start + 2 * 3_600_000L)
            val channels = (o["channels"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.ifEmpty { null } }.orEmpty()
            SportEvent(
                kind = kindOf(s("sport")), league = s("league").ifEmpty { null }, title = title,
                startMillis = start, endMillis = end, channelNames = channels,
                home = s("home").ifEmpty { null }, away = s("away").ifEmpty { null },
                description = s("text").ifEmpty { null }, featured = true,
            )
        }
    }.getOrElse { emptyList() }

    fun kindOf(s: String): SportKind = when (s.lowercase()) {
        "football", "fußball", "fussball", "soccer" -> SportKind.FOOTBALL
        "basketball" -> SportKind.BASKETBALL
        "motorsport", "f1", "formel 1" -> SportKind.MOTORSPORT
        "tennis" -> SportKind.TENNIS
        "eishockey", "icehockey", "ice_hockey", "hockey" -> SportKind.ICE_HOCKEY
        "nfl", "american_football", "american football" -> SportKind.AMERICAN_FOOTBALL
        "boxen", "boxing", "mma", "ufc", "combat" -> SportKind.COMBAT
        else -> SportKind.OTHER
    }
}

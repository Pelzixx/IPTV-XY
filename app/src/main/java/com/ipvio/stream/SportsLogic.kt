package com.ipvio.stream

/*
 * Sport Guide (nach dem Vorbild von StreamVault): Sportereignisse aus dem TV-Programm erkennen.
 * Reine Regeln ohne Android, damit sie getestet werden können.
 */

/** Ein erkanntes Spiel bzw. Ereignis aus einer Sendung. */
data class SportHit(val sport: String, val league: String, val home: String?, val away: String?, val title: String)

/** Ein Ereignis mit allen Sendern, auf denen es läuft (beste zuerst), optional mit Spielstand. */
data class SportEvent(
    val key: String, val sport: String, val league: String, val home: String?, val away: String?, val title: String,
    val desc: String, val start: Long, val end: Long, val channels: List<String>,
    val scoreHome: Int? = null, val scoreAway: Int? = null, val finished: Boolean = false, val source: String = "EPG"
) {
    fun live(now: Long) = !finished && now in start until end
}

object SportsLogic {
    const val FOOTBALL = "Fußball"
    const val MOTOR = "Motorsport"
    const val BASKET = "Basketball"
    const val TENNIS = "Tennis"
    const val ICE = "Eishockey"
    const val US = "US-Sport"
    const val FIGHT = "Kampfsport"
    const val OTHER = "Weitere"

    /** Liga-Stichwort → (Sportart, Liga). Reihenfolge zählt: genauere zuerst. */
    private val LEAGUES: List<Triple<Regex, String, String>> = listOf(
        // Andere Sportarten mit „Bundesliga“ im Titel (Tischtennis, Volleyball …) nicht als Fußball werten
        Triple(Regex("""\b(tischtennis|volleyball|hockey|feldhockey|ringen|turnen|badminton|wasserball|rugby|faustball|kegeln|schach|futsal|american football)\b.*\bbundesliga\b""", RegexOption.IGNORE_CASE), OTHER, "Andere"),
        Triple(Regex("""\beishockey\b|\bpenny del\b""", RegexOption.IGNORE_CASE), ICE, "Eishockey"),
        Triple(Regex("""\bhandball\b.*\bbundesliga\b""", RegexOption.IGNORE_CASE), OTHER, "Handball-Bundesliga"),
        Triple(Regex("""\bbasketball\b.*\bbundesliga\b""", RegexOption.IGNORE_CASE), BASKET, "Basketball-Bundesliga"),
        Triple(Regex("""\b2\.\s*bundesliga\b|\b2\.\s*bl\b|\bzweiten? bundesliga\b""", RegexOption.IGNORE_CASE), FOOTBALL, "2. Bundesliga"),
        Triple(Regex("""\bbundesliga der frauen\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Frauen-Bundesliga"),
        Triple(Regex("""\b3\.\s*liga\b""", RegexOption.IGNORE_CASE), FOOTBALL, "3. Liga"),
        Triple(Regex("""\bfrauen[- ]?bundesliga\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Frauen-Bundesliga"),
        Triple(Regex("""\bbasketball[- ]?bundesliga\b|\bbbl\b|\beasycredit\b""", RegexOption.IGNORE_CASE), BASKET, "Basketball-Bundesliga"),
        Triple(Regex("""\bhandball[- ]?bundesliga\b|\bhbl\b""", RegexOption.IGNORE_CASE), OTHER, "Handball-Bundesliga"),
        Triple(Regex("""\bbundesliga\b|\b1\.\s*bl\b|\bbl\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Bundesliga"),
        Triple(Regex("""\bdfb[- ]?pokal\b""", RegexOption.IGNORE_CASE), FOOTBALL, "DFB-Pokal"),
        Triple(Regex("""\bchampions league\b|\buefa cl\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Champions League"),
        Triple(Regex("""\beuropa league\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Europa League"),
        Triple(Regex("""\bconference league\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Conference League"),
        Triple(Regex("""\bpremier league\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Premier League"),
        Triple(Regex("""\bla ?liga\b|\blaliga\b""", RegexOption.IGNORE_CASE), FOOTBALL, "La Liga"),
        Triple(Regex("""\bserie a\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Serie A"),
        Triple(Regex("""\bligue 1\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Ligue 1"),
        Triple(Regex("""\beredivisie\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Eredivisie"),
        Triple(Regex("""\bsüper lig\b|\bsuper lig\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Süper Lig"),
        Triple(Regex("""\b(wm|em)[- ]?qualifikation\b|\bnations league\b|\bländerspiel\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Länderspiele"),
        Triple(Regex("""\bformel ?1\b|\bformula ?1\b|\bf1\b|\bgrand prix\b""", RegexOption.IGNORE_CASE), MOTOR, "Formel 1"),
        Triple(Regex("""\bmotogp\b|\bmoto ?gp\b""", RegexOption.IGNORE_CASE), MOTOR, "MotoGP"),
        Triple(Regex("""\bdtm\b|\bnascar\b|\bindycar\b|\bformel e\b|\bformula e\b""", RegexOption.IGNORE_CASE), MOTOR, "Motorsport"),
        Triple(Regex("""\bnba\b""", RegexOption.IGNORE_CASE), BASKET, "NBA"),
        Triple(Regex("""\beuroleague\b""", RegexOption.IGNORE_CASE), BASKET, "EuroLeague"),
        Triple(Regex("""\bnfl\b""", RegexOption.IGNORE_CASE), US, "NFL"),
        Triple(Regex("""\bnhl\b""", RegexOption.IGNORE_CASE), ICE, "NHL"),
        Triple(Regex("""\bdel\b""", RegexOption.IGNORE_CASE), ICE, "Eishockey"),
        Triple(Regex("""\bmlb\b""", RegexOption.IGNORE_CASE), US, "MLB"),
        Triple(Regex("""\batp\b|\bwta\b|\bwimbledon\b|\bfrench open\b|\bus open\b|\baustralian open\b|\btennis\b""", RegexOption.IGNORE_CASE), TENNIS, "Tennis"),
        Triple(Regex("""\bufc\b|\bboxen\b|\bboxing\b|\bmma\b""", RegexOption.IGNORE_CASE), FIGHT, "Kampfsport"),
        Triple(Regex("""\bdarts?\b|\bpdc\b""", RegexOption.IGNORE_CASE), OTHER, "Darts"),
        Triple(Regex("""\bhandball\b""", RegexOption.IGNORE_CASE), OTHER, "Handball"),
        Triple(Regex("""\bbasketball\b""", RegexOption.IGNORE_CASE), BASKET, "Basketball"),
        Triple(Regex("""\bfußball\b|\bfussball\b|\bsoccer\b""", RegexOption.IGNORE_CASE), FOOTBALL, "Fußball")
    )

    /** Sender, die vor allem Sport zeigen (dann reicht eine Paarung „A – B“ im Titel). */
    private val SPORT_CHANNEL = Regex("""sport|dazn|eurosport|sky ?sport|magenta ?sport|laola|bundesliga|\bran\b|servus|nitro|\bespn\b|bein|\bsuper ?sport""", RegexOption.IGNORE_CASE)

    /** Wiederholungen, Zusammenfassungen, Magazine: keine Live-Ereignisse. */
    private val NOISE = Regex(
        """\b(highlights?|zusammenfassung|re-?live|wiederholung|magazin|news|nachrichten|vorbericht|analyse|talk|doppelpass|sportschau club|classics?|legenden|best of|reportage|doku)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Trenner zwischen zwei Teams. */
    private val VS = Regex("""\s+(?:vs\.?|v\.?|gegen|[–—-])\s+""", RegexOption.IGNORE_CASE)
    private val PREFIX = Regex("""^\s*(?:live:?|live\s*[-–:]|\(live\)|direkt:?)\s*""", RegexOption.IGNORE_CASE)
    private val LEAGUE_PREFIX = Regex("""^[^:]{2,40}:\s+""")
    private val TRAILING = Regex("""\s*[(\[].*?[)\]]\s*$""")
    private val TIME = Regex("""\b\d{1,2}[:.]\d{2}\b""")

    /** Alle Liga-Stichwörter in einem Ausdruck: schneller Vortest, bevor die einzelnen Ligen geprüft werden. */
    private val ANY_LEAGUE = Regex(LEAGUES.joinToString("|") { "(?:${it.first.pattern})" }, RegexOption.IGNORE_CASE)

    fun league(text: String): Pair<String, String>? =
        if (!ANY_LEAGUE.containsMatchIn(text)) null else LEAGUES.firstOrNull { it.first.containsMatchIn(text) }?.let { it.second to it.third }

    /**
     * Wunsch des Nutzers: deutscher Sport zuerst, nicht zu viel Auswahl. Nur diese Wettbewerbe erscheinen,
     * in dieser Reihenfolge (Bundesliga ganz oben).
     */
    val LEAGUE_PRIO = listOf(
        "Bundesliga", "2. Bundesliga", "DFB-Pokal", "3. Liga", "Champions League", "Europa League", "Conference League",
        "Länderspiele", "Frauen-Bundesliga", "Formel 1", "Handball-Bundesliga", "Basketball-Bundesliga", "Eishockey"
    )
    fun allowed(league: String) = league in LEAGUE_PRIO
    fun prio(league: String) = LEAGUE_PRIO.indexOf(league).let { if (it < 0) 99 else it }

    /** Deutschsprachige Sender, die Fußball & Co. zeigen (dort zählt eine Paarung nur mit erkannter Liga). */
    private val GERMAN_TV = Regex("""\b(sky|dazn|das erste|ard|zdf|sat\.? ?1|rtl|pro ?sieben|kabel ?eins|sport ?1|magenta|sportdigital|servus|orf|srf|puls ?24|one|tagesschau)\b""", RegexOption.IGNORE_CASE)

    /** Sender-Angaben, die für alle Sendungen eines Senders gleich sind (einmal je Sender berechnen). */
    class Chan(val sport: Boolean, val league: Pair<String, String>?, val relevant: Boolean = true)
    fun chan(channel: String): Chan {
        val sport = SPORT_CHANNEL.containsMatchIn(channel)
        return Chan(sport, league(channel), sport || GERMAN_TV.containsMatchIn(channel))
    }

    /**
     * Spielminute eines laufenden Fußballspiels (nur mit Spielstand, also echte Spiele, keine Konferenz/Sendung):
     * 1.–45. Minute, dann 15 Minuten Halbzeit („HZ“), dann 46.–90., danach „90+“.
     */
    fun gameMinute(e: SportEvent, now: Long): String? {
        if (e.scoreHome == null || e.home == null || e.sport != FOOTBALL || now < e.start) return null
        val m = ((now - e.start) / 60).toInt() + 1
        return when {
            m <= 45 -> "$m'"
            m <= 60 -> "HZ"
            m - 15 <= 90 -> "${m - 15}'"
            else -> "90+'"
        }
    }

    /** Sendername ohne Länderkürzel und Qualitätsangabe („DE: Sky Sport Bundesliga 1 FHD“ → „sky sport bundesliga 1“). */
    fun baseName(title: String): String = title.lowercase()
        .replace(Regex("""^\s*[a-z]{2,3}\s*[:|]\s*"""), "")
        .replace(Regex("""[(\[].*?[)\]]"""), " ")
        .replace(Regex("""\b(uhd|fhd|hd|sd|4k|hevc|h265|raw|\d{3,4}p|50 ?fps|60 ?fps|backup|\+)\b"""), " ")
        .replace(Regex("""[^a-z0-9äöüß ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** Sender eines anderen Landes (Kürzel im Namen oder in der Gruppe): für den deutschen Sport Guide überspringen. */
    private val FOREIGN = Regex("""(^|[\s|:\[(])(uk|us|usa|ca|au|ar|arab|tr|nl|fr|it|pt|pl|ro|hu|cz|sk|gr|ex-?yu|rs|hr|ba|ru|ua|pk|br|mx|latino|bein|sky sports|espn|tsn|supersport)(?=$|[\s|:\])])""", RegexOption.IGNORE_CASE)
    fun foreign(channel: String) = FOREIGN.containsMatchIn(channel)

    private val PAYTV = Regex("""\b(sky|dazn|magenta|sport ?1|sportdigital)\b""", RegexOption.IGNORE_CASE)
    fun isPayTvSport(channel: String) = PAYTV.containsMatchIn(channel)

    private val KONFERENZ =Regex("""\bkonferenz\b""", RegexOption.IGNORE_CASE)

    /** Erwähnt [text] das Team (ein Wort ab 4 Buchstaben aus dem Teamnamen)? */
    fun mentions(text: String, team: String): Boolean {
        val words = norm(text).split(' ').toHashSet()
        val tw = norm(team).split(' ').filter { it.length >= 4 }
        return tw.isNotEmpty() && tw.any { it in words }
    }

    /** Ist die Sendung ein Sportereignis? [channel] = Sendername (+ Gruppe). */
    fun parse(title: String, desc: String, channel: String): SportHit? = parse(title, desc, chan(channel))

    fun parse(title: String, desc: String, ch: Chan): SportHit? {
        val t = title.trim()
        if (t.length < 4) return null
        val lgTitle = league(t)
        val hasVs = VS.containsMatchIn(t)
        // Schneller Ausstieg: weder Paarung noch Liga im Titel und kein Sender mit Liga im Namen
        if (!hasVs && lgTitle == null && ch.league == null) return null
        if (NOISE.containsMatchIn(t)) return null
        val sportCh = ch.sport
        val lg = lgTitle ?: (if (hasVs) league(desc.take(300)) else null) ?: ch.league
        // Paarung aus dem Titel: Liga-Vorsilbe („Bundesliga: …“) und „Live“ entfernen
        var core = PREFIX.replace(t, "")
        // Sky-Konferenz („2. Bundesliga: Die Konferenz“): ein Ereignis für alle Spiele der Liga
        if (lg != null && lg.first == FOOTBALL && KONFERENZ.containsMatchIn(t)) return SportHit(lg.first, lg.second, null, null, "${lg.second} – Konferenz")
        // „Fußball: 2. Bundesliga, 11. Spieltag: A – B“ → alles bis zum letzten Doppelpunkt ist Vorspann
        if (LEAGUE_PREFIX.containsMatchIn(core) && league(core.substringBeforeLast(':')) != null) core = core.substringAfterLast(':').trim()
        core = TRAILING.replace(core, "").trim()
        val parts = VS.split(core, 2)
        if (parts.size == 2) {
            // „… - FC St. Pauli, 11. Spieltag“: Zusatz nach dem Komma gehört nicht zum Teamnamen
            val home = clean(parts[0].substringBefore(',')); val away = clean(parts[1].substringBefore(','))
            val ok = home.length in 2..40 && away.length in 2..40 && !TIME.containsMatchIn(home) && home.count { it == ' ' } <= 4 && away.count { it == ' ' } <= 4 &&
                teamLike(home) && teamLike(away)
            if (ok && (sportCh || lg != null)) {
                val (sport, league) = lg ?: (FOOTBALL to "Fußball")
                return SportHit(sport, league, home, away, "$home – $away")
            }
        }
        // Ohne Paarung: nur eindeutige Ereignisse (Rennen, Turniere) auf erkannter Liga
        if (lg != null && (lg.first == MOTOR || lg.first == TENNIS || lg.second == "Darts" || lg.first == FIGHT) &&
            Regex("""\b(rennen|race|qualifying|training|sprint|finale?|halbfinale|runde|match|kampf|fight|live)\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)
        ) return SportHit(lg.first, lg.second, null, null, t)
        return null
    }

    /** Sieht wie ein Teamname aus? (Buchstaben, kein „2026/27“, kein Liganame wie „Bundesliga der Frauen“) */
    private fun teamLike(s: String): Boolean = Regex("""\p{L}{3,}""").containsMatchIn(s) && '/' !in s && league(s) == null

    private fun clean(s: String): String = s.replace(Regex("""\s{2,}"""), " ").trim().trim('-', '–', ':', ',').trim()

    /** Vereinfachter Teamname zum Vergleichen (ohne „FC“, „1.“, Umlaute vereinheitlicht). */
    fun norm(team: String): String = team.lowercase()
        .replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")
        .replace(Regex("""\b(fc|sc|sv|vfb|vfl|tsg|bv|bsc|1\.|fsv|rb|ac|as|ssc|cf|afc|club|borussia)\b"""), " ")
        .replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** Gleiches Team? (ein Wort von mindestens 4 Buchstaben stimmt überein, z. B. „Bayern“ in „FC Bayern München“). */
    fun sameTeam(a: String, b: String): Boolean {
        val wa = norm(a).split(' ').filter { it.length >= 4 }.toSet()
        val wb = norm(b).split(' ').filter { it.length >= 4 }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return norm(a) == norm(b)
        return wa.any { it in wb }
    }

    /** Qualität aus dem Sendernamen: höher = besser (UHD/4K > FHD > HD > SD). */
    fun channelRank(name: String): Int = when {
        Regex("""\b(uhd|4k|2160)""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 4
        Regex("""\bfhd\b|1080""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 3
        Regex("""\bhd\b|720""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 2
        else -> 1
    }

    /** Schlüssel, unter dem gleiche Ereignisse auf verschiedenen Sendern zusammengefasst werden. */
    fun groupKey(h: SportHit, start: Long): String {
        val slot = start / 900
        return if (h.home != null && h.away != null) "${norm(h.home)}|${norm(h.away)}|$slot" else "${h.title.lowercase()}|$slot"
    }

    /** Kürzel für das Team-Abzeichen (z. B. „FC Bayern München“ → „FB“, „Dortmund“ → „DO“). */
    fun initials(team: String): String {
        val w = team.split(' ', '-').filter { it.isNotBlank() && it.first().isLetter() }
        return when {
            w.size >= 2 -> (w[0].take(1) + w[1].take(1)).uppercase()
            w.size == 1 -> w[0].take(2).uppercase()
            else -> team.take(2).uppercase()
        }
    }
}

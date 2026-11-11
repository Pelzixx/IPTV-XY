package com.ipvio.stream

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/**
 * Meine Teams (Wunsch des Nutzers, 2026-11-11): Mannschaften folgen. Im Sport Guide eigene Rubrik „Meine Teams“,
 * und der Tor-Alarm gilt automatisch für alle Spiele dieser Teams.
 */
object MyTeams {
    val teams = mutableStateListOf<String>()
    private var inited = false
    private fun prefs(c: Context) = Profiles.sp(c, "ipvio_teams")

    fun init(c: Context) {
        if (inited) return
        inited = true
        teams.addAll((prefs(c).getString("t", "") ?: "").split("\n").filter { it.isNotBlank() })
    }

    private fun key(name: String) = name.trim().lowercase()

    fun isMine(name: String?): Boolean = name != null && teams.any { key(it) == key(name) }
    fun hasMine(e: SportEvent): Boolean = isMine(e.home) || isMine(e.away)

    /** Folgen bzw. nicht mehr folgen; true = folgt jetzt. */
    fun toggle(c: Context, name: String): Boolean {
        init(c)
        val i = teams.indexOfFirst { key(it) == key(name) }
        val on = if (i >= 0) { teams.removeAt(i); false } else { teams.add(name.trim()); true }
        prefs(c).edit().putString("t", teams.joinToString("\n")).apply()
        return on
    }
}

/** Spoiler-Schutz (Optik > Player): Spielstände im Sport Guide, auf der Startseite und im Player ausblenden. */
object Spoiler {
    fun on(): Boolean = Tm.b(Tm.PL_SPOILER, false)
}

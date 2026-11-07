package com.ipvio.stream

import android.content.Context

/** Aktionen, die einer Taste im Live-Player zugewiesen werden können (TiviMate: Fernbedienung > Player). */
enum class PAct(val label: String) {
    NONE("Keine Aktion"),
    INFO("Infofenster und Bedienfeld anzeigen"),
    MENU("Zeige Menü"),
    STOP("Wiedergabe stoppen"),
    LIST_OV("Senderliste im Overlay-Modus anzeigen"),
    GUIDE("TV-Guide anzeigen"),
    LAST("Den zuletzt angesehenen Sender anzeigen"),
    DESC("Zeige Programmbeschreibung"),
    NEXT("Nächsten Sender anzeigen"),
    PREV("Vorherigen Sender anzeigen"),
    HISTORY("Verlauf"),
    PLAYPAUSE("Play/Pause"),
    LIVE("Geh zum Live Stream"),
    FAV("Favoritenstatus ändern"),
    CH_OPTS("Kanaloptionen"),
    EXTERNAL("Extern öffnen"),
    SEARCH("Suchen"),
    AUDIO("Audiospuren"),
    SUBS("Untertitel"),
    ASPECT("Bildformat"),
    SLEEP("Sleeptimer"),
    SETTINGS("Einstellungen"),
    RECORD("Aufnehmen"),
    MULTIVIEW("Multiview")
}

/** Tasten des Live-Players mit TiviMate-Standardbelegung. [long] = langer Druck. */
enum class PKey(val label: String, val def: PAct) {
    OK("Okay", PAct.INFO),
    OK_LONG("Lang OK", PAct.MENU),
    BACK_LONG("Lang Zurück", PAct.STOP),
    LEFT("Links", PAct.LIST_OV),
    LEFT_LONG("Lang Links", PAct.GUIDE),
    RIGHT("Rechts", PAct.LAST),
    RIGHT_LONG("Lang Rechts", PAct.DESC),
    UP("Hoch", PAct.NEXT),
    UP_LONG("Lang Hoch", PAct.HISTORY),
    DOWN("Runter", PAct.PREV),
    DOWN_LONG("Lang Runter", PAct.LIST_OV),
    MENU("Menü", PAct.MENU),
    MENU_LONG("Lang Menü", PAct.GUIDE),
    PLAYPAUSE("Play / Pause", PAct.PLAYPAUSE),
    PLAYPAUSE_LONG("Lang Play/Pause", PAct.LIVE),
    STOP("Stopp", PAct.STOP),
    RW("Zurückspulen (RW)", PAct.PREV),
    FF("Vorspulen (FF)", PAct.NEXT),
    CH_UP("Pg+ (Ch+)", PAct.NEXT),
    CH_DOWN("Pg- (Ch-)", PAct.PREV),
    INFO("Info", PAct.DESC),
    GUIDE("Guide", PAct.GUIDE);

    val longKey: PKey? get() = entries.firstOrNull { it.name == name + "_LONG" }
}

object PlayerKeys {
    private fun key(k: PKey) = "pk_" + k.name
    fun action(k: PKey): PAct = PAct.entries.getOrNull(Tm.i(key(k), -1)) ?: k.def
    fun set(c: Context, k: PKey, a: PAct) = Tm.set(c, key(k), a.ordinal)
    fun reset(c: Context) = PKey.entries.forEach { Tm.set(c, key(it), -1) }
}

/** Aktionen im TV-Guide (TiviMate: Fernbedienung > EPG). */
enum class EAct(val label: String) {
    NONE("Keine Aktion"),
    WATCH("Sender ansehen"),
    MENU("Zeige Menü"),
    BACK("Gehe zurück"),
    TO_PLAYER("Zurück zum Player"),
    GROUPS("Zeige Gruppen"),
    NEXT_PROG("Zum nächsten Programm scrollen"),
    PREV_PROG("Zum vorherigen Programm scrollen"),
    PAGE_UP("Sender nach oben scrollen"),
    PAGE_DOWN("Sender nach unten scrollen"),
    DESC("Zeige Programmbeschreibung"),
    EXTERNAL("Extern öffnen"),
    FAV("Favoritenstatus ändern"),
    RECORD("Aufnehmen"),
    SEARCH("Suchen")
}

/** Tasten im TV-Guide mit TiviMate-Standardbelegung; [codes] = Android-Tastencodes, [long] = langer Druck. */
enum class EKey(val label: String, val def: EAct, val codes: IntArray, val long: Boolean = false) {
    OK("Okay", EAct.WATCH, intArrayOf(23, 66, 160)),
    OK_LONG("Lang OK", EAct.MENU, intArrayOf(23, 66, 160), true),
    BACK("Zurück", EAct.BACK, intArrayOf(4)),
    BACK_LONG("Lang Zurück", EAct.TO_PLAYER, intArrayOf(4), true),
    LEFT("Links", EAct.GROUPS, intArrayOf(21)),
    RIGHT("Rechts", EAct.NEXT_PROG, intArrayOf(22)),
    CH_UP("Pg+ (Ch+)", EAct.PAGE_UP, intArrayOf(166, 92)),
    CH_DOWN("Pg- (Ch-)", EAct.PAGE_DOWN, intArrayOf(167, 93)),
    PLAYPAUSE("Play / Pause", EAct.DESC, intArrayOf(85, 126, 127)),
    PLAYPAUSE_LONG("Lang Play/Pause", EAct.EXTERNAL, intArrayOf(85, 126, 127), true),
    STOP("Stopp", EAct.FAV, intArrayOf(86)),
    RW("Zurückspulen (RW)", EAct.PAGE_UP, intArrayOf(89)),
    FF("Vorspulen (FF)", EAct.PAGE_DOWN, intArrayOf(90)),
    RECORD("Aufnahme", EAct.RECORD, intArrayOf(130)),
    DEL("Rücktaste", EAct.TO_PLAYER, intArrayOf(67)),
    INFO("Info", EAct.DESC, intArrayOf(165)),
    GUIDE("Guide", EAct.TO_PLAYER, intArrayOf(172));

    companion object {
        fun of(code: Int, long: Boolean): EKey? = entries.firstOrNull { it.long == long && code in it.codes }
        /** Hat diese Taste eine eigene Lang-Druck-Belegung? */
        fun hasLong(code: Int): Boolean = entries.any { it.long && code in it.codes }
    }
}

object GuideKeys {
    private fun key(k: EKey) = "gk_" + k.name
    fun action(k: EKey): EAct = EAct.entries.getOrNull(Tm.i(key(k), -1)) ?: k.def
    fun set(c: Context, k: EKey, a: EAct) = Tm.set(c, key(k), a.ordinal)
    fun reset(c: Context) = EKey.entries.forEach { Tm.set(c, key(it), -1) }
}

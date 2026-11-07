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
    SETTINGS("Einstellungen")
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

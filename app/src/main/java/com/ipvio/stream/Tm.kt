package com.ipvio.stream

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/**
 * Einstellungen im Aufbau von TiviMate (Optik, Wiedergabe, Fernbedienung, …), zentral an einer Stelle.
 * Werte liegen als Compose-Zustand vor, damit Änderungen sofort sichtbar werden.
 */
object Tm {
    private var sp: SharedPreferences? = null
    private val bools = mutableStateMapOf<String, Boolean>()
    private val ints = mutableStateMapOf<String, Int>()
    private val strs = mutableStateMapOf<String, String>()

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("tm_prefs", Context.MODE_PRIVATE)
        sp = p
        for ((k, v) in p.all) when (v) {
            is Boolean -> bools[k] = v
            is Int -> ints[k] = v
            is String -> strs[k] = v
        }
    }

    fun b(key: String, def: Boolean): Boolean = bools[key] ?: def
    fun i(key: String, def: Int): Int = ints[key] ?: def

    /**
     * AFR-Methode: 0 nahtlos, 1 Anzeigemodus selbst wechseln, 2 über das Gerät (Android 12+: die App meldet die Bildrate,
     * die Box schaltet nach ihrer Einstellung „Bildrate anpassen“ um – Wunsch des Nutzers 2026-11-10, Google TV schaltete
     * sonst doppelt um). Standard: ab Android 12 über das Gerät, davor Anzeigemodus wechseln.
     */
    fun afrMode(): Int = i(PB_AFR_MODE, if (android.os.Build.VERSION.SDK_INT >= 31) 2 else 1).let { if (it == 2 && android.os.Build.VERSION.SDK_INT < 31) 1 else it }
    fun s(key: String, def: String = ""): String = strs[key] ?: def

    fun set(c: Context, key: String, v: Boolean) { init(c); bools[key] = v; sp?.edit()?.putBoolean(key, v)?.apply() }
    fun set(c: Context, key: String, v: Int) { init(c); ints[key] = v; sp?.edit()?.putInt(key, v)?.apply() }
    fun set(c: Context, key: String, v: String) { init(c); strs[key] = v; sp?.edit()?.putString(key, v)?.apply() }

    // ---------------------------------------------------------------- Optik > EPG
    const val EPG_CH_SORT = "epg_ch_sort"            // 0 Wiedergabeliste, 1 Name A–Z, 2 Name Z–A
    const val EPG_PREVIEW = "epg_preview"
    const val EPG_NAME_EDIT = "epg_name_edit"        // Präfixe/Suffixe, durch Komma getrennt
    const val EPG_NUMBERS = "epg_numbers"
    const val EPG_NAMES = "epg_names"
    const val EPG_TWO_LINE_NAMES = "epg_two_line_names"
    const val EPG_CATCHUP_ICON = "epg_catchup_icon"
    const val EPG_HL_CURRENT = "epg_hl_current"
    const val EPG_TWO_LINE_TITLES = "epg_two_line_titles"
    const val EPG_MARK_RUNNING = "epg_mark_running"
    const val EPG_MARK_PROGRESS = "epg_mark_progress"
    const val EPG_NOW_FULL = "epg_now_full"
    const val EPG_SCROLL_ANIM = "epg_scroll_anim"
    const val EPG_STAY_OVERLAY = "epg_stay_overlay"
    const val EPG_BACK_NOW = "epg_back_now"

    // ---------------------------------------------------------------- Optik > Gruppen
    const val GRP_SORT = "grp_sort"                  // 0 Wiedergabeliste, 1 Name A–Z, 2 Name Z–A
    const val GRP_ALL_PLAYLISTS = "grp_all_playlists"
    const val GRP_FAVS = "grp_favs"
    const val GRP_ALL = "grp_all"
    const val GRP_PL_TAP = "grp_pl_tap"          // Antippen einer Wiedergabeliste: 0 auf-/zuklappen, 1 alle Kanäle der Liste
    const val GRP_HIDDEN_ALL = "grp_hidden_all"  // Sender ausgeblendeter Gruppen in „Alle Kanäle“ zeigen

    // ---------------------------------------------------------------- Optik > Player
    const val PL_BLACK_SWITCH = "pl_black_switch"
    const val PL_SCORE = "pl_score"              // Spielstand laufender Spiele im Player einblenden (Standard aus)
    const val PL_SPOILER = "pl_spoiler"          // Spoiler-Schutz: Spielstände ausblenden (Standard aus)
    const val PB_LEVEL = "pb_level"              // Lautstärke-Ausgleich (Standard aus)
    const val GEN_DNS = "gen_dns"                // Sichere DNS-Abfrage: -1 Panel-Vorgabe, 0 Aus, 1 An, 2 Automatisch
    const val GEN_SYNC = "gen_sync"              // Favoriten/Weiterschauen über die Sicherung beim Anbieter abgleichen (Standard an)
    // Senderliste
    const val CL_HL_NOW = "cl_hl_now"
    const val CL_HIDE_PAST = "cl_hide_past"
    const val CL_OV_PROGS = "cl_ov_progs"
    const val CL_OV_DESC = "cl_ov_desc"
    const val CL_OV_AUTOPLAY = "cl_ov_autoplay"
    const val CL_OV_STAY = "cl_ov_stay"
    // Senderliste > Vorschaumodus (TV-Guide mit Vorschaubild)
    const val CV_ANIM = "cv_anim"
    const val CV_AUTOPLAY = "cv_autoplay"
    const val CV_STAY = "cv_stay"
    const val CV_SOUND = "cv_sound"   // Vorschau mit Ton (TiviMate)
    const val CV_FS_TIMEOUT = "cv_fs_timeout"   // Sekunden ohne Taste bis Vollbild (0 = aus)
    // Infofeld
    const val IF_CARD = "if_card"
    const val IF_BOTTOM = "if_bottom"          // Kartenstil: Info-Feld beim Kanalwechsel unten statt oben
    const val IF_CLOCK = "if_clock"
    const val IF_DATE = "if_date"
    const val IF_PLGROUP = "if_plgroup"
    const val IF_DESC = "if_desc"
    const val IF_DESC_SWITCH = "if_desc_switch"
    const val IF_MEDIA = "if_media"
    const val IF_RES_NUM = "if_res_num"
    // Historie / Letzte Sender
    const val HI_EPG_BTN = "hi_epg_btn"
    const val HI_HIST_BTN = "hi_hist_btn"
    const val HI_DAYS = "hi_days"
    const val HI_DELAY = "hi_delay"
    const val HI_NOW = "hi_now"
    const val HI_PAST = "hi_past"
    const val RC_COUNT = "rc_count"
    const val RC_DELAY = "rc_delay"
    const val RC_NAMES = "rc_names"
    const val RC_PRESS_AGAIN = "rc_press_again"   // Umschalten erst beim zweiten Drücken (vorher Infofeld des Ziels)
    const val RC_ZAP_DELAY = "rc_zap_delay"       // Infofeld des nächsten Senders: nach N Sekunden umschalten (0 = aus)
    // Menü (Knöpfe der Leiste: Reihenfolge und ausgeblendete)
    const val MENU_ORDER = "menu_order"
    const val MENU_HIDDEN = "menu_hidden"
    // Eingeklappte Wiedergabelisten in der Gruppenübersicht (Namen, durch 0001 getrennt)
    const val PL_COLLAPSED = "pl_collapsed"
    const val PL_SORT = "pl_sort"                 // Playlist Sortierung: 0 Manuell, 1 Name
    const val PC_SETTINGS = "pc_settings"         // PIN für Einstellungen erforderlich
    const val PC_UNLOCK = "pc_unlock"             // Entsperrt bleibt: 0 App-Start, 1 10 min, 2 30 min, 3 1 h, 4 2 h, 5 bis Wechsel
    const val PC_INPUT = "pc_input"               // PIN-Eingabe: 0 Ziffernfeld, 1 Zahlenschloss (TiviMate)

    // ---------------------------------------------------------------- Fernbedienung > Spuloptionen
    const val SP_RWFF = "sp_rwff"                  // RW/FF/Pause zum Suchen/Pausieren während des Anschauens
    const val SP_RW_LIVE = "sp_rw_live"            // RW spult den Live-Stream zurück
    const val SP_LR_CATCHUP = "sp_lr_catchup"      // Links/Rechts spulen im Catchup
    const val SP_UD_CATCHUP = "sp_ud_catchup"      // Hoch/Runter spulen im Catchup

    // ---------------------------------------------------------------- Wiedergabe
    // ---------------------------------------------------------------- Allgemein
    const val GEN_UA = "gen_ua"                   // eigener User-Agent (leer = Standard)
    const val GEN_UDP = "gen_udp"                 // UDP-Proxy „Adresse:Port“ (udpxy)
    const val GEN_SLEEP_START = "gen_sleep_start" // nach dem Aufwachen aus dem Standby starten

    // ---------------------------------------------------------------- Optik > Logos
    const val LOGO_PRIO = "logo_prio"
    const val LOGO_DIR = "logo_dir"
    const val LOGO_FUZZY = "logo_fuzzy"
    const val UI_ALPHA = "ui_alpha"               // Transparenz der Benutzeroberfläche in %

    const val PB_AUDIO_DEC = "pb_audio_dec"          // 0 Hardware, 1 Software
    const val PB_VIDEO_DEC = "pb_video_dec"          // 0 Hardware, 1 Software
    const val PB_SURROUND = "pb_surround"
    const val PB_PASSTHROUGH = "pb_passthrough"
    const val PB_AFR_MODE = "pb_afr_mode"        // 0 nahtlos (Surface-Bildrate), 1 Anzeigemodus wechseln (TiviMate, Standard seit 2026-11-08: RAW-Sender mit 25 fps ruckelten auf 60 Hz)
    const val PB_AFR_AMLOGIC = "pb_afr_amlogic"  // Amlogic Fix: nur Modi mit der aktuellen Auflösung
    const val PB_SUB_ALL = "pb_sub_all"          // Live: Untertitel, falls vorhanden, immer an
    const val PB_TUNNEL = "pb_tunnel"
    const val PB_EXTERNAL = "pb_external"            // 0 Nein, 1 Ja, 2 Nur für VOD
    const val PB_STEP_TL_BACK = "pb_step_tl_back"    // Sekunden
    const val PB_STEP_TL_FWD = "pb_step_tl_fwd"
    const val PB_STEP_RW = "pb_step_rw"
    const val PB_STEP_FF = "pb_step_ff"
    const val PB_AUDIO_DELAY = "pb_audio_delay"     // Audioversatz in ms (positiv = Ton später)
    const val PB_PIP = "pb_pip"                     // Bild-in-Bild beim Druck auf Home

    // ---------------------------------------------------------------- Sonstiges > Suchen / Erinnerungen
    const val SE_VOICE = "se_voice"               // Sprachsuche bevorzugen (beim Öffnen gleich zuhören)
    const val SE_HIST = "se_hist"                 // Suchverlauf anzeigen
    const val SE_FAVS = "se_favs"                 // Lieblingssender zuerst anzeigen
    const val SE_PAST = "se_past"                 // vergangene Sendungen anzeigen (auch ohne Archiv)
    const val SE_STAY = "se_stay"                 // nach dem Umschalten auf der Suchseite bleiben
    const val SE_INC_LIVE = "se_inc_live"
    const val SE_INC_PROGS = "se_inc_progs"
    const val SE_INC_MOVIES = "se_inc_movies"
    const val SE_INC_SERIES = "se_inc_series"
    // Filme/Serien im TiviMate-Aufbau (VodTab)
    const val VOD_TM = "vod_tm"
    const val VOD_SORT_M = "vod_sort_m"
    const val VOD_SORT_S = "vod_sort_s"
    const val VOD_GRP_ALL_M = "vod_grp_all_m"
    const val VOD_GRP_ALL_S = "vod_grp_all_s"
    const val VOD_LAST_M = "vod_last_m"
    const val VOD_LAST_S = "vod_last_s"
    const val REM_TIMEOUT = "rem_timeout"         // Popup-Timeout in Sekunden
    const val REM_ACTION = "rem_action"           // Standardaktion nach dem Timeout: 0 Anschauen, 1 Schließen
    const val REM_WAKE = "rem_wake"               // Gerät aus dem Schlafmodus wecken
    const val REC_DIR = "rec_dir"                 // Aufnahmeverzeichnis (leer = App-Ordner, smb://… = Netzwerk)
    const val REC_SMB_USER = "rec_smb_user"
    const val REC_SMB_PASS = "rec_smb_pass"
    const val REC_BEFORE = "rec_before"           // Beginn der Aufnahme vorher (Minuten)
    const val REC_AFTER = "rec_after"             // Aufnahme stoppen nach (Minuten)

    val SORT_NAMES = listOf("Anordnung in der Wiedergabeliste", "Name (A–Z)", "Name (Z–A)")

    fun <T> sorted(list: List<T>, mode: Int, name: (T) -> String): List<T> = when (mode) {
        1 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, name))
        2 -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, name))
        else -> list
    }

    /** Sendernamen-Editor: entfernt die eingetragenen Zeichenfolgen (z. B. „DE|“, „ HD“) aus den Sendernamen. */
    fun cleanName(name: String): String {
        val parts = s(EPG_NAME_EDIT).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return name
        var n = name
        for (p in parts) n = n.replace(p, "", ignoreCase = true)
        return n.trim().ifEmpty { name }
    }
}

/** Hilfen für Allgemein-Einstellungen (TiviMate): User-Agent und UDP-Proxy. */
object TmNet {
    const val DEFAULT_UA = "IPTV-XY/1.0"
    fun userAgent(): String = Tm.s(Tm.GEN_UA).trim().ifEmpty { DEFAULT_UA }
    /** User-Agent für einen Sender: eigener Wert seiner Wiedergabeliste, sonst der allgemeine. */
    fun userAgentFor(itemId: String?): String =
        PlOpts.ua(itemId?.let { PlaylistStore.playlistOf(it) }).ifEmpty { userAgent() }

    /** User-Agent beim Laden einer Wiedergabeliste (Adresse der Liste). */
    fun userAgentForList(url: String): String =
        PlOpts.ua(PlaylistStore.lists.firstOrNull { it.url.trim() == url.trim() }?.name).ifEmpty { userAgent() }


    /** udp://@239.0.0.1:1234 bzw. rtp://… über den eingestellten udpxy-Proxy: http://proxy/udp/239.0.0.1:1234 */
    fun playUrl(url: String): String {
        val proxy = Tm.s(Tm.GEN_UDP).trim().removePrefix("http://").trimEnd('/')
        if (proxy.isEmpty()) return url
        val m = Regex("""^(udp|rtp)://@?(.+)$""", RegexOption.IGNORE_CASE).find(url.trim()) ?: return url
        return "http://$proxy/${m.groupValues[1].lowercase()}/${m.groupValues[2]}"
    }
}

/** Optik > Transparenz der Benutzeroberfläche (TiviMate): Deckkraft der Overlays im Player (0 % = wie gestaltet). */
fun uiAlpha(base: Float): Float = (base * (1f - Tm.i(Tm.UI_ALPHA, 20) / 100f * 0.8f)).coerceIn(0.25f, 1f)

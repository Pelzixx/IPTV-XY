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

    // ---------------------------------------------------------------- Optik > Player
    const val PL_BLACK_SWITCH = "pl_black_switch"
    // Senderliste
    const val CL_HL_NOW = "cl_hl_now"
    const val CL_HIDE_PAST = "cl_hide_past"
    const val CL_OV_PROGS = "cl_ov_progs"
    const val CL_OV_DESC = "cl_ov_desc"
    const val CL_OV_AUTOPLAY = "cl_ov_autoplay"
    const val CL_OV_STAY = "cl_ov_stay"
    // Infofeld
    const val IF_CARD = "if_card"
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
    // Menü (Knöpfe der Leiste: Reihenfolge und ausgeblendete)
    const val MENU_ORDER = "menu_order"
    const val MENU_HIDDEN = "menu_hidden"
    // Eingeklappte Wiedergabelisten in der Gruppenübersicht (Namen, durch 0001 getrennt)
    const val PL_COLLAPSED = "pl_collapsed"

    // ---------------------------------------------------------------- Fernbedienung > Spuloptionen
    const val SP_RWFF = "sp_rwff"                  // RW/FF/Pause zum Suchen/Pausieren während des Anschauens
    const val SP_RW_LIVE = "sp_rw_live"            // RW spult den Live-Stream zurück
    const val SP_LR_CATCHUP = "sp_lr_catchup"      // Links/Rechts spulen im Catchup
    const val SP_UD_CATCHUP = "sp_ud_catchup"      // Hoch/Runter spulen im Catchup

    // ---------------------------------------------------------------- Wiedergabe
    const val PB_AUDIO_DEC = "pb_audio_dec"          // 0 Hardware, 1 Software
    const val PB_VIDEO_DEC = "pb_video_dec"          // 0 Hardware, 1 Software
    const val PB_SURROUND = "pb_surround"
    const val PB_PASSTHROUGH = "pb_passthrough"
    const val PB_TUNNEL = "pb_tunnel"
    const val PB_EXTERNAL = "pb_external"            // 0 Nein, 1 Ja, 2 Nur für VOD
    const val PB_STEP_TL_BACK = "pb_step_tl_back"    // Sekunden
    const val PB_STEP_TL_FWD = "pb_step_tl_fwd"
    const val PB_STEP_RW = "pb_step_rw"
    const val PB_STEP_FF = "pb_step_ff"

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

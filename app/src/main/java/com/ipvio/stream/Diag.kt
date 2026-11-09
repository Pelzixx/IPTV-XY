package com.ipvio.stream

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Kleine Fehler-Merkliste für den Diagnosebereich (TiviMate: „Logs senden“). Speichert bewusst NUR kurze Meldungen:
 * Adressen werden entfernt, keine Benutzernamen, keine Passwörter.
 */
object Diag {
    var lastError by mutableStateOf<String?>(null)
    var lastErrorAt by mutableLongStateOf(0L)
    var errorCount by mutableLongStateOf(0L)

    /** Die letzten Fehler (neueste zuerst): Zeit und Meldung. */
    var history by mutableStateOf<List<Pair<Long, String>>>(emptyList())
        private set

    private val urlRx = Regex("""(?i)\b(https?|smb|rtmp|rtsp|udp|stalker(-play)?)://\S+""")

    /** Adressen aus einer Meldung entfernen (könnten Zugangsdaten enthalten). */
    fun clean(msg: String): String = urlRx.replace(msg, "<Adresse>").take(160)

    fun report(msg: String) {
        val m = clean(msg)
        val now = System.currentTimeMillis()
        lastError = m
        lastErrorAt = now
        errorCount++
        history = (listOf(now to m) + history).take(MAX)
    }

    fun clear() { history = emptyList(); lastError = null; errorCount = 0 }

    const val MAX = 50
}

/**
 * Diagnose an den Betreiber senden (Wunsch des Nutzers, 2026-11-09; Schnittstelle: docs/panel-schnittstelle.md).
 * Gerät, Android, Bildschirm-Modi, zuletzt gemessene Bildrate, AFR, Fehler und App-Version an das Panel (Adresse aus
 * der Panel-Konfiguration). Dazu der Xtream-Benutzername, damit der Betreiber den Nutzer zuordnen kann –
 * KEIN Passwort, keine Server- oder Stream-Adressen.
 */
object DiagSend {
    /** Zuletzt im Player gemessene Bildrate (siehe FpsProbe). */
    @Volatile var lastFps = 0

    fun available(): Boolean = Presets.diagUrl != null

    private fun modes(c: android.content.Context): List<String> = runCatching {
        val d = if (android.os.Build.VERSION.SDK_INT >= 30) c.display
            else @Suppress("DEPRECATION") (c.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        d!!.supportedModes.map { "${it.physicalWidth}x${it.physicalHeight}@${Math.round(it.refreshRate * 100) / 100.0}" }.distinct()
    }.getOrDefault(emptyList())

    /** Sendet und liefert die kurze Nummer des Berichts (für Rückfragen beim Betreiber). */
    suspend fun send(c: android.content.Context, note: String = ""): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val url = Presets.diagUrl ?: error("Diagnose ist für diese App nicht eingerichtet")
            val pi = c.packageManager.getPackageInfo(c.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
            val pl = PlaylistStore.lists.getOrNull(PlaylistStore.active)
            val user = pl?.url?.let { LoadLogic.xtreamParts(it)?.second }.orEmpty()
            val items = PlaylistStore.channels
            val crash = CrashLog.last(c)
            val afrModes = listOf("nahtlos", "Anzeigemodus wechseln")
            val j = org.json.JSONObject()
                .put("edition", BuildConfig.EDITION).put("app", pi.versionName ?: "?").put("versionCode", code)
                .put("device", (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim())
                .put("android", android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
                .put("modes", org.json.JSONArray(modes(c)))
                .put("fps", lastFps)
                .put("afr", if (!AppSettings.autoFps) "aus" else afrModes.getOrElse(Tm.i(Tm.PB_AFR_MODE, 1)) { "?" })
                .put("lastError", Diag.lastError ?: "")
                .put("errors", org.json.JSONArray(Diag.history.take(20).map { java.text.SimpleDateFormat("dd.MM. HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date(it.first)) + "  " + it.second }))
                .put("crash", crash?.first ?: "")
                .put("content", "${items.count { it.kind == Kind.LIVE }} Sender, ${items.count { it.kind == Kind.MOVIE }} Filme, ${items.count { it.kind == Kind.SERIES }} Serien; EPG ${Xmltv.status} (${Xmltv.channelCount})")
                .put("memoryMb", Mem.totalMb(c))
                .put("user", user).put("member", Member.name).put("server", pl?.name.orEmpty())
                .put("note", note.take(500)).put("ts", System.currentTimeMillis() / 1000)
            val body = j.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            Net.base.newCall(okhttp3.Request.Builder().url(url).post(body).build()).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("Fehler ${r.code}")
                val o = runCatching { org.json.JSONObject(txt) }.getOrNull()
                if (o?.optBoolean("ok") != true) error("Panel hat abgelehnt")
                o.optString("id", "").ifEmpty { "ok" }
            }
        }
    }

}

package com.ipvio.stream

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Nachricht des Betreibers an genau diesen Nutzer. */
data class MemberMsg(val id: String, val title: String, val text: String, val ts: Long)

/** Reine Logik ohne Android (Unit-Tests). */
object MemberLogic {
    private val VALID = Regex("""^[a-z0-9._=\-/]{2,64}$""")

    /** Element-Namen vereinheitlichen: „@vojadsvv_xy:matrix.tld“ → „vojadsvv_xy“, klein; null = ungültig. */
    fun norm(raw: String): String? {
        var s = raw.trim().removePrefix("@")
        if (':' in s) s = s.substringBefore(':')
        s = s.trim().lowercase()
        return s.takeIf { VALID.matches(it) }
    }

    /** Stichtag „2026-11-20“ aus dem Panel als Zeitpunkt (Tagesbeginn, lokale Zeit); null = keiner/ungültig. */
    fun parseDay(s: String?): Long? = runCatching {
        if (s.isNullOrBlank()) return null
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { isLenient = false }
        f.parse(s.trim())!!.time
    }.getOrNull()

    /** Ab wann die Eingabe Pflicht ist: Stichtag des Panels, sonst 7 Tage nach dem ersten Hinweis. */
    fun deadline(panelDay: Long?, firstSeen: Long): Long = panelDay ?: (firstSeen + GRACE_MS)

    const val GRACE_MS = 7L * 24 * 3600 * 1000

    fun parseMessages(body: String): List<MemberMsg> = runCatching {
        val a = JSONObject(body).optJSONArray("messages") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "").trim()
            val text = o.optString("text", "").trim()
            if (id.isEmpty() || text.isEmpty()) null
            else MemberMsg(id, o.optString("title", "").trim(), text.take(2000), o.optLong("ts", 0L))
        }
    }.getOrDefault(emptyList())
}

/**
 * Element-Benutzername (Wunsch des Nutzers, 2026-11-09): Bei XY TV und Township TV muss jeder Nutzer seinen
 * Element-Namen eingeben, den das Panel kennt. Damit ordnet der Betreiber Diagnosen und Sicherungen dem Nutzer zu
 * und kann ihm Nachrichten in die App schicken. Erst Hinweis, ab dem Stichtag Pflicht.
 * Die Original-App hat kein Panel: dort gibt es das nicht. Schnittstelle: docs/panel-schnittstelle.md, Abschnitt 4.
 */
object Member {
    /** Bestätigter Name (leer = keiner). */
    var name by mutableStateOf("")
        private set
    /** Eingabefenster offen. */
    var asking by mutableStateOf(false)
    /** Ungelesene Nachrichten, älteste zuerst. */
    var inbox by mutableStateOf<List<MemberMsg>>(emptyList())
        private set

    private var ready = false
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ipvio_member", Context.MODE_PRIVATE)

    fun required(): Boolean = Presets.memberUrl != null

    /** Vom Panel abgelehnt (fremder Name, Test abgelaufen): sofort Pflicht, ohne „Später“. Grund steht in [lastError]. */
    var blocked by mutableStateOf(false)
        private set
    var lastError by mutableStateOf("")
        private set

    /** Eingabe ist jetzt Pflicht (Stichtag vorbei oder vom Panel abgelehnt, noch kein bestätigter Name). */
    fun mandatory(c: Context): Boolean {
        if (!required() || name.isNotEmpty()) return false
        if (blocked) return true
        val p = prefs(c)
        var first = p.getLong("firstSeen", 0L)
        if (first == 0L) { first = System.currentTimeMillis(); p.edit().putLong("firstSeen", first).apply() }
        return System.currentTimeMillis() >= MemberLogic.deadline(MemberLogic.parseDay(Presets.memberFrom), first)
    }

    fun deadlineText(c: Context): String {
        val first = prefs(c).getLong("firstSeen", System.currentTimeMillis())
        val d = MemberLogic.deadline(MemberLogic.parseDay(Presets.memberFrom), first)
        return java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(d))
    }

    fun init(c: Context) {
        if (ready) return
        ready = true
        name = prefs(c).getString("name", "").orEmpty()
        // Sperre (fremder Name/Testende) gilt auch nach einem Neustart weiter
        blocked = prefs(c).getBoolean("blocked", false)
        lastError = prefs(c).getString("lastError", "").orEmpty()
    }

    /** Zufällige Gerätenummer (nur zur Unterscheidung mehrerer Geräte eines Nutzers im Panel). */
    private fun device(c: Context): String {
        val p = prefs(c)
        return p.getString("device", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    /** Ablehnung des Panels: [code] (unknown, mismatch, bound, trial_expired, auth) und optional eigener Hinweistext. */
    private class Rejected(val code: String, val text: String = "") : Exception(code)

    private fun post(c: Context, j: JSONObject): JSONObject {
        val url = Presets.memberUrl ?: error("Nicht eingerichtet")
        val pi = runCatching { c.packageManager.getPackageInfo(c.packageName, 0) }.getOrNull()
        j.put("edition", BuildConfig.EDITION).put("app", pi?.versionName ?: "?").put("device", device(c))
            .put("model", (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim())
        val body = j.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        Net.withTimeouts(10, 15).newCall(okhttp3.Request.Builder().url(url).post(body).build()).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (r.code == 404) throw Rejected("unknown")
            if (r.code == 429) error("Zu viele Versuche, später erneut")
            if (!r.isSuccessful) error("Fehler ${r.code}")
            val o = runCatching { JSONObject(txt) }.getOrNull() ?: error("Antwort unlesbar")
            if (!o.optBoolean("ok")) throw Rejected(o.optString("error", "unknown"), o.optString("message", "").trim().take(300))
            return o
        }
    }

    /** Xtream-Benutzername der aktiven Playlist (hilft dem Betreiber bei der Zuordnung), sonst leer. */
    private fun xtreamUser(): String =
        PlaylistStore.lists.getOrNull(PlaylistStore.active)?.url?.let { LoadLogic.xtreamParts(it)?.second }.orEmpty()

    /**
     * (Server, Benutzer, Passwort) eines Zugangs auf einem Server des Anbieters: der aktive, sonst der erste.
     * null = kein solcher Zugang (dann gibt es nichts zu prüfen und die App fragt nicht).
     */
    fun presetCreds(): Triple<String, String, String>? {
        val lists = PlaylistStore.lists.toList()
        val order = listOfNotNull(lists.getOrNull(PlaylistStore.active)) + lists
        for (pl in order) {
            val p = LoadLogic.xtreamParts(pl.url) ?: continue
            val i = PresetLogic.indexOf(p.first, Presets.servers)
            if (i >= 0) return Triple(Presets.servers[i].url, p.second, p.third)
        }
        return null
    }

    /** Ablehnungsgrund als Text für den Nutzer: Hinweis aus dem Panel ([text], z. B. „an den Verkäufer wenden“), sonst Standard. */
    fun reason(code: String, text: String = ""): String = text.ifBlank { Lang.tr(when (code) {
        "mismatch", "bound" -> "Dieser Element-Name gehört zu einem anderen Zugang. Bitte deinen eigenen Element-Namen eingeben."
        "trial_expired" -> "Der Testzeitraum ist abgelaufen. Bitte melde dich bei deinem Anbieter."
        "auth" -> "Benutzername oder Passwort stimmen nicht."
        else -> "Diesen Element-Namen kennt der Anbieter nicht. Bitte genau so eingeben, wie er in Element steht."
    }) }

    /** Gründe, bei denen die App sofort gesperrt bleibt (kein „Später“). */
    private fun hard(code: String) = code == "mismatch" || code == "bound" || code == "trial_expired"

    private fun checkJson(n: String, creds: Triple<String, String, String>?) = JSONObject().put("action", "check").put("name", n).apply {
        // Server und Zugang mitschicken: das Panel prüft, ob Element-Name und Xtream-Zugang zusammengehören
        // (Passwort nur zur Prüfung beim Server, wird im Panel nicht gespeichert – wie bei der Sicherung)
        if (creds != null) put("server", creds.first).put("user", creds.second).put("pass", creds.third) else put("user", xtreamUser())
    }

    /**
     * Namen beim Panel prüfen und speichern. [creds] = Zugang aus dem Anmeldeformular (Erstanmeldung), sonst der
     * gespeicherte Zugang beim Anbieter. Fehlertext oder null bei Erfolg.
     */
    suspend fun confirm(c: Context, raw: String, creds: Triple<String, String, String>? = presetCreds()): String? = withContext(Dispatchers.IO) {
        val n = MemberLogic.norm(raw) ?: return@withContext Lang.tr("Bitte den Element-Benutzernamen eingeben, z. B. name_xy.")
        runCatching { post(c, checkJson(n, creds)) }.fold(
            onSuccess = { o ->
                val confirmed = MemberLogic.norm(o.optString("name", n)) ?: n
                prefs(c).edit().putString("name", confirmed).putBoolean("blocked", false).remove("lastError").apply()
                withContext(Dispatchers.Main) { name = confirmed; asking = false; blocked = false; lastError = "" }
                launchPoll(c)
                null
            },
            onFailure = { e ->
                if (e is Rejected) reason(e.code, e.text)
                else Lang.tr("Anbieter gerade nicht erreichbar.") + " " + (e.message ?: "")
            }
        )
    }

    private fun launchPoll(c: Context) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { runCatching { poll(c) }; runCatching { seen(c) } }
    }

    /**
     * Beim Start: gespeicherten Namen still nachprüfen (vom Betreiber gelöscht → wieder fragen; Panel nicht erreichbar →
     * Name bleibt) und Nachrichten holen; danach alle 5 Minuten Nachrichten.
     */
    suspend fun watch(c: Context) {
        init(c)
        // Fernkonfiguration (mit der Panel-Adresse) kommt beim ersten Start erst nach ein paar Sekunden
        var waited = 0
        while (!required() && waited < 30) { delay(1000); waited++ }
        if (!required()) return
        // Playlists sind beim Start evtl. noch nicht gelesen
        delay(1500)
        if (name.isEmpty()) {
            // Nur mit einem Zugang beim Anbieter (sonst gibt es nichts zuzuordnen); vor dem Stichtag einmal am Tag,
            // danach bei jedem Start. Neue Nutzer geben den Namen schon beim Anmelden ein.
            if (presetCreds() != null && (mandatory(c) || !shownToday(c))) { markShown(c); asking = true }
        } else recheck(c)
        while (true) {
            if (name.isNotEmpty()) withContext(Dispatchers.IO) { runCatching { poll(c) } }
            delay(5 * 60_000L)
            // Hinweis-Zeit: einmal am Tag erneut anbieten; ab Stichtag Pflicht
            if (name.isEmpty() && required() && presetCreds() != null && (mandatory(c) || !shownToday(c))) { markShown(c); asking = true }
        }
    }

    /** Gespeicherten Namen still nachprüfen: abgelehnt → Name weg, wieder fragen (bei fremdem Namen/Testende sofort Pflicht). */
    private suspend fun recheck(c: Context) = withContext(Dispatchers.IO) {
        runCatching { post(c, checkJson(name, presetCreds())) }.onFailure { e ->
            if (e is Rejected && e.code != "auth") {
                val code = e.code; val msg = reason(code, e.text)
                prefs(c).edit().remove("name").putBoolean("blocked", hard(code)).putString("lastError", msg).apply()
                withContext(Dispatchers.Main) { name = ""; lastError = msg; blocked = hard(code); asking = true }
            }
        }
    }

    private fun today() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(java.util.Date())
    private fun shownToday(c: Context) = prefs(c).getString("shown", "") == today()
    private fun markShown(c: Context) = prefs(c).edit().putString("shown", today()).apply()

    private suspend fun poll(c: Context) {
        val n = name.ifEmpty { return }
        val o = post(c, JSONObject().put("action", "messages").put("name", n))
        val seen = prefs(c).getStringSet("seen", emptySet()).orEmpty()
        val list = MemberLogic.parseMessages(o.toString()).filter { it.id !in seen }.sortedBy { it.ts }
        // Persönlicher Hinweis für die Startseite (Kachel neben der Laufzeit), z. B. „Dein Paket: Sport“
        val nt = o.optJSONObject("note")?.let { j ->
            val t = j.optString("title", "").trim().take(80); val x = j.optString("text", "").trim().take(500)
            if (t.isEmpty() && x.isEmpty()) null else t to x
        }
        withContext(Dispatchers.Main) { inbox = list; note = nt }
    }

    /** Persönliche Kachel auf der Startseite (Titel, Text) aus dem Panel, null = keine. */
    var note by mutableStateOf<Pair<String, String>?>(null)
        private set

    /** Nachricht gelesen: lokal merken und dem Panel melden (dort „gelesen“). */
    fun markRead(c: Context, m: MemberMsg) {
        val p = prefs(c)
        val seen = (p.getStringSet("seen", emptySet()).orEmpty() + m.id).toList().takeLast(300).toSet()
        p.edit().putStringSet("seen", seen).apply()
        inbox = inbox.filter { it.id != m.id }
        val n = name
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching { post(c, JSONObject().put("action", "read").put("name", n).put("ids", JSONArray().put(m.id))) }
        }
    }

    /**
     * Nutzung melden (Wunsch des Nutzers, 2026-11-09): Das Panel soll sehen, wer die App benutzt – sobald sich jemand
     * angemeldet hat. Gemeldet werden die Xtream-Benutzernamen auf den Servern des Anbieters (ohne Passwort), welcher
     * aktiv ist, der Element-Name (falls bestätigt), Gerät und App-Version. Höchstens alle 6 Stunden, außer die Zugänge
     * haben sich geändert (neue Anmeldung).
     */
    suspend fun seen(c: Context) {
        if (!required()) return
        init(c)
        val accounts = JSONArray()
        var activeUser = ""
        PlaylistStore.lists.toList().forEachIndexed { i, pl ->
            val p = LoadLogic.xtreamParts(pl.url) ?: return@forEachIndexed
            val k = PresetLogic.indexOf(p.first, Presets.servers)
            if (k < 0) return@forEachIndexed
            accounts.put(JSONObject().put("server", Presets.servers[k].name).put("user", p.second))
            if (i == PlaylistStore.active) activeUser = p.second
        }
        if (accounts.length() == 0) return
        val sig = accounts.toString() + "|" + activeUser + "|" + name + "|" + BuildConfig.VERSION_CODE
        val p = prefs(c)
        if (p.getString("seenSig", "") == sig && System.currentTimeMillis() - p.getLong("seenAt", 0L) < 6 * 3600_000L) return
        withContext(Dispatchers.IO) {
            runCatching {
                post(c, JSONObject().put("action", "seen").put("name", name).put("user", activeUser).put("accounts", accounts)
                    .put("android", android.os.Build.VERSION.RELEASE).put("versionCode", BuildConfig.VERSION_CODE))
                p.edit().putString("seenSig", sig).putLong("seenAt", System.currentTimeMillis()).apply()
            }
        }
    }

    /** Name zurücksetzen (Einstellungen → „Element-Name ändern“). */
    fun change() { asking = true }
}

/** Eingabe des Element-Namens, als eigenes Fenster über allem. Vor dem Stichtag mit „Später“, danach Pflicht. */
@Composable
fun MemberGate() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mandatory = remember { Member.mandatory(ctx) }
    var text by remember { mutableStateOf(Member.name) }
    var err by remember { mutableStateOf(Member.lastError) }
    var busy by remember { mutableStateOf(false) }
    val fieldFr = remember { FocusRequester() }
    val okFr = remember { FocusRequester() }
    fun close() { if (!mandatory || Member.name.isNotEmpty()) Member.asking = false else (ctx as? android.app.Activity)?.finish() }
    fun submit() {
        if (busy) return
        busy = true; err = ""
        scope.launch { err = Member.confirm(ctx, text).orEmpty(); busy = false }
    }
    LaunchedEffect(Unit) { delay(150); runCatching { fieldFr.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BackHandler { close() }
        Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(DS.RadiusL)
            Column(
                Modifier.widthIn(max = 720.dp).clip(shape).background(Surf).border(1.dp, Surf2, shape).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Element-Benutzername", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Gib deinen Benutzernamen aus Element ein, z. B. name_xy. Damit kann dir dein Anbieter Nachrichten in die App schicken und dir bei Problemen helfen.",
                    color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
                if (!mandatory && Member.name.isEmpty()) Text(raw = true, text = Lang.tr("Ab dem") + " " + Member.deadlineText(ctx) + " " + Lang.tr("ist die Eingabe nötig, um die App weiter zu nutzen."),
                    color = Color(0xFFFFB86B), fontSize = 15.sp)
                Field(text, { text = it; err = "" }, "name_xy", Modifier.height(56.dp), Modifier.focusRequester(fieldFr), onDone = { runCatching { okFr.requestFocus() } })
                if (err.isNotEmpty()) Text(raw = true, text = err, color = DS.Danger, fontSize = 15.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(if (busy) "Prüfe …" else "Bestätigen", Modifier.focusRequester(okFr), light = true) { submit() }
                    if (!mandatory || Member.name.isNotEmpty()) PillButton(if (Member.name.isNotEmpty()) "Abbrechen" else "Später") { close() }
                    else PillButton("App beenden") { close() }
                }
            }
        }
    }
}

/** Nachricht des Betreibers als Karte oben (wie der Update-Hinweis). */
@Composable
fun MemberMsgCard(m: MemberMsg, modifier: Modifier) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    BackHandler { Member.markRead(ctx, m) }
    LaunchedEffect(m.id) { delay(150); runCatching { fr.requestFocus() } }
    val shape = RoundedCornerShape(DS.RadiusL)
    Row(
        modifier.widthIn(max = 860.dp).clip(shape).background(Tok.GlassStrong).border(1.dp, Accent2, shape).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppIcon(AIcon.Info, Accent2, size = 30.dp)
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(raw = true, text = m.title.ifEmpty { Lang.tr("Nachricht von deinem Anbieter") }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(raw = true, text = m.text, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
        PillButton("OK", Modifier.focusRequester(fr), light = true) { Member.markRead(ctx, m) }
    }
}

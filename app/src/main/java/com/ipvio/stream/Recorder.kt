package com.ipvio.stream

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Aufnahmen (TiviMate): sofort, geplant aus dem TV-Guide oder als eigene Aufnahme (mit Wiederholung).
 * Die Aufnahme schreibt den Live-Stream unverändert in eine .ts-Datei – lokal, auf USB oder auf eine SMB-Freigabe.
 */
data class Rec(
    val id: String,
    val chId: String,
    val chTitle: String,
    val url: String,
    val title: String,
    val start: Long,            // Sekunden
    val end: Long,
    val repeat: Int = 0,        // 0 einmalig, 1 täglich, 2 wöchentlich
    val state: Int = PLANNED,
    val file: String = "",
    val size: Long = 0L,
    val msg: String = "",
) {
    companion object {
        const val PLANNED = 0
        const val RUNNING = 1
        const val DONE = 2
        const val FAILED = 3
    }

    fun json(): JSONObject = JSONObject().put("id", id).put("ch", chId).put("ct", chTitle).put("u", url).put("t", title)
        .put("s", start).put("e", end).put("r", repeat).put("st", state).put("f", file).put("z", size).put("m", msg)
}

object Recorder {
    const val ACTION_ALARM = "com.ipvio.stream.REC_ALARM"
    const val ACTION_STOP = "com.ipvio.stream.REC_STOP"
    const val EXTRA_ID = "rec_id"

    val list = mutableStateListOf<Rec>()
    /** Steigt bei jeder Änderung (Größe laufender Aufnahmen usw.). */
    var version by mutableIntStateOf(0)
        private set
    @Volatile private var inited = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ipvio_recordings", Context.MODE_PRIVATE)

    @Synchronized
    fun init(c: Context) {
        if (inited) return
        inited = true
        runCatching { Tm.init(c) }
        val arr = runCatching { JSONArray(prefs(c).getString("l", "[]")) }.getOrNull() ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                Rec(
                    o.optString("id"), o.optString("ch"), o.optString("ct"), o.optString("u"), o.optString("t"),
                    o.optLong("s"), o.optLong("e"), o.optInt("r"),
                    // Beim Start der App läuft keine Aufnahme mehr (Gerät neu gestartet / App beendet): abgebrochen
                    o.optInt("st").let { if (it == Rec.RUNNING) Rec.FAILED else it },
                    o.optString("f"), o.optLong("z"), o.optString("m")
                )
            )
        }
    }

    @Synchronized
    private fun save(c: Context) {
        val a = JSONArray()
        list.forEach { a.put(it.json()) }
        prefs(c).edit().putString("l", a.toString()).apply()
        version++
    }

    @Synchronized
    fun update(c: Context, id: String, f: (Rec) -> Rec) {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = f(list[i])
        save(c)
    }

    fun before(): Long = Tm.i(Tm.REC_BEFORE, 0) * 60L
    fun after(): Long = Tm.i(Tm.REC_AFTER, 0) * 60L

    /** Neue Aufnahme anlegen. Läuft das Fenster schon, startet sie sofort. */
    fun add(c: Context, ch: Item, title: String, start: Long, end: Long, repeat: Int = 0): Rec {
        init(c)
        val r = Rec(
            id = System.currentTimeMillis().toString(36) + (0..999).random(),
            chId = ch.id, chTitle = ChannelEdit.title(ch), url = ch.url, title = title.ifBlank { ChannelEdit.title(ch) },
            start = start, end = end, repeat = repeat
        )
        synchronized(this) { list.add(r); save(c) }
        schedule(c)
        return r
    }

    /** Sofort aufnehmen: laufende Sendung bis zum Ende, sonst eine Stunde (TiviMate). */
    fun recordNow(c: Context, ch: Item): Rec {
        val now = System.currentTimeMillis() / 1000
        val e = Xmltv.forItem(ch).firstOrNull { it.start <= now && it.end > now }
        return if (e != null) add(c, ch, e.title, now, e.end) else add(c, ch, ChannelEdit.title(ch), now, now + 3600)
    }

    fun isPlanned(chId: String, start: Long): Rec? = list.firstOrNull { it.chId == chId && it.start == start && (it.state == Rec.PLANNED || it.state == Rec.RUNNING) }
    fun runningFor(chId: String): Rec? = list.firstOrNull { it.chId == chId && it.state == Rec.RUNNING }

    /** Geplante abbrechen bzw. laufende stoppen (die Datei bleibt). */
    fun stop(c: Context, id: String) {
        val r = list.firstOrNull { it.id == id } ?: return
        if (r.state == Rec.RUNNING) {
            RecService.cancel(id)
            update(c, id) { it.copy(state = Rec.DONE, end = System.currentTimeMillis() / 1000, repeat = 0) }
        } else if (r.state == Rec.PLANNED) {
            synchronized(this) { list.removeAll { it.id == id }; save(c) }
        }
        schedule(c)
    }

    /** Aufnahme samt Datei löschen. */
    fun delete(c: Context, id: String) {
        val r = list.firstOrNull { it.id == id } ?: return
        if (r.state == Rec.RUNNING) RecService.cancel(id)
        if (r.file.isNotEmpty()) Thread { runCatching { RecStore.delete(r.file) } }.start()
        synchronized(this) { list.removeAll { it.id == id }; save(c) }
        schedule(c)
    }

    /** Nächsten Wecker stellen (Beginn minus Vorlauf) und fällige Aufnahmen gleich starten. */
    fun schedule(c: Context) {
        init(c)
        val now = System.currentTimeMillis() / 1000
        val due = list.filter { it.state == Rec.PLANNED && it.start - before() <= now && it.end + after() > now }
        // Abgelaufene geplante (Gerät war aus): als verpasst markieren bzw. bei Wiederholung weiterschieben
        list.filter { it.state == Rec.PLANNED && it.end + after() <= now }.forEach { r ->
            if (r.repeat > 0) update(c, r.id) { advance(it, now) } else update(c, r.id) { it.copy(state = Rec.FAILED, msg = "Verpasst") }
        }
        if (due.isNotEmpty() || list.any { it.state == Rec.RUNNING }) RecService.start(c)
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            c.applicationContext, 4712, Intent(c.applicationContext, RecReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = list.filter { it.state == Rec.PLANNED && it.start - before() > now }.minOfOrNull { it.start - before() }
        if (next == null) { am.cancel(pi); return }
        val at = next * 1000L
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    /** Wiederholung: nächster Termin (täglich/wöchentlich) nach [now]. */
    fun advance(r: Rec, now: Long): Rec {
        val step = if (r.repeat == 2) 7 * 86400L else 86400L
        var s = r.start; var e = r.end
        while (e + after() <= now) { s += step; e += step }
        return r.copy(start = s, end = e, state = Rec.PLANNED, file = "", size = 0, msg = "")
    }

    /** Nach dem Ende einer Wiederholung die nächste Folge als neue geplante Aufnahme anlegen. */
    fun planNextRepeat(c: Context, done: Rec) {
        if (done.repeat == 0) return
        val now = System.currentTimeMillis() / 1000
        val next = advance(done, now).copy(id = System.currentTimeMillis().toString(36) + (0..999).random())
        synchronized(this) {
            list.add(next)
            val i = list.indexOfFirst { it.id == done.id }
            if (i >= 0) list[i] = list[i].copy(repeat = 0)
            save(c)
        }
    }
}

/** Wecker: Aufnahme beginnt. */
class RecReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        runCatching { General.init(c) }
        runCatching { Recorder.schedule(c) }
    }
}

/**
 * Vordergrund-Dienst für laufende Aufnahmen. Jede Aufnahme liest den Stream bis Ende + Nachlauf und schreibt ihn weg;
 * bei Abbrüchen verbindet sie sich neu. HLS-Streams werden Segment für Segment angehängt.
 */
class RecService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val CHANNEL = "recordings"
        private const val NOTE_ID = 4713
        private val jobs = ConcurrentHashMap<String, Job>()
        @Volatile private var running: RecService? = null

        fun start(c: Context) {
            val i = Intent(c.applicationContext, RecService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) c.applicationContext.startForegroundService(i) else c.applicationContext.startService(i)
            }
        }

        fun cancel(id: String) { jobs.remove(id)?.cancel() }
        fun isActive(id: String) = jobs[id]?.isActive == true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
        startFg()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm?.createNotificationChannel(NotificationChannel(CHANNEL, Lang.tr("Aufnahmen"), NotificationManager.IMPORTANCE_LOW))
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val n = b.setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(Lang.tr("Aufnahme läuft"))
            .setContentText(Recorder.list.filter { it.state == Rec.RUNNING }.joinToString(", ") { it.title })
            .setOngoing(true)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTE_ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        Recorder.init(this)
        val now = System.currentTimeMillis() / 1000
        val due = Recorder.list.filter { it.state == Rec.PLANNED && it.start - Recorder.before() <= now && it.end + Recorder.after() > now }
        for (r in due) if (!jobs.containsKey(r.id)) launchRec(r)
        stopIfIdle()
        return START_STICKY
    }

    private fun stopIfIdle() {
        scope.launch {
            delay(2000)
            if (jobs.isEmpty()) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
            }
        }
    }

    private fun launchRec(r: Rec) {
        val ctx = applicationContext
        // Max. Verbindungen der Wiedergabeliste (TiviMate): laufende Aufnahmen + Player derselben Liste zählen mit
        val list = PlaylistStore.playlistOf(r.chId)
        val max = PlOpts.maxConn(list)
        if (max > 0) {
            val recs = Recorder.list.count { it.state == Rec.RUNNING && it.id != r.id && PlaylistStore.playlistOf(it.chId) == list }
            val player = AppNav.playingId?.let { id -> if (AppNav.playing && PlaylistStore.playlistOf(id) == list) 1 else 0 } ?: 0
            if (recs + player >= max) {
                Recorder.update(ctx, r.id) { it.copy(state = Rec.FAILED, msg = "Verbindungslimit erreicht") }
                Recorder.list.firstOrNull { it.id == r.id }?.let { Recorder.planNextRepeat(ctx, it) }
                return
            }
        }
        val name = RecStore.fileName(r)
        val target = RecStore.target(ctx, name)
        Recorder.update(ctx, r.id) { it.copy(state = Rec.RUNNING, file = target, msg = "") }
        startFg()
        val job = scope.launch {
            var out: OutputStream? = null
            var written = 0L
            var lastErr = ""
            try {
                out = RecStore.open(ctx, target)
                val stopAt = r.end + Recorder.after()
                val ua = TmNet.userAgentFor(r.chId)
                val url = TmNet.playUrl(Stalker.playable(r.url))
                while (isActive && System.currentTimeMillis() / 1000 < stopAt) {
                    try {
                        written += RecNet.copy(url, ua, out, stopAt) { n ->
                            written += n
                            if (written / (4 * 1024 * 1024) != (written - n) / (4 * 1024 * 1024)) {
                                Recorder.update(ctx, r.id) { it.copy(size = written) }
                            }
                        }.let { 0L }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastErr = e.message ?: e.javaClass.simpleName
                        delay(3000)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // gestoppt
            } catch (e: Exception) {
                lastErr = e.message ?: e.javaClass.simpleName
            } finally {
                runCatching { out?.flush(); out?.close() }
                val ok = written > 0
                Recorder.update(ctx, r.id) {
                    it.copy(state = if (ok) Rec.DONE else Rec.FAILED, size = written, msg = if (ok) "" else lastErr.ifEmpty { "Keine Daten" })
                }
                Recorder.list.firstOrNull { it.id == r.id }?.let { Recorder.planNextRepeat(ctx, it) }
                jobs.remove(r.id)
                Recorder.schedule(ctx)
                if (jobs.isEmpty()) { runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }; stopSelf() } else startFg()
            }
        }
        jobs[r.id] = job
    }

    override fun onDestroy() {
        running = null
        super.onDestroy()
    }
}

/** Stream lesen: direkt (TS) oder als HLS (Segmente nacheinander). */
object RecNet {
    private val client by lazy { Net.withTimeouts(20, 30) }

    /** Liest bis [stopAt] (Sekunden) oder bis der Server die Verbindung beendet. Ruft [onBytes] je Block auf. */
    fun copy(url: String, ua: String, out: OutputStream, stopAt: Long, onBytes: (Long) -> Unit): Long {
        if (url.contains(".m3u8", ignoreCase = true)) return hls(url, ua, out, stopAt, onBytes)
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP " + resp.code)
            val ct = resp.header("Content-Type").orEmpty()
            val body = resp.body ?: return 0L
            if (ct.contains("mpegurl", ignoreCase = true)) return hls(resp.request.url.toString(), ua, out, stopAt, onBytes)
            val ins = body.byteStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (System.currentTimeMillis() / 1000 < stopAt) {
                if (Thread.currentThread().isInterrupted) break
                val n = ins.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
                onBytes(n.toLong())
            }
            return total
        }
    }

    private fun get(url: String, ua: String): Pair<String, String> {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP " + r.code)
            return r.request.url.toString() to (r.body?.string() ?: "")
        }
    }

    private fun resolve(base: String, ref: String): String = runCatching { java.net.URI(base).resolve(ref.trim()).toString() }.getOrDefault(ref)

    private fun hls(url0: String, ua: String, out: OutputStream, stopAt: Long, onBytes: (Long) -> Unit): Long {
        var (url, text) = get(url0, ua)
        // Master-Playlist: beste Qualität wählen
        if (text.contains("#EXT-X-STREAM-INF")) {
            val lines = text.lines()
            var best = -1L; var bestUrl: String? = null
            for (i in lines.indices) if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val u = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
                if (bw > best) { best = bw; bestUrl = resolve(url, u) }
            }
            if (bestUrl != null) { val g = get(bestUrl, ua); url = g.first; text = g.second }
        }
        val done = LinkedHashSet<String>()
        var initDone = false
        var total = 0L
        val buf = ByteArray(64 * 1024)
        while (System.currentTimeMillis() / 1000 < stopAt) {
            val lines = text.lines()
            var target = 6L
            for (l in lines) {
                if (l.startsWith("#EXT-X-TARGETDURATION:")) target = l.substringAfter(':').trim().toLongOrNull() ?: 6L
                if (l.startsWith("#EXT-X-MAP:") && !initDone) {
                    val u = Regex("URI=\"([^\"]+)\"").find(l)?.groupValues?.get(1)
                    if (u != null) { total += fetch(resolve(url, u), ua, out, buf, onBytes); initDone = true }
                }
            }
            for (l in lines) {
                if (l.isBlank() || l.startsWith("#")) continue
                val seg = resolve(url, l)
                if (!done.add(seg)) continue
                total += fetch(seg, ua, out, buf, onBytes)
                if (System.currentTimeMillis() / 1000 >= stopAt) return total
            }
            if (text.contains("#EXT-X-ENDLIST")) return total
            while (done.size > 500) done.remove(done.first())
            Thread.sleep((target * 1000 / 2).coerceIn(1000, 6000))
            text = get(url, ua).second
        }
        return total
    }

    private fun fetch(u: String, ua: String, out: OutputStream, buf: ByteArray, onBytes: (Long) -> Unit): Long {
        val req = Request.Builder().url(u).header("User-Agent", ua).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return 0L
            val ins = r.body?.byteStream() ?: return 0L
            var t = 0L
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                out.write(buf, 0, n); t += n; onBytes(n.toLong())
            }
            return t
        }
    }
}

/** Ablage der Aufnahmen: App-Ordner auf dem Gerät/USB oder SMB-Freigabe (smb://server/freigabe/ordner). */
object RecStore {
    fun isSmb(path: String) = path.startsWith("smb://", ignoreCase = true)

    /** Standardordner: interner Speicher der App. */
    fun defaultDir(c: Context): File = File(c.getExternalFilesDir(null) ?: c.filesDir, "Recordings")

    /** Mögliche lokale Ziele: intern und jedes eingesteckte Laufwerk (USB/SD), jeweils im App-Ordner. */
    fun localDirs(c: Context): List<File> =
        c.getExternalFilesDirs(null).filterNotNull().map { File(it, "Recordings") }.ifEmpty { listOf(defaultDir(c)) }

    fun dir(c: Context): String = Tm.s(Tm.REC_DIR).trim().ifEmpty { defaultDir(c).absolutePath }

    fun fileName(r: Rec): String {
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.ROOT).format(java.util.Date(r.start * 1000))
        val base = (r.title + " - " + r.chTitle + " - " + ts).replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").take(150)
        return "$base.ts"
    }

    fun target(c: Context, name: String): String {
        val d = dir(c).trimEnd('/')
        return "$d/$name"
    }

    fun open(c: Context, path: String): OutputStream {
        if (isSmb(path)) return Smb.output(path)
        val f = File(path)
        f.parentFile?.mkdirs()
        return java.io.BufferedOutputStream(java.io.FileOutputStream(f), 256 * 1024)
    }

    fun delete(path: String) {
        if (isSmb(path)) Smb.delete(path) else File(path).delete()
    }

    /** Adresse zum Abspielen (lokal file://, SMB über den eigenen Datenquellen-Zweig im Player). */
    fun playUrl(path: String): String = if (isSmb(path)) path else "file://$path"

    /** Freier Speicher am Ziel (lokal), -1 = unbekannt. */
    fun free(c: Context): Long {
        val d = dir(c)
        if (isSmb(d)) return -1
        return runCatching { File(d).also { it.mkdirs() }.usableSpace }.getOrDefault(-1)
    }
}

package com.ipvio.stream

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sicherung der persönlichen Daten (Einstellungen, Favoriten, Verlauf, Profile) in eine Datei.
 * Playlists und Zugangsdaten sind absichtlich nicht enthalten: Sie bleiben auf dem Gerät.
 */
object Backup {
    private val NAMES = listOf(
        "ipvio_general", "ipvio_settings", "ipvio_lib", "ipvio_quality", "ipvio_hist", "ipvio_watch",
        "ipvio_catfavs", "ipvio_variants", "ipvio_guide", "ipvio_browse", "ipvio_cat", "ipvio_lang",
        "ipvio_chlists", "ipvio_tracks", "ipvio_eprog"
    )

    /** Größer wird eine echte Sicherung nie; größere Dateien werden gar nicht erst gelesen. */
    private const val MAX_BYTES = 4L * 1024 * 1024

    fun file(c: Context): File = File(c.getExternalFilesDir(null) ?: c.filesDir, "ipvio_backup.json")

    private fun fileNames(c: Context): List<String> {
        Profiles.init(c)
        return listOf(Profiles.META) + Profiles.list.flatMap { p -> NAMES.map { Profiles.scopedName(it, p.id) } }
    }

    /** Schreibt die Sicherung und gibt die Datei zurück (oder null bei Fehler). */
    fun export(c: Context): File? = runCatching {
        val root = JSONObject().put("v", 1).put("at", System.currentTimeMillis())
        val files = JSONObject()
        for (n in fileNames(c)) {
            val all = c.applicationContext.getSharedPreferences(n, Context.MODE_PRIVATE).all
            if (all.isEmpty()) continue
            val o = JSONObject()
            for ((k, v) in all) {
                when (v) {
                    is String -> o.put(k, JSONArray().put("s").put(v))
                    is Boolean -> o.put(k, JSONArray().put("b").put(v))
                    is Int -> o.put(k, JSONArray().put("i").put(v))
                    is Long -> o.put(k, JSONArray().put("l").put(v))
                    is Float -> o.put(k, JSONArray().put("f").put(v.toDouble()))
                    is Set<*> -> o.put(k, JSONArray().put("t").put(JSONArray(v.map { it.toString() })))
                }
            }
            files.put(n, o)
        }
        root.put("files", files)
        val f = file(c)
        f.parentFile?.mkdirs()
        f.writeText(root.toString())
        f
    }.getOrNull()

    /** Liest die Sicherung zurück. Gibt die Zahl der wiederhergestellten Dateien zurück, -1 bei Fehler. */
    fun restore(c: Context): Int = runCatching {
        val f = file(c)
        if (!f.exists() || f.length() > MAX_BYTES) return -1
        val root = JSONObject(f.readText())
        if (root.optInt("v", 0) != 1) return -1
        val files = root.getJSONObject("files")
        var count = 0
        for (n in files.keys()) {
            // Nur bekannte Dateien zulassen
            if (n != Profiles.META && NAMES.none { n == it || n.startsWith(it + "_p") }) continue
            val o = files.getJSONObject(n)
            val e = c.applicationContext.getSharedPreferences(n, Context.MODE_PRIVATE).edit().clear()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                when (a.getString(0)) {
                    "s" -> e.putString(k, a.getString(1))
                    "b" -> e.putBoolean(k, a.getBoolean(1))
                    "i" -> e.putInt(k, a.getInt(1))
                    "l" -> e.putLong(k, a.getLong(1))
                    "f" -> e.putFloat(k, a.getDouble(1).toFloat())
                    "t" -> { val arr = a.getJSONArray(1); e.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet()) }
                }
            }
            e.commit()
            count++
        }
        count
    }.getOrDefault(-1)
}

/** Backup auf SMB-Verbindungen und Wiederherstellen aus einem Link (TiviMate: Sichern/Wiederherstellen). */
object BackupRemote {
    private const val NAME = "ipvio_backup.json"

    /** Sicherung auf eine SMB-Verbindung schreiben. null = gut, sonst Fehlertext. */
    fun saveToSmb(c: Context, dir: String): String? = runCatching {
        val f = Backup.export(c) ?: return "Sicherung fehlgeschlagen."
        Smb.output(dir.trimEnd('/') + "/" + NAME).use { out -> f.inputStream().use { it.copyTo(out) } }
        null
    }.getOrElse { it.message ?: it.javaClass.simpleName }

    /** Sicherung von einer SMB-Verbindung holen und einspielen. */
    fun loadFromSmb(c: Context, dir: String): Int = runCatching {
        val raf = Smb.openRead(dir.trimEnd('/') + "/" + NAME)
        val bytes = ByteArray(raf.length().toInt().coerceAtMost(8 * 1024 * 1024))
        raf.readFully(bytes); raf.close()
        Backup.file(c).writeBytes(bytes)
        Backup.restore(c)
    }.getOrDefault(-1)

    /** Freigabe-Links in direkte Download-Adressen umwandeln (Dropbox, Google Drive). */
    fun directLink(u: String): String {
        val s = u.trim()
        Regex("""drive\.google\.com/file/d/([^/]+)""").find(s)?.let { return "https://drive.google.com/uc?export=download&id=" + it.groupValues[1] }
        if (s.contains("dropbox.com")) return s.replace("?dl=0", "?dl=1").let { if (it.contains("dl=1")) it else if (it.contains("?")) "$it&dl=1" else "$it?dl=1" }
        return s
    }

    /** Sicherung aus einem Link laden und einspielen. */
    fun loadFromLink(c: Context, url: String): Int = runCatching {
        val req = okhttp3.Request.Builder().url(directLink(url)).header("User-Agent", TmNet.userAgent()).build()
        Net.withTimeouts(15, 30).newCall(req).execute().use { r ->
            if (!r.isSuccessful) return -1
            val body = r.body?.bytes() ?: return -1
            if (body.size > 8 * 1024 * 1024) return -1
            Backup.file(c).writeBytes(body)
        }
        Backup.restore(c)
    }.getOrDefault(-1)
}

package com.ipvio.stream

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.OutputStream

/**
 * SMB-Netzwerkfreigabe (TiviMate: Aufnahmeverzeichnis „smb://192.168.2.9/Freigabe/Ordner“).
 * Anmeldung mit Benutzer/Passwort aus den Aufnahme-Einstellungen (leer = Gast).
 */
object Smb {
    /** Anmeldung für einen Pfad: die gespeicherte Verbindung, deren Adresse am besten passt (sonst Gast). */
    private fun cifs(path: String): jcifs.CIFSContext {
        val base = jcifs.context.SingletonContext.getInstance()
        val c = SmbConns.forPath(path)
        val user = c?.user?.trim().orEmpty()
        if (user.isEmpty()) return base.withGuestCrendentials()
        val sep = if (user.contains('\\')) '\\' else '/'
        val dom = if (user.contains(sep)) user.substringBefore(sep) else ""
        val name = user.substringAfter(sep)
        return base.withCredentials(jcifs.smb.NtlmPasswordAuthenticator(dom, name, c?.pass.orEmpty()))
    }

    private fun file(path: String) = jcifs.smb.SmbFile(path, cifs(path))

    fun output(path: String): OutputStream {
        val f = file(path)
        runCatching { val p = jcifs.smb.SmbFile(f.parent, cifs(path)); if (!p.exists()) p.mkdirs() }
        return java.io.BufferedOutputStream(jcifs.smb.SmbFileOutputStream(f), 256 * 1024)
    }

    fun delete(path: String) { runCatching { file(path).delete() } }

    /** Verbindung prüfen: Ordner anlegen/lesen. null = in Ordnung, sonst Fehlertext. */
    fun test(dir: String): String? = runCatching {
        val d = jcifs.smb.SmbFile(dir.trimEnd('/') + "/", cifs(dir))
        if (!d.exists()) d.mkdirs()
        d.list()
        null
    }.getOrElse { it.message ?: it.javaClass.simpleName }

    fun openRead(path: String): jcifs.smb.SmbRandomAccessFile = jcifs.smb.SmbRandomAccessFile(file(path), "r")
}

/** Datenquelle für den Player: smb://… lesen, alles andere an die normale Quelle weiterreichen. */
@UnstableApi
class SmbAwareFactory(private val inner: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbAwareSource(inner.createDataSource())
}

@UnstableApi
private class SmbAwareSource(private val inner: DataSource) : DataSource {
    private var raf: jcifs.smb.SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var left = 0L
    private var useInner = false

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) = inner.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        if (dataSpec.uri.scheme?.equals("smb", true) != true) { useInner = true; return inner.open(dataSpec) }
        useInner = false
        val f = Smb.openRead(dataSpec.uri.toString())
        raf = f
        f.seek(dataSpec.position)
        val len = f.length() - dataSpec.position
        left = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, len) else len
        return left
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (useInner) return inner.read(buffer, offset, length)
        if (length == 0) return 0
        if (left <= 0) return C.RESULT_END_OF_INPUT
        val n = raf!!.read(buffer, offset, minOf(length.toLong(), left).toInt())
        if (n < 0) return C.RESULT_END_OF_INPUT
        left -= n
        return n
    }

    override fun getUri(): Uri? = if (useInner) inner.uri else uri

    override fun close() {
        if (useInner) { inner.close(); return }
        runCatching { raf?.close() }
        raf = null
    }
}

/** Eine gespeicherte SMB-Verbindung (Netzwerkfreigabe) für Aufnahmen. */
data class SmbConn(val id: String, val name: String, val url: String, val user: String, val pass: String)

/** Mehrere SMB-Verbindungen (z. B. NAS und PC); jede kann als Aufnahmeverzeichnis gewählt werden. */
object SmbConns {
    private const val KEY = "rec_smb_conns"
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun list(): List<SmbConn> {
        version // Lesen merken (Compose zeichnet neu)
        val arr = runCatching { org.json.JSONArray(Tm.s(KEY, "[]")) }.getOrNull() ?: org.json.JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o -> SmbConn(o.optString("id"), o.optString("n"), o.optString("u"), o.optString("us"), o.optString("p")) }
        }
    }

    private fun save(c: android.content.Context, l: List<SmbConn>) {
        val a = org.json.JSONArray()
        l.forEach { a.put(org.json.JSONObject().put("id", it.id).put("n", it.name).put("u", it.url).put("us", it.user).put("p", it.pass)) }
        Tm.set(c, KEY, a.toString())
        version++
    }

    /** Ältere Einstellung (eine Verbindung mit Benutzer/Passwort) einmalig übernehmen. */
    fun migrate(c: android.content.Context) {
        if (Tm.s(KEY).isNotEmpty()) return
        val dir = Tm.s(Tm.REC_DIR).trim()
        val l = if (RecStore.isSmb(dir)) listOf(SmbConn(newId(), hostOf(dir), dir, Tm.s(Tm.REC_SMB_USER), Tm.s(Tm.REC_SMB_PASS))) else emptyList()
        save(c, l)
    }

    fun newId(): String = System.currentTimeMillis().toString(36) + (0..999).random()
    fun hostOf(url: String): String = norm(url).substring(6)

    /** Eingabe vereinheitlichen: genau ein „smb://“ vorn (auch wenn es doppelt getippt wurde), Backslashes zu /, ohne / am Ende. */
    fun norm(u: String): String = "smb://" + u.trim().replace('\\', '/').replace(Regex("^(smb:/+)+", RegexOption.IGNORE_CASE), "").trimStart('/').trimEnd('/')

    fun upsert(c: android.content.Context, conn: SmbConn) {
        val l = list().toMutableList()
        val i = l.indexOfFirst { it.id == conn.id }
        if (i >= 0) l[i] = conn else l.add(conn)
        save(c, l)
    }

    fun remove(c: android.content.Context, id: String) {
        val old = list().firstOrNull { it.id == id }
        save(c, list().filterNot { it.id == id })
        // War sie das Aufnahmeverzeichnis: zurück auf den Gerätespeicher
        if (old != null && Tm.s(Tm.REC_DIR).trim().equals(old.url.trim(), true)) Tm.set(c, Tm.REC_DIR, "")
    }

    /** Verbindung, deren Adresse den Pfad am längsten abdeckt (Aufnahmen auf verschiedenen Freigaben). */
    fun forPath(path: String): SmbConn? {
        val p = path.lowercase()
        return list().filter { p.startsWith(it.url.trim().trimEnd('/').lowercase()) }.maxByOrNull { it.url.length }
            ?: list().firstOrNull { p.startsWith("smb://" + hostOf(it.url).substringBefore('/').lowercase()) }
    }
}

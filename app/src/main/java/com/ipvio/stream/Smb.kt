package com.ipvio.stream

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

import java.io.OutputStream

/**
 * SMB-Netzwerkfreigabe (TiviMate: Aufnahmeverzeichnis „smb://192.168.2.9/Freigabe/Ordner“).
 * Anmeldung mit Benutzer/Passwort aus den Aufnahme-Einstellungen (leer = Gast).
 */
object Smb {
    private fun cifs(): jcifs.CIFSContext {
        val base = jcifs.context.SingletonContext.getInstance()
        val user = Tm.s(Tm.REC_SMB_USER).trim()
        if (user.isEmpty()) return base.withGuestCrendentials()
        val sep = if (user.contains('\\')) '\\' else '/'
        val dom = if (user.contains(sep)) user.substringBefore(sep) else ""
        val name = user.substringAfter(sep)
        return base.withCredentials(jcifs.smb.NtlmPasswordAuthenticator(dom, name, Tm.s(Tm.REC_SMB_PASS)))
    }

    private fun file(path: String) = jcifs.smb.SmbFile(path, cifs())

    fun output(path: String): OutputStream {
        val f = file(path)
        runCatching { val p = jcifs.smb.SmbFile(f.parent, cifs()); if (!p.exists()) p.mkdirs() }
        return java.io.BufferedOutputStream(jcifs.smb.SmbFileOutputStream(f), 256 * 1024)
    }

    fun delete(path: String) { runCatching { file(path).delete() } }

    /** Verbindung prüfen: Ordner anlegen/lesen. null = in Ordnung, sonst Fehlertext. */
    fun test(dir: String): String? = runCatching {
        val d = jcifs.smb.SmbFile(dir.trimEnd('/') + "/", cifs())
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

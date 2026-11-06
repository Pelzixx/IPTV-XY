package com.ipvio.stream

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Gespeichertes XMLTV-Programm (komprimiert im Zwischenspeicher der App). Beim nächsten Start ist das Programm sofort da,
 * statt eine große EPG-Datei erneut herunterzuladen und einzulesen. Ohne Android, testbar.
 */
object XmltvCache {
    private const val VERSION = 1
    /** Vergangene Sendungen nur so weit zurück behalten (Archiv geht höchstens 7 Tage). */
    private const val KEEP_PAST = 7 * 86400L

    /** Kennung der Quellen: ändern sich die EPG-Adressen, passt das Gespeicherte nicht mehr. */
    fun key(urls: List<String>): String = urls.joinToString("\n").hashCode().toString(16) + ":" + urls.size

    fun write(file: File, key: String, savedAt: Long, progs: Map<String, List<EpgEntry>>) {
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { write(it, key, savedAt, progs) }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun write(out: OutputStream, key: String, savedAt: Long, progs: Map<String, List<EpgEntry>>) {
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(out), 64 * 1024)).use { o ->
            o.writeInt(VERSION); o.writeUTF(key); o.writeLong(savedAt); o.writeInt(progs.size)
            for ((ch, list) in progs) {
                o.writeUTF(ch); o.writeInt(list.size)
                for (e in list) { o.writeUTF(e.title.take(500)); o.writeUTF(e.desc.take(2000)); o.writeLong(e.start); o.writeLong(e.end) }
            }
        }
    }

    /** Gespeichertes Programm (Zeitpunkt, Sender → Sendungen) oder null, wenn nichts Passendes gespeichert ist. */
    fun read(file: File, key: String, now: Long): Pair<Long, Map<String, List<EpgEntry>>>? =
        if (!file.exists()) null else file.inputStream().use { read(it, key, now) }

    fun read(input: InputStream, key: String, now: Long): Pair<Long, Map<String, List<EpgEntry>>>? {
        DataInputStream(BufferedInputStream(GZIPInputStream(input), 64 * 1024)).use { i ->
            if (i.readInt() != VERSION || i.readUTF() != key) return null
            val savedAt = i.readLong()
            val n = i.readInt()
            val pool = StringPool()
            val map = HashMap<String, List<EpgEntry>>(n * 2)
            repeat(n) {
                val ch = i.readUTF()
                val m = i.readInt()
                val l = ArrayList<EpgEntry>(m)
                repeat(m) {
                    val e = EpgEntry(pool.of(i.readUTF()), i.readUTF(), i.readLong(), i.readLong())
                    if (e.end > now - KEEP_PAST) l.add(e)
                }
                if (l.isNotEmpty()) map[ch] = l
            }
            return savedAt to map
        }
    }
}

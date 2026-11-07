package com.ipvio.stream

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

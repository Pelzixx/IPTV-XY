package com.ipvio.stream

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Ein gemeinsamer HTTP-Client für die ganze App. Abgeleitete Clients (andere Zeitlimits) teilen
 * Verbindungs-Pool und Threads, statt dass jeder Bereich eigene anlegt – spart Speicher auf schwachen Geräten.
 */
object Net {
    // Allgemein > User-Agent (TiviMate): eigener User-Agent für alle Abrufe, sonst der bisherige
    val base: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { ch ->
            val custom = Tm.s(Tm.GEN_UA).trim()
            ch.proceed(if (custom.isEmpty()) ch.request() else ch.request().newBuilder().header("User-Agent", custom).build())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun withTimeouts(connectSec: Long, readSec: Long): OkHttpClient =
        base.newBuilder().connectTimeout(connectSec, TimeUnit.SECONDS).readTimeout(readSec, TimeUnit.SECONDS).build()
}
